package com.inigmasgames.persistentnpcs.llm;

public record LlmProviderStatus(
        String endpoint,
        String model,
        boolean configured,
        boolean reachable,
        boolean streamingEnabled,
        String reason) {
}
