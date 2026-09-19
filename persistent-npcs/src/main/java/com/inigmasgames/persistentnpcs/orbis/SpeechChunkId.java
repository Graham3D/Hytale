package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

/** Typed identity for one immutable canonical speech chunk. */
public record SpeechChunkId(UUID value) {
    public SpeechChunkId {
        if (value == null) throw new IllegalArgumentException("speech chunk id required");
    }

    public static SpeechChunkId create() { return new SpeechChunkId(UUID.randomUUID()); }
}
