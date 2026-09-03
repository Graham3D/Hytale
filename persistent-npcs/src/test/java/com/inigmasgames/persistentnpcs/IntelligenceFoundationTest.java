package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.InvalidDialogueException;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.social.NpcSocialAttentionService;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskScheduler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import org.joml.Vector3d;

public final class IntelligenceFoundationTest {
    private IntelligenceFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyActionRegistryAndIsolation();
        verifyTaskPersistence();
        verifyFollowSpacingAndHeldItemFacts();
        verifyHeldItemContextDoesNotStale();
        verifyImmediateAndPersistentPlayerName();
        verifyNameOnlyDialogueIsRejectedWithoutPoisoningSession();
        verifyStreamingToolCallAssembly();
        System.out.println("Persistent NPC intelligence foundation tests passed.");
    }

    private static void verifyActionRegistryAndIsolation() {
        UUID npc = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npc, "Mara", "Blacksmith", "Direct",
                "Smith", "Help", "", "", List.of(), List.of(),
                List.of("BLACKSMITH"), List.of("FOLLOW_PLAYER"), 5).validated();
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), npc, playerA, Instant.now());
        NpcActionContext context = new NpcActionContext(profile, session,
                NpcPerceptionSnapshot.unavailable(npc));
        NpcActionRegistry registry = new NpcActionRegistry();
        AtomicInteger executions = new AtomicInteger();
        registry.register(new NpcActionDefinition("FOLLOW_PLAYER", "Follow", objectSchema(),
                Set.of("FOLLOW_PLAYER"), Set.of(), ignored -> true,
                (request, ignored) -> NpcActionResult.success("valid"),
                (request, ignored) -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            NpcActionResult.success("followed"));
                }, "follow result"));
        assert registry.toolsFor(context).size() == 1;
        assert registry.execute(new NpcActionRequest("FOLLOW_Player",
                new JsonObject(), "call-1"), context).join().success();
        assert executions.get() == 1;
        assert !registry.execute(new NpcActionRequest("RUN_CONSOLE_COMMAND",
                new JsonObject(), "call-2"), context).join().success();
        assert executions.get() == 1;

        ConversationSessionManager sessions =
                new ConversationSessionManager(Duration.ofMinutes(5));
        ConversationSession a = sessions.focus(npc, playerA, Instant.now());
        ConversationSession b = sessions.focus(npc, playerB, Instant.now());
        assert !a.sessionId().equals(b.sessionId());
        assert !a.playerId().equals(b.playerId());
        assert sessions.active(playerA, Instant.now()).orElseThrow().sessionId()
                .equals(a.sessionId());
        assert sessions.active(playerB, Instant.now()).orElseThrow().sessionId()
                .equals(b.sessionId());
        UUID otherNpc = UUID.randomUUID();
        ConversationSession other = sessions.focus(otherNpc, playerA, Instant.now());
        assert sessions.active(playerA, npc, Instant.now()).orElseThrow().sessionId()
                .equals(a.sessionId());
        assert sessions.active(playerA, otherNpc, Instant.now()).orElseThrow().sessionId()
                .equals(other.sessionId());
        sessions.end(playerA, otherNpc);
        assert sessions.active(playerA, otherNpc, Instant.now()).isEmpty();
        assert sessions.active(playerA, npc, Instant.now()).isPresent();
    }

    private static void verifyTaskPersistence() throws Exception {
        Path directory = Files.createTempDirectory("persistent-npc-tasks-");
        try {
            NpcTaskStore store = new NpcTaskStore(directory);
            store.load();
            UUID npc = UUID.randomUUID();
            NpcTask task = new NpcTask(UUID.randomUUID(), npc, UUID.randomUUID(),
                    "SCHEDULE_MEETING", UUID.randomUUID(), 1.0, 2.0, 3.0,
                    Instant.parse("2030-01-01T22:00:00Z"), "Meeting",
                    NpcTaskState.PLANNED, Instant.now(), null);
            store.put(task);
            NpcTaskStore reloaded = new NpcTaskStore(directory);
            reloaded.load();
            assert reloaded.activeFor(npc).size() == 1;
            assert reloaded.activeFor(npc).get(0).scheduledGameTime()
                    .equals(task.scheduledGameTime());
        } finally {
            delete(directory);
        }
    }

    private static void verifyFollowSpacingAndHeldItemFacts() {
        Vector3d target = NpcTaskScheduler.trailingPosition(
                new Vector3d(10, 64, 10), new Vector3d(0, 0, 1));
        assert Math.abs(target.x - 10.0) < 0.0001;
        assert Math.abs(target.y - 64.0) < 0.0001;
        assert Math.abs(target.z - 7.25) < 0.0001;
        assert NpcTaskScheduler.FOLLOW_STOP_DISTANCE
                < NpcTaskScheduler.FOLLOW_RESUME_DISTANCE;
        Vector3d conversation = NpcSocialAttentionService.conversationalPosition(
                new Vector3d(10, 64, 10), new Vector3d(0, 0, 1));
        assert Math.abs(conversation.x - 10.0) < 0.0001;
        assert Math.abs(conversation.y - 64.0) < 0.0001;
        assert Math.abs(conversation.z - 11.75) < 0.0001;

        String held = new PerceivedItem(null, "Weapon_Sword_Iron", "Iron Sword",
                1, 90, 100, "{}", 0).compact();
        assert held.contains("itemId=Weapon_Sword_Iron");
        assert held.contains("displayName=Iron Sword");
        assert held.contains("quantity=1");
    }

    private static void verifyImmediateAndPersistentPlayerName() throws Exception {
        Path directory = Files.createTempDirectory("persistent-npc-name-");
        try {
            UUID npcId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            NpcProfile profile = new NpcProfile(npcId, "Mara", "Villager",
                    "Attentive", "Village resident", "Listen", "", "",
                    List.of(), List.of(), List.of("BLACKSMITH"), List.of(), 5).validated();
            RelationshipStore relationships = new RelationshipStore(directory);
            relationships.load();
            MemoryStore memories = new MemoryStore(directory, 100);
            memories.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, 6);
            List<LlmRequest> requests = new CopyOnWriteArrayList<>();
            AtomicInteger responseIndex = new AtomicInteger();
            LlmProvider provider = new LlmProvider() {
                @Override
                public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                    requests.add(request);
                    String text = responseIndex.getAndIncrement() == 0
                            ? "Graham, noted." : "Your name is Graham.";
                    return CompletableFuture.completedFuture(new LlmResult(text,
                            new LlmLatency(Instant.now(), 0, 0, false)));
                }

                @Override
                public CompletableFuture<LlmProviderStatus> checkStatus() {
                    return CompletableFuture.completedFuture(new LlmProviderStatus(
                            "test", "test", true, true, false, "test"));
                }

                @Override
                public String description() {
                    return "test";
                }
            };
            List<String> logs = new CopyOnWriteArrayList<>();
            ConversationService service = new ConversationService(builder, provider,
                    relationships, memories, 1200, logs::add);
            ConversationSession session = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            service.converse(session, profile, "My name is Graham.",
                    new MinimalWorldContext("Hytale", 0, 64, 0)).join();
            service.converse(session, profile, "What is my name?",
                    new MinimalWorldContext("Hytale", 0, 64, 0)).join();

            String secondPrompt = requests.get(1).messages().get(0).content();
            int current = secondPrompt.indexOf("CURRENT PLAYER MESSAGE");
            int recent = secondPrompt.indexOf("RECENT CONVERSATION");
            int perception = secondPrompt.indexOf("CURRENT_WORLD_STATE (authoritative");
            assert current >= 0 && current < perception && perception < recent;
            assert secondPrompt.contains("Player: My name is Graham.");
            assert secondPrompt.contains("Mara: Graham, noted.");
            assert logs.stream().anyMatch(value -> value.contains("LLM recent conversation")
                    && value.contains("Player: My name is Graham.")
                    && value.contains("Mara: Graham, noted."));

            MemoryStore reloaded = new MemoryStore(directory, 100);
            reloaded.load();
            ConversationContextBuilder afterRestart = new ConversationContextBuilder(
                    relationships, reloaded, 6);
            ConversationSession newSession = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            String restartPrompt = afterRestart.build(newSession, profile,
                    "What is my name?", new MinimalWorldContext("Hytale", 0, 64, 0))
                    .messages().get(0).content();
            assert restartPrompt.contains("Player fact: stated name=Graham.");
            service.shutdown();
        } finally {
            delete(directory);
        }
    }

    private static void verifyNameOnlyDialogueIsRejectedWithoutPoisoningSession()
            throws Exception {
        Path directory = Files.createTempDirectory("persistent-npc-invalid-dialogue-");
        try {
            UUID npcId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            NpcProfile profile = new NpcProfile(npcId, "Mara", "Villager",
                    "Attentive", "Village resident", "Listen", "", "",
                    List.of(), List.of(), List.of(), List.of(), 5).validated();
            RelationshipStore relationships = new RelationshipStore(directory);
            relationships.load();
            MemoryStore memories = new MemoryStore(directory, 100);
            memories.load();
            AtomicInteger responses = new AtomicInteger();
            LlmProvider provider = new LlmProvider() {
                @Override
                public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                    String text = responses.getAndIncrement() == 0
                            ? "Mara" : "I'm doing well, thank you.";
                    return CompletableFuture.completedFuture(new LlmResult(text,
                            new LlmLatency(Instant.now(), 0, 0, false)));
                }

                @Override
                public CompletableFuture<LlmProviderStatus> checkStatus() {
                    return CompletableFuture.completedFuture(new LlmProviderStatus(
                            "test", "test", true, true, false, "test"));
                }

                @Override
                public String description() {
                    return "test";
                }
            };
            ConversationService service = new ConversationService(
                    new ConversationContextBuilder(relationships, memories, 6), provider,
                    relationships, memories, 1200, ignored -> { });
            ConversationSession session = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            try {
                service.converse(session, profile, "Greetings",
                        new MinimalWorldContext("Hytale", 0, 64, 0)).join();
                throw new AssertionError("Name-only non-name reply was accepted");
            } catch (java.util.concurrent.CompletionException failure) {
                assert failure.getCause() instanceof InvalidDialogueException;
            }
            assert session.recentTurns(8).isEmpty()
                    : "Rejected dialogue poisoned recent session history";
            var recovered = service.converse(session, profile, "How are you?",
                    new MinimalWorldContext("Hytale", 0, 64, 0)).join();
            assert recovered.dialogue().equals("I'm doing well, thank you.");
            assert session.recentTurns(8).size() == 1;
            service.shutdown();
        } finally {
            delete(directory);
        }
    }

    private static void verifyHeldItemContextDoesNotStale() throws Exception {
        Path directory = Files.createTempDirectory("persistent-npc-held-item-");
        try {
            UUID npcId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            RelationshipStore relationships = new RelationshipStore(directory);
            relationships.load();
            MemoryStore memories = new MemoryStore(directory, 20);
            memories.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, 4);
            NpcProfile profile = new NpcProfile(npcId, "Mara", "Villager",
                    "Attentive", "Village resident", "Listen", "", "",
                    List.of(), List.of(), List.of(), List.of(), 5).validated();
            ConversationSession session = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            session.appendTurn("What am I holding?", "An iron sword.", Instant.now());

            String sword = builder.build(session, profile, "What am I holding?",
                    new MinimalWorldContext("default", 0, 64, 0),
                    heldSnapshot(npcId, 1, "Weapon_Sword_Iron", "Iron Sword"), List.of())
                    .messages().get(0).content();
            String obscure = builder.build(session, profile, "What am I holding now?",
                    new MinimalWorldContext("default", 0, 64, 0),
                    heldSnapshot(npcId, 6, "Ingredient_Crystal_Void", "Void Crystal"), List.of())
                    .messages().get(0).content();
            String empty = builder.build(session, profile, "And now?",
                    new MinimalWorldContext("default", 0, 64, 0),
                    heldSnapshot(npcId, 3, null, null), List.of())
                    .messages().get(0).content();
            String swordAgain = builder.build(session, profile, "And now?",
                    new MinimalWorldContext("default", 0, 64, 0),
                    heldSnapshot(npcId, 1, "Weapon_Sword_Iron", "Iron Sword"), List.of())
                    .messages().get(0).content();

            assert sword.contains("HELD_ITEM:\n  id=Weapon_Sword_Iron");
            assert obscure.contains("HELD_ITEM:\n  id=Ingredient_Crystal_Void");
            assert obscure.contains("displayName=Void Crystal");
            assert empty.contains("HELD_ITEM: NONE");
            assert swordAgain.contains("HELD_ITEM:\n  id=Weapon_Sword_Iron");
        } finally {
            delete(directory);
        }
    }

    private static NpcPerceptionSnapshot heldSnapshot(
            UUID npcId, int slot, String itemId, String displayName) {
        PerceivedItem held = itemId == null ? null : new PerceivedItem(
                null, itemId, displayName, 1, 0, 0, "{}", 0);
        return new NpcPerceptionSnapshot(npcId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, 64, 0, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), slot, held, List.of());
    }

    private static void verifyStreamingToolCallAssembly() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                String events = """
                        data: {"choices":[{"delta":{"role":"assistant","content":""},"finish_reason":null}]}

                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"FOLLOW_","arguments":"{"}}]},"finish_reason":null}]}

                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"PLAYER","arguments":"}"}}]},"finish_reason":null}]}

                        data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                        """;
                exchange.getResponseBody().write(events.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.setExecutor(command -> Thread.ofVirtual().start(command));
        server.start();
        try {
            FrameworkConfig config = new FrameworkConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort()
                            + "/v1/chat/completions",
                    "nemotron-test", "", 1000, 1000, 0.1, 80,
                    600, 4, 100, 300, true, 1000, 500, "none");
            LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), List.of(new ChatMessage("user", "Follow me")),
                    List.of(new LlmToolDefinition(
                            "FOLLOW_PLAYER", "Follow", objectSchema())));
            var result = new OpenAiCompatibleProvider(config).generateResponse(request).join();
            assert result.text().isEmpty();
            assert result.toolCalls().size() == 1;
            assert result.toolCalls().get(0).name().equals("FOLLOW_PLAYER");
            assert result.toolCalls().get(0).arguments().equals("{}");
            assert result.finishReason().equals("tool_calls");
            assert result.latency().streaming();
        } finally {
            server.stop(0);
        }
    }

    private static JsonObject objectSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static void delete(Path root) throws Exception {
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
