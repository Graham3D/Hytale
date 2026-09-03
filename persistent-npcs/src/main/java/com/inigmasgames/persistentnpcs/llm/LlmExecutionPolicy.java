package com.inigmasgames.persistentnpcs.llm;

import java.util.List;

/** Deterministic Orbis-owned execution policy for one provider request. */
public record LlmExecutionPolicy(
        String reasoningPolicy,
        ReasoningMode requestedReasoningMode,
        List<String> routeReasonCodes,
        int finalAnswerTokenBudget) {

    public enum ReasoningMode {
        DEFAULT,
        DISABLED,
        ENABLED
    }

    public LlmExecutionPolicy {
        reasoningPolicy = clean(reasoningPolicy, "UNSPECIFIED");
        requestedReasoningMode = requestedReasoningMode == null
                ? ReasoningMode.DEFAULT : requestedReasoningMode;
        routeReasonCodes = List.copyOf(routeReasonCodes == null ? List.of()
                : routeReasonCodes.stream().filter(value -> value != null && !value.isBlank())
                        .map(String::strip).toList());
        finalAnswerTokenBudget = Math.max(0, finalAnswerTokenBudget);
    }

    public static LlmExecutionPolicy unspecified() {
        return new LlmExecutionPolicy("UNSPECIFIED", ReasoningMode.DEFAULT, List.of(), 0);
    }

    public boolean thinkingEnabled() {
        return requestedReasoningMode == ReasoningMode.ENABLED;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
