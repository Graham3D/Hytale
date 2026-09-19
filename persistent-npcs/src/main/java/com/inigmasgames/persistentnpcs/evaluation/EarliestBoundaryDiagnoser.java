package com.inigmasgames.persistentnpcs.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reports the first observable contract breach and preserves downstream symptoms. */
public final class EarliestBoundaryDiagnoser {
    public EvaluationContracts.RootCauseDiagnosis diagnose(
            List<EvaluationContracts.StageVerdict> verdicts,
            List<EvaluationContracts.StageObservation> observations) {
        List<EvaluationContracts.StageVerdict> failed = verdicts.stream()
                .filter(value -> value.verdict() == EvaluationContracts.EvaluationVerdict.FAIL)
                .sorted(Comparator.comparingInt(value -> value.boundary().ordinal())).toList();
        if (failed.isEmpty()) return null;
        EvaluationContracts.StageVerdict earliest = failed.getFirst();
        ArrayList<String> symptoms = new ArrayList<>();
        failed.stream().skip(1).forEach(value -> symptoms.add(value.boundary() + ":"
                + value.invariantId() + " actual=" + value.actual()));
        List<Long> supporting = observations.stream().filter(value ->
                value.boundary() == earliest.boundary()).map(
                        EvaluationContracts.StageObservation::sequence).toList();
        return new EvaluationContracts.RootCauseDiagnosis(earliest.boundary(),
                failureClass(earliest.boundary()), earliest.invariantId(),
                owner(earliest.boundary()), earliest.expected(), earliest.actual(),
                supporting, symptoms, supporting.isEmpty() ? 0.70 : 0.95);
    }

    private static EvaluationContracts.FailureClass failureClass(
            EvaluationContracts.BoundaryId boundary) {
        return switch (boundary) {
            case INGRESS -> EvaluationContracts.FailureClass.INGRESS;
            case DIALOGUE_STATE -> EvaluationContracts.FailureClass.DIALOGUE_STATE;
            case QUERY_PLAN -> EvaluationContracts.FailureClass.ROUTE;
            case RETRIEVAL -> EvaluationContracts.FailureClass.RETRIEVAL;
            case ANSWERABILITY -> EvaluationContracts.FailureClass.ANSWERABILITY;
            case ANSWER_PLAN -> EvaluationContracts.FailureClass.ANSWER_PLAN;
            case TURN_PLAN -> EvaluationContracts.FailureClass.TURN_PLAN;
            case CONTEXT_RENDER -> EvaluationContracts.FailureClass.CONTEXT_RENDER;
            case PROVIDER -> EvaluationContracts.FailureClass.PROVIDER_REALIZATION;
            case CLAIM_FIREWALL -> EvaluationContracts.FailureClass.CLAIM_AUTHORITY;
            case CANONICAL_RESPONSE -> EvaluationContracts.FailureClass.CANONICAL_DELIVERY;
            case STATE_DELTA -> EvaluationContracts.FailureClass.STATE_LEARNING;
            case CLEANUP -> EvaluationContracts.FailureClass.CLEANUP;
        };
    }

    private static String owner(EvaluationContracts.BoundaryId boundary) {
        return switch (boundary) {
            case INGRESS -> "OrbisTurnCoordinator/EvaluationTextIngress";
            case DIALOGUE_STATE -> "DialogueStateTracker";
            case QUERY_PLAN -> "EpistemicQueryPlanner/TurnPlanCompiler";
            case RETRIEVAL -> "EpistemicEvidenceRetriever/MemoryStore";
            case ANSWERABILITY -> "AnswerabilityClassifier";
            case ANSWER_PLAN -> "EpistemicAnswerPlanner/ContractBudgetPlanner";
            case TURN_PLAN -> "TurnPlanCompiler";
            case CONTEXT_RENDER -> "ConversationContextBuilder/ContractPromptBuilder";
            case PROVIDER -> "Pinned production LLM provider";
            case CLAIM_FIREWALL -> "EpistemicClaimFirewall/DialogueClaimValidator";
            case CANONICAL_RESPONSE -> "CanonicalSpeechLedger/OrbisCognitionGateway";
            case STATE_DELTA -> "Memory/Belief/Relationship ingestion";
            case CLEANUP -> "OrbisTurnCoordinator/ResourceScheduler";
        };
    }
}
