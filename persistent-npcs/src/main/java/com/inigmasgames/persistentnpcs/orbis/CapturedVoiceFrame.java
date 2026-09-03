package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.UUID;

/** Immutable copy of Hytale voice metadata and Opus bytes. */
public record CapturedVoiceFrame(UUID playerId, UUID worldId, double x, double y,
        double z, short sequenceNumber, int clientTimestamp, byte[] opus,
        Instant receivedAt, long receivedNanos) {
    public CapturedVoiceFrame {
        if (playerId == null || opus == null) throw new IllegalArgumentException("voice frame required");
        opus = opus.clone();
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
    }

    @Override public byte[] opus() { return opus.clone(); }
}
