package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

/** Typed identity for one Hytale spatial clip playback. */
public record PlaybackId(UUID value) {
    public PlaybackId {
        if (value == null) throw new IllegalArgumentException("playback id required");
    }

    public static PlaybackId create() { return new PlaybackId(UUID.randomUUID()); }
}
