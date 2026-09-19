package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.VocalState;

/** Immutable lexical chunk consumed by both native chat display and TTS. */
public record CanonicalSpeechChunk(SpeechChunkId id, int index, String text,
        VocalState vocalState) {
    public CanonicalSpeechChunk {
        if (id == null || index < 0 || text == null || text.isBlank()) {
            throw new IllegalArgumentException("complete canonical speech chunk required");
        }
    }
}
