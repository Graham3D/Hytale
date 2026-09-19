package com.inigmasgames.persistentnpcs.epistemic;

import java.util.Set;

public record ClaimPolicy(int schemaVersion, Set<ClaimMode> allowedModes,
        Set<String> restrictions, boolean requireObjectiveEvidence,
        boolean actionClaimsRequireResult, boolean deceptionEnabled) {
    public static final int SCHEMA_VERSION = 1;
    public ClaimPolicy {
        if (schemaVersion < 1) throw new IllegalArgumentException("schema version required");
        allowedModes = Set.copyOf(allowedModes == null ? Set.of() : allowedModes);
        restrictions = Set.copyOf(restrictions == null ? Set.of() : restrictions);
    }
}
