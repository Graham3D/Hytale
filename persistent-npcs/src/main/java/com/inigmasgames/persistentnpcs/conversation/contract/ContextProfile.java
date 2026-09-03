package com.inigmasgames.persistentnpcs.conversation.contract;

import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict route-scoped context allowlist and deterministic section ceilings. */
public record ContextProfile(
        String id,
        Set<String> allowedSections,
        Map<String, Integer> sectionTokenCeilings,
        int promptTokenCeiling) {

    public ContextProfile {
        id = id == null || id.isBlank() ? "UNKNOWN" : id.strip();
        allowedSections = Set.copyOf(allowedSections == null ? Set.of() : allowedSections);
        sectionTokenCeilings = Map.copyOf(
                sectionTokenCeilings == null ? Map.of() : sectionTokenCeilings);
        if (promptTokenCeiling < 64) throw new IllegalArgumentException(
                "context profile prompt ceiling must be at least 64 tokens");
    }

    public static ContextProfile forRoute(AdaptiveReasoningPolicy policy,
            boolean deterministicAction, boolean discretionaryChoice) {
        AdaptiveReasoningPolicy route = policy == null
                ? AdaptiveReasoningPolicy.DELIBERATIVE : policy;
        if (deterministicAction) return profile("DIRECT_ACTION_RESULT", 1_400,
                Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                        "RECENT_CONVERSATION", "SEMANTIC_WORLD", "ACTIONS"),
                Map.of("PROFILE", 260, "PERSONALITY", 180, "PLAYER_RELATIONSHIP", 120,
                        "RECENT_CONVERSATION", 260, "SEMANTIC_WORLD", 320,
                        "ACTIONS", 120));
        if (discretionaryChoice) return profile("DISCRETIONARY_CHOICE", 2_000,
                Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                        "RELATIONSHIPS", "RECENT_CONVERSATION", "MEMORIES", "BELIEFS",
                        "GOALS", "OBLIGATIONS", "ACTIONS"),
                Map.of("PROFILE", 280, "PERSONALITY", 200, "PLAYER_RELATIONSHIP", 120,
                        "RELATIONSHIPS", 120, "RECENT_CONVERSATION", 280,
                        "MEMORIES", 220, "BELIEFS", 160, "GOALS", 140,
                        "OBLIGATIONS", 140, "ACTIONS", 180));
        return switch (route) {
            case FAST_DIALOGUE -> profile("FAST_DIALOGUE", 600,
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RECENT_CONVERSATION"),
                    Map.of("PROFILE", 180, "PERSONALITY", 160,
                            "PLAYER_RELATIONSHIP", 100, "RECENT_CONVERSATION", 160));
            case GROUNDED_DIALOGUE -> profile("GROUNDED_DIALOGUE", 1_200,
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RECENT_CONVERSATION", "MEMORIES", "BELIEFS",
                            "SEMANTIC_WORLD", "TASKS", "OBLIGATIONS", "SHARED_PLANS"),
                    Map.of("PROFILE", 220, "PERSONALITY", 160,
                            "PLAYER_RELATIONSHIP", 100, "RECENT_CONVERSATION", 220,
                            "MEMORIES", 180, "BELIEFS", 140, "SEMANTIC_WORLD", 300,
                            "TASKS", 100, "OBLIGATIONS", 80, "SHARED_PLANS", 100));
            case DIRECT_ACTION -> forRoute(route, true, false);
            case DELIBERATIVE -> profile("DELIBERATIVE", 3_000,
                    Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP",
                            "RELATIONSHIPS", "RECENT_CONVERSATION", "MEMORIES", "BELIEFS",
                            "SEMANTIC_WORLD", "TASKS", "GOALS", "OBLIGATIONS",
                            "SHARED_PLANS", "ACTIONS"),
                    Map.ofEntries(Map.entry("PROFILE", 300), Map.entry("PERSONALITY", 220),
                            Map.entry("PLAYER_RELATIONSHIP", 120),
                            Map.entry("RELATIONSHIPS", 160),
                            Map.entry("RECENT_CONVERSATION", 320), Map.entry("MEMORIES", 400),
                            Map.entry("BELIEFS", 260), Map.entry("SEMANTIC_WORLD", 420),
                            Map.entry("TASKS", 180), Map.entry("GOALS", 180),
                            Map.entry("OBLIGATIONS", 180), Map.entry("SHARED_PLANS", 180),
                            Map.entry("ACTIONS", 220)));
            case AUTONOMOUS_DELIBERATION -> profile("AUTONOMOUS", 3_000,
                    Set.of("PROFILE", "PERSONALITY", "RELATIONSHIPS", "MEMORIES",
                            "BELIEFS", "SEMANTIC_WORLD", "TASKS", "GOALS",
                            "OBLIGATIONS", "SHARED_PLANS", "ACTIONS"),
                    Map.ofEntries(Map.entry("PROFILE", 300), Map.entry("PERSONALITY", 220),
                            Map.entry("RELATIONSHIPS", 160), Map.entry("MEMORIES", 400),
                            Map.entry("BELIEFS", 260), Map.entry("SEMANTIC_WORLD", 420),
                            Map.entry("TASKS", 220), Map.entry("GOALS", 220),
                            Map.entry("OBLIGATIONS", 180), Map.entry("SHARED_PLANS", 220),
                            Map.entry("ACTIONS", 240)));
        };
    }

    public Restriction restrict(CognitiveContextPlan source) {
        CognitiveContextPlan original = source == null
                ? CognitiveContextPlan.full("UNKNOWN") : source;
        LinkedHashSet<String> included = new LinkedHashSet<>(original.includedSections());
        included.retainAll(allowedSections);
        LinkedHashSet<String> omitted = new LinkedHashSet<>(original.includedSections());
        omitted.removeAll(included);
        omitted.addAll(original.excludedSections());
        CognitiveContextPlan restricted = new CognitiveContextPlan(original.depth(),
                original.detectedIntent(), included, omitted,
                original.authoritativeConstraints());
        return new Restriction(restricted, List.copyOf(omitted), omitted.isEmpty()
                ? "NONE" : "CONTEXT_PROFILE_ALLOWLIST");
    }

    private static ContextProfile profile(String id, int ceiling, Set<String> sections,
            Map<String, Integer> sectionCeilings) {
        return new ContextProfile(id, sections, sectionCeilings, ceiling);
    }

    public record Restriction(CognitiveContextPlan plan, List<String> omittedSections,
            String pruningReason) { }
}
