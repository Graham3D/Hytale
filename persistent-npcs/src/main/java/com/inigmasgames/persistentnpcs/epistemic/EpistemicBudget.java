package com.inigmasgames.persistentnpcs.epistemic;

public record EpistemicBudget(int schemaVersion, int maxEvidenceItems, int maxEvidenceTokens,
        long maximumMillis, boolean allowAdditionalInference) {
    public static final int SCHEMA_VERSION = 1;
    public EpistemicBudget {
        if (schemaVersion < 1 || maxEvidenceItems < 0 || maxEvidenceTokens < 0
                || maximumMillis < 0) throw new IllegalArgumentException("non-negative budget required");
    }
}
