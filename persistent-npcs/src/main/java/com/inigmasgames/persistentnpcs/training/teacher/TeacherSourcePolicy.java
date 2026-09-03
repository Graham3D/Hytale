package com.inigmasgames.persistentnpcs.training.teacher;

import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.Set;

/** Versioned legal/terms decision. UNKNOWN and REJECTED are hard stops. */
public record TeacherSourcePolicy(int schemaVersion, String policyId,
        String sourceId, TeacherSourceStatus status, String termsVersion,
        String licenseId, Set<String> allowedUses, Set<String> prohibitedUses,
        String decisionBasis, Instant reviewedAt) {
    public static final int SCHEMA_VERSION = 1;
    public enum TeacherSourceStatus { APPROVED, REJECTED, UNKNOWN }

    public TeacherSourcePolicy {
        if (schemaVersion != SCHEMA_VERSION || blank(policyId) || blank(sourceId)
                || status == null || blank(termsVersion) || reviewedAt == null) {
            throw new IllegalArgumentException("complete teacher source policy required");
        }
        licenseId = licenseId == null ? "" : licenseId.strip();
        allowedUses = Set.copyOf(allowedUses == null ? Set.of() : allowedUses);
        prohibitedUses = Set.copyOf(prohibitedUses == null ? Set.of() : prohibitedUses);
        decisionBasis = decisionBasis == null ? "" : decisionBasis.strip();
    }

    public String snapshotHash() { return CanonicalJson.sha256(this); }
    public void requireApproved() {
        if (status != TeacherSourceStatus.APPROVED) throw new IllegalStateException(
                "teacher source " + sourceId + " is " + status + "; generation blocked");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
