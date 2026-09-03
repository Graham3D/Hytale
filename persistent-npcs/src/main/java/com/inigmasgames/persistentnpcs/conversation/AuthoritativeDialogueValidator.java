package com.inigmasgames.persistentnpcs.conversation;

import java.util.Locale;

/** Final lexical authority boundary: Nemotron may phrase facts, never contradict them. */
public final class AuthoritativeDialogueValidator {
    public Result validate(String dialogue, CognitiveContextPlan plan) {
        String value = dialogue == null ? "" : dialogue.strip();
        if (plan == null || plan.authoritativeConstraints().isEmpty()) {
            return new Result(value, false, "NO_AUTHORITATIVE_CONSTRAINT");
        }
        String normalized = normalize(value);
        for (CognitiveContextPlan.AuthoritativeConstraint constraint
                : plan.authoritativeConstraints()) {
            boolean satisfied = switch (constraint.kind()) {
                case "SELF_IDENTITY" -> containsPhrase(normalized, constraint.value());
                case "AUTHORED_RELATIONSHIP" -> containsPhrase(normalized, constraint.subject())
                        && containsPhrase(normalized,
                                constraint.value().replace('_', ' '));
                default -> true;
            };
            if (!satisfied) {
                return new Result(constraint.naturalFallback(), true,
                        "CONTRADICTED_OR_OMITTED_" + constraint.kind());
            }
        }
        return new Result(value, false, "AUTHORITATIVE_CONSTRAINTS_SATISFIED");
    }

    private static boolean containsPhrase(String text, String phrase) {
        String expected = normalize(phrase);
        return !expected.isBlank() && (" " + text + " ").contains(" " + expected + " ");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }

    public record Result(String dialogue, boolean rewritten, String reason) { }
}
