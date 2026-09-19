package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;
import java.util.List;

/** Versioned E0-E3 payload carried by the sole live TurnExecutionPlan. */
public record EpistemicContract(int schemaVersion, EpistemicFeatureMode mode,
        DialogueFrame dialogueFrame, EpistemicQueryPlan queryPlan,
        EvidencePacket evidence, Answerability answerability, AnswerPlan answerPlan,
        ClaimPolicy claimPolicy, EpistemicBudget budget, List<String> diagnoses,
        long planningMicros, Instant compiledAt) {
    public static final int SCHEMA_VERSION = 1;
    public EpistemicContract {
        if (schemaVersion < 1 || mode == null || dialogueFrame == null || queryPlan == null
                || evidence == null || answerability == null || answerPlan == null
                || claimPolicy == null || budget == null) {
            throw new IllegalArgumentException("complete versioned epistemic contract required");
        }
        diagnoses = List.copyOf(diagnoses == null ? List.of() : diagnoses);
        planningMicros = Math.max(0, planningMicros);
        compiledAt = compiledAt == null ? Instant.now() : compiledAt;
    }
}
