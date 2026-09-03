package com.inigmasgames.persistentnpcs.config;

public record FrameworkConfig(
        String endpoint,
        String model,
        String apiKey,
        int connectTimeoutMillis,
        int requestTimeoutMillis,
        double temperature,
        int maxTokens,
        int maxPlayerMessageCharacters,
        int recentMemoryCount,
        int maxMemoryRecords,
        int sessionIdleSeconds,
        Boolean streamResponses,
        Integer responseStartTimeoutMillis,
        Integer streamIdleTimeoutMillis,
        String reasoningEffort,
        Integer maxConcurrentLlmRequests,
        Integer autonomousRequestsPerMinute,
        Integer npcToNpcRequestsPerMinute,
        Integer perPlayerRequestsPerMinute,
        Integer perNpcAutonomyCooldownSeconds,
        java.util.Map<String, ModelTierConfig> modelTiers,
        Integer deepConversationTurnThreshold) {

    public FrameworkConfig(
            String endpoint,
            String model,
            String apiKey,
            int connectTimeoutMillis,
            int requestTimeoutMillis,
            double temperature,
            int maxTokens,
            int maxPlayerMessageCharacters,
            int recentMemoryCount,
            int maxMemoryRecords,
            int sessionIdleSeconds,
            Boolean streamResponses,
            Integer responseStartTimeoutMillis,
            Integer streamIdleTimeoutMillis,
            String reasoningEffort) {
        this(endpoint, model, apiKey, connectTimeoutMillis, requestTimeoutMillis,
                temperature, maxTokens, maxPlayerMessageCharacters, recentMemoryCount,
                maxMemoryRecords, sessionIdleSeconds, streamResponses,
                responseStartTimeoutMillis, streamIdleTimeoutMillis, reasoningEffort,
                2, 6, 4, 20, 60, java.util.Map.of(), 6);
    }

    public FrameworkConfig(
            String endpoint,
            String model,
            String apiKey,
            int connectTimeoutMillis,
            int requestTimeoutMillis,
            double temperature,
            int maxTokens,
            int maxPlayerMessageCharacters,
            int recentMemoryCount,
            int maxMemoryRecords,
            int sessionIdleSeconds,
            Boolean streamResponses,
            Integer responseStartTimeoutMillis,
            Integer streamIdleTimeoutMillis,
            String reasoningEffort,
            Integer maxConcurrentLlmRequests,
            Integer autonomousRequestsPerMinute,
            Integer npcToNpcRequestsPerMinute,
            Integer perPlayerRequestsPerMinute,
            Integer perNpcAutonomyCooldownSeconds) {
        this(endpoint, model, apiKey, connectTimeoutMillis, requestTimeoutMillis, temperature,
                maxTokens, maxPlayerMessageCharacters, recentMemoryCount, maxMemoryRecords,
                sessionIdleSeconds, streamResponses, responseStartTimeoutMillis,
                streamIdleTimeoutMillis, reasoningEffort, maxConcurrentLlmRequests,
                autonomousRequestsPerMinute, npcToNpcRequestsPerMinute,
                perPlayerRequestsPerMinute, perNpcAutonomyCooldownSeconds,
                java.util.Map.of(), 6);
    }

    public boolean streamingEnabled() {
        return streamResponses == null || streamResponses;
    }

    public int effectiveResponseStartTimeoutMillis() {
        return responseStartTimeoutMillis == null
                ? Math.max(60_000, requestTimeoutMillis) : responseStartTimeoutMillis;
    }

    public int effectiveStreamIdleTimeoutMillis() {
        return streamIdleTimeoutMillis == null
                ? Math.max(15_000, requestTimeoutMillis) : streamIdleTimeoutMillis;
    }

    public String configuredReasoningEffort() {
        return reasoningEffort == null || reasoningEffort.isBlank()
                ? null : reasoningEffort.strip();
    }

    public int effectiveMaxConcurrentLlmRequests() {
        return maxConcurrentLlmRequests == null ? 2 : maxConcurrentLlmRequests;
    }

    public int effectivePerPlayerRequestsPerMinute() {
        return perPlayerRequestsPerMinute == null ? 20 : perPlayerRequestsPerMinute;
    }

    public int effectiveDeepConversationTurnThreshold() {
        return deepConversationTurnThreshold == null
                ? 6 : Math.max(2, deepConversationTurnThreshold);
    }

    public java.util.Map<String, ModelTierConfig> configuredModelTiers() {
        return modelTiers == null ? java.util.Map.of() : java.util.Map.copyOf(modelTiers);
    }

    public FrameworkConfig forTier(ModelTierConfig tier) {
        if (tier == null || !tier.configured()) {
            return this;
        }
        return new FrameworkConfig(
                tier.endpoint() == null || tier.endpoint().isBlank() ? endpoint : tier.endpoint(),
                tier.model(), tier.apiKey() == null ? apiKey : tier.apiKey(),
                connectTimeoutMillis, requestTimeoutMillis, temperature, maxTokens,
                maxPlayerMessageCharacters, recentMemoryCount, maxMemoryRecords,
                sessionIdleSeconds, streamResponses, responseStartTimeoutMillis,
                streamIdleTimeoutMillis,
                tier.reasoningEffort() == null ? reasoningEffort : tier.reasoningEffort(),
                maxConcurrentLlmRequests, autonomousRequestsPerMinute,
                npcToNpcRequestsPerMinute, perPlayerRequestsPerMinute,
                perNpcAutonomyCooldownSeconds, modelTiers, deepConversationTurnThreshold);
    }

    public FrameworkConfig forAiProvider(
            com.inigmasgames.persistentnpcs.ai.AiProviderDefinition provider) {
        if (provider == null) return this;
        int timeout = provider.effectiveTimeoutMillis(requestTimeoutMillis);
        return new FrameworkConfig(provider.effectiveEndpoint(endpoint),
                provider.effectiveModel(model), apiKey, connectTimeoutMillis, timeout,
                temperature, maxTokens, maxPlayerMessageCharacters, recentMemoryCount,
                maxMemoryRecords, sessionIdleSeconds, streamResponses,
                Math.max(effectiveResponseStartTimeoutMillis(), timeout),
                Math.max(effectiveStreamIdleTimeoutMillis(), Math.min(timeout, 15_000)),
                reasoningEffort, provider.effectiveConcurrency(
                        effectiveMaxConcurrentLlmRequests()), autonomousRequestsPerMinute,
                npcToNpcRequestsPerMinute, perPlayerRequestsPerMinute,
                perNpcAutonomyCooldownSeconds, modelTiers, deepConversationTurnThreshold);
    }

    public FrameworkConfig validated() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (connectTimeoutMillis < 100 || requestTimeoutMillis < 100) {
            throw new IllegalArgumentException("timeouts must be at least 100 ms");
        }
        if (effectiveResponseStartTimeoutMillis() < 100
                || effectiveStreamIdleTimeoutMillis() < 100) {
            throw new IllegalArgumentException(
                    "response-start and stream-idle timeouts must be at least 100 ms");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (maxTokens < 1 || maxPlayerMessageCharacters < 1 || recentMemoryCount < 0
                || maxMemoryRecords < 1 || sessionIdleSeconds < 1) {
            throw new IllegalArgumentException("numeric limits must be positive");
        }
        if (effectiveMaxConcurrentLlmRequests() < 1
                || effectivePerPlayerRequestsPerMinute() < 1) {
            throw new IllegalArgumentException("LLM budget limits must be positive");
        }
        if (effectiveDeepConversationTurnThreshold() < 2) {
            throw new IllegalArgumentException("deep conversation threshold must be at least 2");
        }
        return this;
    }
}
