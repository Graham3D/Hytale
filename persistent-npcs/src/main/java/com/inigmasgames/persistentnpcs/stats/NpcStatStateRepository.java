package com.inigmasgames.persistentnpcs.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One ordered disk lane. ECS callers pass immutable data, never a map/ref or a disk callback. */
public final class NpcStatStateRepository implements AutoCloseable {
    public record Loaded(NpcStatState state, boolean migratedFromLive) { }
    public record Lease(UUID stableId, UUID entityId, UUID token) { }
    private record Observation(NpcProfile profile, Lease lease, long sequence,
            Map<String, NpcStatSample> values, String reason) { }
    private record Pending(Observation latest, CompletableFuture<NpcStatState> completion) { }
    private final ProfileRepository profiles;
    private final Consumer<String> log;
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "npc-stat-persistence"); t.setDaemon(true); return t;
    });
    private final Map<UUID, NpcStatState> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Lease> leases = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Set<UUID> retired = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    // Everything below is accessed only on the writer lane.
    private final Map<UUID, JsonObject> documents = new HashMap<>();
    private final Map<UUID, Observation> dirty = new HashMap<>();
    private final Map<UUID, Long> savedSequences = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public NpcStatStateRepository(ProfileRepository profiles, Consumer<String> log) {
        this.profiles = profiles;
        this.log = log == null ? ignored -> { } : log;
        writer.scheduleWithFixedDelay(this::retryDirty, 1, 1, TimeUnit.SECONDS);
    }
    public Path path(NpcProfile profile) { return profiles.profileDirectory(profile.name()).resolve("npc-stats.json"); }
    public Optional<NpcStatState> cached(UUID stableId) { return Optional.ofNullable(cache.get(stableId)); }
    public CompletableFuture<Loaded> ensure(NpcProfile profile, Map<String, NpcStatRecord> baseline,
            Map<String, NpcStatSample> live, String reason) {
        var baseCopy = Map.copyOf(baseline);
        var liveCopy = live == null ? null : Map.copyOf(live);
        return submit(() -> loadOrInitialize(profile, baseCopy, liveCopy, reason));
    }
    private Loaded loadOrInitialize(NpcProfile profile, Map<String, NpcStatRecord> baseline,
            Map<String, NpcStatSample> live, String reason) {
        requireActive(profile);
        NpcStatState existing = cache.get(profile.stableId());
        if (existing != null) return new Loaded(existing, false);
        Path file = path(profile);
        if (Files.exists(file)) {
            try {
                if (Files.size(file) > 1_048_576) throw new IllegalArgumentException("Oversized stat document");
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.get("schemaVersion").getAsBigDecimal().intValueExact() != 1)
                    throw new IdentityConflict("Unsupported stat schema; manual migration required");
                UUID id = UUID.fromString(json.get("stableNpcId").getAsString());
                if (!id.equals(profile.stableId())) throw new IllegalArgumentException(
                        "Stable NPC identity mismatch; foreign values refused; reconstruct only after preserving original");
                JsonObject entries = json.getAsJsonObject("stats");
                if (entries.size() > 256) throw new IllegalArgumentException("Too many stat records");
                var parsed = new TreeMap<String, NpcStatRecord>();
                // Unknown future records remain verbatim in documents; only the vanilla allowlist is parsed/applied.
                for (String key : VanillaNpcStats.IDS) if (entries.has(key)) {
                    JsonObject item = entries.getAsJsonObject(key);
                    for (String required : List.of("current", "baseInitial", "baseMin", "baseMax",
                            "lastKnownEffectiveMin", "lastKnownEffectiveMax", "source"))
                        if (!item.has(required) || item.get(required).isJsonNull())
                            throw new IllegalArgumentException("Missing stat field " + key + "." + required);
                    parsed.put(key, JsonFiles.GSON.fromJson(item, NpcStatRecord.class));
                }
                existing = new NpcStatState(1, id, json.get("revision").getAsBigDecimal().longValueExact(),
                        json.get("savedAt").getAsString(), json.get("captureReason").getAsString(), parsed);
                documents.put(id, json.deepCopy());
                cache.put(id, existing);
                log.accept("NPC_STATS_LOADED npc=" + profile.name() + " revision=" + existing.revision());
                return new Loaded(existing, false);
            } catch (IdentityConflict conflict) {
                preserveFile(file);
                throw conflict; // Do not reassign, reconstruct, or erase a foreign identity/future schema.
            } catch (IOException inaccessible) {
                throw new UncheckedIOException("Stat authority could not be read; reconstruction refused", inaccessible);
            } catch (RuntimeException corrupt) {
                preserveFile(file); // Copy the exact bytes before any reconstruction.
                log.accept("NPC_STATS_CORRUPT_PRESERVED npc=" + profile.name() + " reason=" + corrupt);
            }
        }
        var records = new TreeMap<>(baseline);
        if (live != null) {
            // Missing live entries are unknown, NOT baseline current values for a spawned NPC.
            records.keySet().removeIf(id -> !live.containsKey(id));
            records.replaceAll((id, base) -> base.observed(live.get(id)));
            reason = "MIGRATION_FROM_LIVE";
        } else if (!"CREATE".equals(reason)) reason = "MIGRATION_FROM_BASELINE";
        var state = new NpcStatState(1, profile.stableId(), 1, Instant.now().toString(), reason, records);
        persist(profile, state);
        return new Loaded(state, live != null);
    }
    public CompletableFuture<Lease> bind(NpcProfile profile, UUID entityId) {
        return submit(() -> {
            requireActive(profile);
            drainPending(profile.stableId());
            Observation pending = dirty.get(profile.stableId());
            if (pending != null) writeObservation(pending);
            return leases.compute(profile.stableId(), (id, old) -> {
                // Even the same entity UUID may be reattached in a different world/lifecycle.
                return new Lease(id, entityId, UUID.randomUUID());
            });
        });
    }
    public boolean owns(Lease lease) { return lease != null && lease.equals(leases.get(lease.stableId())); }
    public CompletableFuture<NpcStatState> capture(NpcProfile profile, Lease lease,
            Map<String, NpcStatSample> values, String reason) {
        Observation observation = new Observation(profile, lease, sequence.incrementAndGet(), Map.copyOf(values), reason);
        if (!owns(lease) || !lease.stableId().equals(profile.stableId()) || retired.contains(profile.stableId()) || writer.isShutdown())
            return CompletableFuture.failedFuture(new IllegalStateException("Stale NPC stat attachment"));
        var result = pending.compute(profile.stableId(), (id, previous) -> {
            if (previous != null) return new Pending(observation.sequence() > previous.latest().sequence()
                    ? observation : previous.latest(), previous.completion());
            var next = new Pending(observation, new CompletableFuture<>());
            writer.execute(() -> drainPending(id));
            return next;
        });
        return result.completion();
    }
    private void drainPending(UUID id) {
        Pending batch = pending.remove(id);
        if (batch == null) return;
        Observation observation = batch.latest();
        try {
            if (!owns(observation.lease()) || retired.contains(id)) throw new IllegalStateException("Stale NPC stat attachment");
            dirty.merge(id, observation, (a, b) -> a.sequence() > b.sequence() ? a : b);
            batch.completion().complete(writeObservation(observation));
        } catch (RuntimeException failure) { batch.completion().completeExceptionally(failure); }
    }
    private NpcStatState writeObservation(Observation o) {
        UUID id = o.profile().stableId();
        if (!owns(o.lease()) || retired.contains(id)) { dirty.remove(id, o); return cache.get(id); }
        if (o.sequence() <= savedSequences.getOrDefault(id, -1L)) { dirty.remove(id, o); return cache.get(id); }
        var old = cache.get(id);
        if (old == null) throw new IllegalStateException("NPC stats must be initialized before capture");
        var records = new TreeMap<>(old.stats());
        o.values().forEach((key, value) -> {
            if (VanillaNpcStats.IDS.contains(key) && records.containsKey(key))
                records.put(key, records.get(key).observed(value));
        });
        NpcStatState next = old;
        if (!records.equals(old.stats())) {
            next = new NpcStatState(1, id, Math.addExact(old.revision(), 1),
                    Instant.now().toString(), o.reason(), records);
            persist(o.profile(), next);
        }
        savedSequences.put(id, o.sequence());
        dirty.remove(id, o);
        return next;
    }
    public CompletableFuture<Void> preserveRuntime(NpcProfile profile, UUID entityId,
            long fileRevision, Map<String, NpcStatSample> live) {
        var snapshot = Map.copyOf(live);
        return submit(() -> {
            requireActive(profile);
            Path evidence = path(profile).resolveSibling("npc-stats.runtime-conflict-" + stamp() + ".json");
            atomicWrite(evidence, JsonFiles.GSON.toJson(Map.of("stableNpcId", profile.stableId(),
                    "entityId", entityId, "persistentRevision", fileRevision,
                    "capturedAt", Instant.now().toString(), "stats", snapshot)));
            log.accept("NPC_STATS_RUNTIME_CONFLICT_PRESERVED file=" + evidence);
            return null;
        });
    }
    /** Barrier before user-confirmed full profile deletion; late callbacks cannot recreate its folder. */
    public CompletableFuture<Void> retire(NpcProfile profile) {
        retired.add(profile.stableId());
        return submit(() -> {
            Pending batch = pending.remove(profile.stableId());
            if (batch != null) batch.completion().completeExceptionally(new IllegalStateException("Profile retired"));
            dirty.remove(profile.stableId()); leases.remove(profile.stableId());
            cache.remove(profile.stableId()); documents.remove(profile.stableId()); return null;
        });
    }
    public CompletableFuture<Void> flush() {
        return submit(() -> {
            for (UUID id : List.copyOf(pending.keySet())) drainPending(id);
            for (var o : List.copyOf(dirty.values())) writeObservation(o);
            if (!dirty.isEmpty()) throw new IllegalStateException("Unflushed NPC stat snapshots");
            return null;
        });
    }
    private void retryDirty() {
        for (var o : List.copyOf(dirty.values())) try { writeObservation(o); }
        catch (RuntimeException error) { log.accept("NPC_STATS_SAVE_FAILED retainedDirty=true reason=" + error); }
    }
    private void persist(NpcProfile profile, NpcStatState state) {
        requireActive(profile);
        JsonObject json = documents.getOrDefault(profile.stableId(), new JsonObject()).deepCopy();
        JsonObject normalized = JsonFiles.GSON.toJsonTree(state).getAsJsonObject();
        JsonObject entries = json.has("stats") ? json.getAsJsonObject("stats").deepCopy() : new JsonObject();
        normalized.getAsJsonObject("stats").entrySet().forEach(e -> entries.add(e.getKey(), e.getValue()));
        normalized.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        json.add("stats", entries);
        atomicWrite(path(profile), JsonFiles.GSON.toJson(json));
        documents.put(profile.stableId(), json); cache.put(profile.stableId(), state);
        log.accept("NPC_STATS_SAVED npc=" + profile.name() + " stableId=" + state.stableNpcId()
                + " revision=" + state.revision() + " reason=" + state.captureReason());
    }
    private void requireActive(NpcProfile profile) {
        if (profile.stableId() == null || retired.contains(profile.stableId()))
            throw new IllegalStateException("Retired or missing NPC stat identity");
        String name = names.computeIfAbsent(profile.stableId(), ignored -> profile.name());
        if (!name.equalsIgnoreCase(profile.name())) throw new IllegalStateException("Stable stat identity already bound to another profile folder");
    }
    private void preserveFile(Path file) {
        try {
            Path evidence = file.resolveSibling("npc-stats.conflict-" + stamp() + ".json");
            Files.copy(file, evidence);
            log.accept("NPC_STATS_CONFLICT_PRESERVED file=" + evidence);
        } catch (IOException e) { throw new UncheckedIOException("Cannot preserve stat conflict; repair refused", e); }
    }
    private static String stamp() { return Instant.now().toEpochMilli() + "-" + UUID.randomUUID(); }
    private <T> CompletableFuture<T> submit(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, writer);
    }
    static void atomicWrite(Path target, String json) {
        Path temp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(json);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            // Fail closed if this filesystem cannot atomically replace. Never truncate a good save.
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { throw new UncheckedIOException("Atomic NPC stat save failed: " + target, error); }
        finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    @Override public void close() {
        try { flush().get(15, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new IllegalStateException("NPC stat shutdown flush failed; dirty data retained", failure); }
        writer.shutdown();
        try { if (!writer.awaitTermination(15, TimeUnit.SECONDS)) throw new IllegalStateException("NPC stat writer not drained"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private static final class IdentityConflict extends IllegalStateException {
        IdentityConflict(String message) { super(message); }
    }
}
