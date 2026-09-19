package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

public record ResponseId(UUID value) {
    public ResponseId { if (value == null) throw new IllegalArgumentException("response id required"); }
    public static ResponseId create() { return new ResponseId(UUID.randomUUID()); }
}
