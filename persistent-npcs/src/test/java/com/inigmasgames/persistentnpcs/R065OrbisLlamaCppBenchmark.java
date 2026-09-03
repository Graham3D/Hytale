package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlamaCppProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Small matched Phase 1 transport A/B; prompts and generation policy are identical. */
public final class R065OrbisLlamaCppBenchmark {
    private static final int REPETITIONS = 3;
    private static final String SYSTEM = """
            You are Mara, an adult human apprentice blacksmith in Hytale. You are curious,
            mechanically minded, warm, direct, and dryly funny. Lycander is your grandfather.
            Treat supplied world state, relationships, and memories as authoritative. Never
            invent current objects, events, actions, memories, or tool results. Speak one short,
            natural, in-character reply without labels or stage directions.
            """;
    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("greeting", "World: player nearby; no danger.", "Hello Mara."),
            new Scenario("identity", "Relationship: Lycander is Mara's grandfather.",
                    "Who are you, and who is Lycander to you?"),
            new Scenario("grounded_perception", "Held item: Onyxium dagger. Visible nearby: player only.",
                    "Can you see what's in my hand?"),
            new Scenario("no_hallucination", "Visible nearby: no fox; no mill. Memories: none about a fox.",
                    "What is the fox doing by the mill?"),
            new Scenario("social", "Mara likes unusual craftsmanship and values honest work.",
                    "Would you like to examine an unusual mechanism with me?"));

    private R065OrbisLlamaCppBenchmark() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("runtime manifest path required");
        ArrayList<Run> runs = new ArrayList<>();
        FrameworkConfig config = new FrameworkConfig(
                "http://127.0.0.1:11434/v1/chat/completions", "nemotron-3-nano:4b", "",
                2_000, 60_000, 0.3, 80, 2_000, 8, 5_000, 900,
                true, 30_000, 30_000, "none").validated();
        try (OpenAiCompatibleProvider ollama = new OpenAiCompatibleProvider(config,
                OpenAiCompatibleProvider.ToolChoicePolicy.NAMED_SINGLE, ignored -> { },
                4, "10m")) {
            ollama.ensurePreferredResidency().get(120, TimeUnit.SECONDS);
            runAll("OLLAMA_NEMOTRON", ollama, runs);
            ollama.unloadResidentModel().get(30, TimeUnit.SECONDS);
        }
        Path data = Files.createTempDirectory("orbisllm-r065-benchmark-");
        try (OrbisLlamaCppProvider llama = new OrbisLlamaCppProvider(data,
                Path.of(args[0]), ignored -> { })) {
            llama.warmUp().get(120, TimeUnit.SECONDS);
            runAll("ORBIS_LLAMA_CPP_NEMOTRON", llama, runs);
        }
        Path output = Path.of("build", "benchmarks", "R065-orbis-llama-cpp-vs-ollama.json");
        Files.createDirectories(output.getParent());
        Report report = new Report("R065-PHASE1", Instant.now(),
                "Same Nemotron Q4_K_M, messages, reasoning disabled, temperature=0.3, "
                        + "maxTokens=80, context=4096, four GPU layers; sequential residency.", runs);
        Files.writeString(output, JsonFiles.GSON.toJson(report), StandardCharsets.UTF_8);
        System.out.println("R065_AB_REPORT=" + output.toAbsolutePath());
        for (String provider : List.of("OLLAMA_NEMOTRON", "ORBIS_LLAMA_CPP_NEMOTRON")) {
            List<Run> selected = runs.stream().filter(run -> provider.equals(run.provider())).toList();
            System.out.println(provider + " avgTTFT=" + average(selected, true)
                    + "ms avgTotal=" + average(selected, false) + "ms");
        }
    }

    private static void runAll(String providerName, LlmProvider provider,
            List<Run> runs) throws Exception {
        for (Scenario scenario : SCENARIOS) {
            for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
                LlmRequest request = request(scenario);
                StringBuilder stream = new StringBuilder();
                LlmResult result = provider.generateResponse(request, stream::append)
                        .get(90, TimeUnit.SECONDS);
                boolean identical = result.text().equals(stream.toString());
                runs.add(new Run(providerName, scenario.id(), repetition, result.text(), identical,
                        result.latency().timeToFirstTokenMillis(),
                        result.latency().completionMillis(), result.usage().promptTokens(),
                        result.usage().completionTokens(), result.usage().exact(),
                        result.reasoningTelemetry().thinkingEnabled(), result.finishReason()));
                if (!identical) throw new AssertionError(providerName + " stream/result divergence");
            }
        }
    }

    private static LlmRequest request(Scenario scenario) {
        LlmExecutionPolicy policy = new LlmExecutionPolicy("REALTIME",
                LlmExecutionPolicy.ReasoningMode.DISABLED, List.of("R065_MATCHED_AB"), 80);
        return new LlmRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ChatMessage("system", SYSTEM + "\n\nAUTHORITATIVE CONTEXT:\n" + scenario.context()),
                new ChatMessage("user", scenario.user())), List.of(), null,
                0.3, 80, UUID.randomUUID(), policy);
    }

    private static long average(List<Run> values, boolean ttft) {
        return Math.round(values.stream().mapToLong(value -> ttft
                ? value.ttftMillis() : value.totalMillis()).average().orElse(-1));
    }

    private record Scenario(String id, String context, String user) { }
    private record Run(String provider, String scenario, int repetition, String response,
            boolean streamResultIdentical, long ttftMillis, long totalMillis,
            int promptTokens, int completionTokens, boolean exactUsage,
            boolean thinkingEnabled, String finishReason) { }
    private record Report(String revision, Instant createdAt, String parity, List<Run> runs) { }
}
