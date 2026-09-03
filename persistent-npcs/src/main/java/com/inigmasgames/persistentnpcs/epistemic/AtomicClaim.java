package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;

public record AtomicClaim(int schemaVersion, String claimId, String subjectKey,
        String predicateKey, String objectValue, ClaimMode mode, String temporalScope,
        int startInclusive, int endExclusive, String text,
        List<EvidenceRef> claimedEvidence) {
    public static final int SCHEMA_VERSION = 1;
    public AtomicClaim {
        if (schemaVersion < 1 || claimId == null || claimId.isBlank() || mode == null
                || startInclusive < 0 || endExclusive <= startInclusive) {
            throw new IllegalArgumentException("complete atomic claim required");
        }
        subjectKey = clean(subjectKey); predicateKey = clean(predicateKey);
        objectValue = clean(objectValue); temporalScope = clean(temporalScope);
        text = clean(text);
        claimedEvidence = List.copyOf(claimedEvidence == null ? List.of() : claimedEvidence);
    }
    public boolean objective() {
        return mode == ClaimMode.OBJECTIVE_FACT || mode == ClaimMode.INFERENCE
                || mode == ClaimMode.COMMITMENT || mode == ClaimMode.INTENTION;
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
