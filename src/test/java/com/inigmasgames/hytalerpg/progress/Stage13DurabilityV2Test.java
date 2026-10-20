package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.progress.Stage13EncounterGroupCommitTest.*;

class Stage13DurabilityV2Test {
    @TempDir Path directory;
    static void advance(FileEncounterStore store,int count,long now)throws Exception {
        var ledger=new EncounterContributions();ledger.restore(store.create(spawn(ENEMY)));
        for(int first=0;first<count;first+=64){var values=new ArrayList<EncounterContributions.Snapshot>();
            for(int i=first;i<Math.min(first+64,count);i++){assertTrue(ledger.damage(WORLD,ENEMY,ACTOR,100,90,100,true,now+i));values.add(ledger.snapshot(WORLD,ENEMY));}
            done(submit(store,values));
        }
    }
    static EncounterJournalV2 journal(FileEncounterStore store)throws Exception {var field=FileEncounterStore.class.getDeclaredField("journal");field.setAccessible(true);return (EncounterJournalV2)field.get(store);}
    static Path active(FileEncounterStore store)throws Exception {var f=EncounterJournalV2.class.getDeclaredField("active");f.setAccessible(true);var segment=f.get(journal(store));var path=segment.getClass().getDeclaredMethod("path");path.setAccessible(true);return (Path)path.invoke(segment);}
    static void closeActive(FileEncounterStore store){try{var f=EncounterJournalV2.class.getDeclaredField("active");f.setAccessible(true);var segment=f.get(journal(store));var c=segment.getClass().getDeclaredMethod("channel");c.setAccessible(true);((FileChannel)c.invoke(segment)).close();}catch(Exception e){throw new IllegalStateException(e);}}
    @Test void sealed64FramesUseOneForceAndReplayThreeTimes()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,64,101);assertEquals(1,forceCount(store));}
        for(int i=0;i<3;i++)try(var store=FileEncounterStore.durableV2(directory)){assertEquals(64,store.journalSequence());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @Test void legitimateHealingOperationSeals64ContextsIntoOneSharedReceipt()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){
            var runtime=new PersistentEncounterRuntime(store,(id,reward)->fail());
            for(int i=0;i<64;i++){UUID id=new UUID(5,i);assertTrue(runtime.attach(WORLD,id,"Wolf_Black",Optional.of(spawn(id))));assertTrue(done(runtime.submitDamage(WORLD,id,ACTOR,100,90,100,true,101).durable()));}
            long before=forceCount(store);var heal=runtime.submitHeal(WORLD,new UUID(3,4),ACTOR,5,true,102);assertEquals(64,heal.provisional());assertEquals(64,done(heal.durable()));assertEquals(before+1,forceCount(store));assertEquals(128,store.journalSequence());
        }
    }
    @Test void sealRejectsLateAppendAndNewLeaseCreatesNextOrderedOperation()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){var values=frames(store,2);
            try(var lease=store.reserveSubmission(WORLD,List.of(ENEMY))){done(lease.submit(values.subList(0,1)));assertThrows(FileEncounterStore.CapacityRejected.class,()->lease.submit(values.subList(1,2)));}
            done(submit(store,values.subList(1,2)));assertEquals(2,store.journalSequence());assertEquals(102,store.load(WORLD,ENEMY).orElseThrow().lastObserved());
        }
    }
    @Test void noDurableReceiptOrDependentMasteryBeforeForce()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var applied=new AtomicInteger();
        try(var store=FileEncounterStore.durableV2(directory,b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();latch(release);}},true);var effects=new DurableEncounterEffects()){
            var receipt=submit(store,frames(store,64));try(var effect=effects.reserve()){effect.submit(receipt,ignored->applied.incrementAndGet());}
            latch(entered);try{assertFalse(receipt.toCompletableFuture().isDone());assertEquals(0,applied.get());assertEquals(0,forceCount(store));}finally{release.countDown();}
            done(receipt);effects.await();assertEquals(1,applied.get());assertEquals(1,forceCount(store));
        }
    }
    @Test void openReservationPreventsDeathPlanBeforeMutation()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){runtime(store);try(var lease=store.reserveSubmission(WORLD,List.of(ENEMY))){
            var invoked=new AtomicBoolean();assertThrows(FileEncounterStore.CapacityRejected.class,()->store.withDeathCapacity(()->{invoked.set(true);return null;}));assertFalse(invoked.get());assertTrue(store.death(WORLD,ENEMY).isEmpty());
        }}
    }
    @ParameterizedTest @ValueSource(strings={"death","exclusion","detach","unload","shutdown"})
    void lifecycleFenceCannotOvertakeSealedContribution(String action)throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var store=FileEncounterStore.durableV2(directory,b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();latch(release);}},true);
        try{var runtime=runtime(store);var receipt=hit(runtime,101).durable();latch(entered);
            var fence=CompletableFuture.runAsync(()->{switch(action){case "death"->runtime.death(WORLD,ENEMY,com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO,102,List.of());case "exclusion"->runtime.disqualify(WORLD,ENEMY);case "detach"->runtime.detach(WORLD,ENEMY);case "unload"->runtime.unload(WORLD);case "shutdown"->store.close();default->throw new AssertionError();}});
            try{assertThrows(TimeoutException.class,()->fence.get(50,TimeUnit.MILLISECONDS));assertFalse(receipt.toCompletableFuture().isDone());}finally{release.countDown();}
            assertTrue(done(receipt));fence.get(5,TimeUnit.SECONDS);
        }finally{release.countDown();store.close();}
        try(var recovered=FileEncounterStore.durableV2(directory)){assertEquals(1,recovered.journalSequence());if(action.equals("exclusion"))assertTrue(recovered.load(WORLD,ENEMY).orElseThrow().disqualified());}
    }
    @Test void admissionLimitsRejectBeforeRuntimeLedgerMutation()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(directory,b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();latch(release);}},true)){
            var runtime=runtime(store);hit(runtime,101);latch(entered);
            try{for(int i=1;i<64;i++)hit(runtime,101+i);assertThrows(FileEncounterStore.CapacityRejected.class,()->hit(runtime,999));assertFalse(runtime.unavailable());assertThrows(FileEncounterStore.CapacityRejected.class,()->store.reserveSubmission(WORLD,Collections.nCopies(65,ENEMY)));}finally{release.countDown();}
            runtime.awaitDurable();assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());
        }
    }
    @ParameterizedTest @ValueSource(strings={"append","force"})
    void realClosedChannelAppendAndForcePoisonStore(String phase)throws Exception {
        var ref=new AtomicReference<FileEncounterStore>();
        try(var store=FileEncounterStore.durableV2(directory,b->{},b->{if(phase.equals("force")&&b==FileEncounterStore.GroupBoundary.BEFORE_FORCE)closeActive(ref.get());},true)){
            ref.set(store);var values=frames(store,8);if(phase.equals("append"))closeActive(store);
            assertThrows(ExecutionException.class,()->done(submit(store,values)));assertThrows(IllegalStateException.class,store::pendingCount);
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(phase.equals("append")?0:8,store.journalSequence());}
    }
    @ParameterizedTest @EnumSource(value=FileEncounterStore.GroupBoundary.class,names={"AFTER_FIRST_FRAME","AFTER_GROUP_APPEND","BEFORE_FORCE","AFTER_GROUP_FORCE","BEFORE_ACKNOWLEDGEMENTS"})
    void immutableOperationFaultNeverReportsDurableSuccess(FileEncounterStore.GroupBoundary boundary)throws Exception {
        try(var store=FileEncounterStore.durableV2(directory,b->{},b->{if(b==boundary)throw new IllegalStateException("test failure");},true)){
            assertThrows(ExecutionException.class,()->done(submit(store,frames(store,64))));assertThrows(IllegalStateException.class,()->store.reserveSubmission(WORLD,List.of(ENEMY)));
        }
        try(var recovered=FileEncounterStore.durableV2(directory)){assertEquals(boundary==FileEncounterStore.GroupBoundary.AFTER_FIRST_FRAME?1:64,recovered.journalSequence());}
    }
    @Test void rotationUsesPreparedHandleAndNoAdditionalForegroundBarrier()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,1024,101);store.awaitPreparation();long before=forceCount(store);advance(store,64,1125);assertEquals(before+1,forceCount(store));assertEquals(1088,store.journalSequence());store.awaitCheckpoints();assertEquals(1L,store.timings().v2().get("bundleCount"));}
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(1088,store.journalSequence());assertEquals(1188,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void growingAndPreallocatedLayoutsReplayIdentically(boolean allocated)throws Exception {
        Path active;
        try(var store=FileEncounterStore.durableV2(directory,b->{},b->{},allocated)){advance(store,64,101);active=active(store);assertEquals(allocated?EncounterJournalV2.EXTENT:EncounterJournalV2.DATA+64*130L,Files.size(active));}
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(64,store.journalSequence());}
    }
    @ParameterizedTest @ValueSource(strings={"nonzero-tail","torn","crc","missing","duplicate"})
    void invalidActiveHistoryFailsClosedWithoutTruncation(String kind)throws Exception {
        Path active;
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,64,101);active=active(store);}
        if(kind.equals("missing"))Files.move(active,active.resolveSibling("retained-evidence"));
        else if(kind.equals("duplicate"))Files.copy(active,active.resolveSibling(UUID.randomUUID()+".wal2"));
        else try(var out=FileChannel.open(active,StandardOpenOption.WRITE)){
            if(kind.equals("torn"))out.truncate(EncounterJournalV2.DATA+19);
            else {out.position(kind.equals("crc")?EncounterJournalV2.DATA+30:EncounterJournalV2.EXTENT-2);out.write(ByteBuffer.wrap(new byte[]{99}));out.force(true);}
        }
        byte[] preserved=Files.exists(active)?Files.readAllBytes(active):null;
        try(var store=FileEncounterStore.durableV2(directory)){assertThrows(IllegalStateException.class,store::pendingCount);}
        if(preserved!=null)assertArrayEquals(preserved,Files.readAllBytes(active));
    }
    @Test void checkpointPublishesTwoForcesNotTwoPerContextAndRetainsOldIndexBranches()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){
            for(int i=0;i<48;i++){var id=new UUID(7,i);var ledger=new EncounterContributions();ledger.restore(store.create(spawn(id)));ledger.damage(WORLD,id,ACTOR,100,90,100,true,101);done(submit(store,List.of(ledger.snapshot(WORLD,id))));}
            store.awaitPreparation();store.resetTimings();store.checkpoint();
            assertEquals(2,store.timings().count(EncounterPersistenceTimings.Phase.CHECKPOINT_FORCE));assertEquals(1L,store.timings().v2().get("bundleCount"));
            advance(store,1,102);store.checkpoint();
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(49,store.journalSequence());for(int i=0;i<48;i++)assertEquals(101,store.load(WORLD,new UUID(7,i)).orElseThrow().lastObserved());assertEquals(102,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @ParameterizedTest @ValueSource(strings={"missing-bundle","corrupt-bundle","corrupt-index","corrupt-manifest"})
    void unavailableOrCorruptPublishedBundleCannotFallBackToStaleCheckpoint(String kind)throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){advance(store,64,101);store.checkpoint();}
        Path manifest=directory.resolve("checkpoint-floor.json");
        if(kind.equals("corrupt-manifest"))Files.writeString(manifest,"{}");
        else{Path bundle;try(var files=Files.list(directory.resolve("bundles-v2"))){bundle=files.filter(p->p.toString().endsWith(".bundle")).findFirst().orElseThrow();}
            if(kind.equals("missing-bundle"))Files.move(bundle,bundle.resolveSibling("preserved-missing-bundle"));
            else{var data=Files.readAllBytes(bundle);data[kind.equals("corrupt-index")?20:data.length/2]^=1;Files.write(bundle,data);}
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertThrows(IllegalStateException.class,store::pendingCount);}
    }
    @ParameterizedTest @EnumSource(value=FileEncounterStore.DurabilityBoundary.class,names={"BUNDLE_SERIALIZATION","BUNDLE_PARTIAL_WRITE","BUNDLE_BEFORE_FORCE","BUNDLE_AFTER_FORCE","BUNDLES_DURABLE","MANIFEST_PARTIAL_WRITE","MANIFEST_BEFORE_FORCE","MANIFEST_AFTER_FORCE"})
    void failedPublicationPreservesOldManifestAndFullWal(FileEncounterStore.DurabilityBoundary boundary)throws Exception {
        var armed=new AtomicBoolean();byte[] old;
        try(var store=FileEncounterStore.durableV2(directory,b->{if(armed.get()&&b==boundary)throw new IllegalStateException("test publication fault");},b->{},true)){
            advance(store,64,101);store.awaitPreparation();old=Files.readAllBytes(directory.resolve("checkpoint-floor.json"));armed.set(true);assertThrows(IllegalStateException.class,store::checkpoint);assertArrayEquals(old,Files.readAllBytes(directory.resolve("checkpoint-floor.json")));
        }
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(64,store.journalSequence());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @Test void explicitV1MigrationPreservesContextWatermarksAndExclusions()throws Exception {
        try(var store=new FileEncounterStore(directory)){advance(store,64,101);store.checkpoint();store.disqualify(WORLD,ENEMY);}
        try(var store=FileEncounterStore.durableV2(directory)){assertEquals(64,store.journalSequence());assertTrue(store.load(WORLD,ENEMY).orElseThrow().disqualified());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
        try(var old=new FileEncounterStore(directory)){assertThrows(IllegalStateException.class,old::pendingCount);}
    }
    @Test void portableStatisticsRetainAllSamplesAndSerializeDeterministically(){
        long[] values={0,4_000_000,8_000_000,100_000_001};var result=StorageDurabilityQualification.statistics(values);
        assertEquals(100.000001,result.p99Ms());assertEquals(2,result.above4Ms());assertEquals(1,result.above8Ms());assertEquals(1,result.above100Ms());assertEquals(0,values[0]);
        var parsed=com.google.gson.JsonParser.parseString(StorageDurabilityQualification.statisticsJson(result)).getAsJsonObject();assertEquals(4,parsed.get("samples").getAsInt());
        assertEquals("a\\b\n\"c",com.google.gson.JsonParser.parseString(StorageDurabilityQualification.quote("a\\b\n\"c")).getAsString());assertThrows(IllegalArgumentException.class,()->StorageDurabilityQualification.statistics(new long[0]));
    }
}
