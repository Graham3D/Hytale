package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueChunker;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.llm.LlmSetupRequiredException;
import com.inigmasgames.persistentnpcs.llm.LlmTimeoutException;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionException;

public final class MilestoneOneTest {
    private MilestoneOneTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyPackagedDefaultsAndIdleRole();
        Path temporary = Files.createTempDirectory("persistent-npcs-test-");
        HttpServer server = startMockServer();
        try {
            runPersistenceContextAndHttpTest(temporary, server.getAddress().getPort());
        } finally {
            server.stop(0);
            deleteTemporaryTree(temporary);
        }
    }

    private static void runPersistenceContextAndHttpTest(Path directory, int port) {
        UUID npcId = UUID.fromString("3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npcId, "Mara", "Blacksmith", "Direct",
                "A village smith.", "Speak believably.", "Above the forge", "The forge",
                List.of("honesty"), List.of("boasting"), 5).validated();

        RelationshipStore relationships = new RelationshipStore(directory);
        relationships.load();
        MemoryStore memories = new MemoryStore(directory, 100);
        memories.load();
        memories.append(new MemoryRecord(UUID.randomUUID(), npcId, playerId,
                Instant.parse("2026-08-24T12:00:00Z"), 0.7,
                "Player previously asked to repair iron tools."));
        memories.append(new MemoryRecord(UUID.randomUUID(), npcId, playerId,
                Instant.parse("2026-08-24T12:30:00Z"), 0.35,
                "Player said hello. Mara replied: Morning. What brings you to the forge?"));
        memories.append(new MemoryRecord(UUID.randomUUID(), npcId, otherPlayerId,
                Instant.parse("2026-08-24T13:00:00Z"), 0.9, "SECRET FROM ANOTHER PLAYER"));

        ConversationContextBuilder contextBuilder = new ConversationContextBuilder(
                relationships, memories, 4);
        ConversationSession session = new ConversationSession(UUID.randomUUID(), npcId,
                playerId, Instant.now());
        var request = contextBuilder.build(session, profile, "Can you repair this?",
                new MinimalWorldContext("default", 10, 64, -2));
        String system = request.messages().get(0).content();
        assert system.contains("Player previously asked to repair iron tools.");
        assert !system.contains("Morning. What brings you to the forge?");
        assert !system.contains("SECRET FROM ANOTHER PLAYER");
        assert system.indexOf("CURRENT PLAYER MESSAGE")
                < system.indexOf("CURRENT_WORLD_STATE (authoritative");
        assert system.indexOf("CURRENT_WORLD_STATE (authoritative")
                < system.indexOf("RECENT CONVERSATION");
        assert system.indexOf("RECENT CONVERSATION")
                < system.indexOf("VALIDATED_ACTIVE_TASK (server-confirmed");
        assert system.indexOf("VALIDATED_ACTIVE_TASK (server-confirmed")
                < system.indexOf("MEMORY (strictly filtered");
        assert system.indexOf("MEMORY (strictly filtered")
                < system.indexOf("PROFILE/BACKSTORY (authored");
        assert system.indexOf("PROFILE/BACKSTORY (authored")
                < system.indexOf("ROLE/CAPABILITIES");
        var greeting = contextBuilder.build(session, profile,
                "Hello Mara, how are you?", new MinimalWorldContext("default", 10, 64, -2));
        assert !greeting.messages().get(0).content()
                .contains("Morning. What brings you to the forge?");
        assert request.messages().size() == 2;
        assert request.messages().get(1).content().equals("Can you repair this?");

        FrameworkConfig config = new FrameworkConfig(
                "http://127.0.0.1:" + port + "/v1/chat/completions",
                "mock-local-model", "", 1000, 100, 0.2, 80, 600, 4, 100, 300,
                true, 1000, 120, "none");
        List<String> providerDiagnostics = new CopyOnWriteArrayList<>();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                config, providerDiagnostics::add);
        var providerStatus = provider.checkStatus().join();
        assert providerStatus.endpoint().equals(config.endpoint());
        assert providerStatus.model().equals("mock-local-model");
        assert providerStatus.configured();
        assert providerStatus.reachable();
        assert providerStatus.streamingEnabled();
        assert providerStatus.reason().contains("configured model is available");

        FrameworkConfig placeholderConfig = new FrameworkConfig(
                config.endpoint(), "CHANGE_ME_TO_LOADED_MODEL_ID", "", 1000, 3000,
                0.2, 80, 600, 4, 100, 300, true, 1000, 120, "none");
        var placeholderStatus = new OpenAiCompatibleProvider(placeholderConfig)
                .checkStatus().join();
        assert !placeholderStatus.configured();
        assert placeholderStatus.reachable();
        assert placeholderStatus.reason().contains("setup is required");
        assert placeholderStatus.reason().contains("mock-local-model");

        ConversationService service = new ConversationService(contextBuilder,
                provider, relationships, memories, 1200, ignored -> { });
        List<String> streamedTokens = new CopyOnWriteArrayList<>();
        ConversationOutcome outcome = service.converse(session, profile,
                "Can you repair this?", new MinimalWorldContext("default", 10, 64, -2),
                streamedTokens::add).join();

        assert outcome.dialogue().equals("Aye. Set it on the bench and I'll inspect it.");
        assert String.join("", streamedTokens).equals(outcome.dialogue());
        assert outcome.llmLatency().requestStartedAt() != null;
        assert outcome.llmLatency().streaming();
        assert outcome.llmLatency().timeToFirstTokenMillis() >= 100;
        assert outcome.llmLatency().completionMillis()
                >= outcome.llmLatency().timeToFirstTokenMillis();
        assert outcome.llmLatency().completionMillis() > config.requestTimeoutMillis();
        assert outcome.llmLatency().completionMillis() < 350;
        assert outcome.totalConversationMillis() >= outcome.llmLatency().completionMillis();
        assert relationships.getOrDefault(npcId, playerId, 5).interactionCount() == 1;
        assert memories.recent(npcId, playerId, 10).size() == 3;

        ConversationOutcome secondOutcome = service.converse(session, profile,
                "Can you sharpen it too?", new MinimalWorldContext("default", 10, 64, -2),
                ignored -> { }).join();
        assert secondOutcome.dialogue().equals(outcome.dialogue());
        assert relationships.getOrDefault(npcId, playerId, 5).interactionCount() == 2;
        assert memories.recent(npcId, playerId, 10).size() == 4;
        assert providerDiagnostics.stream().anyMatch(line -> line.contains("HTTP headers"));
        assert providerDiagnostics.stream().anyMatch(
                line -> line.contains("first dialogue token"));
        assert providerDiagnostics.stream().anyMatch(line -> line.contains("SSE summary")
                && line.contains("reasoningEvents=1") && line.contains("done=true"));

        RelationshipStore reloadedRelationships = new RelationshipStore(directory);
        reloadedRelationships.load();
        MemoryStore reloadedMemories = new MemoryStore(directory, 100);
        reloadedMemories.load();
        assert reloadedRelationships.getOrDefault(npcId, playerId, 5).interactionCount() == 2;
        assert reloadedMemories.recent(npcId, playerId, 10).size() == 4;

        verifyNonStreamingFallback(config, request);
        verifyPhaseSpecificTimeouts(config, request);
        verifyDialogueChunking();

        System.out.printf("Measured SSE mock latency: TTFT=%dms completion=%dms totalConversation=%dms%n",
                outcome.llmLatency().timeToFirstTokenMillis(),
                outcome.llmLatency().completionMillis(), outcome.totalConversationMillis());
    }

    private static void verifyNonStreamingFallback(FrameworkConfig source, LlmRequest request) {
        FrameworkConfig fallbackConfig = new FrameworkConfig(source.endpoint(),
                "fallback-model", "", 1000, 100, 0.2, 80, 600, 4, 100, 300,
                true, 1000, 120, "none");
        List<String> delivered = new CopyOnWriteArrayList<>();
        var result = new OpenAiCompatibleProvider(fallbackConfig)
                .generateResponse(request, delivered::add).join();
        assert result.text().equals("Fallback JSON response.");
        assert !result.latency().streaming();
        assert result.latency().timeToFirstTokenMillis()
                == result.latency().completionMillis();
        assert delivered.equals(List.of("Fallback JSON response."));
    }

    private static void verifyPhaseSpecificTimeouts(FrameworkConfig source, LlmRequest request) {
        FrameworkConfig idleConfig = new FrameworkConfig(source.endpoint(),
                "idle-model", "", 1000, 100, 0.2, 80, 600, 4, 100, 300,
                true, 1000, 100, "none");
        try {
            new OpenAiCompatibleProvider(idleConfig).generateResponse(request).join();
            throw new AssertionError("Idle SSE stream should time out");
        } catch (CompletionException expected) {
            assert expected.getCause() instanceof LlmTimeoutException;
            assert ((LlmTimeoutException) expected.getCause()).phase()
                    == LlmTimeoutException.Phase.STREAM_IDLE;
        }

        FrameworkConfig startConfig = new FrameworkConfig(source.endpoint(),
                "slow-start-model", "", 1000, 100, 0.2, 80, 600, 4, 100, 300,
                true, 100, 1000, "none");
        try {
            new OpenAiCompatibleProvider(startConfig).generateResponse(request).join();
            throw new AssertionError("Missing response start should time out");
        } catch (CompletionException expected) {
            assert expected.getCause() instanceof LlmTimeoutException;
            assert ((LlmTimeoutException) expected.getCause()).phase()
                    == LlmTimeoutException.Phase.RESPONSE_START;
        }
    }

    private static void verifyDialogueChunking() {
        List<String> chunks = new CopyOnWriteArrayList<>();
        DialogueChunker chunker = new DialogueChunker("Mara", chunks::add);
        chunker.accept("The forge is hot enough now.");
        assert chunks.equals(List.of("Mara: The forge is hot enough now."));
        chunker.accept(" Bring me the iron");
        chunker.complete("ignored because deltas were received");
        assert chunks.get(1).equals("Mara: Bring me the iron");
    }

    private static void verifyPackagedDefaultsAndIdleRole() throws IOException {
        FrameworkConfig defaults;
        try (var input = MilestoneOneTest.class.getResourceAsStream("/defaults/config.json")) {
            assert input != null;
            defaults = JsonFiles.GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8), FrameworkConfig.class);
        }
        assert defaults.endpoint().equals("http://127.0.0.1:11434/v1/chat/completions");
        assert defaults.model().equals("nemotron-3-nano:4b");
        assert defaults.streamingEnabled();
        assert defaults.effectiveResponseStartTimeoutMillis() == 60_000;
        assert defaults.effectiveStreamIdleTimeoutMillis() == 15_000;
        assert defaults.configuredReasoningEffort().equals("none");

        FrameworkConfig placeholder = new FrameworkConfig(defaults.endpoint(),
                "CHANGE_ME_TO_LOADED_MODEL_ID", "", 1000, 3000, 0.2, 80,
                600, 4, 100, 300, true, 1000, 120, "none");
        OpenAiCompatibleProvider unconfigured = new OpenAiCompatibleProvider(placeholder);
        try {
            unconfigured.generateResponse(new com.inigmasgames.persistentnpcs.llm.LlmRequest(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    List.of(new com.inigmasgames.persistentnpcs.llm.ChatMessage(
                            "user", "hello")))).join();
            throw new AssertionError("Placeholder model should require setup");
        } catch (java.util.concurrent.CompletionException expected) {
            assert expected.getCause() instanceof LlmSetupRequiredException;
        }

        try (var input = MilestoneOneTest.class.getResourceAsStream(
                "/Server/NPC/Roles/PersistentNPCs/PersistentNPCs_Mara.json")) {
            assert input != null;
            var role = JsonFiles.GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8),
                    com.google.gson.JsonObject.class);
            String roleJson = role.toString();
            assert roleJson.contains("\"TargetSlot\":\"SocialFocus\"");
            assert roleJson.contains("\"HeadMotion\":{\"Type\":\"Watch\"}");
            assert roleJson.contains("\"UsePathfinder\":true");
            assert roleJson.contains("\"BodyMotion\":{\"Type\":\"Nothing\"}");
        }
        try (var input = MilestoneOneTest.class.getResourceAsStream(
                "/defaults/profiles/mara.json")) {
            assert input != null;
            NpcProfile mara = JsonFiles.GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8), NpcProfile.class);
            assert mara.role().equals("Villager with blacksmith training");
            assert mara.biography().contains("not her default conversation topic");
        }
    }

    private static HttpServer startMockServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                if (requestBody.contains("idle-model")) {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    writeReasoningEvent(exchange, "active");
                    pause(250);
                    return;
                }
                if (requestBody.contains("slow-start-model")) {
                    pause(250);
                    writeJson(exchange, "Too late.");
                    return;
                }
                if (!requestBody.contains("mock-local-model")
                        && !requestBody.contains("fallback-model")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                if (requestBody.contains("fallback-model")
                        && requestsStreaming(requestBody)) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                if (requestBody.contains("fallback-model")) {
                    writeJson(exchange, "Fallback JSON response.");
                    return;
                }
                if (!requestBody.contains("Can you repair this?")) {
                    if (!requestBody.contains("Can you sharpen it too?")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                    }
                }
                if (requestsStreaming(requestBody)) {
                    if (!requestBody.contains("\"reasoning_effort\":\"none\"")
                            && !requestBody.contains("\"reasoning_effort\": \"none\"")) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    writeRoleEvent(exchange);
                    pause(70);
                    writeReasoningEvent(exchange, "internal");
                    pause(70);
                    writeEvent(exchange, "Aye. Set it on the bench");
                    pause(70);
                    writeEvent(exchange, " and I'll inspect it.");
                    writeFinishEvent(exchange);
                    exchange.getResponseBody().write("data: [DONE]\n\n"
                            .getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    pause(300);
                    return;
                }
                writeJson(exchange, "Aye. Set it on the bench and I'll inspect it.");
            }
        });
        server.createContext("/v1/models", exchange -> {
            try (exchange) {
                if (!exchange.getRequestMethod().equals("GET")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                byte[] bytes = ("{\"object\":\"list\",\"data\":[{\"id\":"
                        + "\"mock-local-model\",\"object\":\"model\"},"
                        + "{\"id\":\"fallback-model\",\"object\":\"model\"},"
                        + "{\"id\":\"idle-model\",\"object\":\"model\"},"
                        + "{\"id\":\"slow-start-model\",\"object\":\"model\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.setExecutor(command -> Thread.ofVirtual().start(command));
        server.start();
        return server;
    }

    private static void writeEvent(com.sun.net.httpserver.HttpExchange exchange, String content)
            throws IOException {
        String json = JsonFiles.GSON.toJson(content);
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{\"content\":"
                + json + "}}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static void writeRoleEvent(com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{"
                + "\"role\":\"assistant\",\"content\":\"\"},"
                + "\"finish_reason\":null}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static void writeReasoningEvent(
            com.sun.net.httpserver.HttpExchange exchange, String reasoning) throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{"
                + "\"content\":\"\",\"reasoning\":" + JsonFiles.GSON.toJson(reasoning)
                + "},\"finish_reason\":null}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static void writeFinishEvent(com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {
        exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{},"
                + "\"finish_reason\":\"stop\"}]}\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private static boolean requestsStreaming(String requestBody) {
        return requestBody.contains("\"stream\":true")
                || requestBody.contains("\"stream\": true");
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String content)
            throws IOException {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + JsonFiles.GSON.toJson(content) + "}}]}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void deleteTemporaryTree(Path root) throws IOException {
        MemoryStore.flushAll();
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
