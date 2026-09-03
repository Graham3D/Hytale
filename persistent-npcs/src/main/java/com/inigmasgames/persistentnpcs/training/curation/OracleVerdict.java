package com.inigmasgames.persistentnpcs.training.curation;

import java.time.Instant;
import java.util.List;

/** Auditable result from one deterministic, version-pinned curation oracle. */
public record OracleVerdict(int schemaVersion, String oracleId, String oracleVersion,
        Status status, String reasonCode, List<String> evidenceRefs, boolean blocking,
        String evaluatedPayloadSha256, Instant evaluatedAt) {
    public static final int SCHEMA_VERSION = 1;
    public enum Status { PASS, FAIL, NEEDS_REVIEW, ERROR }

    public OracleVerdict {
        if (schemaVersion != SCHEMA_VERSION || blank(oracleId) || blank(oracleVersion)
                || status == null || blank(reasonCode) || evaluatedAt == null
                || evaluatedPayloadSha256 == null
                || !evaluatedPayloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("complete deterministic oracle verdict required");
        }
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
    }

    public boolean preventsAcceptance() {
        return blocking && status != Status.PASS;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
