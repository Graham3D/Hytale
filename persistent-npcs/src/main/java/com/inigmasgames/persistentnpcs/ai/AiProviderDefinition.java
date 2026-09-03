package com.inigmasgames.persistentnpcs.ai;

/** Persistent, secret-free provider selection. Credentials remain outside NPC profiles. */
public record AiProviderDefinition(
        String type,
        String endpoint,
        String model,
        Integer timeoutMillis,
        Integer concurrency,
        String mode,
        Boolean fallbackEnabled,
        AiProviderDefinition fallback,
        String toolChoiceMode,
        Integer ollamaGpuLayers,
        String ollamaKeepAlive) {

    public AiProviderDefinition(String type, String endpoint, String model,
            Integer timeoutMillis, Integer concurrency, String mode,
            Boolean fallbackEnabled, AiProviderDefinition fallback) {
        this(type, endpoint, model, timeoutMillis, concurrency, mode,
                fallbackEnabled, fallback, null, null, null);
    }

    public AiProviderDefinition(String type, String endpoint, String model,
            Integer timeoutMillis, Integer concurrency, String mode,
            Boolean fallbackEnabled, AiProviderDefinition fallback,
            String toolChoiceMode) {
        this(type, endpoint, model, timeoutMillis, concurrency, mode,
                fallbackEnabled, fallback, toolChoiceMode, null, null);
    }

    public String effectiveType(String fallbackType) {
        return type == null || type.isBlank() ? fallbackType : type.strip().toUpperCase(
                java.util.Locale.ROOT);
    }

    public String effectiveEndpoint(String inherited) {
        return endpoint == null || endpoint.isBlank() ? inherited : endpoint.strip();
    }

    public String effectiveModel(String inherited) {
        return model == null || model.isBlank() ? inherited : model.strip();
    }

    public int effectiveTimeoutMillis(int inherited) {
        return timeoutMillis == null ? inherited : Math.max(100, timeoutMillis);
    }

    public int effectiveConcurrency(int inherited) {
        return concurrency == null ? Math.max(1, inherited) : Math.max(1, concurrency);
    }

    public ProviderExecutionMode executionMode() {
        return ProviderExecutionMode.parse(mode);
    }

    public boolean explicitFallbackEnabled() {
        return Boolean.TRUE.equals(fallbackEnabled) && fallback != null;
    }

    public String effectiveToolChoiceMode(String inherited) {
        return toolChoiceMode == null || toolChoiceMode.isBlank()
                ? inherited : toolChoiceMode.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public String effectiveOllamaKeepAlive() {
        return ollamaKeepAlive == null || ollamaKeepAlive.isBlank()
                ? "10m" : ollamaKeepAlive.strip();
    }
}
