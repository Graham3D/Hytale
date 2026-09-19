package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Persists only the global runtime LLM selection; NPC state is never stored here. */
public final class LlmProviderCatalogRepository {
    private final Path path;
    private volatile LlmProviderCatalog current;

    public LlmProviderCatalogRepository(Path dataDirectory) {
        path = dataDirectory.resolve("llm-providers.json");
    }

    public Path path() { return path; }

    public synchronized LlmProviderCatalog load(AiProviderDefinition currentNemotron) {
        LlmProviderCatalog loaded;
        if (Files.isRegularFile(path)) {
            loaded = JsonFiles.read(path, LlmProviderCatalog.class).validated();
            LinkedHashMap<String, AiProviderDefinition> providers =
                    new LinkedHashMap<>(loaded.providers());
            LlmProviderCatalog defaults = LlmProviderCatalog.defaults(currentNemotron);
            providers.putIfAbsent(LlmProviderCatalog.NEMOTRON,
                    defaults.providers().get(LlmProviderCatalog.NEMOTRON));
            providers.putIfAbsent(LlmProviderCatalog.QWEN,
                    defaults.providers().get(LlmProviderCatalog.QWEN));
            // R065 experimental runtimes are archived source only and are not selectable
            // from a production catalog.
            providers.remove(LlmProviderCatalog.ORBIS_LLAMA_CPP_NEMOTRON);
            AiProviderDefinition nemotron = providers.get(LlmProviderCatalog.NEMOTRON);
            if (nemotron.ollamaGpuLayers() == null) {
                providers.put(LlmProviderCatalog.NEMOTRON,
                        defaults.providers().get(LlmProviderCatalog.NEMOTRON));
            } else if (isPriorBalancedDefault(nemotron)) {
                // Forward migrate the measured R062/R063 target-host profiles. Four layers
                // preserves the 512 MiB Hytale reserve plus connected-client drift headroom.
                providers.put(LlmProviderCatalog.NEMOTRON, new AiProviderDefinition(
                        nemotron.type(), nemotron.endpoint(), nemotron.model(),
                        nemotron.timeoutMillis(), nemotron.concurrency(), nemotron.mode(),
                        nemotron.fallbackEnabled(), nemotron.fallback(),
                        nemotron.toolChoiceMode(),
                        LlmProviderCatalog.NEMOTRON_BALANCED_GPU_LAYERS,
                        nemotron.ollamaKeepAlive()));
            }
            AiProviderDefinition qwen = providers.get(LlmProviderCatalog.QWEN);
            if (qwen.toolChoiceMode() == null || qwen.toolChoiceMode().isBlank()) {
                providers.put(LlmProviderCatalog.QWEN, new AiProviderDefinition(
                        qwen.type(), qwen.endpoint(), qwen.model(), qwen.timeoutMillis(),
                        qwen.concurrency(), qwen.mode(), qwen.fallbackEnabled(),
                        qwen.fallback(), "REQUIRED", qwen.ollamaGpuLayers(),
                        qwen.ollamaKeepAlive()));
            }
            loaded = new LlmProviderCatalog(loaded.activeProvider(), providers).validated();
            JsonFiles.writeAtomic(path, loaded);
        } else {
            loaded = LlmProviderCatalog.defaults(currentNemotron);
            JsonFiles.writeAtomic(path, loaded);
        }
        current = loaded;
        return loaded;
    }

    public synchronized void select(String provider) {
        if (current == null) throw new IllegalStateException("LLM provider catalog is not loaded");
        current = current.withActiveProvider(provider);
        JsonFiles.writeAtomic(path, current);
    }

    private static boolean isPriorBalancedDefault(AiProviderDefinition value) {
        return value != null && value.ollamaGpuLayers() != null
                && (value.ollamaGpuLayers() == 12 || value.ollamaGpuLayers() == 6)
                && "nemotron-3-nano:4b".equalsIgnoreCase(value.model())
                && "10m".equalsIgnoreCase(value.ollamaKeepAlive());
    }
}
