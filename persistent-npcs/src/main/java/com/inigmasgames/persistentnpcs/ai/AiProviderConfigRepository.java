package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfig;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Migrates existing config.json/voice.json selections into one authoritative router file. */
public final class AiProviderConfigRepository {
    private final Path path;

    public AiProviderConfigRepository(Path dataDirectory) {
        this.path = dataDirectory.resolve("ai-providers.json");
    }

    public Path path() { return path; }

    public AiProviderConfig load(FrameworkConfig framework, VoiceRuntimeConfig voice) {
        if (!Files.isRegularFile(path)) {
            AiProviderConfig migrated = defaults(framework, voice);
            JsonFiles.writeAtomic(path, migrated);
            return migrated;
        }
        return JsonFiles.read(path, AiProviderConfig.class).validated();
    }

    private static AiProviderConfig defaults(
            FrameworkConfig framework, VoiceRuntimeConfig voice) {
        String llmMode = isLoopback(framework.endpoint()) ? "LOCAL" : "REMOTE";
        return new AiProviderConfig(
                new AiProviderDefinition("LOCAL_WORKER", "",
                        voice.effectiveSttProvider() + ":" + voice.effectiveWhisperModel(),
                        300_000, 1, "LOCAL", false, null),
                new AiProviderDefinition("OPENAI_COMPATIBLE", framework.endpoint(),
                        framework.model(), framework.requestTimeoutMillis(),
                        framework.effectiveMaxConcurrentLlmRequests(), llmMode, false, null),
                new AiProviderDefinition("LOCAL_WORKER", "", "chatterbox-turbo",
                        300_000, 1, "LOCAL", false, null)).validated();
    }

    private static boolean isLoopback(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host == null || host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1");
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
