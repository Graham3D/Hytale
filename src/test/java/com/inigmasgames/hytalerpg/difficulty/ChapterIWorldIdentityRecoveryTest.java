package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.*;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ChapterIWorldIdentityRecoveryTest {
    @TempDir Path root;
    private static final UUID OLD_DEFAULT=UUID.fromString("9a19439a-004d-44e6-934d-e82f0fdf662c");
    private static final UUID NEW_DEFAULT=UUID.fromString("3d366b79-f367-419a-87b7-a8d53cde7ec0");
    private static final UUID OLD_ZONE=UUID.fromString("5cc8eef5-cf14-4e85-81ba-2dd651909658");
    private static final UUID NEW_ZONE=UUID.fromString("6e88444d-898b-4a9d-99a6-f5f19d0257a1");
    private Path registry(){return root.resolve("difficulty-worlds.json");}
    private Path nativeWorlds(){return root.resolve("universe/worlds");}
    private void nativeWorld(String name,UUID id)throws Exception {
        Path path=nativeWorlds().resolve(name);Files.createDirectories(path);
        Files.writeString(path.resolve("config.json"),new BsonDocument("UUID",Codec.UUID_BINARY.encode(id,new ExtraInfo())).toJson());
    }
    private void copiedRegistry()throws Exception {
        try(var source=Objects.requireNonNull(getClass().getResourceAsStream("/chapter1/copied-difficulty-worlds-v1.json"))){
            Files.copy(source,registry());
        }
    }
    @Test void copiedReleaseWorldsReconcileTogetherAndRetainOldProvenanceAcrossRestart()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_ZONE);
        byte[] before=Files.readAllBytes(registry());
        var runtime=new DifficultyRuntime(registry(),nativeWorlds());
        for(var pair:List.of(Map.entry(NEW_DEFAULT,"default"),Map.entry(NEW_ZONE,"zone3_taiga1_world"))){
            assertEquals(pair.getValue(),runtime.worldLoaded(pair.getKey(),pair.getValue()).orElseThrow().worldName());
            assertEquals(DifficultyId.NORMAL,runtime.currentWorld(pair.getKey()).difficulty());
        }
        assertEquals(OLD_DEFAULT,runtime.worlds().historical(OLD_DEFAULT).orElseThrow().worldId());
        assertEquals(OLD_ZONE,runtime.worlds().historical(OLD_ZONE).orElseThrow().worldId());
        assertTrue(runtime.worlds().find(OLD_DEFAULT).isEmpty());
        assertEquals(5,runtime.worlds().bindings().size());
        assertEquals("rpg_hell",runtime.worlds().bindings().stream().filter(b->b.difficulty()==DifficultyId.HELL).findFirst().orElseThrow().worldName());
        assertArrayEquals(before,Files.readAllBytes(registry().resolveSibling("difficulty-worlds.json.bak")));
        byte[] reconciled=Files.readAllBytes(registry());
        var restarted=new DifficultyRuntime(registry(),nativeWorlds());
        assertEquals(runtime.currentWorld(NEW_DEFAULT),restarted.currentWorld(NEW_DEFAULT));
        assertEquals(runtime.currentWorld(NEW_ZONE),restarted.currentWorld(NEW_ZONE));
        assertEquals(runtime.worlds().historical(OLD_DEFAULT),restarted.worlds().historical(OLD_DEFAULT));
        assertArrayEquals(reconciled,Files.readAllBytes(registry()));
        assertArrayEquals(before,Files.readAllBytes(registry().resolveSibling("difficulty-worlds.json.bak")));
        String biome="Default/Zone1_Tier1/Forest_Birch";
        var classification=restarted.encounters().classifyNatural(EnemyRewardRegistry.load(),NEW_DEFAULT,UUID.randomUUID(),
                "Wolf_Black",biome,1,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100);
        assertEquals(EncounterProfileResolver.Admission.READY,classification.reason());
        assertEquals(NEW_DEFAULT,classification.spawn().orElseThrow().world());
        assertEquals(EncounterProfileResolver.Admission.WORLD_NOT_READY,restarted.encounters().classifyNatural(
                EnemyRewardRegistry.load(),OLD_DEFAULT,UUID.randomUUID(),"Wolf_Black",biome,1,
                EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).reason());
    }
    @Test void matchingWorldRemainsByteIdenticalAndUnknownIdIsNotRebound()throws Exception {
        copiedRegistry();nativeWorld("default",OLD_DEFAULT);nativeWorld("zone3_taiga1_world",UUID.randomUUID());
        byte[] before=Files.readAllBytes(registry());
        var runtime=new DifficultyRuntime(registry(),nativeWorlds());
        assertEquals(OLD_DEFAULT,runtime.currentWorld(OLD_DEFAULT).worldId());
        assertTrue(runtime.worldLoaded(UUID.randomUUID(),"zone3_taiga1_world").isEmpty());
        assertArrayEquals(before,Files.readAllBytes(registry()));
    }
    @Test void conflictingAndDuplicateNativeIdentitiesFailBeforeAnyRegistryWrite()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_DEFAULT);
        byte[] before=Files.readAllBytes(registry());
        assertThrows(IllegalStateException.class,()->new DifficultyRuntime(registry(),nativeWorlds()));
        assertArrayEquals(before,Files.readAllBytes(registry()));
    }
    @Test void duplicateConflictingAndUnknownLineageCannotRewriteCopiedRegistry()throws Exception {
        copiedRegistry();byte[] before=Files.readAllBytes(registry());
        var current=new WorldDifficultyRegistry(registry());
        assertThrows(IllegalArgumentException.class,()->current.reconcile(Map.of(OLD_DEFAULT,NEW_DEFAULT,OLD_ZONE,NEW_DEFAULT)));
        assertThrows(IllegalStateException.class,()->current.reconcile(Map.of(OLD_DEFAULT,OLD_ZONE)));
        assertThrows(IllegalStateException.class,()->current.reconcile(Map.of(UUID.randomUUID(),NEW_DEFAULT)));
        assertArrayEquals(before,Files.readAllBytes(registry()));
    }
    @Test void failedAtomicReconciliationKeepsOriginalRegistryAndBackup()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_ZONE);
        byte[] before=Files.readAllBytes(registry());
        Files.createDirectory(registry().resolveSibling("difficulty-worlds.json.tmp"));
        assertThrows(IllegalStateException.class,()->new DifficultyRuntime(registry(),nativeWorlds()));
        assertArrayEquals(before,Files.readAllBytes(registry()));
        assertTrue(new WorldDifficultyRegistry(registry()).find(OLD_DEFAULT).isPresent());
    }
    @Test void historicalEncounterKeyRemainsSeparateFromNewReleaseKey()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_ZONE);
        UUID enemy=UUID.randomUUID();Path records=root.resolve("encounters");
        var spawn=EnemyRewardRegistry.load().classify(OLD_DEFAULT,enemy,"Wolf_Black",
                "Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
        try(var store=new FileEncounterStore(records)){assertEquals(spawn,store.create(spawn).spawn());}
        new DifficultyRuntime(registry(),nativeWorlds());
        try(var store=new FileEncounterStore(records)){
            assertEquals(spawn,store.load(OLD_DEFAULT,enemy).orElseThrow().spawn());
            assertTrue(store.load(NEW_DEFAULT,enemy).isEmpty());
        }
    }
    @Test void copiedDestinationProvisionRetainsOldIdAsHistoricalAndSurvivesRestart()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_ZONE);
        var runtime=new DifficultyRuntime(registry(),nativeWorlds());
        var old=runtime.worlds().bindings().stream().filter(b->b.difficulty()==DifficultyId.HELL).findFirst().orElseThrow();
        assertTrue(DifficultyRuntime.copiedDestination(old,DifficultyId.HELL));
        assertFalse(DifficultyRuntime.copiedDestination(old,DifficultyId.NIGHTMARE));
        UUID created=UUID.randomUUID();nativeWorld("rpg_hell",created);
        assertEquals(created,runtime.worldLoaded(created,"rpg_hell").orElseThrow().worldId());
        assertTrue(runtime.recoveredCopiedDestination(created,"rpg_hell"));
        assertFalse(runtime.recoveredCopiedDestination(created,"rpg_nightmare"));
        assertTrue(com.inigmasgames.hytalerpg.enemies.Me12QaAuthority.automaticWorldSelected(
                "QA2","QA2","Hywind","Hywind",created,"rpg_hell",NEW_DEFAULT.toString(),runtime));
        assertFalse(com.inigmasgames.hytalerpg.enemies.Me12QaAuthority.automaticWorldSelected(
                "QA3","QA2","Hywind","Hywind",created,"rpg_hell",NEW_DEFAULT.toString(),runtime));
        assertFalse(com.inigmasgames.hytalerpg.enemies.Me12QaAuthority.automaticWorldSelected(
                "QA2","QA2","Other","Hywind",created,"rpg_hell",NEW_DEFAULT.toString(),runtime));
        assertTrue(com.inigmasgames.hytalerpg.enemies.Me12QaAuthority.automaticWorldSelected(
                "QA2","QA2","Hywind","Hywind",NEW_DEFAULT,"default",NEW_DEFAULT.toString(),runtime));
        assertFalse(com.inigmasgames.hytalerpg.enemies.Me12QaAuthority.automaticWorldSelected(
                "QA2","QA2","Hywind","Hywind",old.worldId(),"rpg_hell",NEW_DEFAULT.toString(),runtime));
        assertTrue(runtime.worlds().find(old.worldId()).isEmpty());
        assertEquals(old,runtime.worlds().historical(old.worldId()).orElseThrow());
        assertEquals(created,new DifficultyRuntime(registry(),nativeWorlds()).worlds().require(created).worldId());
    }
    @Test void interruptedDestinationProvisionReconcilesOnlyAnActualNativeConfig()throws Exception {
        copiedRegistry();nativeWorld("default",NEW_DEFAULT);nativeWorld("zone3_taiga1_world",NEW_ZONE);
        UUID created=UUID.randomUUID();nativeWorld("rpg_nightmare",created);
        var runtime=new DifficultyRuntime(registry(),nativeWorlds());
        assertEquals(created,runtime.worlds().require(created).worldId());
        assertTrue(runtime.recoveredCopiedDestination(created,"rpg_nightmare"));
        assertEquals(UUID.fromString("31cb4ecd-7908-4aa4-b7b3-46a687ff2ee3"),
                runtime.worlds().historical(UUID.fromString("31cb4ecd-7908-4aa4-b7b3-46a687ff2ee3")).orElseThrow().worldId());
        assertFalse(DifficultyRuntime.copiedDestination(runtime.worlds().require(created),DifficultyId.NIGHTMARE));
    }
}
