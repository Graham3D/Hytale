package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.ModelRoutingProvider;
import com.inigmasgames.persistentnpcs.llm.ModelTier;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.llm.SelectableLlmProvider;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlamaCppProvider;
import com.inigmasgames.persistentnpcs.voice.LocalWorkerSpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.LocalWorkerTextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.RemoteSpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.RemoteTextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.TextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfig;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.nio.file.Path;

public final class AiServiceRouterFactory {
    private AiServiceRouterFactory() { }

    public static AiServiceRouter create(AiProviderConfig providers,
            FrameworkConfig framework, VoiceRuntimeConfig voice,
            VoicePresetRepository presets, Consumer<String> log) {
        AiProviderConfig config = providers.validated();
        SpeechToTextProvider stt = stt(config.stt(), voice, presets, log);
        SpeechToTextProvider sttFallback = config.stt().explicitFallbackEnabled()
                ? stt(config.stt().fallback(), voice, presets, log) : null;
        LlmProvider llm = llm(config.llm(), framework, log);
        LlmProvider llmFallback = config.llm().explicitFallbackEnabled()
                ? llm(config.llm().fallback(), framework, log) : null;
        TextToSpeechProvider tts = tts(config.tts(), voice, presets, log);
        TextToSpeechProvider ttsFallback = config.tts().explicitFallbackEnabled()
                ? tts(config.tts().fallback(), voice, presets, log) : null;
        return new AiServiceRouter(stt, sttFallback, llm, llmFallback,
                tts, ttsFallback, log);
    }

    /** R037 selectable LLM path. STT/TTS construction remains exactly the R036 path. */
    public static AiServiceRouter createSelectable(AiProviderConfig providers,
            LlmProviderCatalog llmCatalog, Consumer<String> persistLlmSelection,
            FrameworkConfig framework, VoiceRuntimeConfig voice,
            VoicePresetRepository presets, Path dataDirectory, Consumer<String> log) {
        AiProviderConfig config = providers.validated();
        LlmProviderCatalog catalog = llmCatalog.validated();
        SpeechToTextProvider stt = stt(config.stt(), voice, presets, log);
        SpeechToTextProvider sttFallback = config.stt().explicitFallbackEnabled()
                ? stt(config.stt().fallback(), voice, presets, log) : null;
        LinkedHashMap<String, SelectableLlmProvider.Entry> entries = new LinkedHashMap<>();
        catalog.providers().forEach((name, definition) -> {
            LlmProvider provider;
            if (definition.effectiveType("OPENAI_COMPATIBLE").equals("ORBIS_LLAMA_CPP")) {
                String configured = System.getProperty("immersivenpcs.orbisllm.manifest", "");
                Path manifest = configured.isBlank()
                        ? dataDirectory.resolve("runtime").resolve("manifests")
                                .resolve("orbisllm-windows-x64-cuda.json")
                        : Path.of(configured);
                provider = new OrbisLlamaCppProvider(dataDirectory, manifest, log);
            } else {
                provider = new OpenAiCompatibleProvider(
                        framework.forAiProvider(definition).validated(),
                        OpenAiCompatibleProvider.ToolChoicePolicy.parse(
                                definition.effectiveToolChoiceMode("NAMED_SINGLE")), log,
                        definition.ollamaGpuLayers(),
                        definition.effectiveOllamaKeepAlive());
            }
            entries.put(name, new SelectableLlmProvider.Entry(provider,
                    definition.effectiveModel(framework.model()),
                    definition.effectiveEndpoint(framework.endpoint())));
        });
        SelectableLlmProvider selectable = new SelectableLlmProvider(entries,
                catalog.activeProvider(), persistLlmSelection, log);
        LlmProvider llm = selectable;
        TextToSpeechProvider tts = tts(config.tts(), voice, presets, log);
        TextToSpeechProvider ttsFallback = config.tts().explicitFallbackEnabled()
                ? tts(config.tts().fallback(), voice, presets, log) : null;
        // Deliberately no LLM fallback: a selected provider failure must be visible to the test.
        return new AiServiceRouter(stt, sttFallback, llm, null,
                tts, ttsFallback, log);
    }

    private static SpeechToTextProvider stt(AiProviderDefinition definition,
            VoiceRuntimeConfig voice, VoicePresetRepository presets, Consumer<String> log) {
        return switch (definition.effectiveType("LOCAL_WORKER")) {
            case "LOCAL_WORKER", "MOONSHINE", "FASTER_WHISPER" ->
                    new LocalWorkerSpeechToTextProvider(voice, presets, log);
            case "IMMERSIVE_HTTP", "REMOTE_HTTP" -> new RemoteSpeechToTextProvider(
                    definition.effectiveEndpoint(""), definition.effectiveModel(
                            voice.effectiveSttProvider()),
                    definition.effectiveTimeoutMillis(300_000),
                    definition.effectiveConcurrency(1));
            default -> throw new IllegalArgumentException("Unsupported STT provider type: "
                    + definition.type());
        };
    }

    private static TextToSpeechProvider tts(AiProviderDefinition definition,
            VoiceRuntimeConfig voice, VoicePresetRepository presets, Consumer<String> log) {
        return switch (definition.effectiveType("LOCAL_WORKER")) {
            case "LOCAL_WORKER", "CHATTERBOX_TURBO" ->
                    new LocalWorkerTextToSpeechProvider(voice, presets, log);
            case "IMMERSIVE_HTTP", "REMOTE_HTTP" -> new RemoteTextToSpeechProvider(
                    definition.effectiveEndpoint(""),
                    definition.effectiveModel("chatterbox-turbo"),
                    definition.effectiveTimeoutMillis(300_000),
                    definition.effectiveConcurrency(1));
            default -> throw new IllegalArgumentException("Unsupported TTS provider type: "
                    + definition.type());
        };
    }

    private static LlmProvider llm(AiProviderDefinition definition,
            FrameworkConfig framework, Consumer<String> log) {
        String type = definition.effectiveType("OPENAI_COMPATIBLE");
        if (!type.equals("OPENAI_COMPATIBLE")) {
            throw new IllegalArgumentException("Unsupported LLM provider type: "
                    + definition.type());
        }
        FrameworkConfig selected = framework.forAiProvider(definition).validated();
        LlmProvider generic = new OpenAiCompatibleProvider(selected, log);
        EnumMap<ModelTier, LlmProvider> tiers = new EnumMap<>(ModelTier.class);
        selected.configuredModelTiers().forEach((tierName, tierConfig) -> {
            try {
                ModelTier tier = ModelTier.valueOf(tierName.strip().toUpperCase(Locale.ROOT));
                if (tier != ModelTier.GENERIC && tierConfig.configured()) {
                    tiers.put(tier, new OpenAiCompatibleProvider(selected.forTier(tierConfig), log));
                }
            } catch (IllegalArgumentException failure) {
                log.accept("AI_PROVIDER_CONFIG ignored unknown LLM tier=" + tierName);
            }
        });
        return new ModelRoutingProvider(generic, tiers,
                selected.effectiveDeepConversationTurnThreshold());
    }
}
