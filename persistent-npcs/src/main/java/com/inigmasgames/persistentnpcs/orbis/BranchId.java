package com.inigmasgames.persistentnpcs.orbis;

import java.util.UUID;

public record BranchId(UUID value) {
    public BranchId { if (value == null) throw new IllegalArgumentException("branch id required"); }
    public static BranchId create() { return new BranchId(UUID.randomUUID()); }
}
