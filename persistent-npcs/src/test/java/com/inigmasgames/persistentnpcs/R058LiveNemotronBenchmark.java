package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Manual live R058 probe. It is deliberately excluded from the deterministic suite. */
public final class R058LiveNemotronBenchmark {
    private R058LiveNemotronBenchmark() { }

    public static void main(String[] args) {
        String endpoint = args.length > 0 ? args[0]
                : "http://127.0.0.1:11434/v1/chat/completions";
        int gpuLayers = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        FrameworkConfig config = new FrameworkConfig(endpoint, "nemotron-3-nano:4b", "",
                2_000, 90_000, 0.2, 640, 800, 0, 10, 600,
                true, 90_000, 30_000, "none");
        try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config,
                OpenAiCompatibleProvider.ToolChoicePolicy.NAMED_SINGLE,
                value -> System.out.println("PROVIDER " + value), gpuLayers, "10m")) {
            run(provider, gpuLayers, "FAST_DIALOGUE", false, 56,
                    "You are Mara, a direct, observant young blacksmith. Reply naturally in "
                            + "one short spoken sentence. Do not invent facts.",
                    "Hello Mara, how are you?");
            run(provider, gpuLayers, "FAST_DIALOGUE", false, 56,
                    "You are Mara, a direct, observant young blacksmith. Reply naturally in "
                            + "one short spoken sentence. You like practical craftsmanship.",
                    "What sort of work do you enjoy?");
            run(provider, gpuLayers, "GROUNDED_DIALOGUE", false, 88,
                    "You are Mara. Known fact: the player said their name is Graham. Reply "
                            + "naturally in one short spoken sentence using only that fact.",
                    "Do you remember my name?");
            run(provider, gpuLayers, "DELIBERATIVE", true, 512,
                    "You are Mara, loyal but independent. Think through the conflict privately, "
                            + "then give only a concise spoken decision. Never reveal private reasoning.",
                    "Your friend asks you to abandon an urgent promise to your father so you can "
                            + "help with a risky two-step repair. What do you decide, and why?");
        }
    }

    private static void run(OpenAiCompatibleProvider provider, int gpuLayers, String policy,
            boolean reasoning, int wireBudget, String system, String user) {
        UUID id = UUID.randomUUID();
        LlmExecutionPolicy execution = new LlmExecutionPolicy(policy,
                reasoning ? LlmExecutionPolicy.ReasoningMode.ENABLED
                        : LlmExecutionPolicy.ReasoningMode.DISABLED,
                List.of("R058_LIVE_BENCHMARK"), reasoning ? 160 : wireBudget);
        LlmRequest request = new LlmRequest(id, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ChatMessage("system", system), new ChatMessage("user", user)))
                .withExecutionPolicy(execution).withGenerationParameters(0.2, wireBudget);
        long started = System.nanoTime();
        AtomicLong firstPhrase = new AtomicLong(-1);
        StringBuilder stream = new StringBuilder();
        var result = provider.generateResponse(request, delta -> {
            stream.append(delta);
            if (firstPhrase.get() < 0 && stream.toString().matches("(?s).*?[.!?](?:\\s|$).*$")) {
                firstPhrase.compareAndSet(-1,
                        (System.nanoTime() - started) / 1_000_000L);
            }
        }).join();
        System.out.printf("R058_BENCH policy=%s gpuLayers=%d reasoning=%s ttftMs=%d "
                        + "firstPhraseMs=%d totalMs=%d promptTokens=%d completionTokens=%d "
                        + "tokensPerSec=%.2f actualReasoning=%s response=%s%n",
                policy, gpuLayers, reasoning, result.latency().timeToFirstTokenMillis(),
                firstPhrase.get(), result.latency().completionMillis(),
                result.usage().promptTokens(), result.usage().completionTokens(),
                result.usage().tokensPerSecond(result.latency().completionMillis()),
                result.reasoningTelemetry().actualMode(),
                result.text().replaceAll("\\s+", " ").strip());
    }
}
