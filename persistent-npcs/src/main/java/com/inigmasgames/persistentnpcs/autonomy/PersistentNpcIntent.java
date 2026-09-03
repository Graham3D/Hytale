package com.inigmasgames.persistentnpcs.autonomy;

import java.time.Instant;
import java.util.UUID;

/** Exactly one persisted intention may be active for an NPC. */
public record PersistentNpcIntent(
        UUID intentId,
        String intentType,
        String actionId,
        GroundedStimulus target,
        CognitionActivity activity,
        double utility,
        String reason,
        Instant startedAt,
        Instant stateSince,
        int planStep,
        String lastResult,
        UUID operationId) {

    public PersistentNpcIntent(
            UUID intentId, String intentType, String actionId, GroundedStimulus target,
            CognitionActivity activity, double utility, String reason, Instant startedAt,
            Instant stateSince, int planStep, String lastResult) {
        this(intentId, intentType, actionId, target, activity, utility, reason,
                startedAt, stateSince, planStep, lastResult, null);
    }

    public PersistentNpcIntent withState(
            CognitionActivity next, Instant now, int step, String result) {
        return new PersistentNpcIntent(intentId, intentType, actionId, target, next,
                utility, reason, startedAt, now, step, result == null ? "" : result,
                operationId);
    }

    public PersistentNpcIntent withOperation(UUID id) {
        return new PersistentNpcIntent(intentId, intentType, actionId, target, activity,
                utility, reason, startedAt, stateSince, planStep, lastResult, id);
    }
}
