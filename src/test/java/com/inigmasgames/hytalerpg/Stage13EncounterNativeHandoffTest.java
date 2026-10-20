package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.diagnostics.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real production handoff helpers, not connected/native rendering or native tick-performance proof. */
class Stage13EncounterNativeHandoffTest {
    @TempDir Path temp;
    private EnemyRewardRegistry.Spawn spawn(UUID world,UUID enemy){return EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    private HytaleEncounterRewards.DamageObservation hit(UUID world,UUID enemy,UUID actor){return new HytaleEncounterRewards.DamageObservation(world,enemy,actor,100,90,100,10,101,"root","skill","corr");}
    private List<EncounterContributions.Participant> participants(UUID world,UUID actor){return List.of(new EncounterContributions.Participant(actor,world,Vec3.ZERO,1,true,null));}
    @Test void heldRealForceDoesNotBlockProductionDamageDeathDeliveryDetachOrPlayerRead() throws Exception {
        var hold=new AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var trace=new CopyOnWriteArrayList<RpgTraceRecord>();var bundle=Stage01BTestSupport.bundle(new Stage01BTestSupport.InMemoryRepository(),trace::add);
        var actor=UUID.randomUUID();var world=UUID.randomUUID();var enemy=UUID.randomUUID();var second=UUID.randomUUID();
        bundle.service().configureEarnedRewards(new FileEarnedRewardStore(temp.resolve("rewards")));bundle.service().getPresentationView(actor);bundle.service().enableNonblockingReads();
        try(var store=FileEncounterStore.durableV2(temp.resolve("encounters"),ignored->{},boundary->{
            if(hold.get()&&boundary==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();await(release);}
        },false);var rewards=new HytaleEncounterRewards(store,bundle.service(),trace::add,RpgCombatKernel.createProduction())){
            assertTrue(rewards.attachObserved(world,enemy,"Wolf_Black",Optional.of(spawn(world,enemy))).toCompletableFuture().get(2,TimeUnit.SECONDS));
            assertTrue(rewards.attachObserved(world,second,"Wolf_Black",Optional.of(spawn(world,second))).toCompletableFuture().get(2,TimeUnit.SECONDS));
            hold.set(true);var mastery=new AtomicInteger();
            var contribution=rewards.observeDamage(hit(world,enemy,actor),()->mastery::incrementAndGet);
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                    rewards.captureDeath(world,enemy,Vec3.ZERO,102,participants(world,actor),"fixture-at-death");
                    rewards.captureDeath(world,enemy,new Vec3(99,0,0),103,List.of(),"duplicate-must-not-replace");
                    rewards.deliveryTick();rewards.detachObserved(world,enemy);
                    assertTrue(rewards.observeDamage(hit(world,second,actor),()->()->{}).provisional());
                    assertEquals(1,bundle.service().characterLevel(actor));bundle.service().getPresentationView(actor);
                });
                assertFalse(contribution.durable().toCompletableFuture().isDone());assertEquals(0,mastery.get());
                assertFalse(trace.stream().anyMatch(e->e.eventType()==RpgTraceEventType.ENCOUNTER_DEATH_FROZEN));
            }finally{release.countDown();}
            assertTrue(contribution.durable().toCompletableFuture().get(2,TimeUnit.SECONDS));rewards.durableFrontier().toCompletableFuture().get(3,TimeUnit.SECONDS);
            assertEquals(1,mastery.get());var plan=store.death(world,enemy).orElseThrow();assertEquals(Vec3.ZERO,plan.deathPosition());assertEquals(102,plan.deathAtMillis());
            assertEquals(1,trace.stream().filter(e->e.eventType()==RpgTraceEventType.ENCOUNTER_DEATH_FROZEN).count());
            assertEquals(131,bundle.service().getPresentationView(actor).state().currentXp);
        }finally{release.countDown();bundle.service().close();}
    }
    @Test void heldContextAuthorityLoadRetainsDamageAndDeathAcrossRemoval() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var hold=new AtomicBoolean(true);
        try(var store=FileEncounterStore.durableV2(temp.resolve("load"),boundary->{if(hold.get()&&boundary==FileEncounterStore.Boundary.AFTER_CONTEXT){entered.countDown();await(release);}},ignored->{},ignored->{},false);
            var runtime=new PersistentEncounterRuntime(store,(player,reward)->{})){
            var world=UUID.randomUUID();var enemy=UUID.randomUUID();var actor=UUID.randomUUID();
            var loaded=runtime.attachNative(world,enemy,"Wolf_Black",Optional.of(spawn(world,enemy)));
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                var receipts=new ArrayList<CompletionStage<?>>();
                assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                    receipts.add(runtime.submitDamage(world,enemy,actor,100,90,100,true,101).durable());
                    receipts.add(runtime.captureMasteryEligibility(world,enemy,actor,1,101));
                    receipts.add(runtime.prepareDeathNative(world,enemy,Vec3.ZERO,102));runtime.detachNative(world,enemy);
                });
                assertTrue(receipts.stream().noneMatch(f->f.toCompletableFuture().isDone()));
                hold.set(false);release.countDown();
                assertTrue(loaded.toCompletableFuture().get(2,TimeUnit.SECONDS));assertEquals(true,receipts.get(0).toCompletableFuture().get(2,TimeUnit.SECONDS));
                assertEquals(true,receipts.get(1).toCompletableFuture().get(2,TimeUnit.SECONDS));
                @SuppressWarnings("unchecked") var death=(Optional<PersistentEncounterRuntime.PreparedDeath>)receipts.get(2).toCompletableFuture().get(2,TimeUnit.SECONDS);
                var plan=runtime.finishDeath(death.orElseThrow(),participants(world,actor));assertEquals(131,plan.shares().getFirst().xp());
            }finally{release.countDown();}
        }
    }
    private static void await(CountDownLatch gate){try{if(!gate.await(4,TimeUnit.SECONDS))throw new AssertionError("Held storage hook exceeded watchdog");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
