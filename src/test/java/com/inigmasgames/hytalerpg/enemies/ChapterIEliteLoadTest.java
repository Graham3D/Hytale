package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.hytale.NativeBirthContinuation;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Overlapping birth acknowledgments under unchanged production limits; native rendering is owner QA. */
class ChapterIEliteLoadTest {
    @TempDir Path directory;
    private static final UUID WORLD=UUID.fromString("3d366b79-f367-419a-87b7-a8d53cde7ec0");
    private static final Gson JSON=new Gson();

    private EnemyBirthRoot root(int cell) {
        var template=new EnemyBirthPersistenceTest().root();
        var tree=JSON.toJsonTree(template).getAsJsonObject();
        tree.getAsJsonObject("plan").getAsJsonObject("pack").getAsJsonObject("anchor")
                .addProperty("x",cell*64);
        return JSON.fromJson(JSON.toJson(tree).replace(template.world().toString(),WORLD.toString()),EnemyBirthRoot.class);
    }

    private final class Pending implements NativeBirthContinuation.Steps<EnemyBirthRoot,EnemyBirthRoot> {
        final EnemyBirthRoot root;
        final FileEncounterStore store;
        final EnemyWorldAdmission admission;
        final CompletableFuture<Optional<EnemyBirthRoot>> acknowledgment=new CompletableFuture<>();
        final CompletableFuture<NativeBirthContinuation.Outcome> done=new CompletableFuture<>();
        int finished;
        Pending(EnemyBirthRoot root,FileEncounterStore store,EnemyWorldAdmission admission,Executor world) {
            this.root=root;this.store=store;this.admission=admission;
            var lifetime=admission.watch(WORLD,done);
            new NativeBirthContinuation<>(world,()->admission.current(WORLD,lifetime),this,done).start(acknowledgment);
        }
        public CompletionStage<EnemyBirthRoot> prepare(EnemyBirthRoot sealed) {
            assertEquals(root,store.enemyBirthRoot(WORLD,root.encounter()).orElseThrow());
            assertEquals(EnemyPackRecord.State.RESERVED,pack().state());
            return CompletableFuture.completedStage(sealed);
        }
        public void activate(EnemyBirthRoot prepared) {assertEquals(root,prepared);}
        public CompletionStage<Void> publish(EnemyBirthRoot sealed,EnemyBirthRoot prepared) {
            store.transitionEnemyPack(WORLD,root.plan().pack().packId(),EnemyPackRecord::staged);
            store.transitionEnemyPack(WORLD,root.plan().pack().packId(),EnemyPackRecord::publish);
            return CompletableFuture.completedStage(null);
        }
        public void finish(EnemyBirthRoot sealed,EnemyBirthRoot prepared) {
            admission.activatePublished(root.plan(),pack(),actors());
            admission.birthPhase(root.plan(),"ELITE_PUBLISHED",null);finished++;
        }
        public CompletionStage<Void> compensate(EnemyBirthRoot sealed,EnemyBirthRoot prepared,Throwable cause) {
            throw new AssertionError("Unexpected compensation",cause);
        }
        public void uncertain(EnemyBirthRoot sealed,EnemyBirthRoot prepared,String phase,Throwable cause) {
            throw new AssertionError(phase,cause);
        }
        EnemyPackRecord pack(){return store.enemyPack(WORLD,root.plan().pack().packId()).orElseThrow();}
        List<UUID> actors(){return root.plan().actors().stream().map(EnemyDescriptor::entityId).toList();}
    }

    @Test void overlappingBirthsRespectCellAndWorldLimitsThroughPublicationSuspensionAndRestart() {
        var retained=new ArrayList<EnemyBirthRoot>();
        try(var store=new FileEncounterStore(directory)) {
            var admission=new EnemyWorldAdmission(w->CompletableFuture.completedStage(store.recoverEnemyWorld(w)),
                    new EnemyPackCapacity(EnemyBalance.canonical()),true);
            admission.begin(WORLD).toCompletableFuture().join();
            assertFalse(admission.admits(WORLD));
            admission.rebindComplete(WORLD,List.of());
            var queue=new ArrayDeque<Runnable>();
            int cellDenied=0,worldDenied=0;
            for(int wave=0;wave<4;wave++) {
                var pending=new ArrayList<Pending>();
                // Six jobs per cell, four cells, then continuing native pressure while acknowledgments are withheld.
                for(int job=0;job<120;job++) {
                    var birth=root((job/6)%4);var lease=EnemyPackCapacity.Reservation.of(birth.plan().pack());
                    var gate=admission.reserve(lease).gate();
                    if(gate!=EnemyPackCapacity.Gate.RESERVED) {
                        if(gate==EnemyPackCapacity.Gate.CELL_LIMIT)cellDenied++;
                        else {assertEquals(EnemyPackCapacity.Gate.WORLD_LIMIT,gate);worldDenied++;}
                        assertTrue(store.enemyBirthRoot(WORLD,birth.encounter()).isEmpty());
                        continue;
                    }
                    assertEquals(EnemyPackCapacity.Gate.ALREADY_RESERVED,admission.reserve(lease).gate());
                    admission.birthSubmitted(birth.plan(),wave*120+job,1,"Trork_Warrior");
                    store.reserveEnemyBirthRoot(birth);
                    pending.add(new Pending(birth,store,admission,queue::add));
                }
                var before=admission.status(WORLD);
                assertEquals(12,pending.size());assertEquals(12,before.pendingTransactions());
                assertEquals(12,before.leases().pendingNewBirths());assertEquals(0,before.leases().loadedActiveProductionPacks());
                assertTrue(before.leases().activeBy64mCell().values().stream().allMatch(count->count==3));
                // Acknowledgments arrive in reverse order; keep loaded and still-pending packs overlapping.
                Collections.reverse(pending);
                for(int i=0;i<pending.size();i++) {
                    var p=pending.get(i);assertTrue(p.acknowledgment.complete(Optional.of(p.root)));
                    assertFalse(p.acknowledgment.complete(Optional.of(p.root)),"Duplicate acknowledgment cannot republish");
                    while(!queue.isEmpty())queue.remove().run();
                    assertEquals(NativeBirthContinuation.Outcome.PUBLISHED,p.done.join());assertEquals(1,p.finished);
                    assertEquals(12,admission.activePackReservations(WORLD));
                    assertEquals(i+1,admission.status(WORLD).leases().loadedActiveProductionPacks());
                    assertEquals(11-i,admission.status(WORLD).leases().pendingNewBirths());
                }
                assertEquals(0,admission.status(WORLD).pendingTransactions());
                for(var p:pending) {
                    admission.beginSuspension(p.root.plan());
                    for(var actor:p.actors()){
                        admission.memberRemoved(p.root.plan(),actor,"UNLOAD");
                        admission.memberRemoved(p.root.plan(),actor,"UNLOAD");
                    }
                    var suspended=store.transitionEnemyPack(WORLD,p.root.plan().pack().packId(),EnemyPackRecord::suspend);
                    admission.finishSuspension(p.root.plan(),suspended);retained.add(p.root);
                }
                assertEquals(0,admission.activePackReservations(WORLD));assertTrue(admission.admits(WORLD));
            }
            assertEquals(36,cellDenied);assertEquals(396,worldDenied);
            assertEquals(48,store.recoverEnemyWorld(WORLD).births().size());
            assertEquals(96,retained.stream().flatMap(r->r.plan().actors().stream()).map(EnemyDescriptor::entityId).distinct().count());
        }
        try(var reopened=new FileEncounterStore(directory)) {
            var admission=new EnemyWorldAdmission(w->CompletableFuture.completedStage(reopened.recoverEnemyWorld(w)),
                    new EnemyPackCapacity(EnemyBalance.canonical()),true);
            admission.begin(WORLD).toCompletableFuture().join();
            assertFalse(admission.admits(WORLD));assertEquals(0,admission.activePackReservations(WORLD));
            for(var root:retained) {
                assertEquals(root,reopened.enemyBirthRoot(WORLD,root.encounter()).orElseThrow());
                assertEquals(root.plan().pack().staged().publish().suspend(),
                        reopened.enemyPack(WORLD,root.plan().pack().packId()).orElseThrow());
            }
            admission.rebindComplete(WORLD,retained.stream().map(EnemyBirthRoot::encounter).toList());
            assertTrue(admission.admits(WORLD));assertEquals(0,admission.activePackReservations(WORLD));
        }
    }
}
