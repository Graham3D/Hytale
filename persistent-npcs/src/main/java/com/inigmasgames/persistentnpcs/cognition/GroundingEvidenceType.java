package com.inigmasgames.persistentnpcs.cognition;

import java.util.Locale;

/** Deterministic evidence namespaces used by the structured decision contract. */
public enum GroundingEvidenceType {
    PROFILE,
    RELATIONSHIP,
    MEMORY_OR_BELIEF,
    SEMANTIC_WORLD,
    TASK_PLAN_OR_OBLIGATION,
    ACTION_RESULT,
    RECENT_DELIVERED_CONVERSATION,
    UNKNOWN;

    public static GroundingEvidenceType fromReference(String reference) {
        String prefix = reference == null ? "" : reference.strip();
        int separator = prefix.indexOf(':');
        if (separator >= 0) prefix = prefix.substring(0, separator);
        return switch (prefix.toUpperCase(Locale.ROOT)) {
            case "PROFILE" -> PROFILE;
            case "RELATIONSHIP" -> RELATIONSHIP;
            case "MEMORY", "BELIEF" -> MEMORY_OR_BELIEF;
            case "PERCEPTION", "ENVIRONMENT", "WORLD_TIME", "KNOWN_NPC_LOCATOR",
                    "AUTHORITATIVE_LOCATOR" -> SEMANTIC_WORLD;
            case "TASK", "SHARED_PLAN", "OBLIGATION" -> TASK_PLAN_OR_OBLIGATION;
            case "ACTION_RESULT" -> ACTION_RESULT;
            case "CONVERSATION", "PLAYER_UTTERANCE" -> RECENT_DELIVERED_CONVERSATION;
            default -> UNKNOWN;
        };
    }
}
