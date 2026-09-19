package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Intent-first routing that prevents unrelated NPC state from polluting simple dialogue. */
public final class CognitiveContextRouter {
    private static final Set<String> ALL = Set.of("PROFILE", "PERSONALITY",
            "PLAYER_RELATIONSHIP", "RELATIONSHIPS", "MEMORIES", "BELIEFS",
            "SEMANTIC_WORLD", "WEATHER", "TASKS", "GOALS", "OBLIGATIONS",
            "SHARED_PLANS", "ACTIONS", "RECENT_CONVERSATION");

    private CognitiveContextRouter() { }

    public static CognitiveContextPlan route(NpcProfile speaker, String playerMessage,
            DialogueMode mode, NpcProfileRegistry profiles, RelationshipStore relationships) {
        String text = normalize(playerMessage);
        List<CognitiveContextPlan.AuthoritativeConstraint> facts = directFacts(
                speaker, text, profiles, relationships);
        if (!facts.isEmpty()) {
            Set<String> included = Set.of("PROFILE", "PERSONALITY", "RELATIONSHIPS");
            return plan(CognitiveDepth.DIRECT_FACT,
                    facts.getFirst().kind().equals("SELF_IDENTITY")
                            ? "QUERY_SELF_IDENTITY" : "QUERY_AUTHORITATIVE_RELATIONSHIP",
                    included, facts);
        }
        if (isSubjectiveOrSocialInvitation(text)) {
            return plan(CognitiveDepth.SIMPLE_SOCIAL, "SUBJECTIVE_OR_SOCIAL_INVITATION",
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RECENT_CONVERSATION", "GOALS"), List.of());
        }
        // Conversational repair refers to the immediately preceding NPC utterance. It does not
        // request crafting merely because English phrases such as "doesn't make sense" contain
        // the verb "make", and it does not need tasks/goals/action schemas or private reasoning.
        if (isDialogueClarification(text)) {
            return plan(CognitiveDepth.CONTEXTUAL_CONVERSATION,
                    "CLARIFY_PREVIOUS_DIALOGUE",
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RECENT_CONVERSATION"), List.of());
        }
        // Explicit perception questions must be classified before the broad "can you"
        // action heuristic. Seeing/identifying a currently perceived object is a bounded
        // grounded lookup, not an action commitment or planning request.
        if (isEnvironmentQuery(text, mode)) {
            LinkedHashSet<String> included = new LinkedHashSet<>(Set.of(
                    "PROFILE", "PERSONALITY", "SEMANTIC_WORLD", "RECENT_CONVERSATION"));
            if (asksWeather(text)) included.add("WEATHER");
            return plan(CognitiveDepth.CONTEXTUAL_CONVERSATION,
                    asksWeather(text) ? "QUERY_WEATHER"
                            : asksHeldItem(text) ? "QUERY_HELD_ITEM"
                                    : "QUERY_CURRENT_ENVIRONMENT",
                    included, List.of());
        }
        if (mode == DialogueMode.CURRENT_WORLD_STATE
                || mode == DialogueMode.VALIDATED_ACTIVE_TASK) {
            return plan(CognitiveDepth.CONTEXTUAL_CONVERSATION,
                    "QUERY_CURRENT_ACTIVITY",
                    Set.of("PROFILE", "PERSONALITY", "SEMANTIC_WORLD", "TASKS",
                            "OBLIGATIONS", "SHARED_PLANS", "RECENT_CONVERSATION"),
                    List.of());
        }
        if (isComplex(text, mode)) {
            CognitiveContextPlan full = CognitiveContextPlan.full(
                    detectedComplexIntent(text, mode));
            if (!asksWeather(text)) return full;
            LinkedHashSet<String> included = new LinkedHashSet<>(full.includedSections());
            included.add("WEATHER");
            LinkedHashSet<String> excluded = new LinkedHashSet<>(full.excludedSections());
            excluded.remove("WEATHER");
            return new CognitiveContextPlan(full.depth(), full.detectedIntent(), included,
                    excluded, full.authoritativeConstraints());
        }
        if (isRecallOrRelationshipConversation(text)) {
            return plan(CognitiveDepth.CONTEXTUAL_CONVERSATION,
                    text.contains("remember") ? "EXPLICIT_RECALL" : "CONTEXTUAL_SOCIAL",
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RELATIONSHIPS", "MEMORIES", "BELIEFS",
                            "RECENT_CONVERSATION"), List.of());
        }
        return plan(CognitiveDepth.SIMPLE_SOCIAL, detectedSocialIntent(text),
                Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                        "RECENT_CONVERSATION"), List.of());
    }

    private static List<CognitiveContextPlan.AuthoritativeConstraint> directFacts(
            NpcProfile speaker, String text, NpcProfileRegistry profiles,
            RelationshipStore relationships) {
        if (asksIdentity(text)) {
            String identity = speaker.selfIdentity() == null || speaker.selfIdentity().isBlank()
                    ? speaker.name() : speaker.selfIdentity();
            return List.of(new CognitiveContextPlan.AuthoritativeConstraint(
                    "SELF_IDENTITY", speaker.name(), identity,
                    "Your identity is " + identity + ".",
                    "PROFILE:stableId=" + speaker.id(), "I'm " + identity + "."));
        }
        if (!asksRelationship(text) || profiles == null || relationships == null) {
            return List.of();
        }
        for (NpcProfile target : profiles.profiles()) {
            if (target.id().equals(speaker.id()) || !containsName(text, target.name())) continue;
            RelationshipRecord relationship = relationships.get(speaker.id(), target.id())
                    .filter(RelationshipRecord::knowsEntity).orElse(null);
            if (relationship == null || relationship.relationshipType().isBlank()) continue;
            String kind = natural(relationship.relationshipType());
            String fallback = target.name() + " is my " + kind + ".";
            if (!relationship.description().isBlank()) {
                String first = relationship.description().split("(?<=[.!?])\\s+", 2)[0];
                if (!first.toLowerCase(Locale.ROOT).contains(target.name().toLowerCase(Locale.ROOT))) {
                    first = fallback;
                }
                fallback = first;
            }
            return List.of(new CognitiveContextPlan.AuthoritativeConstraint(
                    "AUTHORED_RELATIONSHIP", target.name(), relationship.relationshipType(),
                    target.name() + " is your " + kind + ". " + relationship.description(),
                    "RELATIONSHIP:stableTarget=" + target.id(), fallback));
        }
        return List.of();
    }

    private static CognitiveContextPlan plan(CognitiveDepth depth, String intent,
            Set<String> included,
            List<CognitiveContextPlan.AuthoritativeConstraint> constraints) {
        LinkedHashSet<String> excluded = new LinkedHashSet<>(ALL);
        excluded.removeAll(included);
        return new CognitiveContextPlan(depth, intent, included, excluded, constraints);
    }

    private static boolean asksIdentity(String text) {
        return text.contains("your name") || text.contains("who are you")
                || text.contains("what are you called") || text.contains("identify yourself");
    }

    private static boolean asksRelationship(String text) {
        return text.matches(".*\\b(?:who|what) is .+ (?:to you|your)\\b.*")
                || text.matches(".*\\bhow do you know\\b.*")
                || text.contains("relationship with") || text.contains("related to");
    }

    private static boolean containsName(String text, String name) {
        return (" " + text + " ").contains(" " + normalize(name) + " ");
    }

    private static boolean isComplex(String text, DialogueMode mode) {
        if (mode == DialogueMode.PROPOSED_PLAN || mode == DialogueMode.FICTIONAL_STORY) return true;
        if (text.matches(".*\\bwhere (?:is|are) [\\p{L}][\\p{L}'-]*\\b.*")
                && !text.contains("where are we")) return true;
        boolean explicitAction = text.matches(".*\\b(?:adventure|promise|commit|quest|mission|danger|attack|"
                + "betray|threat|follow|escort|lead|bring|deliver|craft|build|find|"
                + "place|take|give|go with|come with|help me)\\b.*");
        boolean authoredMakeRequest = text.matches(".*\\bmake (?:me|us|a|an|the|some|this|that)\\b.*");
        return explicitAction || authoredMakeRequest;
    }

    private static boolean isSubjectiveOrSocialInvitation(String text) {
        boolean subjective = text.matches(".*\\b(?:what do you (?:like|love|want|prefer|think|feel)|"
                + "how do you feel|what's your opinion|whats your opinion)\\b.*")
                ;
        boolean invitation = text.matches("^(?:would you like|do you want) to .*")
                && !text.matches(".*\\b(?:adventure|quest|mission|follow|escort|lead|"
                        + "help me|bring|deliver|craft|build|attack|defend)\\b.*");
        return subjective || invitation;
    }

    private static boolean isDialogueClarification(String text) {
        return text.matches(".*\\b(?:what do you mean|what did you mean|"
                + "doesn't make (?:any )?sense|does not make (?:any )?sense|"
                + "that makes no sense|clarify|explain what you mean|"
                + "explain what you meant|why did you say that)\\b.*");
    }

    private static boolean isEnvironmentQuery(String text, DialogueMode mode) {
        return mode == DialogueMode.ENVIRONMENT_QUERY
                || text.contains("what do you see") || text.contains("what can you see")
                || text.contains("around us") || text.contains("where are we")
                || asksHeldItem(text)
                || text.matches(".*\\b(?:what time|time is it|time of day)\\b.*")
                || asksWeather(text);
    }

    private static boolean asksHeldItem(String text) {
        return text.matches(".*\\b(?:what(?:'s| is) in my hand|what am i holding|"
                + "what(?:'s| is) this in my hand|can you see what(?:'s| is) in my hand|"
                + "can you see (?:this|the item) in my hand|identify what i(?:'m| am) holding)\\b.*");
    }

    private static boolean asksWeather(String text) {
        return text.matches(".*\\b(?:weather|rain|raining|storm|snow|wind|sunny|temperature)\\b.*");
    }

    private static boolean isRecallOrRelationshipConversation(String text) {
        return text.matches(".*\\b(?:remember|recall|earlier|yesterday|last time|"
                + "feel|felt|family|friend|love|hate|trust|afraid|regret)\\b.*");
    }

    private static String detectedComplexIntent(String text, DialogueMode mode) {
        if (mode == DialogueMode.PROPOSED_PLAN || text.contains("adventure")) return "PROPOSE_PLAN";
        if (text.matches(".*\\b(?:danger|attack|threat)\\b.*")) return "RESPOND_TO_DANGER";
        if (mode == DialogueMode.FICTIONAL_STORY) return "TELL_FICTIONAL_STORY";
        return "REQUEST_ACTION_OR_COMMITMENT";
    }

    private static String detectedSocialIntent(String text) {
        if (text.matches(".*\\b(?:hello|hi|hey|greetings|good morning|good evening)\\b.*")) {
            return "GREETING";
        }
        if (text.matches(".*\\b(?:thanks|thank you)\\b.*")) return "ACKNOWLEDGEMENT";
        if (text.matches(".*\\bmy name is\\b.*")) return "PLAYER_SELF_DISCLOSURE";
        return "SIMPLE_SOCIAL_RESPONSE";
    }

    private static String natural(String value) {
        return normalize(value == null ? "" : value.replace('_', ' '));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }
}
