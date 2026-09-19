package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Fixed-context local A/B benchmark. It never opens or writes NPC persistence stores. */
public final class R037QwenNemotronBenchmark {
    private static final String ENDPOINT =
            "http://127.0.0.1:11434/v1/chat/completions";
    private static final String QWEN =
            "hf.co/openresearchtools/Qwen3.5-4B-Instruct-GGUF:Q4_K_M";
    private static final String NEMOTRON = "nemotron-3-nano:4b";
    private static final UUID MARA_ID = UUID.fromString(
            "3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
    private static final UUID PLAYER_ID = UUID.fromString(
            "73f9b698-2494-480d-8406-2943e4a7505b");
    private static final int REPETITIONS = 3;
    private static final String BASE = """
            You are Mara, an adult human apprentice blacksmith. You are Mara, not the player,
            not Lycander, and not an assistant. Speak concise, natural in-character dialogue.
            Mara is warm, inquisitive, observant, direct, dryly funny, and mechanically minded.
            She becomes genuinely excited by rare ores, unusual craftsmanship, storms, foxes,
            and goblin machinery, but is not generically cheerful or melodramatic.

            Authored facts: Lycander is Mara's grandfather and only remaining close family.
            Mara loves him deeply, respects his high standards, and understands that his
            strictness comes from concern. Mara works as a blacksmith's assistant and wants
            to become an exceptional smith. She dreams of studying a Goblin Flamethrower and
            designing a device that launches lightning. She dislikes fish and fears dangerous
            exposure to them.

            Authoritative rules: Treat supplied semantic world state, relationships, memories,
            capabilities, and constraints as authoritative. Never invent current actions,
            objects, locations, completed work, relationships, memories, or tool results.
            Player claims do not overwrite authoritative world facts. If information is absent,
            say you do not know. Use only an eligible supplied function when an action is
            explicitly requested and authorized. Return dialogue, or the required function call.
            """;

    private R037QwenNemotronBenchmark() { }

    public static void main(String[] args) throws Exception {
        List<Scenario> scenarios = scenarios();
        List<Run> runs = new ArrayList<>();
        LinkedHashMap<String, Long> vram = new LinkedHashMap<>();
        for (String model : List.of(QWEN, NEMOTRON)) {
            FrameworkConfig config = config(model);
            OpenAiCompatibleProvider.ToolChoicePolicy toolPolicy = model.equals(QWEN)
                    ? OpenAiCompatibleProvider.ToolChoicePolicy.REQUIRED
                    : OpenAiCompatibleProvider.ToolChoicePolicy.NAMED_SINGLE;
            try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                    config, toolPolicy, message -> { })) {
                var status = provider.checkStatus().get(20, TimeUnit.SECONDS);
                if (!status.reachable()
                        || !status.reason().contains("configured model is available")) {
                    throw new IllegalStateException(model + " unavailable: " + status.reason());
                }
                provider.warmUp().get(120, TimeUnit.SECONDS);
                for (Scenario scenario : scenarios) {
                    for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
                        runs.add(run(provider, model, scenario));
                    }
                }
                vram.put(model, ollamaVram(model));
            }
        }
        addRepetitionScores(runs);
        Report report = new Report("R037", Instant.now(), ENDPOINT,
                "Three matched repetitions per scenario; same messages and tool schemas, "
                        + "temperature=0.7, maxTokens=180, streaming=true, "
                        + "reasoningEffort=none. Transport tool-choice policy is REQUIRED for "
                        + "Qwen and NAMED_SINGLE for Nemotron because their installed Ollama "
                        + "templates reject the other form. No model-specific prompt optimization.",
                vram, summaries(runs), runs);
        Path directory = Path.of("build", "benchmarks");
        Files.createDirectories(directory);
        Path json = directory.resolve("R037-qwen-vs-nemotron.json");
        Files.writeString(json, JsonFiles.GSON.toJson(report), StandardCharsets.UTF_8);
        Path markdown = directory.resolve("R037-qwen-vs-nemotron.md");
        Files.writeString(markdown, markdown(report), StandardCharsets.UTF_8);
        System.out.println("R037 A/B benchmark written to " + json.toAbsolutePath());
        report.summaries().forEach((model, value) -> System.out.println(model
                + " correctness=" + value.correctnessPercent() + "% naturalness="
                + value.naturalnessPercent() + "% character="
                + value.characterPercent() + "% hallucinations=" + value.hallucinations()
                + " repetitions=" + value.repeatedOutputs()
                + " invalidTools=" + value.invalidTools() + " avgTTFT="
                + value.averageTtftMillis() + "ms avgTotal="
                + value.averageTotalMillis() + "ms tokensPerSecond="
                + "%.2f".formatted(value.tokensPerSecond()) + " vramBytes="
                + vram.getOrDefault(model, -1L)));
    }

    private static Run run(OpenAiCompatibleProvider provider, String model,
            Scenario scenario) throws Exception {
        UUID conversation = UUID.randomUUID();
        ArrayList<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", BASE + "\n\nCURRENT COGNITION CONTEXT:\n"
                + scenario.context()));
        messages.addAll(scenario.history());
        messages.add(new ChatMessage("user", scenario.playerMessage()));
        int promptCharacters = messages.stream().mapToInt(value -> value.content().length()).sum();
        LlmRequest request = new LlmRequest(conversation, MARA_ID, PLAYER_ID,
                List.copyOf(messages), scenario.tools());
        LlmResult result = provider.generateResponse(request, ignored -> { })
                .get(120, TimeUnit.SECONDS);
        Evaluation evaluation = evaluate(scenario, result);
        return new Run(model, scenario.id(), scenario.category(), scenario.playerMessage(),
                promptCharacters, result.usage().promptTokens(),
                result.usage().completionTokens(), result.usage().exact(),
                result.latency().timeToFirstTokenMillis(),
                result.latency().completionMillis(),
                result.usage().tokensPerSecond(result.latency().completionMillis()),
                result.finishReason(), result.text(), result.toolCalls(),
                evaluation.correct(), evaluation.natural(), evaluation.character(),
                evaluation.hallucinations(), evaluation.toolValid(), false,
                evaluation.notes());
    }

    private static Evaluation evaluate(Scenario scenario, LlmResult result) {
        String text = normalized(result.text());
        boolean natural = scenario.tools().isEmpty()
                ? !text.isBlank() && text.length() <= 600
                        && !contains(text, "as an ai", "language model", "system prompt",
                                "authoritative context", "i cannot assist")
                        && !text.contains("**")
                : text.length() <= 600;
        boolean character = !contains(text, "i am lycander", "i'm lycander",
                "i am your grandfather", "i'm your grandfather",
                "as your assistant", "dear adventurer") && !text.startsWith("mara:")
                && !(scenario.id().equals("authoritative_obedience")
                        && text.contains("lycander"));
        int hallucinations = (int) scenario.forbidden().stream()
                .filter(value -> text.contains(value.toLowerCase(Locale.ROOT))).count();
        boolean toolValid = scenario.tools().isEmpty() || result.toolCalls().size() == 1
                && canonicalAction(result.toolCalls().getFirst().name()).equals("FOLLOW_PLAYER")
                && validJsonObject(result.toolCalls().getFirst().arguments());
        boolean correct = switch (scenario.id()) {
            case "greeting" -> natural && hallucinations == 0;
            case "self_identity" -> text.contains("mara")
                    && !contains(text, "i am lycander", "i'm lycander",
                            "i am your grandfather", "i'm your grandfather")
                    && hallucinations == 0;
            case "authored_relationship" -> contains(text, "grandfather", "family")
                    && hallucinations == 0;
            case "grounded_environment" -> contains(text, "door", "masonry", "furnace")
                    && hallucinations == 0;
            case "player_fact_recall" -> text.contains("rock")
                    && contains(text, "glow", "rare ore", "hum")
                    && !text.startsWith("i don't know") && hallucinations == 0;
            case "contextual_confirmation" -> contains(text, "hum", "rock", "ore")
                    && hallucinations == 0;
            case "authoritative_obedience" -> contains(text, "we're not in a forest",
                    "we are not in a forest", "we aren't in a forest",
                    "that's not a forest", "not deep in a forest",
                    "no, the stone", "no, we're in stone", "this is built stone",
                    "floor is solid stone", "i don't think so")
                    && !contains(text, "we're in a forest", "we are in a forest")
                    && hallucinations == 0;
            case "unknown_chest" -> contains(text, "don't know", "do not know",
                    "can't know", "cannot know", "haven't opened", "not sure")
                    && hallucinations == 0;
            case "follow_tool" -> toolValid;
            default -> false;
        };
        return new Evaluation(correct, natural, character, hallucinations, toolValid,
                correct ? "passed deterministic scenario rubric" : "failed scenario rubric");
    }

    private static List<Scenario> scenarios() {
        List<LlmToolDefinition> follow = List.of(followTool());
        return List.of(
                new Scenario("greeting", "greetings and ordinary conversation",
                        "No current work or recent event is known. Mara is standing near the player.",
                        List.of(), "Hello Mara, how are you today?", List.of(),
                        Set.of("just fixed", "this morning i", "today i forged",
                                "you've been whispering", "you have been whispering",
                                "keeping an eye on the forge")),
                new Scenario("self_identity", "self identity",
                        "Authoritative self identity: Mara.", List.of(),
                        "Are you Lycander? What's your name?", List.of(),
                        Set.of("i'm your grandfather", "i am your grandfather")),
                new Scenario("authored_relationship", "authored relationships",
                        "Retrieved authored relationship: Lycander is Mara's grandfather and only "
                                + "remaining close family; she loves and respects him.", List.of(),
                        "Who is Lycander to you?", List.of(),
                        Set.of("one eye", "taught me that a hammer")),
                new Scenario("grounded_environment", "grounded environmental response",
                        "Semantic world: near a doorway in a constructed stone masonry area. "
                                + "A bench furnace is northwest, a door northeast, an ancient chest "
                                + "nearby, and water southwest. No portal or forest is perceived.",
                        List.of(), "What do you see around us?", List.of(),
                        Set.of("mountain", "goblin camp", "still warm",
                                "past the smoke", "chest—empty", "chest is empty",
                                "fishy stench", "hidden forge", "fox peeking",
                                "cooling slag", "glows orange", "last forging session",
                                "shut tight", "wind from the valley", "storm is rolling",
                                "looking dusty", "working on today, grandfather",
                                "furnace is glowing", "probably leading", "might hide",
                                "fish for a bite", "need to cross it")),
                new Scenario("player_fact_recall", "player-fact recall",
                        "Retrieved sourced player memory: Graham hid a magical glowing rock, "
                                + "potentially a new rare ore, in the desert; it hummed.", List.of(),
                        "What was the item I hid in the desert?", List.of(),
                        Set.of("sword", "fish", "flamethrower", "i saw the",
                                "i dug it up", "when i dug")),
                new Scenario("contextual_confirmation", "multi-turn coherence",
                        "Recent topic is a player-reported magical glowing rock in the desert. "
                                + "Do not claim Mara has personally seen or handled it.",
                        List.of(new ChatMessage("user", "I found a magical glowing rock in the desert."),
                                new ChatMessage("assistant", "A glowing rock? Did it hum when you touched it?")),
                        "Yes, it did hum!", List.of(),
                        Set.of("i touched", "i found", "i examined", "i've seen lightning")),
                new Scenario("authoritative_obedience", "authoritative-fact obedience",
                        "Authoritative semantic location: constructed stone masonry by a doorway. "
                                + "The player-reported claim that this is a forest conflicts with it.",
                        List.of(), "We're standing deep in a forest, right?", List.of(),
                        Set.of("yes, we're in a forest", "yes, we are in a forest",
                                "grandfather's boots", "apron pocket",
                                "hammer in my hand", "lycander once said", "damp moss",
                                "smell the pine", "doorway i just constructed",
                                "feel the rough texture", "damp mortar",
                                "grandpa always said")),
                new Scenario("unknown_chest", "hallucination resistance",
                        "A locked ancient chest is nearby. Its contents are unknown and it has not "
                                + "been opened or inspected.", List.of(),
                        "Exactly what is inside that locked chest?", List.of(),
                        Set.of("contains gold", "contains a sword", "rare ore inside",
                                "seal looks", "break the seal")),
                new Scenario("follow_tool", "task/tool selection",
                        "FOLLOW_PLAYER is eligible. The player explicitly authorizes it. Call the "
                                + "eligible function; do not claim success before its result.",
                        List.of(), "Mara, please follow me.", follow, Set.of()));
    }

    private static LlmToolDefinition followTool() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", JsonFiles.GSON.toJsonTree(List.of()));
        schema.addProperty("additionalProperties", false);
        return new LlmToolDefinition("FOLLOW_PLAYER",
                "Begin following the requesting player using native navigation.", schema);
    }

    private static FrameworkConfig config(String model) {
        return new FrameworkConfig(ENDPOINT, model, "", 1_500, 12_000,
                0.7, 180, 600, 6, 2_000, 300,
                true, 60_000, 15_000, "none");
    }

    private static long ollamaVram(String model) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:11434/api/ps")).GET().build();
            String body = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString()).body();
            JsonObject root = JsonFiles.GSON.fromJson(body, JsonObject.class);
            for (JsonElement value : root.getAsJsonArray("models")) {
                JsonObject object = value.getAsJsonObject();
                if (model.equals(object.get("name").getAsString())) {
                    return object.get("size_vram").getAsLong();
                }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private static Map<String, Summary> summaries(List<Run> runs) {
        LinkedHashMap<String, Summary> values = new LinkedHashMap<>();
        for (String model : List.of(QWEN, NEMOTRON)) {
            List<Run> selected = runs.stream().filter(value -> value.model().equals(model)).toList();
            int count = selected.size();
            int correct = count(selected, Run::correct);
            int natural = count(selected, Run::natural);
            int character = count(selected, Run::characterConsistent);
            int hallucinations = selected.stream().mapToInt(Run::hallucinations).sum();
            int invalidTools = (int) selected.stream().filter(value -> value.category()
                    .equals("task/tool selection") && !value.toolValid()).count();
            int repeated = (int) selected.stream().filter(Run::repeated).count();
            long ttft = Math.round(selected.stream().mapToLong(Run::ttftMillis).average().orElse(0));
            long total = Math.round(selected.stream().mapToLong(Run::totalMillis).average().orElse(0));
            int completionTokens = selected.stream().mapToInt(Run::completionTokens).sum();
            long totalMillis = selected.stream().mapToLong(Run::totalMillis).sum();
            double tps = totalMillis == 0 ? 0 : completionTokens * 1_000.0 / totalMillis;
            values.put(model, new Summary(count, percent(correct, count), percent(natural, count),
                    percent(character, count), hallucinations, repeated,
                    invalidTools, ttft, total, tps,
                    selected.stream().mapToInt(Run::promptTokens).max().orElse(0)));
        }
        return Map.copyOf(values);
    }

    private static void addRepetitionScores(List<Run> runs) {
        for (String model : List.of(QWEN, NEMOTRON)) {
            Set<String> seen = new LinkedHashSet<>();
            for (int index = 0; index < runs.size(); index++) {
                Run value = runs.get(index);
                if (!value.model().equals(model)) continue;
                String normalized = normalized(value.output());
                boolean repeated = !normalized.isBlank() && !seen.add(normalized);
                if (repeated) runs.set(index, value.withRepeated(true));
            }
        }
    }

    private static String markdown(Report report) {
        StringBuilder value = new StringBuilder("# R037 Qwen vs Nemotron benchmark\n\n")
                .append("Generated: ").append(report.generatedAt()).append("\n\n")
                .append(report.method()).append("\n\n")
                .append("| Model | Correct | Natural | Character | Hallucinations | Repeated outputs | Invalid tools | Avg TTFT | Avg total | tok/s | Max context tokens | VRAM |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        report.summaries().forEach((model, summary) -> value.append("| ").append(model)
                .append(" | ").append(summary.correctnessPercent()).append("% | ")
                .append(summary.naturalnessPercent()).append("% | ")
                .append(summary.characterPercent()).append("% | ")
                .append(summary.hallucinations()).append(" | ")
                .append(summary.repeatedOutputs()).append(" | ")
                .append(summary.invalidTools()).append(" | ")
                .append(summary.averageTtftMillis()).append(" ms | ")
                .append(summary.averageTotalMillis()).append(" ms | ")
                .append("%.2f".formatted(summary.tokensPerSecond())).append(" | ")
                .append(summary.maxContextTokens()).append(" | ")
                .append(report.ollamaSizeVramBytes().getOrDefault(model, -1L))
                .append(" bytes |\n"));
        value.append("\n## Scenario outputs\n\n");
        for (Run run : report.runs()) {
            value.append("### ").append(run.scenario()).append(" — ")
                    .append(run.model()).append("\n\n")
                    .append("Input: ").append(run.input()).append("\n\n")
                    .append("Output: ").append(run.output().isBlank()
                            ? "(tool call only)" : run.output()).append("\n\n")
                    .append("Tools: ").append(run.toolCalls()).append("\n\n")
                    .append("Correct=").append(run.correct()).append(", natural=")
                    .append(run.natural()).append(", character=")
                    .append(run.characterConsistent()).append(", hallucinations=")
                    .append(run.hallucinations()).append(", TTFT=")
                    .append(run.ttftMillis()).append(" ms, total=")
                    .append(run.totalMillis()).append(" ms, tokens/sec=")
                    .append("%.2f".formatted(run.tokensPerSecond())).append("\n\n");
        }
        return value.toString();
    }

    private static int count(List<Run> values, java.util.function.Predicate<Run> predicate) {
        return (int) values.stream().filter(predicate).count();
    }
    private static int percent(int value, int total) {
        return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }
    private static boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'').replace('\u2018', '\'')
                .replaceAll("\\s+", " ").strip();
    }
    private static String canonicalAction(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_").toUpperCase(Locale.ROOT);
    }
    private static boolean validJsonObject(String value) {
        try {
            JsonElement parsed = JsonFiles.GSON.fromJson(
                    value == null || value.isBlank() ? "{}" : value, JsonElement.class);
            return parsed != null && parsed.isJsonObject();
        } catch (RuntimeException ignored) { return false; }
    }

    private record Scenario(String id, String category, String context,
            List<ChatMessage> history, String playerMessage,
            List<LlmToolDefinition> tools, Set<String> forbidden) { }
    private record Evaluation(boolean correct, boolean natural, boolean character,
            int hallucinations, boolean toolValid, String notes) { }
    private record Run(String model, String scenario, String category, String input,
            int promptCharacters, int promptTokens, int completionTokens,
            boolean exactTokenCounts, long ttftMillis, long totalMillis,
            double tokensPerSecond, String finishReason, String output,
            List<?> toolCalls, boolean correct, boolean natural,
            boolean characterConsistent, int hallucinations, boolean toolValid,
            boolean repeated, String notes) {
        Run withRepeated(boolean value) {
            return new Run(model, scenario, category, input, promptCharacters, promptTokens,
                    completionTokens, exactTokenCounts, ttftMillis, totalMillis,
                    tokensPerSecond, finishReason, output, toolCalls, correct, natural,
                    characterConsistent, hallucinations, toolValid, value, notes);
        }
    }
    private record Summary(int scenarios, int correctnessPercent, int naturalnessPercent,
            int characterPercent, int hallucinations, int repeatedOutputs, int invalidTools,
            long averageTtftMillis, long averageTotalMillis,
            double tokensPerSecond, int maxContextTokens) { }
    private record Report(String revision, Instant generatedAt, String endpoint,
            String method, Map<String, Long> ollamaSizeVramBytes,
            Map<String, Summary> summaries, List<Run> runs) { }
}
