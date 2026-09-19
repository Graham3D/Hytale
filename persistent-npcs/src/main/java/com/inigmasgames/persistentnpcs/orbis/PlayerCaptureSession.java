package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;

/** Coordinator-owned bounded Opus capture for one player PTT packet run. */
public final class PlayerCaptureSession {
    private final TurnId turnId;
    private final UUID playerId;
    private final UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private final Instant startedAt;
    private final long firstFrameNanos;
    private final int maximumFrames;
    private final List<byte[]> frames = new ArrayList<>();
    private long lastFrameNanos;
    private long boundaryGeneration;
    private CompletableFuture<Void> streamStart;
    private CompletableFuture<Void> streamTail = CompletableFuture.completedFuture(null);
    private OrbisResourceScheduler.Lease streamLease;
    private int streamedFrames;
    private volatile String stablePartial = "";
    private UUID streamSessionId;

    public PlayerCaptureSession(TurnId turnId, UUID playerId, UUID worldId,
            double x, double y, double z, Instant startedAt, long firstFrameNanos,
            int maximumFrames) {
        this.turnId = turnId;
        this.playerId = playerId;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAt = startedAt;
        this.firstFrameNanos = firstFrameNanos;
        this.lastFrameNanos = firstFrameNanos;
        this.maximumFrames = Math.max(1, maximumFrames);
    }

    public boolean append(byte[] opus, long receivedNanos) {
        if (frames.size() >= maximumFrames) return false;
        frames.add(opus.clone());
        lastFrameNanos = receivedNanos;
        boundaryGeneration++;
        return true;
    }

    public List<byte[]> snapshotFrames() {
        return frames.stream().map(byte[]::clone).toList();
    }

    public synchronized void beginStream(UUID sessionId,
            CompletableFuture<OrbisResourceScheduler.Lease> lease,
            SpeechToTextProvider provider) {
        if (streamStart != null) return;
        streamSessionId = sessionId;
        streamStart = lease.thenCompose(value -> {
            streamLease = value;
            return provider.startStream(streamSessionId);
        });
    }

    public synchronized void beginStreamDirect(UUID sessionId,
            SpeechToTextProvider provider) {
        if (streamStart != null) return;
        streamSessionId = sessionId;
        streamStart = provider.startStream(streamSessionId);
    }

    public synchronized void queueUnsentFrames(SpeechToTextProvider provider) {
        if (streamStart == null || streamedFrames >= frames.size()) return;
        List<byte[]> unsent = frames.subList(streamedFrames, frames.size()).stream()
                .map(byte[]::clone).toList();
        streamedFrames = frames.size();
        streamTail = streamTail.thenCompose(ignored -> streamStart)
                .thenCompose(ignored -> provider.appendStream(streamSessionId, unsent))
                .thenAccept(partial -> {
                    if (partial != null && !partial.isBlank()) stablePartial = partial.strip();
                });
    }

    public synchronized boolean streaming() { return streamStart != null; }
    public synchronized String stablePartial() { return stablePartial; }

    public synchronized CompletableFuture<SpeechTranscript> finishStream(
            SpeechToTextProvider provider) {
        queueUnsentFrames(provider);
        return streamTail.thenCompose(ignored -> provider.finishStream(streamSessionId));
    }

    public synchronized void closeStreamLease() {
        if (streamLease != null) {
            streamLease.close();
            streamLease = null;
        }
    }

    public TurnId turnId() { return turnId; }
    public UUID playerId() { return playerId; }
    public UUID worldId() { return worldId; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public Instant startedAt() { return startedAt; }
    public long firstFrameNanos() { return firstFrameNanos; }
    public long lastFrameNanos() { return lastFrameNanos; }
    public long boundaryGeneration() { return boundaryGeneration; }
    public int frameCount() { return frames.size(); }
}
