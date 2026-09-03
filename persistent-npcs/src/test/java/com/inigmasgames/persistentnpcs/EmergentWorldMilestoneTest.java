package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AutonomousEventDirector;
import com.inigmasgames.persistentnpcs.autonomy.AutonomousOpportunity;
import com.inigmasgames.persistentnpcs.autonomy.AutonomyGate;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.ModelRoutingProvider;
import com.inigmasgames.persistentnpcs.llm.ModelTier;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.monster.AgentPersistence;
import com.inigmasgames.persistentnpcs.monster.HighLevelIntent;
import com.inigmasgames.persistentnpcs.monster.ImmersiveAgentStore;
import com.inigmasgames.persistentnpcs.monster.ImmersiveEntityAgent;
import com.inigmasgames.persistentnpcs.monster.MonsterReasoningAdapter;
import com.inigmasgames.persistentnpcs.monster.MonsterReasoningContext;
import com.inigmasgames.persistentnpcs.monster.ReasoningTrigger;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestDirector;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.quest.QuestOpportunityContext;
import com.inigmasgames.persistentnpcs.quest.QuestProposal;
import com.inigmasgames.persistentnpcs.quest.QuestStatus;
import com.inigmasgames.persistentnpcs.quest.QuestTargetKind;
import com.inigmasgames.persistentnpcs.quest.QuestType;
import com.inigmasgames.persistentnpcs.quest.ResolvedWorldTarget;
import com.inigmasgames.persistentnpcs.quest.RewardBudget;
import com.inigmasgames.persistentnpcs.quest.RewardCandidate;
import com.inigmasgames.persistentnpcs.quest.RewardResolver;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.scene.NpcSceneRunner;
import com.inigmasgames.persistentnpcs.scene.NpcSceneService;
import com.inigmasgames.persistentnpcs.social.GossipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic acceptance coverage for the emergent-world framework milestone. */
public final class EmergentWorldMilestoneTest {
    private EmergentWorldMilestoneTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("immersive-ai-emergent-");
        try {
            verifyDynamicQuestAndConsequences(directory.resolve("quest"));
            verifyAutonomyIsEventDriven();
            verifyMonsterOverlayAndPromotion(directory.resolve("monster"));
            verifyProfileSchemaAndGrounding(directory.resolve("profile"));
            verifyBoundedNpcScene(directory.resolve("scene"));
            verifyModelRoutingAndFallback();
            verifyBoundedVocalSemantics();
            System.out.println("Immersive AI emergent-world A-G acceptance tests passed.");
        } finally {
            delete(directory);
        }
    }

    private static void verifyDynamicQuestAndConsequences(Path directory) throws Exception {
        Files.createDirectories(directory);
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        DynamicQuestStore quests = new DynamicQuestStore(directory);
        NpcTaskStore tasks = new NpcTaskStore(directory);
        MemoryStore memories = new MemoryStore(directory, 100);
        RelationshipStore relationships = new RelationshipStore(directory);
        quests.load();
        tasks.load();
        memories.load();
        relationships.load();
        DynamicQuestDirector director = new DynamicQuestDirector(quests,
                new RewardResolver(new RewardBudget(4, 20)), tasks, memories, relationships);

        ResolvedWorldTarget camp = new ResolvedWorldTarget(QuestTargetKind.LOCATION,
                "world:goblin-camp-17", "Goblin Camp", world, 25.0, 65.0, -9.0,
                Set.of("GOBLIN", "CAMP"));
        RewardCandidate apple = new RewardCandidate("Item_Apple", "Apple", 2, 4,
                "LOADED_ITEM_ASSET_POOL", false);
        var created = director.createValidated(new QuestProposal(QuestType.RETURN_HOME,
                        "A frightened goblin wants safe passage home",
                        "Return the frightened goblin to its known camp",
                        "goblin camp", "imaginary diamond", 2, 2, 2),
                new QuestOpportunityContext(npc, Set.of(player), LocalDateTime.now(),
                        List.of(camp), List.of(apple), world, "event:goblin-spared"));
        assert created.accepted() : created.reason();
        var quest = created.quest();
        assert quest.resolvedWorldTargets().getFirst().authoritativeId()
                .equals("world:goblin-camp-17");
        assert quest.reward().itemId().equals("Item_Apple");
        assert !quest.reward().itemId().toLowerCase().contains("diamond");
        assert tasks.all().stream().anyMatch(task -> quest.questId().toString()
                .equals(task.data().get("questId")));

        var rejected = director.createValidated(new QuestProposal(QuestType.FETCH,
                        "A sheep is hungry", "Fetch food for the sheep", "moonberry", "gold",
                        1, 1, 1),
                new QuestOpportunityContext(npc, Set.of(player), LocalDateTime.now(),
                        List.of(camp), List.of(apple), world, "event:sheep-hungry"));
        assert !rejected.accepted();

        director.onEvent(new NpcFrameworkEvent(UUID.randomUUID(), NpcEventType.TASK_COMPLETED,
                npc, player, null, Instant.now(), Map.of(
                        "questId", quest.questId().toString(),
                        "targetId", "world:goblin-camp-17"))).orElseThrow();
        DynamicQuestStore reloadedQuests = new DynamicQuestStore(directory);
        MemoryStore reloadedMemories = new MemoryStore(directory, 100);
        RelationshipStore reloadedRelationships = new RelationshipStore(directory);
        reloadedQuests.load();
        reloadedMemories.load();
        reloadedRelationships.load();
        assert reloadedQuests.get(quest.questId()).orElseThrow().status()
                == QuestStatus.COMPLETED;
        assert reloadedMemories.relevant(npc, player, "completed quest camp", 10).stream()
                .anyMatch(memory -> memory.summary().contains(quest.questId().toString()));
        assert reloadedRelationships.getOrDefault(npc, player, 0).trust() > 0;

        var failure = director.createValidated(new QuestProposal(QuestType.INVESTIGATE,
                        "Tracks were found", "Investigate the camp", "goblin camp", "apple",
                        2, 2, 1),
                new QuestOpportunityContext(npc, Set.of(player), LocalDateTime.now(),
                        List.of(camp), List.of(apple), world, "event:tracks"));
        assert failure.accepted();
        director.fail(failure.quest().questId(), "Player abandoned the investigation",
                LocalDateTime.now());
        assert quests.get(failure.quest().questId()).orElseThrow().status() == QuestStatus.FAILED;
    }

    private static void verifyAutonomyIsEventDriven() {
        UUID npc = UUID.randomUUID();
        Instant now = Instant.now();
        NpcFrameworkEvent event = new NpcFrameworkEvent(UUID.randomUUID(),
                NpcEventType.ENTITY_ENTERED_PERCEPTION, npc, UUID.randomUUID(), null, now,
                Map.of("need", "safe route home"));
        AutonomyGate gate = new AutonomyGate(2, 60);
        AutonomousEventDirector director = new AutonomousEventDirector(gate, 4);
        AutonomousOpportunity opportunity = new AutonomousOpportunity(event,
                "safe route home", true, false, false, false, 1,
                List.of("GO_TO"), List.of("RETURN_HOME"));
        var intent = director.evaluate(opportunity).orElseThrow();
        assert intent.questType().equals("RETURN_HOME");
        assert gate.claimCount() == 1;
        assert director.evaluate(opportunity).isEmpty();
        assert gate.claimCount() == 1 : "Cooldown must prevent autonomous loops";
    }

    private static void verifyMonsterOverlayAndPromotion(Path directory) throws Exception {
        Files.createDirectories(directory);
        Instant now = Instant.now();
        ImmersiveEntityAgent ephemeral = new ImmersiveEntityAgent(UUID.randomUUID(),
                UUID.randomUUID(), "GOBLIN", "wary", AgentPersistence.EPHEMERAL,
                HighLevelIntent.CONTINUE_NATIVE_BEHAVIOR, true, false, null, "");
        ImmersiveAgentStore store = new ImmersiveAgentStore(directory);
        store.load();
        assert store.put(ephemeral).isEmpty();
        MonsterReasoningAdapter adapter = new MonsterReasoningAdapter(Duration.ZERO);
        var surrendered = adapter.apply(ephemeral, new MonsterReasoningContext(
                        ReasoningTrigger.HEALTH_CRITICAL, 0.18, 0, 1, false, false,
                        false, false, false), HighLevelIntent.SURRENDER, now);
        assert surrendered.accepted();
        assert surrendered.agent().nativeCombatSuspended();
        assert !surrendered.nativeBehaviorContinues();
        var truce = adapter.apply(surrendered.agent(), new MonsterReasoningContext(
                        ReasoningTrigger.PLAYER_ATTEMPTS_CONVERSATION, 0.18, 0, 1, true, true,
                        true, true, false), HighLevelIntent.TEMPORARY_TRUCE, now.plusSeconds(1));
        assert truce.accepted();
        assert truce.agent().persistence() == AgentPersistence.PERSISTENT;
        assert store.put(truce.agent()).isPresent();
        var resumed = adapter.apply(truce.agent(), new MonsterReasoningContext(
                        ReasoningTrigger.SOCIAL_STATE_CHANGED, 0.18, 0, 1, false, false,
                        true, true, true), HighLevelIntent.RESUME_HOSTILITY, now.plusSeconds(2));
        assert resumed.accepted() && resumed.agent().nativeHostile();
        assert !resumed.agent().nativeCombatSuspended();
        ImmersiveAgentStore reloaded = new ImmersiveAgentStore(directory);
        reloaded.load();
        assert reloaded.get(ephemeral.stableId()).isPresent();
    }

    private static void verifyProfileSchemaAndGrounding(Path directory) throws Exception {
        Files.createDirectories(directory);
        RelationshipStore relationships = new RelationshipStore(directory);
        MemoryStore memories = new MemoryStore(directory, 50);
        relationships.load();
        memories.load();
        UUID player = UUID.randomUUID();
        NpcProfile cautious = profile(UUID.randomUUID(), "Iria", "quiet and cautious",
                List.of("Caution", "Community"), List.of("Deep water"), List.of("Protect home"));
        NpcProfile bold = profile(UUID.randomUUID(), "Toren", "bold and humorous",
                List.of("Courage", "Discovery"), List.of("Being trapped"), List.of("Explore ruins"));
        assert cautious.stableId().equals(cautious.id());
        assert cautious.schemaVersion() == 1;
        ConversationContextBuilder builder = new ConversationContextBuilder(
                relationships, memories, 4);
        String cautiousPrompt = builder.build(new ConversationSession(UUID.randomUUID(),
                        cautious.id(), player, Instant.now()), cautious, "How do you feel?",
                new MinimalWorldContext("Hytale", 0, 64, 0)).messages().getFirst().content();
        String boldPrompt = builder.build(new ConversationSession(UUID.randomUUID(),
                        bold.id(), player, Instant.now()), bold, "How do you feel?",
                new MinimalWorldContext("Hytale", 0, 64, 0)).messages().getFirst().content();
        assert cautiousPrompt.contains("Deep water") && cautiousPrompt.contains("Protect home");
        assert boldPrompt.contains("Being trapped") && boldPrompt.contains("Explore ruins");
        assert !cautiousPrompt.equals(boldPrompt);
        assert cautiousPrompt.contains("FICTIONAL_STORY");
        assert cautiousPrompt.contains("Do not convert a");
    }

    private static void verifyBoundedNpcScene(Path directory) throws Exception {
        Files.createDirectories(directory);
        RelationshipStore relationships = new RelationshipStore(directory);
        GossipStore gossip = new GossipStore(directory);
        relationships.load();
        gossip.load();
        AtomicInteger calls = new AtomicInteger();
        LlmProvider provider = provider("scene", request -> {
            int turn = calls.incrementAndGet();
            return switch (turn) {
                case 1 -> "I saw fresh tracks by the known eastern path.";
                case 2 -> "Then I will keep watch near the marked crossing.";
                case 3 -> "The mud suggests whoever passed was carrying weight.";
                default -> "I will report back after checking the bridge.";
            };
        });
        try (ConversationRateLimiter budget = new ConversationRateLimiter(20)) {
            NpcSceneRunner runner = new NpcSceneRunner(new NpcSceneService(4, 60),
                    provider, budget, relationships, gossip);
            NpcProfile first = profile(UUID.randomUUID(), "Iria", "careful", List.of(),
                    List.of(), List.of());
            NpcProfile second = profile(UUID.randomUUID(), "Toren", "direct", List.of(),
                    List.of(), List.of());
            var outcome = runner.run(first, second,
                    "AUTHORITATIVE EVENT: tracks detected on eastern path", 3.0,
                    Instant.now()).join();
            assert outcome.generatedTurns() == 4 : "turns=" + outcome.generatedTurns()
                    + " calls=" + calls.get() + " summary=" + outcome.outcomeSummary();
            assert outcome.scene().complete();
            assert calls.get() == 4;
            GossipStore reloaded = new GossipStore(directory);
            reloaded.load();
            assert reloaded.knownBy(second.id()).size() == 1;
            assert relationships.getOrDefault(first.id(), second.id(), 0).trust() == 1;
        }
    }

    private static void verifyModelRoutingAndFallback() {
        List<LlmRequest> genericRequests = new ArrayList<>();
        List<LlmRequest> importantRequests = new ArrayList<>();
        List<LlmRequest> deepRequests = new ArrayList<>();
        LlmProvider generic = provider("generic", request -> {
            genericRequests.add(request);
            return "generic";
        });
        LlmProvider important = provider("important", request -> {
            importantRequests.add(request);
            return "important";
        });
        LlmProvider failingDeep = failingProvider("deep", deepRequests);
        EnumMap<ModelTier, LlmProvider> routes = new EnumMap<>(ModelTier.class);
        routes.put(ModelTier.IMPORTANT, important);
        routes.put(ModelTier.DEEP_CONVERSATION, failingDeep);
        ModelRoutingProvider router = new ModelRoutingProvider(generic, routes, 2);
        NpcProfile npc = profile(UUID.randomUUID(), "Iria", "careful", List.of(),
                List.of(), List.of());
        ConversationSession planning = new ConversationSession(UUID.randomUUID(), npc.id(),
                UUID.randomUUID(), Instant.now());
        assert router.selectTier(planning, npc, "Can you plan a quest?") == ModelTier.IMPORTANT;
        LlmRequest request = new LlmRequest(planning.sessionId(), npc.id(), planning.playerId(),
                List.of(new com.inigmasgames.persistentnpcs.llm.ChatMessage("user", "same")),
                List.of());
        assert router.generateResponse(request).join().text().equals("important");
        assert importantRequests.getFirst() == request;

        ConversationSession deep = new ConversationSession(UUID.randomUUID(), npc.id(),
                UUID.randomUUID(), Instant.now());
        deep.appendTurn("one", "reply", Instant.now());
        deep.appendTurn("two", "reply", Instant.now());
        assert router.selectTier(deep, npc, "continue") == ModelTier.DEEP_CONVERSATION;
        LlmRequest deepRequest = new LlmRequest(deep.sessionId(), npc.id(), deep.playerId(),
                request.messages(), request.tools());
        assert router.generateResponse(deepRequest).join().text().equals("generic");
        assert deepRequests.getFirst() == deepRequest;
        assert genericRequests.getFirst() == deepRequest;
        assert router.selectedTier(deep.sessionId()) == ModelTier.DEEP_CONVERSATION;
        router.endSession(deep.sessionId());
        assert router.selectedTier(deep.sessionId()) == ModelTier.GENERIC;
    }

    private static void verifyBoundedVocalSemantics() {
        VocalState urgent = VocalState.infer("Stop! Run now!");
        assert urgent.emotion() == VocalEmotion.AFRAID;
        assert urgent.pace() != null;
        assert urgent.intensity().name().equals("HIGH");
    }

    private static NpcProfile profile(UUID id, String name, String personality,
            List<String> values, List<String> fears, List<String> goals) {
        return new NpcProfile(id, name, "Village resident", personality,
                "A resident with grounded local knowledge.", "Live a believable daily life.",
                "village-home", "village", List.of(), List.of(), List.of("CIVILIAN"),
                List.of("TALK"), 0, 1, name, "ADULT", "Natural and concise",
                List.of("local area"), List.of(), "", id, "HUMAN",
                List.of(personality), values, fears, goals, "", "GENERIC").validated();
    }

    private static LlmProvider provider(
            String name, java.util.function.Function<LlmRequest, String> response) {
        return new LlmProvider() {
            @Override
            public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                return CompletableFuture.completedFuture(new LlmResult(response.apply(request),
                        new LlmLatency(Instant.now(), 1, 2, false)));
            }

            @Override
            public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(new LlmProviderStatus(
                        name, name, true, true, false, "test"));
            }

            @Override
            public String description() { return name; }
        };
    }

    private static LlmProvider failingProvider(String name, List<LlmRequest> requests) {
        return new LlmProvider() {
            @Override
            public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                requests.add(request);
                return CompletableFuture.failedFuture(new IllegalStateException("offline"));
            }

            @Override
            public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(new LlmProviderStatus(
                        name, name, true, false, false, "offline"));
            }

            @Override
            public String description() { return name; }
        };
    }

    private static void delete(Path directory) throws Exception {
        MemoryStore.flushAll();
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }
}
