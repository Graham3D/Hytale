package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.Set;

/** D5 algorithms and thresholds are part of immutable dataset identity. */
public record DatasetPolicy(int schemaVersion, String policyId,
        String normalizationVersion, String fuzzyAlgorithm,
        double fuzzyDuplicateThreshold, double fuzzyReviewThreshold,
        String splitSeed, int trainPercent, int devPercent, int testPercent,
        Set<String> profileHoldoutIds, boolean requireAllRowsAccepted,
        boolean forbidConnectedAndCanaryTrainerRows, Instant approvedAt) {
    public DatasetPolicy {
        if (schemaVersion != 1 || policyId == null || policyId.isBlank()
                || normalizationVersion == null || fuzzyAlgorithm == null
                || fuzzyDuplicateThreshold <= fuzzyReviewThreshold
                || fuzzyReviewThreshold < 0 || fuzzyDuplicateThreshold > 1
                || splitSeed == null || splitSeed.isBlank()
                || trainPercent + devPercent + testPercent != 100
                || approvedAt == null) throw new IllegalArgumentException(
                        "complete deterministic dataset policy required");
        profileHoldoutIds = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                profileHoldoutIds == null ? Set.of() : profileHoldoutIds));
    }
    public static DatasetPolicy defaultOffline() {
        return new DatasetPolicy(1, "orbis-d5-dataset-v1", DatasetNormalization.VERSION,
                DatasetNormalization.FUZZY_ALGORITHM,
                DatasetNormalization.FUZZY_DUPLICATE_THRESHOLD,
                DatasetNormalization.FUZZY_REVIEW_THRESHOLD,
                "orbis-family-split-2026-09-03", 80, 10, 10,
                Set.of("profile-holdout"), true, true,
                Instant.parse("2026-09-03T00:00:00Z"));
    }
    public String policyHash() { return CanonicalJson.sha256(this); }
    public String dedupPolicyHash() { return CanonicalJson.sha256(new DedupSeed(
            normalizationVersion, fuzzyAlgorithm, fuzzyDuplicateThreshold,
            fuzzyReviewThreshold)); }
    public String splitPolicyHash() { return CanonicalJson.sha256(new SplitSeed(
            splitSeed, trainPercent, devPercent, testPercent,
            profileHoldoutIds.stream().sorted().toList())); }
    public String contaminationPolicyHash() { return CanonicalJson.sha256(new ContaminationSeed(
            normalizationVersion, fuzzyAlgorithm, fuzzyDuplicateThreshold,
            forbidConnectedAndCanaryTrainerRows)); }
    private record DedupSeed(String normalization, String algorithm, double duplicate,
            double review) { }
    private record SplitSeed(String seed, int train, int dev, int test,
            java.util.List<String> holdout) { }
    private record ContaminationSeed(String normalization, String algorithm,
            double threshold, boolean protectedSplitsExcluded) { }
}
