package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.autonomy.SimulationTier;
import com.inigmasgames.persistentnpcs.background.BackgroundActivityType;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeSimulator;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeState;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeStore;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class R025BackgroundLifeTest {
    private R025BackgroundLifeTest() { }

    public static void main(String[] args) throws Exception {
        unloadedLifeTravelsThenWorksAndPersistsHistory();
        oneInProgressOperationPerNpcIsEnforcedAndRecovered();
        cognitionRetrievalCombinesEntityRecencyImportanceAndAutonomousMemory();
        System.out.println("R025 background life targeted tests passed.");
    }

    private static void unloadedLifeTravelsThenWorksAndPersistsHistory() throws Exception {
        Path root = Files.createTempDirectory("r025-background-");
        NpcProfile mara = new ProfileRepository(root).loadTestProfile();
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        BackgroundLifeStore store = new BackgroundLifeStore(root);
        store.load();
        BackgroundLifeSimulator simulator = new BackgroundLifeSimulator(
                store, memories, ignored -> { });
        UUID world = UUID.randomUUID();
        Instant nine = Instant.parse("2026-08-27T09:00:00Z");
        BackgroundLifeState travel = simulator.advanceUnloaded(mara, world, nine);
        assert travel.tier() == SimulationTier.DORMANT;
        assert travel.activity() == BackgroundActivityType.TRAVEL : travel;
        assert travel.destination().equals(mara.workplace());
        BackgroundLifeState work = simulator.advanceUnloaded(
                mara, world, nine.plus(Duration.ofMinutes(21)));
        assert work.activity() == BackgroundActivityType.WORK : work;
        assert work.logicalLocation().equals(mara.workplace());
        assert work.history().size() == 1;
        assert !work.history().getFirst().physicallySimulated();
        assert work.history().getFirst().source().equals("BACKGROUND_LOGICAL_SIMULATION");
        BackgroundLifeStore reloaded = new BackgroundLifeStore(root);
        reloaded.load();
        assert reloaded.get(mara.id()).activity() == BackgroundActivityType.WORK;
        assert memories.forNpc(mara.id()).stream().anyMatch(record ->
                record.source().equals("BACKGROUND_LOGICAL_SIMULATION"));
    }

    private static void oneInProgressOperationPerNpcIsEnforcedAndRecovered() throws Exception {
        Path root = Files.createTempDirectory("r025-operation-");
        AgentOperationStore operations = new AgentOperationStore(root);
        operations.load();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = Instant.now();
        AgentOperation claimed = operations.claim("NPC_RADIANT_CONVERSATION",
                Set.of(first, second), "authoritative encounter", now, Duration.ofMinutes(2));
        boolean rejected = false;
        try {
            operations.claim("REFLECTION", Set.of(first), "memory ids", now,
                    Duration.ofMinutes(1));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assert rejected;
        operations.complete(claimed.operationId(), true, "scene completed");
        assert !operations.busy(first, now);
        AgentOperationStore reloaded = new AgentOperationStore(root);
        reloaded.load();
        assert reloaded.all().getFirst().status().equals("COMPLETED");
    }

    private static void cognitionRetrievalCombinesEntityRecencyImportanceAndAutonomousMemory()
            throws Exception {
        Path root = Files.createTempDirectory("r025-retrieval-");
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        UUID npc = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Instant now = Instant.now();
        memories.append(new MemoryRecord(UUID.randomUUID(), npc, other, now,
                MemoryType.EPISODIC, 0.7, "Discussed village repairs directly",
                1.0, "NPC_DIRECT_CONVERSATION", List.of(other), "village",
                "I discussed repairs with them."));
        memories.append(new MemoryRecord(UUID.randomUUID(), npc, null, now.minusSeconds(30),
                MemoryType.EPISODIC, 0.5, "Inspected a flower near the workshop",
                1.0, "HYTALE_BLOCK_STATE", List.of(), "workshop",
                "I inspected a real flower."));
        List<MemoryRecord> retrieved = memories.retrieveForCognition(
                npc, other, "repairs near the workshop flower", 2);
        assert retrieved.size() == 2;
        assert retrieved.stream().anyMatch(record -> record.playerId() == null);
        assert retrieved.stream().anyMatch(record -> other.equals(record.playerId()));
    }
}
