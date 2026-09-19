package com.inigmasgames.persistentnpcs.diagnostics;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.orbis.OrbisEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** In-memory operator trace registry. No active session means no trace filesystem writes. */
public final class NpcTraceManager implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final java.util.Set<String> MEANINGFUL_EVENTS = java.util.Set.of(
            "INPUT_RECEIVED", "CONTEXT_ROUTED", "COGNITION_DECISION", "MODEL_OUTPUT",
            "CANONICAL_RESPONSE", "STRUCTURED_NPC_DECISION", "RUNTIME_DIAGNOSTICS",
            "RESOURCE_SNAPSHOT",
            "TRACE_DIAGNOSTIC_FAILED", "TURN_COMPLETED",
            "DIALOGUE_REJECTED", "RESPONSE_CANCELLED", "TURN_FAILED",
            "TTS_CHUNK_COMMITTED", "TTS_STARTED", "TTS_COMPLETED", "TTS_FAILED",
            "TTS_CHUNK_COUNT_COMMITTED", "VOICE_RESPONSE_BEGIN",
            "VOICE_RESPONSE_CANCELLED", "VOICE_PLAYBACK_STARTED",
            "VOICE_PLAYBACK_COMPLETED", "VOICE_PLAYBACK_FAILED",
            "VOICE_INPUT_REJECTED", "VOICE_CAPTURE_STARTED",
            "VOICE_CAPTURE_FINALIZED", "VOICE_TRANSCRIPTION_STARTED",
            "VOICE_TRANSCRIPTION_COMPLETED", "VOICE_TRANSCRIPTION_FAILED",
            "VOICE_TRANSCRIPTION_IGNORED", "ORBIS_EVENT");
    private final ProfileRepository profiles;
    private final Clock clock;
    private final Consumer<String> diagnostics;
    private final java.util.function.Supplier<JsonObject> resourceSnapshot;
    private final Map<Key, NpcTraceSession> active = new LinkedHashMap<>();
    private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4_096), task -> {
                Thread thread = new Thread(task, "immersive-npc-trace-writer");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public NpcTraceManager(ProfileRepository profiles, Consumer<String> diagnostics) {
        this(profiles, Clock.systemDefaultZone(), diagnostics, () -> null);
    }

    public NpcTraceManager(ProfileRepository profiles, Consumer<String> diagnostics,
            java.util.function.Supplier<JsonObject> resourceSnapshot) {
        this(profiles, Clock.systemDefaultZone(), diagnostics, resourceSnapshot);
    }

    /** Visible for deterministic lifecycle tests. */
    public NpcTraceManager(ProfileRepository profiles, Clock clock,
            Consumer<String> diagnostics) {
        this(profiles, clock, diagnostics, () -> null);
    }

    public NpcTraceManager(ProfileRepository profiles, Clock clock,
            Consumer<String> diagnostics,
            java.util.function.Supplier<JsonObject> resourceSnapshot) {
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.resourceSnapshot = resourceSnapshot == null ? () -> null : resourceSnapshot;
    }

    /** Returns a started session, or the stopped session when toggling an active key off. */
    public synchronized ToggleResult toggle(UUID operatorId, NpcProfile profile) {
        java.util.Objects.requireNonNull(operatorId, "operatorId");
        java.util.Objects.requireNonNull(profile, "profile");
        Key key = new Key(operatorId, profile.id());
        NpcTraceSession existing = active.remove(key);
        if (existing != null) {
            submit(() -> existing.stop("operator-toggle"));
            diagnostics.accept("NPC_TRACE_STOPPED operator=" + operatorId + " npc="
                    + profile.id() + " path=" + existing.outputPath());
            return new ToggleResult(false, existing.outputPath());
        }

        NpcTraceSession started = create(operatorId, profile);
        active.put(key, started);
        JsonObject event = new JsonObject();
        event.addProperty("event", "TRACE_STARTED");
        event.addProperty("responseId", "");
        submit(() -> append(started, event));
        submit(() -> appendResourceSnapshot(started));
        diagnostics.accept("NPC_TRACE_STARTED operator=" + operatorId + " npc="
                + profile.id() + " path=" + started.outputPath());
        return new ToggleResult(true, started.outputPath());
    }

    /** Filesystem setup/close runs on the trace writer, never on a Hytale UI callback. */
    public CompletableFuture<ToggleResult> toggleAsync(UUID operatorId, NpcProfile profile) {
        if (writer.isShutdown()) return CompletableFuture.failedFuture(
                new IllegalStateException("NPC trace manager is closed"));
        return CompletableFuture.supplyAsync(() -> toggle(operatorId, profile), writer);
    }

    public void record(UUID npcId, JsonObject event) {
        if (npcId == null || event == null) return;
        String type = event.has("event") ? event.get("event").getAsString() : "";
        if (!MEANINGFUL_EVENTS.contains(type)) return;
        List<NpcTraceSession> targets;
        synchronized (this) {
            targets = active.entrySet().stream()
                    .filter(entry -> entry.getKey().npcId().equals(npcId))
                    .map(Map.Entry::getValue).toList();
        }
        JsonObject copy = event.deepCopy();
        for (NpcTraceSession session : targets) submit(() -> append(session, copy));
    }

    /**
     * Records pre-cognition microphone/STT events in every trace explicitly started by
     * this operator. No authoritative NPC audience exists until transcription finishes.
     */
    public void recordOperator(UUID operatorId, JsonObject event) {
        if (operatorId == null || event == null) return;
        String type = event.has("event") ? event.get("event").getAsString() : "";
        if (!MEANINGFUL_EVENTS.contains(type)) return;
        List<NpcTraceSession> targets;
        synchronized (this) {
            targets = active.entrySet().stream()
                    .filter(entry -> entry.getKey().operatorId().equals(operatorId))
                    .map(Map.Entry::getValue).toList();
        }
        JsonObject copy = event.deepCopy();
        for (NpcTraceSession session : targets) submit(() -> append(session, copy));
    }

    /** Observer adapter; writing remains gated by an explicit active trace session. */
    public void recordOrbis(OrbisEvent source) {
        if (source == null) return;
        if (source.type() == com.inigmasgames.persistentnpcs.orbis.OrbisEventType
                .CAPTURE_FRAME_ACCEPTED) return;
        JsonObject event = new JsonObject();
        event.addProperty("event", "ORBIS_EVENT");
        event.addProperty("orbisType", source.type().name());
        event.addProperty("sequence", source.sequence());
        event.addProperty("turnId", source.turnId() == null ? "" : source.turnId().value().toString());
        event.addProperty("branchId", source.branchId() == null ? "" : source.branchId().value().toString());
        event.addProperty("responseId", source.responseId() == null ? "" : source.responseId().value().toString());
        event.addProperty("providerRequestId", source.providerRequestId() == null ? ""
                : source.providerRequestId().value().toString());
        event.addProperty("epoch", source.epoch());
        source.facts().forEach(event::addProperty);
        String npc = source.facts().get("npcId");
        if (npc != null) {
            try { record(UUID.fromString(npc), event); } catch (IllegalArgumentException ignored) { }
            return;
        }
        String player = source.facts().get("playerId");
        if (player != null) {
            try { recordOperator(UUID.fromString(player), event); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    public synchronized int disconnect(UUID operatorId) {
        if (operatorId == null) return 0;
        List<Map.Entry<Key, NpcTraceSession>> matches = active.entrySet().stream()
                .filter(entry -> entry.getKey().operatorId().equals(operatorId)).toList();
        matches.forEach(entry -> active.remove(entry.getKey()));
        matches.forEach(entry -> submit(() -> entry.getValue().stop("operator-disconnect")));
        if (!matches.isEmpty()) diagnostics.accept("NPC_TRACE_OPERATOR_DISCONNECTED operator="
                + operatorId + " sessionsClosed=" + matches.size());
        return matches.size();
    }

    public synchronized boolean isActive(UUID operatorId, UUID npcId) {
        return active.containsKey(new Key(operatorId, npcId));
    }

    public synchronized boolean isNpcTraced(UUID npcId) {
        return npcId != null && active.keySet().stream()
                .anyMatch(key -> key.npcId().equals(npcId));
    }

    public synchronized int activeSessionCount() { return active.size(); }

    @Override
    public void close() {
        List<NpcTraceSession> sessions;
        synchronized (this) {
            sessions = new ArrayList<>(active.values());
            active.clear();
        }
        sessions.forEach(session -> submit(() -> session.stop("plugin-shutdown")));
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private NpcTraceSession create(UUID operatorId, NpcProfile profile) {
        String name = ProfileRepository.sanitizeProfileName(profile.name());
        Path profileDirectory = profiles.profileDirectory(name).toAbsolutePath().normalize();
        Path traces = profileDirectory.resolve("traces").normalize();
        if (!traces.startsWith(profileDirectory)) {
            throw new IllegalArgumentException("Unsafe NPC trace path");
        }
        try {
            Files.createDirectories(traces);
            Instant started = clock.instant();
            ZoneId zone = clock.getZone();
            LocalDateTime filenameTime = LocalDateTime.ofInstant(started, zone);
            Path path;
            int collisionOffset = 0;
            do {
                String stamp = FILE_TIME.format(filenameTime.plusSeconds(collisionOffset++));
                path = traces.resolve(name + "_" + stamp + ".jsonl").normalize();
            } while (Files.exists(path));
            if (!path.startsWith(traces) || !path.getParent().equals(traces)) {
                throw new IllegalArgumentException("Unsafe NPC trace file path");
            }
            return new NpcTraceSession(UUID.randomUUID(), operatorId, profile.id(),
                    profile.name(), started, path);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not start NPC trace", failure);
        }
    }

    private synchronized void failSession(NpcTraceSession session, RuntimeException failure) {
        active.remove(new Key(session.operatorId(), session.npcId()), session);
        session.close();
        diagnostics.accept("NPC_TRACE_FAILED operator=" + session.operatorId() + " npc="
                + session.npcId() + " reason=" + failure.getMessage());
    }

    private void append(NpcTraceSession session, JsonObject event) {
        try {
            session.append(event);
        } catch (RuntimeException failure) {
            failSession(session, failure);
        }
    }

    private void appendResourceSnapshot(NpcTraceSession session) {
        try {
            JsonObject snapshot = resourceSnapshot.get();
            if (snapshot == null) return;
            JsonObject event = snapshot.deepCopy();
            String snapshotId = ResourceSnapshotIdentity.id(event);
            event.addProperty("event", "RESOURCE_SNAPSHOT");
            event.addProperty("responseId", "");
            event.addProperty("resourceSnapshotId", snapshotId);
            event.addProperty("resourceSnapshotMode", "FULL");
            event.addProperty("snapshotReason", "TRACE_STARTED");
            append(session, event);
        } catch (RuntimeException failure) {
            diagnostics.accept("NPC_TRACE_RESOURCE_SNAPSHOT_FAILED npc=" + session.npcId()
                    + " reason=" + failure.getMessage());
        }
    }

    private void submit(Runnable operation) {
        try {
            writer.execute(operation);
        } catch (RejectedExecutionException ignored) {
            // Shutdown/disconnect has already detached the sessions. Diagnostics never affect NPCs.
        }
    }

    /** Test/support barrier; production turn paths never wait for trace I/O. */
    public void awaitIdle() {
        try {
            CompletableFuture.runAsync(() -> { }, writer).get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Trace writer did not become idle", failure);
        }
    }

    private record Key(UUID operatorId, UUID npcId) { }

    public record ToggleResult(boolean started, Path path) { }
}
