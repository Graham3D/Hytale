package com.inigmasgames.persistentnpcs.llm;

/** Black-box reasoning telemetry. Hidden reasoning text is intentionally never retained. */
public record LlmReasoningTelemetry(
        String requestedMode,
        String actualMode,
        boolean thinkingEnabled,
        int reasoningEventCount,
        int reasoningTokenCount,
        int finalAnswerTokenCount,
        boolean finalAnswerTokenCountExact,
        long promptEvaluationMillis) {

    public LlmReasoningTelemetry {
        requestedMode = clean(requestedMode);
        actualMode = clean(actualMode);
        reasoningEventCount = Math.max(0, reasoningEventCount);
        reasoningTokenCount = Math.max(-1, reasoningTokenCount);
        finalAnswerTokenCount = Math.max(0, finalAnswerTokenCount);
        promptEvaluationMillis = Math.max(-1, promptEvaluationMillis);
    }

    public static LlmReasoningTelemetry unknown() {
        return new LlmReasoningTelemetry("DEFAULT", "UNKNOWN", false,
                0, -1, 0, false, -1);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.strip();
    }
}
