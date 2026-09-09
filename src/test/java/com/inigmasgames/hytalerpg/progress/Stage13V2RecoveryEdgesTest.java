package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32C;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.progress.Stage13EncounterGroupCommitTest.*;
import static com.inigmasgames.hytalerpg.progress.Stage13DurabilityV2Test.*;

class Stage13V2RecoveryEdgesTest {
    @TempDir Path directory;
    @Test void failedAuthorityWriteRequiresRestartAndDoesNotPermitLaterContributions()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){
            var runtime=runtime(store);advance(store,1,101);
            Files.writeString(directory.resolve("excluded"),"not a directory");
            assertThrows(IllegalStateException.class,()->store.disqualify(WORLD,ENEMY));
            assertEquals("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",assertThrows(IllegalStateException.class,store::pendingCount).getMessage());
            assertThrows(IllegalStateException.class,()->hit(runtime,102));
        }
    }
    @ParameterizedTest @ValueSource(ints={1,3})
    void preparedOrphansAreNotHistoryAndAreSafelyReplacedOnlyAfterValidation(int count)throws Exception {
        Path active;
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,64,101);store.awaitPreparation();active=active(store);}
        for(int i=0;i<count;i++){
            UUID id=UUID.randomUUID();byte[] data=new byte[EncounterJournalV2.DATA];var header=ByteBuffer.wrap(data);header.putLong(EncounterJournalV2.MAGIC).putInt(2).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).putLong(EncounterJournalV2.EXTENT);
            var crc=new CRC32C();crc.update(data,0,EncounterJournalV2.HEADER-8);header.putLong(EncounterJournalV2.HEADER-8,crc.getValue());Files.write(directory.resolve("journal-v2").resolve(id+".wal2"),data);
        }
        for(int i=0;i<3;i++)try(var store=FileEncounterStore.durableV2(directory)){assertEquals(64,store.journalSequence());store.awaitPreparation();try(var files=Files.list(directory.resolve("journal-v2"))){assertEquals(2,files.filter(p->p.toString().endsWith(".wal2")).count());}}
        assertTrue(Files.exists(active));
    }
    @Test void malformedNonzeroPreparedTailIsNeverSilentlyDiscarded()throws Exception {
        Path active;
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,1,101);store.awaitPreparation();active=active(store);}
        Path prepared;try(var files=Files.list(directory.resolve("journal-v2"))){prepared=files.filter(p->p.toString().endsWith(".wal2")&&!p.equals(active)).findFirst().orElseThrow();}
        try(var file=java.nio.channels.FileChannel.open(prepared,StandardOpenOption.WRITE)){file.position(EncounterJournalV2.DATA+1);file.write(ByteBuffer.wrap(new byte[]{1}));}
        try(var store=FileEncounterStore.durableV2(directory)){assertThrows(IllegalStateException.class,store::pendingCount);}assertTrue(Files.exists(prepared));
    }
    @Test void checkpointWorkerBacklogBackpressuresBeforeThirdEpochAndEventuallyProgresses()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=FileEncounterStore.durableV2(directory,b->{if(b==FileEncounterStore.DurabilityBoundary.BUNDLE_SERIALIZATION&&once.compareAndSet(false,true)){entered.countDown();latch(release);}},b->{},true)){
            advance(store,1025,101);latch(entered);
            // Avoid the public read/checkpoint fence: the ledger remains the provisional authority.
            var ledger=new EncounterContributions();var previous=journal(store).entry(new EncounterJournal.Key(WORLD,ENEMY));ledger.restore(previous.snapshot());
            var remaining=CompletableFuture.runAsync(()->{try{for(int first=0;first<2048;first+=64){var values=new ArrayList<EncounterContributions.Snapshot>();for(int i=first;i<first+64;i++){assertTrue(ledger.damage(WORLD,ENEMY,ACTOR,100,90,100,true,1126+i));values.add(ledger.snapshot(WORLD,ENEMY));}done(submit(store,values));}}catch(Exception e){throw new CompletionException(e);}});
            try{assertThrows(TimeoutException.class,()->remaining.get(100,TimeUnit.MILLISECONDS));}finally{release.countDown();}
            remaining.get(5,TimeUnit.SECONDS);store.awaitCheckpoints();assertEquals(3073,store.journalSequence());assertEquals(2,store.timings().grouping().get("maxCheckpointBacklog"));
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(3073,store.journalSequence());}
    }
    @Test void maximumDeltaFramesStayInsideDerivedExtentAndByteRolloversRemainBounded()throws Exception {
        assertEquals(144+1024L*(16384+4),EncounterJournalV2.EXTENT);
        try(var store=FileEncounterStore.durableV2(directory)){
            var initial=store.create(spawn(ENEMY));
            for(int first=0;first<1024;first+=64){var values=new ArrayList<EncounterContributions.Snapshot>();
                for(int i=first;i<first+64;i++){var credits=new ArrayList<EncounterContributions.Credit>();for(int j=0;j<256;j++)credits.add(new EncounterContributions.Credit(new UUID(i+9,j),EncounterContributions.Kind.DAMAGE,101+i,1));values.add(new EncounterContributions.Snapshot(initial.spawn(),credits,101,101+i,101+i,.9,false));}
                done(submit(store,values));
            }
            assertEquals(EncounterJournalV2.EXTENT,Files.size(active(store)));assertTrue(forceCount(store)>16);assertTrue(forceCount(store)<=16*EncounterGroupCommit.MAX_PENDING_GROUPS);
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(1024,store.journalSequence());assertEquals(256,store.load(WORLD,ENEMY).orElseThrow().credits().size());}
    }
    @Test void actualArchivedHRejectsV2AndCoordinatedLegacyCopyRemainsReadable()throws Exception {
        Path legacy=directory.resolve("legacy"),migrated=directory.resolve("migrated");String expected;
        try(var store=new FileEncounterStore(legacy)){advance(store,64,101);store.checkpoint();expected=new com.google.gson.Gson().toJson(store.load(WORLD,ENEMY).orElseThrow());}
        try(var paths=Files.walk(legacy)){for(var path:paths.toList()){var target=migrated.resolve(legacy.relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);}}
        try(var store=FileEncounterStore.durableV2(migrated)){assertEquals(64,store.journalSequence());advance(store,1,165);store.checkpoint();}
        Path jar=Path.of("evidence/stage-13/cohort-h/artifacts/HytaleRPG-0.0.25.jar").toAbsolutePath();
        assertEquals("8849f88cb9225e847c05580ce1eb e3e2fb6be0d487c73aa4408526c1ed6381d6".replace(" ",""),EncounterCheckpointBundle.digest(Files.readAllBytes(jar)));
        Path server=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL(),server.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var type=loader.loadClass("com.inigmasgames.hytalerpg.progress.FileEncounterStore");
            Object incompatible=type.getConstructor(Path.class).newInstance(migrated);
            try{assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod("journalSequence").invoke(incompatible));}finally{type.getMethod("close").invoke(incompatible);}
            Object restored=type.getConstructor(Path.class).newInstance(legacy);
            try{assertEquals(64L,type.getMethod("journalSequence").invoke(restored));var value=(Optional<?>)type.getMethod("load",UUID.class,UUID.class).invoke(restored,WORLD,ENEMY);var gson=loader.loadClass("com.google.gson.Gson");assertEquals(expected,gson.getMethod("toJson",Object.class).invoke(gson.getConstructor().newInstance(),value.orElseThrow()));}finally{type.getMethod("close").invoke(restored);}
        }
    }
}
