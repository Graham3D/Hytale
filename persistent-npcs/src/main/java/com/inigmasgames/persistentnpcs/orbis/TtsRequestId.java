package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

/** Typed identity for one provider synthesis request. */
public record TtsRequestId(UUID value) {
    public TtsRequestId {
        if (value == null) throw new IllegalArgumentException("TTS request id required");
    }

    public static TtsRequestId create() { return new TtsRequestId(UUID.randomUUID()); }
}
