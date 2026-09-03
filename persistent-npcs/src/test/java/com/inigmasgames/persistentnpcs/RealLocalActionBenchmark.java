package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RealLocalActionBenchmark {
    private RealLocalActionBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        FrameworkConfig config;
        try (var input = RealLocalActionBenchmark.class
                .getResourceAsStream("/defaults/config.json")) {
            config = JsonFiles.GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8),
                    FrameworkConfig.class).validated();
        }
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real local action benchmark skipped: " + status.reason());
            return;
        }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(
                        new ChatMessage("system",
                                "You are an NPC action selector. The only valid function name is "
                                        + "FOLLOW_PLAYER. Call that exact function for the request. "
                                        + "Never rename registered tools."),
                        new ChatMessage("user", "Mara, follow me.")),
                List.of(new LlmToolDefinition("FOLLOW_PLAYER",
                        "The NPC begins following the requesting player. "
                                + "Use this exact function for follow requests.", schema)));
        var result = provider.generateResponse(request).join();
        assert !result.toolCalls().isEmpty()
                : "Nemotron did not emit a tool call: " + result.text();
        String normalized = result.toolCalls().get(0).name()
                .strip().replaceAll("[\\s-]+", "_").replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);
        boolean registered = request.tools().stream().anyMatch(tool ->
                tool.function().name().equals(normalized));
        // Model output is untrusted. An invented name is a successful fail-closed probe,
        // not evidence that the deterministic action registry would execute it.
        if (!registered) {
            System.out.printf("Real local action safely rejected unknown model tool=%s "
                            + "TTFT=%dms completion=%dms%n",
                    result.toolCalls().get(0).name(),
                    result.latency().timeToFirstTokenMillis(),
                    result.latency().completionMillis());
            return;
        }
        System.out.printf(
                "Real local action latency: TTFT=%dms completion=%dms streaming=%s tool=%s%n",
                result.latency().timeToFirstTokenMillis(),
                result.latency().completionMillis(),
                result.latency().streaming(),
                result.toolCalls().get(0).name());
    }
}
