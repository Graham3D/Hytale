package com.inigmasgames.persistentnpcs.training.candidate;

import com.inigmasgames.persistentnpcs.evaluation.EvaluationContracts;
import java.util.EnumSet;

/** Fail-closed classifier that reuses the existing earliest-boundary diagnosis. */
public final class TrainingEligibilityClassifier {
    private static final EnumSet<EvaluationContracts.BoundaryId> MODEL_INPUT_BOUNDARIES =
            EnumSet.range(EvaluationContracts.BoundaryId.INGRESS,
                    EvaluationContracts.BoundaryId.CONTEXT_RENDER);

    public EligibilityDecision classify(EligibilityEvidence evidence) {
        if (evidence == null || !evidence.artifactComplete()) {
            return decision(TrainingEligibility.NEEDS_REVIEW,
                    "COMPLETE_EVALUATION_ARTIFACT_REQUIRED", evidence);
        }
        if (evidence.oracleAndData() == EligibilityEvidence.BoundaryState.FAIL) {
            return decision(TrainingEligibility.ORACLE_OR_DATA_REPAIR_REQUIRED,
                    "ORACLE_OR_DATA_FAILED", evidence);
        }
        if (unknownRequiredInput(evidence)) {
            return decision(TrainingEligibility.NEEDS_REVIEW,
                    "MODEL_INPUT_STAGE_EXIT_UNKNOWN", evidence);
        }
        if (failedInput(evidence)) {
            return decision(TrainingEligibility.ORBIS_SOURCE_REPAIR_REQUIRED,
                    "PRE_PROVIDER_ORBIS_BOUNDARY_FAILED", evidence);
        }
        EvaluationContracts.RootCauseDiagnosis diagnosis = evidence.diagnosis();
        if (diagnosis == null) {
            return decision(TrainingEligibility.NOT_TRAINABLE,
                    "NO_MODEL_DEFECT_DIAGNOSED", evidence);
        }
        if (diagnosis.earliestFailedBoundary() == EvaluationContracts.BoundaryId.PROVIDER
                && diagnosis.failureClass()
                == EvaluationContracts.FailureClass.PROVIDER_REALIZATION) {
            return decision(TrainingEligibility.MODEL_TRAINING_ELIGIBLE,
                    "PROVIDER_REALIZATION_IS_EARLIEST_FAILURE", evidence);
        }
        if (requiresConnectedValidation(diagnosis)
                && evidence.connectedRuntime() != EligibilityEvidence.BoundaryState.PASS) {
            return decision(TrainingEligibility.CONNECTED_VALIDATION_REQUIRED,
                    "CONNECTED_RUNTIME_EVIDENCE_REQUIRED", evidence);
        }
        if (diagnosis.failureClass() == EvaluationContracts.FailureClass.LIFECYCLE
                || diagnosis.failureClass() == EvaluationContracts.FailureClass.RESOURCE
                || diagnosis.failureClass() == EvaluationContracts.FailureClass.CLEANUP) {
            return decision(TrainingEligibility.NOT_TRAINABLE,
                    "NON_MODEL_RUNTIME_FAILURE", evidence);
        }
        return decision(TrainingEligibility.ORBIS_SOURCE_REPAIR_REQUIRED,
                "EARLIEST_FAILURE_OWNED_BY_ORBIS", evidence);
    }

    private static boolean failedInput(EligibilityEvidence evidence) {
        return MODEL_INPUT_BOUNDARIES.stream().anyMatch(boundary ->
                evidence.state(boundary) == EligibilityEvidence.BoundaryState.FAIL);
    }

    private static boolean unknownRequiredInput(EligibilityEvidence evidence) {
        return MODEL_INPUT_BOUNDARIES.stream().anyMatch(boundary ->
                evidence.state(boundary) == EligibilityEvidence.BoundaryState.UNKNOWN);
    }

    private static boolean requiresConnectedValidation(
            EvaluationContracts.RootCauseDiagnosis diagnosis) {
        return switch (diagnosis.failureClass()) {
            case CANONICAL_DELIVERY, ACTION_TRUTH, PERSISTENCE, CLEANUP, LIFECYCLE,
                    RESOURCE -> true;
            default -> false;
        };
    }

    private static EligibilityDecision decision(TrainingEligibility eligibility,
            String rule, EligibilityEvidence evidence) {
        String boundary = evidence == null || evidence.diagnosis() == null ? ""
                : evidence.diagnosis().earliestFailedBoundary().name();
        return new EligibilityDecision(1, eligibility, rule, boundary,
                evidence == null ? "" : evidence.sourceEvaluationRunId());
    }

    public record EligibilityDecision(int schemaVersion, TrainingEligibility eligibility,
            String rule, String earliestFailedBoundary, String sourceEvaluationRunId) { }
}
