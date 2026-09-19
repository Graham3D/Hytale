package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Regressions derived directly from Mara_2026-08-30_11-42-33.jsonl. */
public final class R061TraceLagRepairTest {
    private R061TraceLagRepairTest() { }

    public static void main(String[] args) throws Exception {
        conversationalRepairCannotBecomeCraftingOrDeliberation();
        realCraftRequestRemainsComplex();
        transientGpuGatesCannotEvictTheActiveLlm();
        reasoningOnlyStreamRecoversOnTheSameModel();
        System.out.println("R061 trace-driven routing, residency, and reasoning recovery tests passed.");
    }

    private static void conversationalRepairCannotBecomeCraftingOrDeliberation() {
        NpcProfile mara = profile();
        CognitiveContextPlan plan = CognitiveContextRouter.route(mara,
                "That doesn't make any sense. What do you mean, Mara?",
                DialogueMode.ORDINARY_CONVERSATION, null, null);
        assert plan.depth() == CognitiveDepth.CONTEXTUAL_CONVERSATION : plan;
        assert plan.detectedIntent().equals("CLARIFY_PREVIOUS_DIALOGUE") : plan;
        assert !plan.includedSections().contains("ACTIONS") : plan;
        assert !plan.includedSections().contains("TASKS") : plan;
        var reasoning = AdaptiveReasoningRouter.route(plan,
                DialogueMode.ORDINARY_CONVERSATION, null, 4,
                "That doesn't make any sense. What do you mean, Mara?");
        assert reasoning.policy() == AdaptiveReasoningPolicy.GROUNDED_DIALOGUE : reasoning;
        assert !reasoning.policy().reasoningEnabled();
    }

    private static void realCraftRequestRemainsComplex() {
        CognitiveContextPlan plan = CognitiveContextRouter.route(profile(),
                "Please make me a steel sword.", DialogueMode.ORDINARY_CONVERSATION,
                null, null);
        assert plan.depth() == CognitiveDepth.COMPLEX_INTENT : plan;
        assert plan.detectedIntent().equals("REQUEST_ACTION_OR_COMMITMENT") : plan;
    }

    private static void transientGpuGatesCannotEvictTheActiveLlm() {
        assert !AiServiceRouter.ttsMayReclaimActiveLlm("local-gpu-gate");
        assert !AiServiceRouter.ttsMayReclaimActiveLlm("gpu-utilization-pressure");
        assert !AiServiceRouter.ttsMayReclaimActiveLlm("hytale-frame-pressure");
        assert AiServiceRouter.ttsMayReclaimActiveLlm("vram-headroom-pressure");
    }

    private static void reasoningOnlyStreamRecoversOnTheSameModel() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> logs = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                String body = new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                bodies.add(body);
                calls.incrementAndGet();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                String effort = JsonFiles.GSON.fromJson(body,
                        com.google.gson.JsonObject.class)
                        .get("reasoning_effort").getAsString();
                if ("none".equals(effort)) {
                    writeContent(exchange, "I meant my greeting was getting carried away.");
                    writeFinish(exchange);
                    exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(
                            StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    return;
                }
                try {
                    for (int index = 0;
                            index < OpenAiCompatibleProvider.MAX_REASONING_ONLY_EVENTS + 8;
                            index++) {
                        writeReasoning(exchange, "internal");
                    }
                } catch (IOException clientClosedAfterBound) {
                    // The watchdog intentionally closes the reasoning-only response body.
                }
            }
        });
        server.setExecutor(command -> Thread.ofVirtual().start(command));
        server.start();
        try {
            FrameworkConfig config = new FrameworkConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort()
                            + "/v1/chat/completions",
                    "nemotron-test", "", 2_000, 30_000, 0.2, 640,
                    800, 0, 10, 600, true, 30_000, 15_000, "none");
            LlmExecutionPolicy policy = new LlmExecutionPolicy("DELIBERATIVE",
                    LlmExecutionPolicy.ReasoningMode.ENABLED,
                    List.of("TRACE_REPRODUCTION"), 160);
            LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), List.of(
                            new ChatMessage("system", "Reply briefly."),
                            new ChatMessage("user", "What did you mean?")), List.of())
                    .withExecutionPolicy(policy).withGenerationParameters(0.2, 512);
            var result = new OpenAiCompatibleProvider(config, logs::add)
                    .generateResponse(request).join();
            assert result.text().equals(
                    "I meant my greeting was getting carried away.") : result.text();
            assert calls.get() == 2 : calls;
            assert JsonFiles.GSON.fromJson(bodies.getFirst(),
                    com.google.gson.JsonObject.class).get("reasoning_effort")
                            .getAsString().equals("low") : bodies.getFirst();
            assert JsonFiles.GSON.fromJson(bodies.get(1),
                    com.google.gson.JsonObject.class).get("reasoning_effort")
                            .getAsString().equals("none") : bodies.get(1);
            assert result.reasoningTelemetry().actualMode().equals(
                    "RECOVERED_WITH_REASONING_DISABLED") : result.reasoningTelemetry();
            assert logs.stream().anyMatch(value -> value.contains(
                    "RETRY_SAME_MODEL_REASONING_DISABLED"));
        } finally {
            server.stop(0);
        }
    }

    private static NpcProfile profile() {
        return new NpcProfile(UUID.randomUUID(), "Mara", "blacksmith", "direct",
                "A practical smith.", "Do good work.", "home", "forge",
                List.of("craft"), List.of("lies"), List.of(), List.of(), 5);
    }

    private static void writeReasoning(com.sun.net.httpserver.HttpExchange exchange,
            String reasoning) throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{"
                + "\"content\":\"\",\"reasoning\":" + JsonFiles.GSON.toJson(reasoning)
                + "},\"finish_reason\":null}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static void writeContent(com.sun.net.httpserver.HttpExchange exchange,
            String content) throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{"
                + "\"content\":" + JsonFiles.GSON.toJson(content)
                + "},\"finish_reason\":null}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static void writeFinish(com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{},"
                + "\"finish_reason\":\"stop\"}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }
}
