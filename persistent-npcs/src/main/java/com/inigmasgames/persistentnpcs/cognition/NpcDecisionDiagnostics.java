package com.inigmasgames.persistentnpcs.cognition;

import java.util.List;

/** Latest safe black-box diagnostics for the native inspector and operator trace. */
public record NpcDecisionDiagnostics(
        List<String> offeredActions,
        String rawStructuredDecision,
        String schemaValidationResult,
        List<String> rejectedFieldsOrActions,
        String actionValidationResult,
        String committedAgentOperation,
        String canonicalSpokenText,
        String finalActionResult,
        NpcDecision decision,
        List<String> groundingValidation) {

    public NpcDecisionDiagnostics {
        offeredActions = List.copyOf(offeredActions == null ? List.of() : offeredActions);
        rawStructuredDecision = clean(rawStructuredDecision);
        schemaValidationResult = clean(schemaValidationResult);
        rejectedFieldsOrActions = List.copyOf(
                rejectedFieldsOrActions == null ? List.of() : rejectedFieldsOrActions);
        actionValidationResult = clean(actionValidationResult);
        committedAgentOperation = clean(committedAgentOperation);
        canonicalSpokenText = clean(canonicalSpokenText);
        finalActionResult = clean(finalActionResult);
        groundingValidation = List.copyOf(
                groundingValidation == null ? List.of() : groundingValidation);
    }

    public NpcDecisionDiagnostics(List<String> offeredActions,
            String rawStructuredDecision, String schemaValidationResult,
            List<String> rejectedFieldsOrActions, String actionValidationResult,
            String committedAgentOperation, String canonicalSpokenText,
            String finalActionResult, NpcDecision decision) {
        this(offeredActions, rawStructuredDecision, schemaValidationResult,
                rejectedFieldsOrActions, actionValidationResult, committedAgentOperation,
                canonicalSpokenText, finalActionResult, decision, List.of());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public NpcDecisionDiagnostics withCanonicalSpokenText(String text) {
        return new NpcDecisionDiagnostics(offeredActions, rawStructuredDecision,
                schemaValidationResult, rejectedFieldsOrActions, actionValidationResult,
                committedAgentOperation, text, finalActionResult, decision,
                groundingValidation);
    }
}
