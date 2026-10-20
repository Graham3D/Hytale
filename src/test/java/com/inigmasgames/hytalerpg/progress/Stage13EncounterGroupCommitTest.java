package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
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

class Stage13EncounterGroupCommitTest {
    @TempDir Path directory;
    static final UUID WORLD=new UUID(1,1),ENEMY=new UUID(2,2),ACTOR=new UUID(3,3);
    static EnemyRewardRegistry.Spawn spawn(UUID enemy){return EnemyRewardRegistry.load().classify(WORLD,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    static PersistentEncounterRuntime runtime(FileEncounterStore store){var runtime=new PersistentEncounterRuntime(store,(id,reward)->{});assertTrue(runtime.attach(WORLD,ENEMY,"Wolf_Black",Optional.of(spawn(ENEMY))));return runtime;}
    static void latch(CountDownLatch latch){try{assertTrue(latch.await(4,TimeUnit.SECONDS),"test barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    static <T>T done(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static PersistentEncounterRuntime.Submission<Boolean> hit(PersistentEncounterRuntime runtime,long now){return runtime.submitDamage(WORLD,ENEMY,ACTOR,100,90,100,true,now);}
    static List<EncounterContributions.Snapshot> frames(FileEncounterStore store,int count){
        var ledger=new EncounterContributions();assertTrue(ledger.restore(store.create(spawn(ENEMY))));var values=new ArrayList<EncounterContributions.Snapshot>();
        for(int i=0;i<count;i++){assertTrue(ledger.damage(WORLD,ENEMY,ACTOR,100,90,100,true,101+i));values.add(ledger.snapshot(WORLD,ENEMY));}return values;
    }
    static CompletionStage<Void> submit(FileEncounterStore store,List<EncounterContributions.Snapshot> frames){try(var lease=store.reserveSubmission(WORLD,frames.stream().map(s->s.spawn().enemy()).toList())){return lease.submit(frames);}}
    static long forceCount(FileEncounterStore store){return store.timings().count(EncounterPersistenceTimings.Phase.JOURNAL_FORCE);}
    private List<Path> segments()throws Exception{try(var files=Files.list(directory.resolve("journal"))){return files.filter(p->p.toString().endsWith(".wal")).sorted().toList();}}

    @Test void sixtyFourIndividuallySequencedFramesUseOneForceAndReplayExactlyOnce()throws Exception{
        EncounterContributions.Snapshot expected;
        try(var store=new FileEncounterStore(directory)){var values=frames(store,64);store.timings().reset();done(submit(store,values));assertEquals(1,forceCount(store));assertEquals(64,store.journalSequence());expected=values.getLast();assertEquals(expected,store.load(WORLD,ENEMY).orElseThrow());}
        for(int restart=0;restart<3;restart++)try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());assertEquals(expected,store.load(WORLD,ENEMY).orElseThrow());}
    }
    @Test void serialProductionSubmissionsContinueDuringForceAndAllWaitForDurability()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE&&once.compareAndSet(false,true)){entered.countDown();latch(release);}})){
            var runtime=runtime(store);var receipts=new ArrayList<CompletionStage<Boolean>>();receipts.add(hit(runtime,101).durable());latch(entered);
            try{for(int i=1;i<64;i++)receipts.add(hit(runtime,101+i).durable());assertTrue(receipts.stream().noneMatch(r->r.toCompletableFuture().isDone()));assertEquals(0,forceCount(store));}
            finally{release.countDown();}
            for(var receipt:receipts)assertTrue(done(receipt));assertEquals(2,forceCount(store));assertEquals(64,store.journalSequence());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());
        }
    }
    @Test void independentContextsHaveGlobalAndLocalPredecessors()throws Exception{
        try(var store=new FileEncounterStore(directory)){
            var ledger=new EncounterContributions();var a=spawn(ENEMY);var b=spawn(new UUID(2,3));ledger.restore(store.create(a));ledger.restore(store.create(b));var values=new ArrayList<EncounterContributions.Snapshot>();
            for(int i=0;i<32;i++)for(var spawn:List.of(a,b)){ledger.damage(WORLD,spawn.enemy(),ACTOR,100,90,100,true,101+i);values.add(ledger.snapshot(WORLD,spawn.enemy()));}
            done(submit(store,values));assertEquals(1,forceCount(store));
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());for(var id:List.of(ENEMY,new UUID(2,3))){var value=store.load(WORLD,id).orElseThrow();assertEquals(132,value.lastObserved());assertEquals(1,value.credits().size());}}
    }
    @ParameterizedTest @EnumSource(value=FileEncounterStore.GroupBoundary.class,names={"AFTER_FIRST_FRAME","AFTER_GROUP_APPEND","BEFORE_FORCE","AFTER_GROUP_FORCE","BEFORE_ACKNOWLEDGEMENTS"})
    void groupFaultNeverAcknowledgesMembersAndPoisonsFutureSubmissions(FileEncounterStore.GroupBoundary boundary)throws Exception{
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==boundary)throw new IllegalStateException("GROUP_FAULT");})){
            var values=frames(store,64);var receipt=submit(store,values);assertThrows(ExecutionException.class,()->done(receipt));
            assertThrows(IllegalStateException.class,()->store.reserveSubmission(WORLD,List.of(ENEMY)));
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(boundary==FileEncounterStore.GroupBoundary.AFTER_FIRST_FRAME?1:64,store.journalSequence());}
    }
    @ParameterizedTest @ValueSource(strings={"append","force"})
    void actualClosedFileChannelFailsAppendOrForce(String phase)throws Exception{
        var ref=new AtomicReference<FileEncounterStore>();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(phase.equals("force")&&b==FileEncounterStore.GroupBoundary.BEFORE_FORCE)closeWal(ref.get());})){
            ref.set(store);var values=frames(store,8);if(phase.equals("append"))closeWal(store);
            assertThrows(ExecutionException.class,()->done(submit(store,values)));assertThrows(IllegalStateException.class,store::pendingCount);
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(phase.equals("append")?0:8,store.journalSequence());}
    }
    static void closeWal(FileEncounterStore store){try{var f=FileEncounterStore.class.getDeclaredField("journal");f.setAccessible(true);Object journal=f.get(store);var c=journal.getClass().getDeclaredField("channel");c.setAccessible(true);((FileChannel)c.get(journal)).close();}catch(Exception e){throw new IllegalStateException(e);}}
    @ParameterizedTest @ValueSource(strings={"torn","crc","predecessor"})
    void corruptionInsideGroupedWriteIsRetainedAndRejected(String fault)throws Exception{
        try(var store=new FileEncounterStore(directory)){done(submit(store,frames(store,64)));}
        var segment=segments().getFirst();var bytes=Files.readAllBytes(segment);int second=24+4+ByteBuffer.wrap(bytes).getInt(24);
        if(fault.equals("torn"))bytes=Arrays.copyOf(bytes,second+19);
        else if(fault.equals("crc"))bytes[second+30]^=1;
        else {int length=ByteBuffer.wrap(bytes).getInt(second);ByteBuffer.wrap(bytes).putLong(second+44,777);var crc=new java.util.zip.CRC32C();crc.update(bytes,second+4,length-8);ByteBuffer.wrap(bytes).putLong(second+4+length-8,crc.getValue());}
        Files.write(segment,bytes);try(var store=new FileEncounterStore(directory)){assertThrows(IllegalStateException.class,store::pendingCount);}assertArrayEquals(bytes,Files.readAllBytes(segment));
    }
    @Test void maximumFrameDeltasRollOverAtBoundedGroupBytes()throws Exception{
        try(var store=new FileEncounterStore(directory)){
            var initial=store.create(spawn(ENEMY));var values=new ArrayList<EncounterContributions.Snapshot>();
            for(int i=0;i<64;i++){var credits=new ArrayList<EncounterContributions.Credit>();for(int j=0;j<256;j++)credits.add(new EncounterContributions.Credit(new UUID(i+9,j),EncounterContributions.Kind.DAMAGE,101+i,1));values.add(new EncounterContributions.Snapshot(initial.spawn(),credits,101,101+i,101+i,.9,false));}
            done(submit(store,values));assertTrue(forceCount(store)>1);assertTrue(forceCount(store)<=EncounterGroupCommit.MAX_PENDING_GROUPS);
            var histogram=(Map<Integer,Long>)store.timings().grouping().get("bytesPerGroup");assertTrue(histogram.keySet().stream().allMatch(n->n<=EncounterGroupCommit.MAX_GROUP_BYTES));
            assertEquals(values.getLast(),store.load(WORLD,ENEMY).orElseThrow());
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());assertEquals(256,store.load(WORLD,ENEMY).orElseThrow().credits().size());}
    }
    @Test void oversizedOperationAndPerContextPendingRejectBeforeMutation()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE&&once.compareAndSet(false,true)){entered.countDown();latch(release);}})){
            var runtime=runtime(store);assertThrows(FileEncounterStore.CapacityRejected.class,()->store.reserveSubmission(WORLD,Collections.nCopies(65,ENEMY)));
            hit(runtime,101);latch(entered);try{for(int i=1;i<64;i++)hit(runtime,101+i);assertThrows(FileEncounterStore.CapacityRejected.class,()->hit(runtime,999));assertFalse(runtime.unavailable());}finally{release.countDown();}
            runtime.awaitDurable();assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());assertTrue(done(hit(runtime,165).durable()));
        }
    }
    @Test void totalPendingAndOperationBoundsRejectBeforeAnyStateChange()throws Exception{
        try(var store=new FileEncounterStore(directory)){
            runtime(store);var leases=new ArrayList<FileEncounterStore.SubmissionReservation>();
            try{for(int i=0;i<256;i++)leases.add(store.reserveSubmission(WORLD,List.of(new UUID(7,i))));assertThrows(FileEncounterStore.CapacityRejected.class,()->store.reserveSubmission(WORLD,List.of(ENEMY)));}
            finally{leases.forEach(FileEncounterStore.SubmissionReservation::close);}
            assertEquals(0,store.journalSequence());assertTrue(store.load(WORLD,ENEMY).orElseThrow().credits().isEmpty());
            try(var next=store.reserveSubmission(WORLD,List.of(ENEMY))){assertNotNull(next);}
        }
    }
    @Test void sparseSingleSubmissionHasNoCollectionWait()throws Exception{
        try(var store=new FileEncounterStore(directory)){var runtime=runtime(store);store.timings().reset();double[] samples=new double[60];
            for(int i=0;i<samples.length;i++){long start=System.nanoTime();assertTrue(done(hit(runtime,101+i).durable()));samples[i]=(System.nanoTime()-start)/1e6;assertTrue(samples[i]<5000);}
            assertEquals(60,forceCount(store));var sorted=samples.clone();Arrays.sort(sorted);Path out=Path.of("build/stage13-hardening/group-sparse-load.json");Files.createDirectories(out.getParent());
            Files.writeString(out,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("samples",60,"orderedLatencyMs",samples,"p50Ms",sorted[29],"p95Ms",sorted[56],"p99Ms",sorted[59],"forceCalls",60,"persistenceTimings",store.timings().snapshot(),"connectedProof",false)));
        }
    }
    @Test void acknowledgementDeadlineFailsClosedInsteadOfLeavingAnUnboundedWait()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}})){
            var runtime=runtime(store);var receipt=hit(runtime,101).durable();latch(entered);
            try{var failure=assertThrows(ExecutionException.class,()->receipt.toCompletableFuture().get(6,TimeUnit.SECONDS));assertInstanceOf(TimeoutException.class,failure.getCause());assertThrows(IllegalStateException.class,()->hit(runtime,102));}
            finally{release.countDown();}
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(1,store.journalSequence());assertEquals(101,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @Test void checkpointWorkerFailureBeforePublicationCannotDiscardTheWal()throws Exception{
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED)throw new IllegalStateException("CHECKPOINT_WORKER_FAILED");})){
            done(submit(store,frames(store,64)));assertThrows(IllegalStateException.class,store::checkpoint);assertFalse(Files.exists(directory.resolve("checkpoint-floor.json")));assertEquals(2,segments().size());assertThrows(IllegalStateException.class,store::pendingCount);
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @Test void concurrentProducersCannotDuplicateSequenceOrLoseCredits()throws Exception{
        try(var store=new FileEncounterStore(directory);var pool=Executors.newFixedThreadPool(4)){
            var runtime=runtime(store);var ready=new CountDownLatch(4);var start=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();
            for(int t=0;t<4;t++){final int actor=t;tasks.add(pool.submit(()->{ready.countDown();latch(start);try{var receipts=new ArrayList<CompletionStage<Boolean>>();for(int i=0;i<16;i++)receipts.add(runtime.submitDamage(WORLD,ENEMY,new UUID(3,actor),100,90,100,true,101).durable());for(var receipt:receipts)assertTrue(done(receipt));}catch(Exception e){throw new IllegalStateException(e);}}));}
            latch(ready);start.countDown();for(var task:tasks)task.get(10,TimeUnit.SECONDS);assertEquals(64,store.journalSequence());assertEquals(4,runtime.contributors(WORLD,ENEMY).size());
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());assertEquals(4,store.load(WORLD,ENEMY).orElseThrow().credits().size());}
    }
    @Test void healingUsesOneExplicitMultiEncounterGroup()throws Exception{
        try(var store=new FileEncounterStore(directory)){
            var runtime=runtime(store);for(int i=0;i<16;i++){var id=new UUID(5,i);runtime.attach(WORLD,id,"Wolf_Black",Optional.of(spawn(id)));assertTrue(runtime.damage(WORLD,id,ACTOR,100,90,100,true,101));}
            store.timings().reset();var receipt=runtime.submitHeal(WORLD,new UUID(6,1),ACTOR,4,true,102);assertEquals(16,done(receipt.durable()));assertEquals(1,forceCount(store));assertEquals(16,store.timings().count(EncounterPersistenceTimings.Phase.DURABLE_ACK));
        }
    }
    @ParameterizedTest @ValueSource(strings={"death","exclusion","detach","shutdown"})
    void finalizationCannotOvertakePendingContributions(String action)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.BEFORE_FORCE&&once.compareAndSet(false,true)){entered.countDown();latch(release);}});var pool=Executors.newSingleThreadExecutor()){
            var runtime=runtime(store);var receipt=hit(runtime,101).durable();latch(entered);var begun=new CountDownLatch(1);
            var finish=pool.submit(()->{begun.countDown();switch(action){case "death"->runtime.death(WORLD,ENEMY,Vec3.ZERO,102,List.of(new EncounterContributions.Participant(ACTOR,WORLD,Vec3.ZERO,5,true,null)));case "exclusion"->runtime.disqualify(WORLD,ENEMY);case "detach"->runtime.detach(WORLD,ENEMY);case "shutdown"->store.close();}});
            latch(begun);try{assertFalse(finish.isDone());assertFalse(receipt.toCompletableFuture().isDone());assertFalse(Files.exists(directory.resolve("pending")));}finally{release.countDown();}
            assertTrue(done(receipt));finish.get(5,TimeUnit.SECONDS);
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(1,store.journalSequence());if(action.equals("death"))assertEquals(1,store.death(WORLD,ENEMY).orElseThrow().shares().size());else assertEquals(101,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    @Test void deferredEffectsAreOrderedAfterForceAndBeforeDeathFence()throws Exception{
        var persisted=new CompletableFuture<Boolean>();var calls=new ArrayList<Integer>();
        try(var effects=new DurableEncounterEffects();var pool=Executors.newSingleThreadExecutor()){
            try(var first=effects.reserve()){first.submit(persisted,accepted->{assertTrue(accepted);calls.add(1);});}
            try(var second=effects.reserve()){second.submit(CompletableFuture.completedStage(true),accepted->calls.add(2));}
            var fence=pool.submit(effects::await);assertFalse(fence.isDone());assertTrue(calls.isEmpty());persisted.complete(true);fence.get(5,TimeUnit.SECONDS);assertEquals(List.of(1,2),calls);
        }
    }
    @Test void deferredEffectsCapacityRejectsBeforeContributionAndCanBeReleased(){
        try(var effects=new DurableEncounterEffects()){
            var leases=new ArrayList<DurableEncounterEffects.Reservation>();for(int i=0;i<DurableEncounterEffects.MAX_PENDING;i++)leases.add(effects.reserve());
            assertThrows(FileEncounterStore.CapacityRejected.class,effects::reserve);leases.forEach(DurableEncounterEffects.Reservation::close);try(var next=effects.reserve()){assertNotNull(next);}
        }
    }
    @Test void failedDurabilityNeverRunsDependentEffect()throws Exception{
        var effects=new DurableEncounterEffects();var calls=new AtomicInteger();try(var lease=effects.reserve()){lease.submit(CompletableFuture.failedStage(new IllegalStateException("force failed")),ignored->calls.incrementAndGet());}
        assertThrows(IllegalStateException.class,effects::await);assertEquals(0,calls.get());assertThrows(IllegalStateException.class,effects::close);
    }
    @Test void immutableCheckpointSLeavesNewerRecordsReplayableAndOldSegmentsRetained()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED&&once.compareAndSet(false,true)){entered.countDown();latch(release);}});var pool=Executors.newSingleThreadExecutor()){
            var runtime=runtime(store);assertTrue(done(hit(runtime,101).durable()));var checkpoint=pool.submit(store::checkpoint);latch(entered);
            try{assertEquals(2,segments().size());assertFalse(Files.exists(directory.resolve("checkpoint-floor.json")));assertTrue(done(hit(runtime,102).durable()));assertTrue(done(hit(runtime,103).durable()));assertEquals(3,store.journalSequence());assertFalse(checkpoint.isDone());}
            finally{release.countDown();}
            checkpoint.get(5,TimeUnit.SECONDS);assertEquals(1,floor());assertEquals(1,segments().size());assertEquals(103,store.load(WORLD,ENEMY).orElseThrow().lastObserved());
            try(var files=Files.walk(directory.resolve("checkpoints"))){var snapshot=files.filter(p->p.toString().endsWith(".snapshot")).findFirst().orElseThrow();assertTrue(Files.readString(snapshot).contains("\"lastObserved\":101"));}
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(3,store.journalSequence());assertEquals(103,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    private long floor()throws Exception{return com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("checkpoint-floor.json"))).getAsJsonObject().getAsJsonObject("payload").get("sequence").getAsLong();}
    @Test void asynchronousCheckpointBacklogStopsBeforeThirdSealedSegment()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
        try(var store=new FileEncounterStore(directory,b->{},b->{},b->{if(b==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED&&once.compareAndSet(false,true)){entered.countDown();latch(release);}});var pool=Executors.newFixedThreadPool(3)){
            var runtime=runtime(store);done(hit(runtime,101).durable());var cp1=pool.submit(store::checkpoint);latch(entered);
            try{done(hit(runtime,102).durable());var cp2=pool.submit(store::checkpoint);awaitSegments(3);done(hit(runtime,103).durable());var cp3=pool.submit(store::checkpoint);
                assertEquals(3,segments().size());assertFalse(cp3.isDone());assertEquals(2,store.timings().grouping().get("maxCheckpointBacklog"));release.countDown();cp1.get(5,TimeUnit.SECONDS);cp2.get(5,TimeUnit.SECONDS);cp3.get(5,TimeUnit.SECONDS);
            }finally{release.countDown();}
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(3,store.journalSequence());assertEquals(103,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
    private void awaitSegments(int count)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(segments().size()!=count&&System.nanoTime()<deadline)Thread.yield();assertEquals(count,segments().size());}
    @ParameterizedTest @EnumSource(value=FileEncounterStore.JournalBoundary.class,names={"AFTER_CHECKPOINT_FILE","AFTER_CHECKPOINT_POINTER","AFTER_CHECKPOINTS","AFTER_ROTATION","AFTER_FLOOR"})
    void checkpointPublicationFailureRetainsRecoverableForcedGroups(FileEncounterStore.JournalBoundary boundary)throws Exception{
        try(var store=new FileEncounterStore(directory,b->{},b->{if(b==boundary)throw new IllegalStateException("checkpoint failure");})){
            done(submit(store,frames(store,64)));assertThrows(IllegalStateException.class,store::checkpoint);assertFalse(segments().isEmpty());assertThrows(IllegalStateException.class,store::pendingCount);
        }
        try(var store=new FileEncounterStore(directory)){assertEquals(64,store.journalSequence());assertEquals(164,store.load(WORLD,ENEMY).orElseThrow().lastObserved());}
    }
}
