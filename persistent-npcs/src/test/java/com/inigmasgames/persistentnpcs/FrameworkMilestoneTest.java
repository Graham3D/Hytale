package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AutonomyGate;
import com.inigmasgames.persistentnpcs.director.WorldStoryProposal;
import com.inigmasgames.persistentnpcs.director.WorldStoryValidator;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.event.NpcTriggerService;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.scene.NpcSceneService;
import com.inigmasgames.persistentnpcs.social.GossipRecord;
import com.inigmasgames.persistentnpcs.social.GossipStore;
import com.inigmasgames.persistentnpcs.task.NpcRuntimeState;
import com.inigmasgames.persistentnpcs.task.NpcRuntimeStateStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public final class FrameworkMilestoneTest {
    private FrameworkMilestoneTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("persistent-npcs-r006-");
        try {
            profileAndTaskMigration(directory);
            memoryRelationshipAndGossip(directory);
            triggersScenesAutonomyAndDirector(directory);
            runtimeAndBudgets(directory);
            System.out.println("Persistent NPC R006 framework tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    private static void profileAndTaskMigration(Path directory) {
        var profile = new ProfileRepository(directory).loadTestProfile();
        assert profile.id().toString().equals("3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
        assert profile.schemaVersion() == 1;
        assert profile.hasRole("BLACKSMITH");
        assert profile.hasCapability("BRING_ITEM");
        assert profile.knowledgeDomains().contains("metalworking");

        NpcTaskStore tasks = new NpcTaskStore(directory);
        tasks.load();
        NpcTask task = new NpcTask(UUID.randomUUID(), profile.id(), UUID.randomUUID(),
                "CRAFT_FOR_PLAYER", UUID.randomUUID(), 1.0, 2.0, 3.0, null,
                "craft", NpcTaskState.TRAVELING, Instant.now(), null,
                Map.of("phase", "CRAFT", "recipeId", "test"));
        tasks.put(task);
        NpcTaskStore reloaded = new NpcTaskStore(directory);
        reloaded.load();
        assert reloaded.activeFor(profile.id()).getFirst().data().get("phase").equals("CRAFT");
    }

    private static void memoryRelationshipAndGossip(Path directory) {
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        MemoryStore memories = new MemoryStore(directory, 100);
        memories.load();
        memories.append(new MemoryRecord(UUID.randomUUID(), npc, player, Instant.now(),
                MemoryType.PLAYER_FACT, 0.95, "Player fact: stated name=Graham.",
                1.0, "PLAYER_STATEMENT", List.of(player), "village", "I learned his name."));
        for (int i = 0; i < 3; i++) {
            memories.append(new MemoryRecord(UUID.randomUUID(), npc, player,
                    Instant.now().plusSeconds(i), MemoryType.CONVERSATION, 0.2,
                    "Player greeted Mara; Mara greeted player."));
        }
        MemoryStore reloaded = new MemoryStore(directory, 100);
        reloaded.load();
        assert reloaded.relevant(npc, player, "What is my name?", 4).stream()
                .anyMatch(memory -> memory.summary().contains("Graham"));
        assert reloaded.recent(npc, player, 10).stream()
                .anyMatch(memory -> memory.source().equals("CONSOLIDATED"));

        RelationshipStore relationships = new RelationshipStore(directory);
        relationships.load();
        relationships.adjust(npc, player, 5, 4, 2, 1, 0, 0, 3, Instant.now());
        RelationshipStore relationReload = new RelationshipStore(directory);
        relationReload.load();
        var relation = relationReload.getOrDefault(npc, player, 0);
        assert relation.trust() == 9;
        assert relation.obligation() == 3;

        UUID event = UUID.randomUUID();
        UUID toldTo = UUID.randomUUID();
        GossipStore gossip = new GossipStore(directory);
        gossip.load();
        gossip.append(new GossipRecord(UUID.randomUUID(), "Graham kept the meeting", event,
                player, npc, toldTo, Instant.now(), 0.8));
        GossipStore gossipReload = new GossipStore(directory);
        gossipReload.load();
        assert gossipReload.knownBy(toldTo).getFirst().originalEventId().equals(event);
        assert gossipReload.knownBy(toldTo).getFirst().toldByEntityId().equals(npc);
        assert Files.exists(directory.resolve("persistence/gossip.json"));
    }

    private static void triggersScenesAutonomyAndDirector(Path directory) {
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        MemoryStore memories = new MemoryStore(directory.resolve("events"), 100);
        memories.load();
        NpcTriggerService triggers = new NpcTriggerService(directory.resolve("events"), memories);
        NpcEventBus events = new NpcEventBus();
        events.register(triggers::onEvent);
        events.emit(new NpcFrameworkEvent(UUID.randomUUID(), NpcEventType.ITEM_TAKEN,
                npc, player, player, Instant.now(),
                Map.of("itemId", "Weapon_Sword_Iron", "quantity", "1")));
        assert memories.relevant(npc, player, "sword", 4).stream()
                .anyMatch(memory -> memory.summary().contains("Weapon_Sword_Iron"));

        NpcSceneService scenes = new NpcSceneService(2, 60);
        UUID other = UUID.randomUUID();
        var scene = scenes.start(npc, other, "GOSSIP", 2.0, Instant.now());
        scenes.append(scene.sceneId(), npc, "I heard something grounded.");
        var finished = scenes.append(scene.sceneId(), other, "Tell me later.");
        assert finished.complete();
        assert finished.turns().size() == 2;

        AutonomyGate gate = new AutonomyGate(2, 60);
        NpcFrameworkEvent important = new NpcFrameworkEvent(UUID.randomUUID(),
                NpcEventType.TASK_FAILED, npc, npc, player, Instant.now(), Map.of());
        assert gate.claim(important, true);
        assert !gate.claim(important, true);

        WorldStoryValidator director = new WorldStoryValidator(
                Set.of("UNFINISHED_BUSINESS"), Set.of("FOLLOW_PLAYER"));
        assert director.valid(new WorldStoryProposal("UNFINISHED_BUSINESS",
                important.eventId().toString(), "FOLLOW_PLAYER", Map.of(), "real task failed"));
        assert !director.valid(new WorldStoryProposal("INVENT_REWARD", "",
                "RUN_CONSOLE_COMMAND", Map.of(), "ungrounded"));
    }

    private static void runtimeAndBudgets(Path directory) throws Exception {
        UUID npc = UUID.randomUUID();
        Instant now = Instant.now();
        NpcRuntimeStateStore runtime = new NpcRuntimeStateStore(directory);
        runtime.load();
        runtime.put(new NpcRuntimeState(npc, UUID.randomUUID(), "work", "GO_HOME",
                "home", now.plusSeconds(10), "OFF_SHIFT", Map.of("Apple", 2),
                5, now));
        assert runtime.advanceUnloaded(npc, now.plusSeconds(11)).logicalLocation().equals("home");
        NpcRuntimeStateStore runtimeReload = new NpcRuntimeStateStore(directory);
        runtimeReload.load();
        assert runtimeReload.get(npc).taskType().equals("IDLE");

        try (ConversationRateLimiter budget = new ConversationRateLimiter(1)) {
            UUID limited = UUID.randomUUID();
            var permit = budget.acquire(limited).join();
            assert permit.queueLatencyMillis() >= 0;
            var one = budget.acquire(limited);
            boolean queueRejected = false;
            try { one.join(); } catch (CompletionException expected) { queueRejected = true; }
            assert queueRejected;
            permit.close();
        }
    }
}
