package com.inigmasgames.persistentnpcs.training.curation;

import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.Set;

/** Version-pinned D4 policy. Policy changes necessarily change dataset identity. */
public record CurationPolicy(int schemaVersion, String policyId, String oracleSuiteVersion,
        CurationPrivacyPolicy privacyPolicy, int stockResponseMaximumOccurrences,
        Set<String> prohibitedVendorIdentityTerms, Instant approvedAt) {
    public static final int SCHEMA_VERSION = 1;
    public CurationPolicy {
        if (schemaVersion != SCHEMA_VERSION || policyId == null || policyId.isBlank()
                || oracleSuiteVersion == null || oracleSuiteVersion.isBlank()
                || privacyPolicy == null || stockResponseMaximumOccurrences < 1
                || approvedAt == null) throw new IllegalArgumentException(
                        "complete curation policy required");
        prohibitedVendorIdentityTerms = java.util.Collections.unmodifiableSet(
                new java.util.TreeSet<>(prohibitedVendorIdentityTerms == null
                        ? Set.of() : prohibitedVendorIdentityTerms));
    }
    public static CurationPolicy defaultOffline() {
        return new CurationPolicy(SCHEMA_VERSION, "orbis-d4-curation-v1",
                "deterministic-oracle-suite-v1", CurationPrivacyPolicy.failClosedDefault(),
                4, Set.of("as an ai language model", "openai", "chatgpt", "anthropic"),
                Instant.parse("2026-09-03T00:00:00Z"));
    }
    public String policyHash() { return CanonicalJson.sha256(this); }
}
