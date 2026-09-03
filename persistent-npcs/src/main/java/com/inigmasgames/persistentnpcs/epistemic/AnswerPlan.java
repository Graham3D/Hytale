package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.Set;

public record AnswerPlan(int schemaVersion, String answerKind,
        List<String> authorizedPropositions, List<EvidenceRef> evidence,
        String uncertaintyMode, int maxSentences, int maxObjectiveClaims,
        Set<String> requiredSlots, Set<String> forbiddenClaimClasses, String status,
        String responseGoal, List<String> unsupportedRequestedProperties,
        String requestedAction, List<String> uncertaintyReasons,
        DisclosureDecision disclosureDecision, String secretId) {
    public static final int SCHEMA_VERSION = 2;
    public AnswerPlan {
        if (schemaVersion < 1) throw new IllegalArgumentException("schema version required");
        answerKind = clean(answerKind); uncertaintyMode = clean(uncertaintyMode);
        status = clean(status); responseGoal = clean(responseGoal);
        requestedAction = clean(requestedAction);
        authorizedPropositions = List.copyOf(authorizedPropositions == null
                ? List.of() : authorizedPropositions);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        requiredSlots = Set.copyOf(requiredSlots == null ? Set.of() : requiredSlots);
        forbiddenClaimClasses = Set.copyOf(forbiddenClaimClasses == null
                ? Set.of() : forbiddenClaimClasses);
        unsupportedRequestedProperties = List.copyOf(unsupportedRequestedProperties == null
                ? List.of() : unsupportedRequestedProperties);
        uncertaintyReasons = List.copyOf(uncertaintyReasons == null
                ? List.of() : uncertaintyReasons);
        disclosureDecision = disclosureDecision == null
                ? DisclosureDecision.SHARE : disclosureDecision.safe();
        secretId = clean(secretId);
        maxSentences = Math.max(0, maxSentences); maxObjectiveClaims = Math.max(0, maxObjectiveClaims);
    }
    /** E0-E5 source-compatible constructor. */
    public AnswerPlan(int schemaVersion, String answerKind,
            List<String> authorizedPropositions, List<EvidenceRef> evidence,
            String uncertaintyMode, int maxSentences, int maxObjectiveClaims,
            Set<String> requiredSlots, Set<String> forbiddenClaimClasses, String status,
            String responseGoal, List<String> unsupportedRequestedProperties,
            String requestedAction, List<String> uncertaintyReasons) {
        this(schemaVersion, answerKind, authorizedPropositions, evidence, uncertaintyMode,
                maxSentences, maxObjectiveClaims, requiredSlots, forbiddenClaimClasses,
                status, responseGoal, unsupportedRequestedProperties, requestedAction,
                uncertaintyReasons, DisclosureDecision.SHARE, "");
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
