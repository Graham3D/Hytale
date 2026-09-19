package com.inigmasgames.persistentnpcs.llm;

/** Token accounting reported by the backend, or a clearly marked local estimate. */
public record LlmUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean exact) {
    public static LlmUsage unknown() { return new LlmUsage(0, 0, 0, false); }

    public double tokensPerSecond(long completionMillis) {
        return completionMillis <= 0 ? 0.0
                : completionTokens * 1_000.0 / completionMillis;
    }
}
