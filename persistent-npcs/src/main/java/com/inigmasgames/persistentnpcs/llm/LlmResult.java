package com.inigmasgames.persistentnpcs.llm;

public record LlmResult(
        String text,
        LlmLatency latency,
        java.util.List<LlmToolCall> toolCalls,
        String finishReason,
        LlmUsage usage,
        LlmReasoningTelemetry reasoningTelemetry) {

    public LlmResult {
        usage = usage == null ? LlmUsage.unknown() : usage;
        reasoningTelemetry = reasoningTelemetry == null
                ? LlmReasoningTelemetry.unknown() : reasoningTelemetry;
    }

    public LlmResult(String text, LlmLatency latency,
            java.util.List<LlmToolCall> toolCalls, String finishReason,
            LlmUsage usage) {
        this(text, latency, toolCalls, finishReason, usage,
                LlmReasoningTelemetry.unknown());
    }

    public LlmResult(String text, LlmLatency latency,
            java.util.List<LlmToolCall> toolCalls, String finishReason) {
        this(text, latency, toolCalls, finishReason, LlmUsage.unknown(),
                LlmReasoningTelemetry.unknown());
    }

    public LlmResult(String text, LlmLatency latency) {
        this(text, latency, java.util.List.of(), null, LlmUsage.unknown(),
                LlmReasoningTelemetry.unknown());
    }
}
