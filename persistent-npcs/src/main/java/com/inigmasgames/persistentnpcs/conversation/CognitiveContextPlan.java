package com.inigmasgames.persistentnpcs.conversation;

import java.util.List;
import java.util.Set;

/** Deterministic, inspectable routing decision made before context collection. */
public record CognitiveContextPlan(
        CognitiveDepth depth,
        String detectedIntent,
        Set<String> includedSections,
        Set<String> excludedSections,
        List<AuthoritativeConstraint> authoritativeConstraints) {

    public CognitiveContextPlan {
        depth = depth == null ? CognitiveDepth.COMPLEX_INTENT : depth;
        detectedIntent = detectedIntent == null ? "UNKNOWN" : detectedIntent;
        includedSections = Set.copyOf(includedSections == null ? Set.of() : includedSections);
        excludedSections = Set.copyOf(excludedSections == null ? Set.of() : excludedSections);
        authoritativeConstraints = List.copyOf(authoritativeConstraints == null
                ? List.of() : authoritativeConstraints);
    }

    public boolean includes(String section) {
        return includedSections.contains(section);
    }

    public String constraintBlock() {
        return authoritativeConstraints.isEmpty() ? "None."
                : authoritativeConstraints.stream()
                        .map(value -> "- " + value.statement() + " [" + value.evidence() + "]")
                        .collect(java.util.stream.Collectors.joining("\n"));
    }

    public CognitiveContextPlan withoutSections(Set<String> sections) {
        java.util.LinkedHashSet<String> included = new java.util.LinkedHashSet<>(includedSections);
        java.util.LinkedHashSet<String> excluded = new java.util.LinkedHashSet<>(excludedSections);
        if (sections != null) {
            included.removeAll(sections);
            excluded.addAll(sections);
        }
        return new CognitiveContextPlan(depth, detectedIntent, included, excluded,
                authoritativeConstraints);
    }

    public static CognitiveContextPlan full(String intent) {
        return new CognitiveContextPlan(CognitiveDepth.COMPLEX_INTENT, intent,
                Set.of("PROFILE", "PERSONALITY", "PLAYER_RELATIONSHIP", "RELATIONSHIPS",
                        "MEMORIES", "BELIEFS", "SEMANTIC_WORLD", "TASKS", "GOALS",
                        "OBLIGATIONS", "SHARED_PLANS", "ACTIONS", "RECENT_CONVERSATION"),
                Set.of("WEATHER"), List.of());
    }

    public record AuthoritativeConstraint(
            String kind, String subject, String value, String statement,
            String evidence, String naturalFallback) {
        public AuthoritativeConstraint {
            kind = clean(kind);
            subject = clean(subject);
            value = clean(value);
            statement = clean(statement);
            evidence = clean(evidence);
            naturalFallback = clean(naturalFallback);
        }

        private static String clean(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").strip();
        }
    }
}
