package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.memory.MemoryDurability;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public final class R034MemoryImportanceTest {
    private R034MemoryImportanceTest() { }

    public static void main(String[] args) throws Exception {
        mundaneAndLandmarkAppraisalsAreDeterministic();
        oldLandmarksSurviveDecayAndSoftCapacity();
        directlyRelevantOrdinaryMemoriesRemainRetrievable();
        repeatedRecallReinforcesWithoutDuplication();
        legacyJsonMigratesWithoutLosingProvenance();
        retrievalUsesRamAndPersistenceIsAsynchronousAndFlushable();
        inspectorShowsImportanceAndBreakdown();
        System.out.println("R034 memory importance, durability, and recall tests passed.");
    }

    private static void mundaneAndLandmarkAppraisalsAreDeterministic() {
        UUID npc = UUID.randomUUID();
        MemoryRecord mundane = memory(npc, Instant.now(), MemoryType.EPISODIC, 0.20,
                "Walked past an ordinary wooden chair.").normalized();
        assert mundane.importance() < 0.30 : mundane;
        assert mundane.durability() == MemoryDurability.TRANSIENT : mundane;

        MemoryRecord landmark = memory(npc, Instant.now(), MemoryType.RELATIONSHIP, 0.70,
                "My daughter died in a terrifying attack that destroyed our home, "
                        + "changed my life forever, and exposed a terrible betrayal.").normalized();
        assert landmark.importance() >= 0.82 : landmark;
        assert landmark.durability() == MemoryDurability.LANDMARK : landmark;
        assert landmark.emotionalIntensity() >= 0.70 : landmark;
        assert landmark.relationshipImpact() > 0.0;
        assert landmark.dangerImpact() > 0.0;
    }

    private static void oldLandmarksSurviveDecayAndSoftCapacity() throws Exception {
        Path root = Files.createTempDirectory("r034-landmark-");
        MemoryStore store = new MemoryStore(root, 2);
        store.load();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant old = Instant.now().minus(40 * 365L, ChronoUnit.DAYS);
        MemoryRecord landmark = memory(npc, old, MemoryType.WORLD_EVENT, 0.95,
                "I survived the red keep fire that killed my family and changed my life forever.");
        store.append(landmark);
        store.append(memory(npc, Instant.now(), MemoryType.CONVERSATION, 0.10,
                "Exchanged a routine greeting."));
        store.append(memory(npc, Instant.now(), MemoryType.CONVERSATION, 0.12,
                "Noticed an ordinary pebble."));
        assert store.forNpc(npc).stream().anyMatch(value ->
                value.durability() == MemoryDurability.LANDMARK);
        var recalled = store.retrieveScoredForCognition(npc, player,
                "What happened in the red keep fire?", 4);
        assert !recalled.isEmpty();
        assert recalled.getFirst().memory().durability() == MemoryDurability.LANDMARK;
        assert recalled.getFirst().breakdown().recency() == 1.0;
    }

    private static void directlyRelevantOrdinaryMemoriesRemainRetrievable() throws Exception {
        Path root = Files.createTempDirectory("r034-ordinary-");
        MemoryStore store = new MemoryStore(root, 20);
        store.load();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        store.append(new MemoryRecord(UUID.randomUUID(), npc, player, Instant.now(),
                MemoryType.EPISODIC, 0.18, "Placed the blue cup on the pantry shelf.",
                0.95, "DIRECT", List.of(player), "pantry shelf", "A mundane action."));
        var results = store.retrieveScoredForCognition(npc, player,
                "Where was the blue cup placed?", 4);
        assert !results.isEmpty() : results;
        assert results.getFirst().memory().summary().contains("blue cup");
        assert results.getFirst().breakdown().semanticRelevance() >= 0.5;
    }

    private static void repeatedRecallReinforcesWithoutDuplication() throws Exception {
        Path root = Files.createTempDirectory("r034-rehearsal-");
        MemoryStore store = new MemoryStore(root, 20);
        store.load();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant now = Instant.now();
        store.append(new MemoryRecord(UUID.randomUUID(), npc, player, now,
                MemoryType.PLAYER_FACT, 0.40, "Player-reported belief: I hid the brass "
                        + "compass beneath the mill stairs.", 0.8,
                "PLAYER_REPORT:source=" + player, List.of(player), "mill stairs",
                "The player reported this."));
        var first = store.retrieveScoredForCognition(npc, player,
                "Where did I hide the brass compass?", 4, now.plusSeconds(31));
        double firstImportance = first.getFirst().memory().importance();
        assert first.getFirst().memory().rehearsalCount() == 1;
        var second = store.retrieveScoredForCognition(npc, player,
                "Where did I stash the brass compass?", 4, now.plusSeconds(62));
        assert second.getFirst().memory().rehearsalCount() == 2;
        assert second.getFirst().memory().importance() > firstImportance;
        assert store.forNpc(npc).size() == 1;
    }

    private static void legacyJsonMigratesWithoutLosingProvenance() throws Exception {
        Path root = Files.createTempDirectory("r034-migration-");
        Path path = root.resolve("persistence/memories.json");
        Files.createDirectories(path.getParent());
        UUID memoryId = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        String legacy = "[{\"memoryId\":\"" + memoryId + "\",\"npcId\":\"" + npc
                + "\",\"playerId\":\"" + player + "\",\"timestamp\":\""
                + Instant.now() + "\",\"type\":\"PLAYER_FACT\",\"importance\":0.55,"
                + "\"summary\":\"Player-reported belief: I live above the forge.\","
                + "\"confidence\":0.68,\"source\":\"PLAYER_REPORT:source=" + player
                + ";utterance=legacy\",\"involvedEntities\":[\"" + player
                + "\"],\"location\":\"above the forge\","
                + "\"npcPerspective\":\"The player reported this.\"}]";
        Files.writeString(path, legacy);
        MemoryStore store = new MemoryStore(root, 20);
        store.load();
        MemoryRecord migrated = store.forNpc(npc).getFirst();
        assert migrated.memoryId().equals(memoryId);
        assert migrated.source().contains("utterance=legacy");
        assert migrated.durability() != null;
        store.flush();
        String persisted = Files.readString(path);
        assert persisted.contains("\"durability\"");
        assert persisted.contains(memoryId.toString());
    }

    private static void retrievalUsesRamAndPersistenceIsAsynchronousAndFlushable()
            throws Exception {
        Path root = Files.createTempDirectory("r034-ram-");
        MemoryStore store = new MemoryStore(root, 50);
        store.load();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        store.append(new MemoryRecord(UUID.randomUUID(), npc, player, Instant.now(),
                MemoryType.EPISODIC, 0.35, "Saw a silver lantern beside the gate."));
        long reads = store.persistenceReadCount();
        long started = System.nanoTime();
        store.retrieveScoredForCognition(npc, player, "silver lantern", 4);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        assert store.persistenceReadCount() == reads : "retrieval reread persistent JSON";
        assert elapsedMillis < 100 : "RAM retrieval blocked for " + elapsedMillis + "ms";
        store.flush();
        MemoryStore reloaded = new MemoryStore(root, 50);
        reloaded.load();
        assert reloaded.forNpc(npc).size() == 1;
    }

    private static void inspectorShowsImportanceAndBreakdown() throws Exception {
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/CognitionInspectorPage.java"));
        for (String required : List.of("importance=", "durability=", "emotionalIntensity=",
                "relationshipImpact=", "goalImpact=", "rehearsalCount=", "breakdown=")) {
            assert page.contains(required) : required;
        }
    }

    private static MemoryRecord memory(UUID npc, Instant at, MemoryType type,
            double importance, String summary) {
        return new MemoryRecord(UUID.randomUUID(), npc, null, at, type, importance,
                summary, 0.9, "DIRECT", List.of(), "", "");
    }
}
