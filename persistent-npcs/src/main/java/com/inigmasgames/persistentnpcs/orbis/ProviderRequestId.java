package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

public record ProviderRequestId(UUID value) {
    public ProviderRequestId {
        if (value == null) throw new IllegalArgumentException("provider request id required");
    }
    public static ProviderRequestId create() { return new ProviderRequestId(UUID.randomUUID()); }
}
