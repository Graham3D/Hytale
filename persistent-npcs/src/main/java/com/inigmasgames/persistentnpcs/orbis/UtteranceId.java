package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

public record UtteranceId(UUID value) {
    public UtteranceId { if (value == null) throw new IllegalArgumentException("utterance id required"); }
    public static UtteranceId create() { return new UtteranceId(UUID.randomUUID()); }
}
