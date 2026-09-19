package com.inigmasgames.persistentnpcs.training.corpus;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.inigmasgames.persistentnpcs.evaluation.EvaluationContracts;
import com.inigmasgames.persistentnpcs.training.candidate.EligibilityEvidence;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibilityClassifier;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** D2 candidate. It is evidence, not a label and never a training row. */
public record DistillationCorpusCandidate(int schemaVersion,
        ArtifactIds.TrainingCandidateId id,
        ProductionInputSnapshot productionInput,
        String originalModelOutput,
        JsonElement claimFirewallOutcome,
        TrainingEligibilityClassifier.EligibilityDecision eligibility,
        EligibilityEvidence eligibilityEvidence,
        SourceProvenance provenance,
        CandidateState state,
        Instant createdAt) {
    public static final int SCHEMA_VERSION = 1;
    public enum CandidateState { DRAFT, REVIEW_REQUIRED, REJECTED, ELIGIBLE_UNLABELED }

    public DistillationCorpusCandidate {
        if (schemaVersion != SCHEMA_VERSION || id == null || productionInput == null
                || eligibility == null || eligibilityEvidence == null || provenance == null
                || state == null || createdAt == null) {
            throw new IllegalArgumentException("complete corpus candidate required");
        }
        originalModelOutput = originalModelOutput == null ? "" : originalModelOutput;
        claimFirewallOutcome = claimFirewallOutcome == null ? JsonNull.INSTANCE
                : claimFirewallOutcome.deepCopy();
    }

    public record SourceProvenance(String evaluationRunId, String scenarioId,
            String turnId, List<Long> supportingSequences,
            Map<String, String> artifactHashes) {
        public SourceProvenance {
            evaluationRunId = clean(evaluationRunId); scenarioId = clean(scenarioId);
            turnId = clean(turnId);
            supportingSequences = List.copyOf(supportingSequences == null
                    ? List.of() : supportingSequences);
            artifactHashes = Map.copyOf(artifactHashes == null ? Map.of() : artifactHashes);
        }
        private static String clean(String value) { return value == null ? "" : value.strip(); }
    }
}
