package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32C;
import static org.junit.jupiter.api.Assertions.*;

class Stage13EncounterJournalTest {
    @TempDir Path directory;
    static final UUID WORLD=new UUID(1,1), ENEMY=new UUID(2,2), ACTOR=new UUID(3,3);
    static EnemyRewardRegistry.Spawn spawn(){return EnemyRewardRegistry.load().classify(WORLD,ENEMY,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    static PersistentEncounterRuntime runtime(FileEncounterStore store){var runtime=new PersistentEncounterRuntime(store,(id,reward)->{});assertTrue(runtime.attach(WORLD,ENEMY,"Wolf_Black",Optional.of(spawn())));return runtime;}
    static void hit(PersistentEncounterRuntime runtime,long now){assertTrue(runtime.damage(WORLD,ENEMY,ACTOR,100,90,100,true,now));}
    private Path segment()throws Exception{try(var paths=Files.list(directory.resolve("journal"))){return paths.filter(p->p.toString().endsWith(".wal")).sorted().toList().getLast();}}
    private Path context(){String key=RewardIntent.digest(WORLD+"/"+ENEMY);return directory.resolve("contexts").resolve(key.substring(0,2)).resolve(key+".json");}

    @Test void acceptedDeltaIsForcedCompactAndDoesNotRewriteTheSnapshot()throws Exception {
        EncounterContributions.Snapshot expected;
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);byte[] original=Files.readAllBytes(context());store.timings().reset();hit(runtime,101);
            assertArrayEquals(original,Files.readAllBytes(context()));assertTrue(Files.size(segment())<256);
            assertEquals(1,store.journalSequence());expected=store.load(WORLD,ENEMY).orElseThrow();
            assertEquals(1L,((Map<?,?>)store.timings().snapshot().get("JOURNAL_APPEND")).get("count"));
            assertEquals(1L,((Map<?,?>)store.timings().snapshot().get("JOURNAL_FORCE")).get("count"));
            assertEquals(0L,((Map<?,?>)store.timings().snapshot().get("CHECKPOINT_SERIALIZATION")).get("count"));
        }
        for(int restart=0;restart<3;restart++)try(var store=new FileEncounterStore(directory)){assertEquals(expected,store.load(WORLD,ENEMY).orElseThrow());assertEquals(1,store.journalSequence());}
    }
    @Test void lockIsHeldBetweenOperationsAndReleasedOnlyOnClose()throws Exception {
        try(var owner=new FileEncounterStore(directory)){
            runtime(owner);
            try(var other=new FileEncounterStore(directory)){assertThrows(IllegalStateException.class,other::pendingCount);}
            try(var channel=FileChannel.open(directory.resolve("writer.lock"),StandardOpenOption.WRITE)){assertThrows(java.nio.channels.OverlappingFileLockException.class,channel::tryLock);}
        }
        try(var next=new FileEncounterStore(directory)){assertEquals(0,next.pendingCount());}
    }
    @ParameterizedTest @EnumSource(FileEncounterStore.JournalBoundary.class)
    void everyJournalAndCheckpointFaultReplaysAcceptedCreditWithoutDuplication(FileEncounterStore.JournalBoundary boundary)throws Exception {
        try(var store=new FileEncounterStore(directory,ignored->{},at->{if(at==boundary)throw new IllegalStateException("SIMULATED_PROCESS_INTERRUPTION");})){
            var runtime=runtime(store);
            if(boundary==FileEncounterStore.JournalBoundary.AFTER_APPEND||boundary==FileEncounterStore.JournalBoundary.AFTER_FORCE){assertThrows(IllegalStateException.class,()->hit(runtime,101));assertTrue(runtime.unavailable());}
            else {hit(runtime,101);assertThrows(IllegalStateException.class,store::checkpoint);}
            assertThrows(IllegalStateException.class,store::pendingCount);
        }
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);assertEquals(List.of(ACTOR),runtime.contributors(WORLD,ENEMY));assertEquals(1,store.journalSequence());
            store.checkpoint();hit(runtime,102);assertEquals(2,store.journalSequence());
        }
        try(var store=new FileEncounterStore(directory)){var saved=store.load(WORLD,ENEMY).orElseThrow();assertEquals(1,saved.credits().size());assertEquals(102,saved.credits().getFirst().observedAtMillis());}
    }
    @Test void actualClosedChannelWriteFailureFreezesUntilRestart()throws Exception {
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);hit(runtime,101);
            var field=FileEncounterStore.class.getDeclaredField("journal");field.setAccessible(true);Object journal=field.get(store);
            var channel=journal.getClass().getDeclaredField("channel");channel.setAccessible(true);((FileChannel)channel.get(journal)).close();
            assertThrows(IllegalStateException.class,()->hit(runtime,102));assertTrue(runtime.unavailable());
            assertEquals("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",assertThrows(IllegalStateException.class,()->hit(runtime,103)).getMessage());
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(101,store.load(WORLD,ENEMY).orElseThrow().credits().getFirst().observedAtMillis());}
    }
    @Test void periodicCheckpointRetiresOnlyFullyCoveredJournalAndUsesOriginalSnapshotEnvelope()throws Exception {
        EncounterContributions.Snapshot expected;
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);for(int i=0;i<1030;i++)hit(runtime,101+i);
            expected=store.load(WORLD,ENEMY).orElseThrow();assertEquals(1030,store.journalSequence());
            assertEquals(1024,JsonParser.parseString(Files.readString(directory.resolve("checkpoint-floor.json"))).getAsJsonObject().getAsJsonObject("payload").get("sequence").getAsLong());
            assertTrue(Files.size(segment())<2048);
            try(var files=Files.walk(directory.resolve("checkpoints"))){for(Path path:files.filter(p->p.toString().endsWith(".snapshot")).toList()){
                var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();assertEquals(Set.of("schema","checksum","payload"),json.keySet());assertEquals(1,json.get("schema").getAsInt());
                assertEquals(EncounterContributions.Snapshot.class.getRecordComponents().length,json.getAsJsonObject("payload").size());
            }}
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(expected,store.load(WORLD,ENEMY).orElseThrow());assertEquals(1030,store.journalSequence());}
    }
    @ParameterizedTest @ValueSource(strings={"torn","checksum","sequence","duplicate","missing"})
    void malformedJournalIsRetainedAndFailsClosed(String mutation)throws Exception {
        try(var store=new FileEncounterStore(directory)){hit(runtime(store),101);}
        Path path=segment();byte[] good=Files.readAllBytes(path);
        if(mutation.equals("missing"))Files.move(path,path.resolveSibling("evidence.saved"));
        else {
            byte[] bad=good.clone();
            switch(mutation){
                case "torn" -> bad=Arrays.copyOf(good,good.length-1);
                case "checksum" -> bad[40]^=1;
                case "sequence" -> {ByteBuffer.wrap(bad).putLong(28,7);var crc=new CRC32C();crc.update(bad,28,bad.length-36);ByteBuffer.wrap(bad).putLong(bad.length-8,crc.getValue());}
                case "duplicate" -> {bad=Arrays.copyOf(good,good.length*2-24);System.arraycopy(good,24,bad,good.length,good.length-24);}
            }
            Files.write(path,bad);
        }
        Map<String,byte[]> before=new HashMap<>();try(var paths=Files.list(directory.resolve("journal"))){for(Path p:paths.toList())before.put(p.getFileName().toString(),Files.readAllBytes(p));}
        try(var store=new FileEncounterStore(directory)){assertThrows(IllegalStateException.class,()->store.load(WORLD,ENEMY));}
        for(var entry:before.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(directory.resolve("journal").resolve(entry.getKey())));
    }
    @Test void replayPreservesCreditReplacementsExclusionsAndExpiredFarmWatermarks()throws Exception {
        EncounterContributions.Snapshot expected;
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);hit(runtime,101);assertTrue(runtime.control(WORLD,ENEMY,ACTOR,true,true,true,102));
            hit(runtime,60200);assertFalse(runtime.masteryEligible(WORLD,ENEMY,ACTOR,5,60200));
            store.checkpoint();runtime.disqualify(WORLD,ENEMY);expected=store.load(WORLD,ENEMY).orElseThrow();
        }
        try(var store=new FileEncounterStore(directory)){
            assertEquals(expected,store.load(WORLD,ENEMY).orElseThrow());assertTrue(expected.disqualified());assertEquals(101,expected.progressAt());
            assertFalse(new PersistentEncounterRuntime(store,(id,reward)->fail()).attach(WORLD,ENEMY,"Wolf_Black",Optional.empty()));
        }
    }
    @Test void nestedAdmissionRejectsBeforeAnyRuntimeCreditOrWatermarkMutation()throws Exception {
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);var initial=store.load(WORLD,ENEMY).orElseThrow();
            try(var reservation=store.reserve(1)){assertThrows(FileEncounterStore.CapacityRejected.class,()->hit(runtime,101));}
            assertFalse(runtime.unavailable());assertTrue(runtime.contributors(WORLD,ENEMY).isEmpty());assertEquals(initial,store.load(WORLD,ENEMY).orElseThrow());
            hit(runtime,102);assertEquals(102,store.load(WORLD,ENEMY).orElseThrow().firstCombat());
        }
    }
    @Test void fullDeathQueueRejectsBeforeLedgerFreezeAndCanRetryAfterDrain()throws Exception {
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);hit(runtime,101);
            for(int i=0;i<FileEncounterStore.MAX_PENDING;i++){
                var other=EnemyRewardRegistry.load().classify(WORLD,new UUID(9,i),"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
                store.create(other);store.freeze(new EncounterContributions.DeathPlan(other,Vec3.ZERO,101,List.of()));
            }
            assertThrows(FileEncounterStore.CapacityRejected.class,()->runtime.death(WORLD,ENEMY,Vec3.ZERO,102,List.of()));
            assertFalse(runtime.unavailable());assertTrue(runtime.contains(WORLD,ENEMY));hit(runtime,103);
            assertEquals(0,store.drain(1,(id,reward)->fail()));
            assertTrue(runtime.death(WORLD,ENEMY,Vec3.ZERO,104,List.of()).isPresent());
        }
    }
    @ParameterizedTest @EnumSource(FileEncounterStore.JournalBoundary.class)
    void abruptProcessDeathReleasesLockAndRecoversJournalWithoutClose(FileEncounterStore.JournalBoundary boundary)throws Exception {
        String separator=System.getProperty("path.separator");var cp=new ArrayList<String>();
        for(Class<?> type:List.of(EncounterJournalCrashProcess.class,FileEncounterStore.class,com.google.gson.Gson.class))cp.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        cp.add(Path.of("build/resources/main").toAbsolutePath().toString());
        Path log=directory.resolve("child.log");
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",String.join(separator,cp),EncounterJournalCrashProcess.class.getName(),directory.toString(),boundary.name()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(child.waitFor(30,TimeUnit.SECONDS),"Crash child timeout");assertEquals(73,child.exitValue(),Files.readString(log));
        try(var store=new FileEncounterStore(directory)){assertEquals(1,store.journalSequence());assertEquals(ACTOR,store.load(WORLD,ENEMY).orElseThrow().credits().getFirst().player());store.checkpoint();}
    }
}
