package com.inigmasgames.persistentnpcs.ai;

public record AiProviderConfig(
        AiProviderDefinition stt,
        AiProviderDefinition llm,
        AiProviderDefinition tts) {

    public AiProviderConfig validated() {
        if (stt == null || llm == null || tts == null) {
            throw new IllegalArgumentException("stt, llm, and tts provider definitions are required");
        }
        validate(stt, "stt");
        validate(llm, "llm");
        validate(tts, "tts");
        return this;
    }

    private static void validate(AiProviderDefinition value, String service) {
        value.executionMode();
        value.effectiveConcurrency(1);
        value.effectiveTimeoutMillis(30_000);
        if (value.explicitFallbackEnabled()) validate(value.fallback(), service + ".fallback");
    }
}
