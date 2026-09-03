package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.Set;

public record EpistemicQueryPlan(int schemaVersion, String queryKind,
        Set<String> entityKeys, Set<String> predicateKeys,
        Set<EvidenceSourceKind> allowedSources, boolean requireCurrentPerception,
        boolean requireSelfState, boolean requireMemory, boolean requireRelationship,
        boolean requireConversationWorkspace, boolean includeContradictions,
        boolean evidenceMandatory, boolean allowFabrication, boolean allowMemorySubstitution,
        String requestedAction, String sourceProposition,
        Set<String> evidenceCategories, boolean ambiguous, String ambiguityReason,
        int maxEvidenceItems, int maxTokens, String abstentionPolicy,
        List<String> currentOwnerHints) {
    public static final int SCHEMA_VERSION = 1;
    public EpistemicQueryPlan {
        if (schemaVersion < 1) throw new IllegalArgumentException("schema version required");
        queryKind = clean(queryKind); abstentionPolicy = clean(abstentionPolicy);
        requestedAction = clean(requestedAction); sourceProposition = clean(sourceProposition);
        ambiguityReason = clean(ambiguityReason);
        entityKeys = Set.copyOf(entityKeys == null ? Set.of() : entityKeys);
        predicateKeys = Set.copyOf(predicateKeys == null ? Set.of() : predicateKeys);
        allowedSources = Set.copyOf(allowedSources == null ? Set.of() : allowedSources);
        evidenceCategories = Set.copyOf(evidenceCategories == null ? Set.of() : evidenceCategories);
        currentOwnerHints = List.copyOf(currentOwnerHints == null ? List.of() : currentOwnerHints);
        maxEvidenceItems = Math.max(0, maxEvidenceItems); maxTokens = Math.max(0, maxTokens);
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
