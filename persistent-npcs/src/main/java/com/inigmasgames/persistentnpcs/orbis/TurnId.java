package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

public record TurnId(UUID value) {
    public TurnId { if (value == null) throw new IllegalArgumentException("turn id required"); }
    public static TurnId create() { return new TurnId(UUID.randomUUID()); }
}
