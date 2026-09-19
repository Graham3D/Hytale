package com.inigmasgames.persistentnpcs.training.candidate;

import com.inigmasgames.persistentnpcs.evaluation.EvaluationContracts;
import java.util.EnumMap;
import java.util.Map;

/** Stage-exit evidence consumed by the deterministic eligibility classifier. */
public record EligibilityEvidence(int schemaVersion,
        Map<EvaluationContracts.BoundaryId, BoundaryState> stageExits,
        EvaluationContracts.RootCauseDiagnosis diagnosis,
        BoundaryState oracleAndData,
        BoundaryState connectedRuntime,
        boolean artifactComplete,
        String sourceEvaluationRunId) {
    public static final int SCHEMA_VERSION = 1;
    public enum BoundaryState { PASS, FAIL, UNKNOWN, NOT_APPLICABLE }

    public EligibilityEvidence {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException(
                "unsupported eligibility evidence schema");
        EnumMap<EvaluationContracts.BoundaryId, BoundaryState> copy =
                new EnumMap<>(EvaluationContracts.BoundaryId.class);
        if (stageExits != null) copy.putAll(stageExits);
        stageExits = Map.copyOf(copy);
        oracleAndData = oracleAndData == null ? BoundaryState.UNKNOWN : oracleAndData;
        connectedRuntime = connectedRuntime == null ? BoundaryState.UNKNOWN : connectedRuntime;
        sourceEvaluationRunId = sourceEvaluationRunId == null ? "" : sourceEvaluationRunId.strip();
    }

    public BoundaryState state(EvaluationContracts.BoundaryId id) {
        return stageExits.getOrDefault(id, BoundaryState.UNKNOWN);
    }
}
