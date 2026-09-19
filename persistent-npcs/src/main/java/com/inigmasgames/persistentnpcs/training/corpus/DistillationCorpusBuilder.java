package com.inigmasgames.persistentnpcs.training.corpus;

import com.google.gson.JsonElement;
import com.inigmasgames.persistentnpcs.training.candidate.EligibilityEvidence;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibility;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibilityClassifier;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import java.time.Instant;

/** Builds an unlabeled candidate only from an already-rendered production snapshot. */
public final class DistillationCorpusBuilder {
    private final TrainingEligibilityClassifier classifier;
    public DistillationCorpusBuilder(TrainingEligibilityClassifier classifier) {
        this.classifier = java.util.Objects.requireNonNull(classifier, "classifier");
    }

    public DistillationCorpusCandidate build(ProductionInputSnapshot input,
            String originalOutput, JsonElement claimFirewallOutcome,
            EligibilityEvidence evidence,
            DistillationCorpusCandidate.SourceProvenance provenance) {
        var decision = classifier.classify(evidence);
        var state = switch (decision.eligibility()) {
            case MODEL_TRAINING_ELIGIBLE ->
                    DistillationCorpusCandidate.CandidateState.ELIGIBLE_UNLABELED;
            case NEEDS_REVIEW, CONNECTED_VALIDATION_REQUIRED ->
                    DistillationCorpusCandidate.CandidateState.REVIEW_REQUIRED;
            default -> DistillationCorpusCandidate.CandidateState.REJECTED;
        };
        CandidateIdentity identity = new CandidateIdentity(1, input.providerInputSha256(),
                originalOutput == null ? "" : originalOutput, claimFirewallOutcome,
                decision, provenance);
        return new DistillationCorpusCandidate(1, ArtifactIds.candidate(identity), input,
                originalOutput, claimFirewallOutcome, decision, evidence, provenance,
                state, Instant.now());
    }

    private record CandidateIdentity(int schemaVersion, String productionInputHash,
            String originalOutput, JsonElement firewall,
            TrainingEligibilityClassifier.EligibilityDecision eligibility,
            DistillationCorpusCandidate.SourceProvenance provenance) { }
}
