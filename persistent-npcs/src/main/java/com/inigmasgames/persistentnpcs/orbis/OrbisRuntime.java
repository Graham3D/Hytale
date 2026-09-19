package com.inigmasgames.persistentnpcs.orbis;

import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.modules.voice.PlayerVoiceFrame;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import com.inigmasgames.persistentnpcs.voice.VoiceCaptureLeaseManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3dc;

/** Authoritative Update 6 entry point for player capture, STT, and audience turns. */
public final class OrbisRuntime implements AutoCloseable {
    private final OrbisTurnCoordinator coordinator;
    private final Registration interceptor;
    private final OrbisResourceScheduler resources;
    private final VoiceCaptureLeaseManager captureLeases;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OrbisRuntime(VoiceModule voiceModule, OrbisTurnCoordinator coordinator) {
        this(voiceModule, coordinator, null, null);
    }

    public OrbisRuntime(VoiceModule voiceModule, OrbisTurnCoordinator coordinator,
            OrbisResourceScheduler resources) {
        this(voiceModule, coordinator, resources, null);
    }

    public OrbisRuntime(VoiceModule voiceModule, OrbisTurnCoordinator coordinator,
            OrbisResourceScheduler resources, VoiceCaptureLeaseManager captureLeases) {
        if (coordinator == null) throw new IllegalArgumentException("coordinator required");
        this.coordinator = coordinator;
        this.resources = resources;
        this.captureLeases = captureLeases;
        interceptor = voiceModule == null || !voiceModule.isVoiceEnabled()
                ? null : voiceModule.addPlayerVoiceInterceptor(this::acceptVoiceFrame);
    }

    /** Hytale voice-thread callback: copy bytes/metadata and enqueue; no STT or world work. */
    private void acceptVoiceFrame(PlayerVoiceFrame frame) {
        if (frame == null || closed.get()) return;
        UUID playerId = frame.speaker().getUuid();
        // Recorder ownership is decided before any byte copy or STT admission.
        if (captureLeases != null && !captureLeases.admitConversationFrame(playerId)) return;
        UUID worldId = frame.worldId();
        Vector3dc position = frame.position();
        byte[] opus = frame.opus() == null ? new byte[0] : frame.opus().clone();
        long receivedNanos = System.nanoTime();
        CapturedVoiceFrame copy = new CapturedVoiceFrame(playerId, worldId,
                position == null ? 0 : position.x(),
                position == null ? 0 : position.y(),
                position == null ? 0 : position.z(),
                frame.sequenceNumber(), frame.timestamp(), opus,
                Instant.now(), receivedNanos);
        coordinator.accept(copy);
    }

    public void playerDisconnected(UUID playerId) {
        coordinator.playerDisconnected(playerId);
    }

    public void conversationFocusLost(UUID npcId, UUID playerId) {
        coordinator.conversationFocusLost(npcId, playerId);
    }

    public void npcUnloaded(UUID npcId) {
        coordinator.cancelNpc(npcId, CancellationReason.NPC_DESPAWN);
    }

    public void worldUnloaded(UUID worldId) {
        coordinator.worldUnloaded(worldId);
    }

    /** Text/chat enters the same Orbis turn graph after the STT boundary. */
    public void submitText(UUID playerId, UUID worldId, double x, double y, double z,
            String text) {
        submitText(playerId, worldId, x, y, z, text, TurnIngressSource.MANUAL_SUBMISSION);
    }

    public void submitText(UUID playerId, UUID worldId, double x, double y, double z,
            String text, TurnIngressSource ingressSource) {
        if (closed.get()) throw new IllegalStateException("Orbis runtime is closed");
        long now = System.nanoTime();
        UUID logicalUtteranceId = UUID.randomUUID();
        coordinator.accept(new TranscribedPlayerUtterance(logicalUtteranceId, playerId,
                text, worldId, x, y, z, Instant.now(), now, now, now, now,
                ingressSource == null ? TurnIngressSource.UNKNOWN_TEXT : ingressSource, null));
    }

    public void operatorCancel(TurnId turnId) {
        coordinator.cancelTurn(turnId, CancellationReason.ADMIN_CANCEL);
    }

    public OrbisDiagnostics diagnostics() { return coordinator.diagnostics(); }

    public String resourcesSummary() {
        return resources == null ? "Orbis resource scheduler unavailable."
                : resources.inspectorSummary();
    }

    public CompletableFuture<ResourcePolicy> selectResourcePolicy(String requested) {
        if (resources == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Orbis resource scheduler unavailable"));
        try {
            return resources.selectPolicy(ResourcePolicy.valueOf(
                    requested == null ? "" : requested.strip().toUpperCase(
                            java.util.Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Unknown Orbis resource policy: " + requested, failure));
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (interceptor != null && interceptor.isRegistered()) interceptor.unregister();
        coordinator.close();
    }
}
