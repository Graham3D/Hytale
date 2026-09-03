package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AffordanceRegistry;
import com.inigmasgames.persistentnpcs.autonomy.CognitionActivity;
import com.inigmasgames.persistentnpcs.autonomy.GroundedStimulus;
import com.inigmasgames.persistentnpcs.autonomy.NpcCognitionRuntimeState;
import com.inigmasgames.persistentnpcs.autonomy.NpcCognitionStateStore;
import com.inigmasgames.persistentnpcs.autonomy.NpcReflectionService;
import com.inigmasgames.persistentnpcs.autonomy.PersistentNpcIntent;
import com.inigmasgames.persistentnpcs.autonomy.SimulationTier;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class R024PersistentAutonomyTest {
    private R024PersistentAutonomyTest() { }

    public static void main(String[] args) throws Exception {
        affordancesAreEngineControlledAndFailClosed();
        oneIntentAndGroundedTargetSurviveReload();
        autonomousEpisodesSupportNullPlayerAndSourcedReflection();
        simulationTiersAreBounded();
        System.out.println("R024 persistent autonomy targeted tests passed.");
    }

    private static void affordancesAreEngineControlledAndFailClosed() {
        AffordanceRegistry registry = new AffordanceRegistry();
        assert registry.forType("FLOWER").equals(List.of("INVESTIGATE", "ADMIRE"));
        assert registry.forType("DRAGON_FROM_MODEL_TEXT").isEmpty();
    }

    private static void oneIntentAndGroundedTargetSurviveReload() throws Exception {
        Path root = Files.createTempDirectory("r024-cognition-");
        UUID npc = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.now();
        GroundedStimulus flower = new GroundedStimulus(
                "block:" + world + ":1:2:3", "FLOWER", "Plant_Rose_Red",
                world, 1.5, 2.5, 3.5, 4.0, "HYTALE_BLOCK_STATE", now);
        PersistentNpcIntent intent = new PersistentNpcIntent(UUID.randomUUID(),
                "INVESTIGATE_WORLD_OBJECT", "INSPECT", flower,
                CognitionActivity.APPROACHING, 0.8, "authoritative flower", now, now, 0, "");
        NpcCognitionStateStore store = new NpcCognitionStateStore(root);
        store.load();
        store.put(new NpcCognitionRuntimeState(npc, SimulationTier.ACTIVE, intent,
                List.of(flower), Map.of(), "none", "inspect flower", "novelty",
                List.of("chair: cooldown"), "none", now, Instant.EPOCH));
        NpcCognitionStateStore reloaded = new NpcCognitionStateStore(root);
        reloaded.load();
        NpcCognitionRuntimeState actual = reloaded.get(npc);
        assert actual.activeIntent().intentId().equals(intent.intentId());
        assert actual.activeIntent().target().source().equals("HYTALE_BLOCK_STATE");
        assert actual.attendedWorldFacts().size() == 1;
    }

    private static void autonomousEpisodesSupportNullPlayerAndSourcedReflection()
            throws Exception {
        Path root = Files.createTempDirectory("r024-memory-");
        UUID npc = UUID.randomUUID();
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        Instant base = Instant.now().minusSeconds(10);
        for (int i = 0; i < 3; i++) {
            memories.append(new MemoryRecord(UUID.randomUUID(), npc, null,
                    base.plusSeconds(i), MemoryType.EPISODIC, 0.55,
                    "Inspected real flower " + i, 1.0, "HYTALE_BLOCK_STATE",
                    List.of(), "world:1,2," + i, "I inspected a flower."));
        }
        assert memories.relevant(npc, null, "flower", 5).size() == 3;
        assert !new NpcReflectionService(memories).maybeReflect(
                npc, Instant.EPOCH, Instant.now());
        assert memories.forNpc(npc).stream().noneMatch(record ->
                record.type() == MemoryType.KNOWLEDGE
                        && record.source().startsWith("REFLECTION_FROM:"));
    }

    private static void simulationTiersAreBounded() {
        assert SimulationTier.ACTIVE.intervalMillis() < SimulationTier.BACKGROUND.intervalMillis();
        assert SimulationTier.BACKGROUND.intervalMillis() < SimulationTier.DORMANT.intervalMillis();
        assert SimulationTier.DORMANT.intervalMillis() >= 300_000;
    }
}
