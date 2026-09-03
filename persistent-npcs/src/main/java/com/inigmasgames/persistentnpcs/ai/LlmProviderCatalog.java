package com.inigmasgames.persistentnpcs.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Global, secret-free catalog for selectable LLM inference backends. */
public record LlmProviderCatalog(
        String activeProvider,
        Map<String, AiProviderDefinition> providers) {
    public static final String QWEN = "QWEN";
    public static final String NEMOTRON = "NEMOTRON";
    public static final String ORBIS_LLAMA_CPP_NEMOTRON = "ORBIS_LLAMA_CPP_NEMOTRON";
    /** R064 target-host steady-state calibration for the 12 GB BALANCED profile. */
    public static final int NEMOTRON_BALANCED_GPU_LAYERS = 4;
    public static final String QWEN_MODEL =
            "hf.co/openresearchtools/Qwen3.5-4B-Instruct-GGUF:Q4_K_M";
    public static final String OLLAMA_CHAT_ENDPOINT =
            "http://127.0.0.1:11434/v1/chat/completions";

    public LlmProviderCatalog validated() {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one selectable LLM provider is required");
        }
        LinkedHashMap<String, AiProviderDefinition> normalized = new LinkedHashMap<>();
        providers.forEach((name, definition) -> {
            String key = normalize(name);
            if (key.isBlank() || definition == null) {
                throw new IllegalArgumentException("LLM provider names and definitions are required");
            }
            String type = definition.effectiveType("OPENAI_COMPATIBLE");
            if (!type.equals("OPENAI_COMPATIBLE") && !type.equals("ORBIS_LLAMA_CPP")) {
                throw new IllegalArgumentException("Unsupported selectable LLM type: "
                        + definition.type());
            }
            normalized.put(key, definition);
        });
        String selected = normalize(activeProvider);
        if (!normalized.containsKey(selected)) {
            throw new IllegalArgumentException("Unknown active LLM provider: " + activeProvider);
        }
        return new LlmProviderCatalog(selected, Map.copyOf(normalized));
    }

    public LlmProviderCatalog withActiveProvider(String provider) {
        return new LlmProviderCatalog(normalize(provider), providers).validated();
    }

    public static LlmProviderCatalog defaults(AiProviderDefinition existingNemotron) {
        AiProviderDefinition prior = java.util.Objects.requireNonNull(
                existingNemotron, "existingNemotron");
        AiProviderDefinition qwen = new AiProviderDefinition(
                "OPENAI_COMPATIBLE", OLLAMA_CHAT_ENDPOINT, QWEN_MODEL,
                prior.effectiveTimeoutMillis(12_000), prior.effectiveConcurrency(2),
                "LOCAL", false, null, "REQUIRED");
        AiProviderDefinition nemotron = new AiProviderDefinition(prior.type(),
                prior.endpoint(), prior.model(), prior.timeoutMillis(), prior.concurrency(),
                prior.mode(), prior.fallbackEnabled(), prior.fallback(),
                prior.effectiveToolChoiceMode("NAMED_SINGLE"),
                NEMOTRON_BALANCED_GPU_LAYERS, "10m");
        return new LlmProviderCatalog(NEMOTRON, Map.of(
                NEMOTRON, nemotron,
                QWEN, qwen)).validated();
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
