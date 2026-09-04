package com.inigmasgames.persistentnpcs.voice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Single per-player authority for mutually-exclusive conversation and recorder capture. */
public final class VoiceCaptureLeaseManager {
    public enum VoiceCaptureMode { NONE, ORBIS_CONVERSATION, VOICE_SAMPLE_RECORDING }

    private static final long CONVERSATION_HOLD_NANOS = 800_000_000L;
    private final Map<UUID, Lease> leases = new ConcurrentHashMap<>();
    private final AtomicLong dualAdmissionRejections = new AtomicLong();
    private final Consumer<String> diagnostics;

    public VoiceCaptureLeaseManager(Consumer<String> diagnostics) {
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    /** Called on the voice executor before Orbis copies a frame. */
    public boolean admitConversationFrame(UUID playerId) {
        if (playerId == null) return false;
        long now = System.nanoTime();
        final boolean[] admitted = {false};
        leases.compute(playerId, (ignored, current) -> {
            if (current != null && current.mode == VoiceCaptureMode.VOICE_SAMPLE_RECORDING) {
                dualAdmissionRejections.incrementAndGet();
                return current;
            }
            admitted[0] = true;
            return new Lease(VoiceCaptureMode.ORBIS_CONVERSATION, null,
                    now + CONVERSATION_HOLD_NANOS);
        });
        return admitted[0];
    }

    public RecordingLease acquireRecording(UUID playerId, UUID ownerId) {
        if (playerId == null || ownerId == null) {
            throw new IllegalArgumentException("Player and recording identity are required.");
        }
        long now = System.nanoTime();
        final RecordingLease[] result = {null};
        leases.compute(playerId, (ignored, current) -> {
            if (current != null && current.mode == VoiceCaptureMode.VOICE_SAMPLE_RECORDING) {
                throw new IllegalStateException("A voice recording or private preview is already active.");
            }
            if (current != null && current.mode == VoiceCaptureMode.ORBIS_CONVERSATION
                    && current.expiresAtNanos > now) {
                throw new IllegalStateException(
                        "Finish speaking to the NPC, then press Record again.");
            }
            UUID token = UUID.randomUUID();
            result[0] = new RecordingLease(playerId, ownerId, token);
            return new Lease(VoiceCaptureMode.VOICE_SAMPLE_RECORDING, token, Long.MAX_VALUE);
        });
        diagnostics.accept("NPC_AUTHORING_VOICE_LEASE_ACQUIRED timestamp=" + Instant.now()
                + " playerId=" + playerId + " ownerId=" + ownerId
                + " mode=VOICE_SAMPLE_RECORDING");
        return result[0];
    }

    public VoiceCaptureMode mode(UUID playerId) {
        Lease value = leases.get(playerId);
        if (value == null) return VoiceCaptureMode.NONE;
        if (value.mode == VoiceCaptureMode.ORBIS_CONVERSATION
                && value.expiresAtNanos <= System.nanoTime()) {
            leases.remove(playerId, value);
            return VoiceCaptureMode.NONE;
        }
        return value.mode;
    }

    public long dualAdmissionRejections() { return dualAdmissionRejections.get(); }
    public int activeRecordingLeases() {
        return (int) leases.values().stream()
                .filter(value -> value.mode == VoiceCaptureMode.VOICE_SAMPLE_RECORDING).count();
    }

    public void releaseAll() { leases.clear(); }

    private void release(RecordingLease lease) {
        if (lease == null) return;
        leases.computeIfPresent(lease.playerId, (ignored, current) ->
                current.token != null && current.token.equals(lease.token) ? null : current);
        diagnostics.accept("NPC_AUTHORING_VOICE_LEASE_RELEASED timestamp=" + Instant.now()
                + " playerId=" + lease.playerId + " ownerId=" + lease.ownerId
                + " mode=NONE");
    }

    private record Lease(VoiceCaptureMode mode, UUID token, long expiresAtNanos) { }

    public final class RecordingLease implements AutoCloseable {
        private final UUID playerId;
        private final UUID ownerId;
        private final UUID token;
        private volatile boolean closed;
        private RecordingLease(UUID playerId, UUID ownerId, UUID token) {
            this.playerId = playerId;
            this.ownerId = ownerId;
            this.token = token;
        }
        public boolean valid() {
            Lease current = leases.get(playerId);
            return !closed && current != null && token.equals(current.token);
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            release(this);
        }
    }
}
