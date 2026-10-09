package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.hytale.NativeBirthContinuation;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fault injection through the production continuation, durable writer and actual admission/lease owners. */
class R245BirthTransactionTest {
    @TempDir Path directory;
    static class WorldQueue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();boolean reject;
        public void execute(Runnable action){if(reject)throw new RejectedExecutionException("world closed");tasks.add(action);}
        void drain(){while(!tasks.isEmpty())tasks.remove().run();}
    }
    class Run implements NativeBirthContinuation.Steps<EnemyBirthRoot,EnemyBirthRoot>,AutoCloseable {
        final EnemyBirthRoot root=new EnemyBirthPersistenceTest().root();
        final FileEncounterStore store;
        final EnemyWorldAdmission admission;
        final WorldQueue queue=new WorldQueue();
        final CompletableFuture<NativeBirthContinuation.Outcome> done=new CompletableFuture<>();
        final Object lifetime;
        final String failure;
        int restored,finished,activated,quarantined;
        Run(String failure){
            this.failure=failure;store=new FileEncounterStore(directory.resolve(UUID.randomUUID().toString()));
            admission=new EnemyWorldAdmission(w->CompletableFuture.completedFuture(store.recoverEnemyWorld(w)),new EnemyPackCapacity(EnemyBalance.canonical()));
            admission.begin(root.world()).toCompletableFuture().join();lifetime=admission.watch(root.world(),done);
            assertTrue(admission.reserve(EnemyPackCapacity.Reservation.of(root.plan().pack())).accepted());
            admission.birthSubmitted(root.plan(),42,1,"Trork_Warrior");
        }
        void start(){
            CompletionStage<Optional<EnemyBirthRoot>> start;
            if(failure.equals("DECLINE")){
                admission.releaseUnsealed(EnemyPackCapacity.Reservation.of(root.plan().pack()));
                admission.birthPhase(root.plan(),"BIRTH_DECLINED",null);restored++;
                start=CompletableFuture.completedStage(Optional.empty());
            }else{
                store.reserveEnemyBirthRoot(root);
                start=failure.equals("ROOT_ACK")?CompletableFuture.failedStage(new IllegalStateException("root acknowledgment lost"))
                        :CompletableFuture.completedStage(Optional.of(root));
            }
            new NativeBirthContinuation<>(queue,()->admission.current(root.world(),lifetime),this,done).start(start);
        }
        void fail(String phase){if(failure.equals(phase))throw new IllegalStateException("injected "+phase);}
        public CompletionStage<EnemyBirthRoot> prepare(EnemyBirthRoot value){
            fail("ATTACH");admission.birthPhase(root.plan(),"BIRTH_ATTACHMENT_READY",null);
            return failure.equals("ATTACH_ASYNC")||failure.equals("COMPENSATE_ACK")
                    ?CompletableFuture.failedStage(new IllegalStateException("attach failed")):CompletableFuture.completedStage(value);
        }
        public void activate(EnemyBirthRoot value){fail("ACTIVATE");activated++;}
        public CompletionStage<Void> publish(EnemyBirthRoot sealed,EnemyBirthRoot value){
            fail("PUBLISH_SUBMIT");store.transitionEnemyPack(root.world(),root.plan().pack().packId(),EnemyPackRecord::staged);
            fail("STAGED_ACK");store.transitionEnemyPack(root.world(),root.plan().pack().packId(),EnemyPackRecord::publish);
            admission.birthPhase(root.plan(),"BIRTH_PACK_PUBLISHED_DURABLE",null);
            return failure.equals("PUBLISH_ACK")?CompletableFuture.failedStage(new IllegalStateException("publish acknowledgment lost"))
                    :CompletableFuture.completedStage(null);
        }
        public void finish(EnemyBirthRoot sealed,EnemyBirthRoot value){
            fail("FINISH");var published=store.enemyPack(root.world(),root.plan().pack().packId()).orElseThrow();
            var actors=root.plan().actors().stream().map(EnemyDescriptor::entityId).toList();
            admission.activatePublished(root.plan(),published,actors);admission.birthPhase(root.plan(),"ELITE_PUBLISHED",null);finished++;
        }
        public CompletionStage<Void> compensate(EnemyBirthRoot value,EnemyBirthRoot prepared,Throwable cause){
            store.compensateEnemyBirth(root.world(),root.encounter());
            if(failure.equals("COMPENSATE_ACK"))return CompletableFuture.failedStage(new IllegalStateException("compensation acknowledgment lost"));
            admission.releaseTerminal(store.enemyPack(root.world(),root.plan().pack().packId()).orElseThrow());
            admission.birthPhase(root.plan(),"BIRTH_COMPENSATED",cause);restored++;
            return CompletableFuture.completedStage(null);
        }
        public void uncertain(EnemyBirthRoot sealed,EnemyBirthRoot prepared,String phase,Throwable cause){
            quarantined++;admission.failClosed(root.world(),phase,cause);
        }
        public void close(){store.close();}
    }
    @Test void definitePrepublicationFailuresCompensateOnceAndKeepAdmissionOpen(){
        for(String phase:List.of("DECLINE","ATTACH","ATTACH_ASYNC","ACTIVATE"))try(var run=new Run(phase)){
            run.start();run.queue.drain();assertFalse(run.done.isCompletedExceptionally(),phase);
            assertEquals(1,run.restored);assertEquals(0,run.finished);assertEquals(0,run.quarantined);
            assertTrue(run.admission.admits(run.root.world()));assertEquals(0,run.admission.activePackReservations(run.root.world()));
            assertEquals(0,run.admission.status(run.root.world()).pendingTransactions());
            run.queue.drain();assertEquals(1,run.restored);
        }
    }
    @Test void uncertainWritesAndInterruptedCommittedPublicationNeverFallBackOrFreeLease(){
        for(String phase:List.of("ROOT_ACK","PUBLISH_SUBMIT","STAGED_ACK","PUBLISH_ACK","FINISH","COMPENSATE_ACK"))try(var run=new Run(phase)){
            run.start();run.queue.drain();assertTrue(run.done.isCompletedExceptionally(),phase);
            assertEquals(0,run.restored);assertEquals(1,run.quarantined);assertFalse(run.admission.admits(run.root.world()));
            assertEquals(1,run.admission.activePackReservations(run.root.world()));
            assertEquals(run.root,run.store.enemyBirthRoot(run.root.world(),run.root.encounter()).orElseThrow());
            var inventory=run.store.recoverEnemyWorld(run.root.world());assertEquals(1,inventory.births().size());assertEquals(1,inventory.packs().size());
            if(phase.equals("FINISH")||phase.equals("PUBLISH_ACK")){
                var pack=inventory.packs().getFirst();assertEquals(EnemyPackRecord.State.RELEASED,pack.state());
                run.admission.worldUnload(run.root.world());run.admission.begin(run.root.world()).toCompletableFuture().join();
                var ids=run.root.plan().actors().stream().map(EnemyDescriptor::entityId).toList();
                run.admission.activatePublished(run.root.plan(),pack,ids);run.admission.activatePublished(run.root.plan(),pack,ids);
                run.admission.rebindComplete(run.root.world(),List.of(run.root.encounter()));
                assertEquals(1,run.admission.activePackReservations(run.root.world()));assertTrue(run.admission.admits(run.root.world()));
                for(var id:ids)assertTrue(run.store.death(run.root.world(),id).isEmpty());
            }
        }
    }
    @Test void worldReplacementCompletesPendingFutureAndOldQueuedWorkCannotCloseReplacement(){
        try(var run=new Run("")){
            run.start();run.admission.worldUnload(run.root.world());
            run.admission.begin(run.root.world()).toCompletableFuture().join();
            run.admission.rebindComplete(run.root.world(),List.of(run.root.encounter()));
            run.queue.drain();assertTrue(run.done.isCompletedExceptionally());assertEquals(0,run.activated);assertEquals(0,run.quarantined);
            assertTrue(run.admission.admits(run.root.world()));
        }
    }
    @Test void queueFailureHasAnOwnedExceptionalCompletionInsteadOfSilentlyHanging(){
        try(var run=new Run("")){run.queue.reject=true;run.start();assertTrue(run.done.isCompletedExceptionally());assertEquals(1,run.quarantined);}
    }
    @Test void oldChecksummedRootReadsWithoutRewritingAndStillRejectsUnknownFields() throws Exception {
        var root=new EnemyBirthPersistenceTest().root();var path=directory.resolve("legacy-envelope");
        try(var store=new FileEncounterStore(path)){store.reserveEnemyBirthRoot(root);}
        Path file;try(var files=java.nio.file.Files.walk(path.resolve("enemy-birth-roots"))){file=files.filter(f->f.toString().endsWith(".json")).findFirst().orElseThrow();}
        var gson=new com.google.gson.Gson();var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        json.getAsJsonObject("payload").remove("attachmentSpawns");
        json.addProperty("checksum",com.inigmasgames.hytalerpg.progress.RewardIntent.digest(gson.toJson(json.get("payload"))));
        java.nio.file.Files.writeString(file,gson.toJson(json));var before=java.nio.file.Files.readAllBytes(file);
        try(var store=new FileEncounterStore(path)){assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());}
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(file));
        json.getAsJsonObject("payload").addProperty("unapproved",true);
        json.addProperty("checksum",com.inigmasgames.hytalerpg.progress.RewardIntent.digest(gson.toJson(json.get("payload"))));java.nio.file.Files.writeString(file,gson.toJson(json));
        try(var store=new FileEncounterStore(path)){assertThrows(IllegalStateException.class,()->store.enemyBirthRoot(root.world(),root.encounter()));}
    }
    @Test void frozenAttachmentSourcesSurviveLegacyRootAndExplicitAllActorSnapshotReload(){
        var legacy=new EnemyBirthPersistenceTest().root();
        for(var actor:legacy.plan().actors()){
            var source=legacy.attachmentSpawn(actor);assertEquals(actor.entityId(),source.enemy());assertEquals(actor.entityId(),source.combat().enemyId());
            assertEquals(legacy.originalSpawns().getFirst().combat().maxHealth(),source.combat().maxHealth());
        }
        var root=new EnemyBirthRoot(legacy.plan(),legacy.originalSpawns(),legacy.plan().actors().stream().map(legacy::attachmentSpawn).toList());
        var path=directory.resolve("frozen-source");
        try(var store=new FileEncounterStore(path)){store.reserveEnemyBirthRoot(root);}
        try(var store=new FileEncounterStore(path)){
            var saved=store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow();assertEquals(root,saved);
            for(var actor:root.plan().actors())assertEquals(root.attachmentSpawn(actor),saved.attachmentSpawn(actor));
        }
        var json=new com.google.gson.Gson().toJsonTree(legacy).getAsJsonObject();json.remove("attachmentSpawns");
        var decoded=new com.google.gson.Gson().fromJson(json,EnemyBirthRoot.class);assertEquals(legacy,decoded);
    }
    @Test void thirtyTwoIndependentBirthsInSameWorldPublishConsecutivelyWithoutClosingAdmission(){
        var world=UUID.randomUUID();var gson=new com.google.gson.Gson();
        try(var store=new FileEncounterStore(directory.resolve("consecutive"))){
            var admission=new EnemyWorldAdmission(w->CompletableFuture.completedStage(store.recoverEnemyWorld(w)),new EnemyPackCapacity(EnemyBalance.canonical()));
            admission.begin(world).toCompletableFuture().join();
            for(int i=0;i<32;i++){
                var template=new EnemyBirthPersistenceTest().root();
                var root=gson.fromJson(gson.toJson(template).replace(template.world().toString(),world.toString()),EnemyBirthRoot.class);
                var birth=root.plan();assertTrue(admission.reserve(EnemyPackCapacity.Reservation.of(birth.pack())).accepted());
                admission.birthSubmitted(birth,i,1,"Trork_Warrior");store.reserveEnemyBirthRoot(root);
                var done=new CompletableFuture<NativeBirthContinuation.Outcome>();var token=admission.watch(world,done);
                new NativeBirthContinuation<EnemyBirthRoot,EnemyBirthRoot>(Runnable::run,()->admission.current(world,token),new NativeBirthContinuation.Steps<>(){
                    public CompletionStage<EnemyBirthRoot> prepare(EnemyBirthRoot b){return CompletableFuture.completedStage(b);}
                    public void activate(EnemyBirthRoot b){}
                    public CompletionStage<Void> publish(EnemyBirthRoot a,EnemyBirthRoot b){store.transitionEnemyPack(world,birth.pack().packId(),EnemyPackRecord::staged);store.transitionEnemyPack(world,birth.pack().packId(),EnemyPackRecord::publish);return CompletableFuture.completedStage(null);}
                    public void finish(EnemyBirthRoot a,EnemyBirthRoot b){admission.activatePublished(birth,store.enemyPack(world,birth.pack().packId()).orElseThrow(),birth.actors().stream().map(EnemyDescriptor::entityId).toList());admission.birthPhase(birth,"ELITE_PUBLISHED",null);}
                    public CompletionStage<Void> compensate(EnemyBirthRoot a,EnemyBirthRoot b,Throwable e){throw new AssertionError(e);}
                    public void uncertain(EnemyBirthRoot a,EnemyBirthRoot b,String phase,Throwable e){throw new AssertionError(phase,e);}
                },done).start(CompletableFuture.completedStage(Optional.of(root)));
                assertEquals(NativeBirthContinuation.Outcome.PUBLISHED,done.join());assertTrue(admission.admits(world));
                for(var actor:birth.actors())admission.memberRemoved(birth,actor.entityId(),"UNLOAD");
                assertEquals(0,admission.activePackReservations(world));
            }
            assertEquals(32,store.recoverEnemyWorld(world).births().size());assertTrue(admission.admits(world));
        }
    }
    @Test void consecutivePublicationsStayOpenAndUnloadReactivationKeepsOneIdentity(){
        for(int n=0;n<32;n++)try(var run=new Run("")){
            run.start();run.queue.drain();assertEquals(NativeBirthContinuation.Outcome.PUBLISHED,run.done.join());
            assertEquals(1,run.finished);assertTrue(run.admission.admits(run.root.world()));
            var birth=run.root.plan();var pack=run.store.enemyPack(run.root.world(),birth.pack().packId()).orElseThrow();
            var ids=birth.actors().stream().map(EnemyDescriptor::entityId).toList();
            run.admission.memberRemoved(birth,ids.getFirst(),"UNLOAD");assertEquals(1,run.admission.activePackReservations(run.root.world()));
            for(var id:ids)run.admission.memberRemoved(birth,id,"UNLOAD");assertEquals(0,run.admission.activePackReservations(run.root.world()));
            run.admission.activatePublished(birth,pack,ids);assertEquals(1,run.admission.activePackReservations(run.root.world()));
            assertEquals(birth,run.store.reserveEnemyBirthRoot(run.root));
        }
    }
}
