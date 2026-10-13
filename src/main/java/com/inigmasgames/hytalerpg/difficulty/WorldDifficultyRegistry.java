package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Persistent server-owned world identities. Reads are memory-only; writes run outside the world tick. */
public final class WorldDifficultyRegistry {
    public enum Kind { CAMPAIGN, SHARED_HUB }
    public record Binding(UUID worldId, String worldName, Kind kind, DifficultyId difficulty,
                          String profileId, String progressionScope, boolean enabled) {
        public Binding {
            Objects.requireNonNull(worldId); Objects.requireNonNull(kind); Objects.requireNonNull(difficulty);
            for (String value : List.of(worldName, profileId, progressionScope))
                if (value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("INVALID_WORLD_BINDING");
            // Hub has a separate future event context, never a private difficulty per visitor.
            if (kind == Kind.SHARED_HUB && difficulty != DifficultyId.NORMAL) throw new IllegalArgumentException("HUB_REQUIRES_EXPLICIT_EVENT_CONTEXT");
        }
    }
    private record Data(int schemaVersion, List<Binding> worlds, List<Binding> historical) {}
    private record Envelope(String checksum, Data data) {}
    private final Path path;
    private final Gson gson = new Gson();
    private volatile Map<UUID, Binding> worlds;
    private volatile Map<UUID, Binding> historical = Map.of();
    public WorldDifficultyRegistry(Path path) {
        this.path = path;
        try {
            if (!Files.exists(path)) { worlds = Map.of(); return; }
            var envelope = gson.fromJson(Files.readString(path), Envelope.class);
            if (!hash(gson.toJson(envelope.data())).equals(envelope.checksum())
                    || envelope.data().schemaVersion() != 1 && envelope.data().schemaVersion() != 2)
                throw new IllegalStateException("WORLD_REGISTRY_CHECKSUM_OR_SCHEMA");
            if (envelope.data().schemaVersion() == 1 && envelope.data().historical() != null
                    || envelope.data().schemaVersion() == 2
                    && (envelope.data().historical() == null || envelope.data().historical().isEmpty()))
                throw new IllegalStateException("WORLD_REGISTRY_HISTORY_SCHEMA");
            worlds = validated(envelope.data().worlds());
            if (envelope.data().schemaVersion() == 2)
                historical = validatedHistorical(envelope.data().historical(), worlds);
        } catch (Exception error) { throw new IllegalStateException("Refusing to reset world difficulty registry " + path, error); }
    }
    public Optional<Binding> find(UUID world) { return Optional.ofNullable(worlds.get(Objects.requireNonNull(world))); }
    public Binding require(UUID world) { return find(world).orElseThrow(() -> new IllegalStateException("WORLD_DIFFICULTY_UNREGISTERED:" + world)); }
    public Collection<Binding> bindings() { return worlds.values(); }
    /** Old identities remain inspectable, but cannot classify a newly loaded native world. */
    public Optional<Binding> historical(UUID world) { return Optional.ofNullable(historical.get(Objects.requireNonNull(world))); }
    /** Identity is immutable after registration; stage 2 registers actual persistent native worlds before admission. */
    public synchronized Binding register(Binding binding) {
        registerAll(List.of(binding));
        return require(binding.worldId());
    }
    /** One startup transaction captures even unloaded legacy worlds; partial migration cannot reclassify a later world. */
    public synchronized void registerAll(Collection<Binding> bindings) {
        var list = new ArrayList<>(worlds.values());
        for (var binding : bindings) {
            var prior = worlds.get(binding.worldId());
            if (prior != null) {
                if (!prior.equals(binding)) throw new IllegalStateException("WORLD_DIFFICULTY_REBIND_FORBIDDEN");
            } else list.add(binding);
        }
        if (list.size() == worlds.size()) return;
        var candidate = validated(list);
        persist(candidate, historical);
    }
    /** Caller has verified exact native config UUIDs and the approved old/new lineage. */
    synchronized void reconcile(Map<UUID,UUID> replacements) {
        if (replacements.isEmpty()) return;
        if (new HashSet<>(replacements.values()).size() != replacements.size())
            throw new IllegalArgumentException("DUPLICATE_WORLD_RECONCILIATION");
        var active = new HashMap<>(worlds);
        var history = new HashMap<>(historical);
        boolean changed = false;
        for (var entry : replacements.entrySet()) {
            UUID oldId = Objects.requireNonNull(entry.getKey()), newId = Objects.requireNonNull(entry.getValue());
            if (oldId.equals(newId)) throw new IllegalArgumentException("WORLD_RECONCILIATION_SAME_ID");
            var old = active.get(oldId);
            if (old == null) {
                var prior = history.get(oldId);
                var current = active.get(newId);
                if (prior == null || current == null || !sameDefinition(prior,current))
                    throw new IllegalStateException("WORLD_RECONCILIATION_PROVENANCE_MISSING");
                continue;
            }
            if (active.containsKey(newId) || history.containsKey(newId) || history.containsKey(oldId))
                throw new IllegalStateException("WORLD_RECONCILIATION_IDENTITY_CONFLICT");
            var current = withId(old, newId);
            active.remove(oldId);
            active.put(newId, current);
            history.put(oldId, old);
            changed = true;
        }
        if (!changed) return;
        var candidate = validated(new ArrayList<>(active.values()));
        var provenance = validatedHistorical(new ArrayList<>(history.values()),candidate);
        persist(candidate, provenance);
    }
    private static Binding withId(Binding old, UUID id) {
        return new Binding(id,old.worldName(),old.kind(),old.difficulty(),old.profileId(),old.progressionScope(),old.enabled());
    }
    private static boolean sameDefinition(Binding old, Binding current) {
        return withId(old,current.worldId()).equals(current);
    }
    private void persist(Map<UUID,Binding> candidate, Map<UUID,Binding> provenance) {
        var order = Comparator.comparing((Binding b) -> b.worldId().toString());
        var data = new Data(provenance.isEmpty() ? 1 : 2,
                candidate.values().stream().sorted(order).toList(),
                provenance.isEmpty() ? null : provenance.values().stream().sorted(order).toList());
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(Objects.requireNonNull(path.toAbsolutePath().getParent(),"WORLD_REGISTRY_PARENT_MISSING"));
            var bytes = gson.toJson(new Envelope(hash(gson.toJson(data)), data)).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            var backup = path.resolveSibling(path.getFileName() + ".bak");
            if (Files.exists(path) && !Files.exists(backup)) Files.copy(path, backup);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            worlds = candidate;
            historical = provenance;
        } catch (Exception error) { throw new IllegalStateException("WORLD_DIFFICULTY_PERSISTENCE_FAILED", error); }
    }
    private static Map<UUID, Binding> validated(List<Binding> values) {
        Objects.requireNonNull(values,"WORLD_REGISTRY_WORLDS_MISSING");
        if (values.size() > 1024) throw new IllegalArgumentException("WORLD_REGISTRY_BUDGET");
        Map<UUID, Binding> result = new HashMap<>(); Set<String> names = new HashSet<>(); Set<String> scopes = new HashSet<>();
        for (var value : values) {
            if (result.put(value.worldId(), value) != null || !names.add(value.worldName())) throw new IllegalArgumentException("DUPLICATE_WORLD_BINDING");
            scopes.add(value.progressionScope());
        }
        if (scopes.size() > 1) throw new IllegalArgumentException("CHARACTER_PROGRESSION_MUST_BE_SHARED");
        return Map.copyOf(result);
    }
    private static Map<UUID, Binding> validatedHistorical(List<Binding> values, Map<UUID,Binding> active) {
        Objects.requireNonNull(values,"WORLD_REGISTRY_HISTORY_MISSING");
        if (values.size() > 1024) throw new IllegalArgumentException("WORLD_REGISTRY_HISTORY_BUDGET");
        Map<UUID,Binding> result = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (var old : values) {
            if (result.put(old.worldId(), old) != null || !names.add(old.worldName()) || active.containsKey(old.worldId()))
                throw new IllegalArgumentException("DUPLICATE_HISTORICAL_WORLD_BINDING");
            var current = active.values().stream().filter(b -> b.worldName().equals(old.worldName())).findFirst().orElse(null);
            if (current == null || !sameDefinition(old,current))
                throw new IllegalArgumentException("WORLD_REGISTRY_HISTORY_CONFLICT");
        }
        return Map.copyOf(result);
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
