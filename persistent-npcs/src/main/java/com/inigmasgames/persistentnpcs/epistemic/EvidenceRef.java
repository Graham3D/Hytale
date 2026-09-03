package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;

/** Compact fact reference. Unknown confidence is represented explicitly, never as fake zero. */
public record EvidenceRef(int schemaVersion, String stableId, EvidenceSourceKind sourceKind,
        EpistemicStatus status, double confidence, boolean confidenceKnown,
        String compactProposition, String subjectKey, String predicateKey, String objectValue,
        Instant acquiredAt, String freshness, String provenanceActorKey,
        boolean direct, boolean authoritative, String worldKey, String temporalScope) {
    public static final int SCHEMA_VERSION = 1;
    public EvidenceRef {
        if (schemaVersion < 1 || sourceKind == null || status == null) {
            throw new IllegalArgumentException("versioned evidence reference required");
        }
        stableId = stableId == null ? "" : stableId.strip();
        compactProposition = compactProposition == null ? "" : compactProposition.strip();
        subjectKey = clean(subjectKey); predicateKey = clean(predicateKey);
        objectValue = clean(objectValue); freshness = clean(freshness);
        provenanceActorKey = clean(provenanceActorKey); worldKey = clean(worldKey);
        temporalScope = clean(temporalScope);
        confidence = confidenceKnown ? Math.max(0.0, Math.min(1.0, confidence)) : -1.0;
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
