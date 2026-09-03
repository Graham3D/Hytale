package com.inigmasgames.persistentnpcs.cognition;

public record CognitionTurn(
        NpcSelfModel selfModel,
        NpcAppraisal appraisal,
        NpcResponsePlan responsePlan,
        long appraisalLatencyMillis,
        CognitionContext context,
        GroundedNpcDecision decision) {

    public CognitionTurn(
            NpcSelfModel selfModel,
            NpcAppraisal appraisal,
            NpcResponsePlan responsePlan,
            long appraisalLatencyMillis) {
        this(selfModel, appraisal, responsePlan, appraisalLatencyMillis, null, null);
    }

    public CognitionTurn withDecision(CognitionContext authoritativeContext,
            GroundedNpcDecision groundedDecision) {
        return new CognitionTurn(selfModel, appraisal, responsePlan,
                appraisalLatencyMillis, authoritativeContext, groundedDecision);
    }
}
