package com.inigmasgames.persistentnpcs.conversation;

import java.util.Locale;

/** Explicit semantic mode for one NPC dialogue turn. */
public enum DialogueMode {
    ORDINARY_CONVERSATION,
    ENVIRONMENT_QUERY,
    CURRENT_WORLD_STATE,
    FICTIONAL_STORY,
    PROPOSED_PLAN,
    VALIDATED_ACTIVE_TASK,
    VALIDATED_QUEST,
    NPC_INITIATED_CURIOSITY;

    public static DialogueMode classify(
            String playerMessage, boolean hasActiveTask, boolean hasActiveQuest) {
        String text = normalize(playerMessage);
        if (text.startsWith("npc initiated curiosity")) {
            return NPC_INITIATED_CURIOSITY;
        }
        if (contains(text, "tell me a story", "tell me a tale", "can you tell a story",
                "make up a story", "fictional story")) {
            return FICTIONAL_STORY;
        }
        if (contains(text, "what do you see", "what can you see", "where are we",
                "where we are", "what's around", "whats around", "what is around",
                "what is that", "what's that", "whats that", "do you see the",
                "see the portal", "describe this place", "describe our surroundings")) {
            return ENVIRONMENT_QUERY;
        }
        if (contains(text, "what are you doing", "what are we doing", "where are you going",
                "are you following", "are you waiting", "what's on your mind",
                "whats on your mind", "how does today feel", "what is happening",
                "what's happening", "whats happening")) {
            return hasActiveTask ? VALIDATED_ACTIVE_TASK : CURRENT_WORLD_STATE;
        }
        if (hasActiveQuest && contains(text, "quest", "mission", "objective", "our task")) {
            return VALIDATED_QUEST;
        }
        if (contains(text, "what should we", "could we", "would you", "what's next",
                "whats next", "make a plan", "plan to", "suppose we")) {
            return PROPOSED_PLAN;
        }
        return ORDINARY_CONVERSATION;
    }

    /** Whole-response validation prevents unsafe raw SSE chunks reaching chat first. */
    public boolean buffersStreaming() {
        // Hytale chat cannot retract an already displayed SSE chunk. All modes therefore
        // keep provider streaming/TTFT instrumentation but release dialogue only after the
        // completed response passes current-scene validation.
        return this != ORDINARY_CONVERSATION;
    }

    private static boolean contains(String text, String... values) {
        return java.util.Arrays.stream(values).anyMatch(text::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }
}
