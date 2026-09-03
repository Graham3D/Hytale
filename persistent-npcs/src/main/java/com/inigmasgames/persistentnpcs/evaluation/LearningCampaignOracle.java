package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.BeliefAssertion;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceSourceKind;
import java.util.List;

/** Provenance and contamination oracle for E4-E7 learning campaigns. */
public final class LearningCampaignOracle {
    public LearningVerdict validate(List<BeliefAssertion> assertions,
            List<String> generatedCanonicalSpeech) {
        List<BeliefAssertion> generatedTruth = assertions.stream().filter(value ->
                value.provenance().generatedSpeechOnly()
                        || value.provenance().sourceKind() == EvidenceSourceKind
                                .CONVERSATION_WORKSPACE
                                && generatedCanonicalSpeech.stream().anyMatch(speech ->
                                        speech.equalsIgnoreCase(value.statement()))).toList();
        List<BeliefAssertion> unsourced = assertions.stream().filter(value ->
                value.provenance().evidenceIds().isEmpty()
                        && value.provenance().sourceKind() != EvidenceSourceKind.AUTHORED_CANON
                        && value.provenance().sourceKind() != EvidenceSourceKind.SELF_STATE)
                .toList();
        return new LearningVerdict(generatedTruth.isEmpty() && unsourced.isEmpty(),
                generatedTruth.size(), unsourced.size());
    }
    public record LearningVerdict(boolean passed, int generatedSpeechTruthCount,
            int unsourcedAssertionCount) { }
}
