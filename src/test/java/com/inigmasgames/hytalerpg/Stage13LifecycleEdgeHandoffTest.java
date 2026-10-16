package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Stage13LifecycleEdgeHandoffTest {
    @TempDir Path root;
    private EnemyRewardRegistry.Spawn spawn(UUID w,UUID e){return EnemyRewardRegistry.load().classify(w,e,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    private HytaleEncounterRewards.DamageObservation hit(UUID w,UUID e,UUID a){return new HytaleEncounterRewards.DamageObservation(w,e,a,100,90,100,10,101,"root","instance","correlation");}
    @Test void deliveryDoesNotStartWhileWorldRecoveryOwnsTheDurableStore()throws Exception{
        var bundle=Stage01BTestSupport.bundle();var clock=new AtomicLong();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(root.resolve("startup-recovery"));
            var rewards=new HytaleEncounterRewards(store,bundle.service(),ignored->{},RpgCombatKernel.createProduction(),clock::get)){
            var held=CompletableFuture.runAsync(()->store.withDeathCapacity(()->{
                entered.countDown();waitFor(release);return null;
            }));
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                var recovery=rewards.recoverEnemyWorld(UUID.randomUUID());
                assertFalse(recovery.toCompletableFuture().isDone());
                rewards.deliveryTick();
                var effects=(Map<?,?>)rewards.handoffMetrics().get("effects");
                assertEquals(0,effects.get("pendingCount"),"Startup recovery must not race the short delivery deadline");
                release.countDown();held.get(4,TimeUnit.SECONDS);recovery.toCompletableFuture().get(4,TimeUnit.SECONDS);
                clock.set(1_000_000_000L);rewards.deliveryTick();
                rewards.durableFrontier().toCompletableFuture().get(4,TimeUnit.SECONDS);
                assertEquals(0L,((Map<?,?>)rewards.handoffMetrics().get("effects")).get("failures"));
                assertEquals(false,((Map<?,?>)rewards.handoffMetrics().get("runtime")).get("uncertain"));
            }finally{release.countDown();}
        }finally{release.countDown();bundle.service().close();}
    }
    @Test void alreadyStartedDeliverySurvivesBoundedRecoveryContention()throws Exception{
        var bundle=Stage01BTestSupport.bundle();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(root.resolve("delivery-recovery-race"));
            var rewards=new HytaleEncounterRewards(store,bundle.service(),ignored->{},RpgCombatKernel.createProduction())){
            var held=CompletableFuture.runAsync(()->store.withDeathCapacity(()->{
                entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new AssertionError("Recovery lock not released");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return null;
            }));
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                rewards.deliveryTick();
                assertEquals(1,((Map<?,?>)rewards.handoffMetrics().get("effects")).get("pendingCount"));
                // The old five-second delivery deadline poisoned the durable owner here.
                assertFalse(release.await(6,TimeUnit.SECONDS));
                assertEquals(0L,((Map<?,?>)rewards.handoffMetrics().get("effects")).get("failures"));
                release.countDown();held.get(4,TimeUnit.SECONDS);
                rewards.durableFrontier().toCompletableFuture().get(4,TimeUnit.SECONDS);
                assertEquals(false,((Map<?,?>)rewards.handoffMetrics().get("runtime")).get("uncertain"));
            }finally{release.countDown();}
        }finally{release.countDown();bundle.service().close();}
    }
    @Test void twoWorldsShareOneEightAttemptOneSecondDeliveryBudget()throws Exception{
        var bundle=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();bundle.service().configureEarnedRewards(new FileEarnedRewardStore(root.resolve("rewards")));bundle.service().getLoadout(actor);
        var clock=new AtomicLong();
        try(var store=FileEncounterStore.durableV2(root.resolve("encounters"));var rewards=new HytaleEncounterRewards(store,bundle.service(),ignored->{},RpgCombatKernel.createProduction(),clock::get)){
            var worlds=List.of(UUID.randomUUID(),UUID.randomUUID());
            for(int i=0;i<10;i++){
                var w=worlds.get(i%2);var e=UUID.randomUUID();rewards.attachObserved(w,e,"Wolf_Black",Optional.of(spawn(w,e))).toCompletableFuture().get(3,TimeUnit.SECONDS);
                rewards.observeDamage(hit(w,e,actor),()->()->{});
                rewards.captureDeath(w,e,Vec3.ZERO,102,List.of(new EncounterContributions.Participant(actor,w,Vec3.ZERO,1,true,null)),"no-party");
            }
            rewards.durableFrontier().toCompletableFuture().get(4,TimeUnit.SECONDS);assertEquals(10,store.pendingCount());
            for(int worldTick=0;worldTick<120;worldTick++)rewards.deliveryTick();
            rewards.durableFrontier().toCompletableFuture().get(4,TimeUnit.SECONDS);assertEquals(8,bundle.service().getLoadout(actor).state().rewards.sequence());assertEquals(2,store.pendingCount());
            clock.set(1_000_000_000L);rewards.deliveryTick();rewards.durableFrontier().toCompletableFuture().get(4,TimeUnit.SECONDS);
            assertEquals(10,bundle.service().getLoadout(actor).state().rewards.sequence());assertEquals(0,store.pendingCount());
        }finally{bundle.service().close();}
    }
    @Test void exclusionIsImmediateIdempotentAndItsDurableReceiptOutlivesRemoval()throws Exception{
        var hold=new AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var bundle=Stage01BTestSupport.bundle();
        try(var store=FileEncounterStore.durableV2(root.resolve("exclude"),ignored->{},at->{if(hold.get()&&at==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();waitFor(release);}},false);
            var rewards=new HytaleEncounterRewards(store,bundle.service(),ignored->{},RpgCombatKernel.createProduction())){
            var w=UUID.randomUUID();var e=UUID.randomUUID();var a=UUID.randomUUID();rewards.attachObserved(w,e,"Wolf_Black",Optional.of(spawn(w,e))).toCompletableFuture().get(3,TimeUnit.SECONDS);
            hold.set(true);rewards.observeDamage(hit(w,e,a),()->()->{});assertTrue(entered.await(2,TimeUnit.SECONDS));
            try{
                var excluded=assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->rewards.invalidateConverted(w,e));
                assertSame(excluded,rewards.invalidateConverted(w,e));assertFalse(excluded.toCompletableFuture().isDone());
                assertFalse(rewards.observeDamage(hit(w,e,a),()->()->{fail("excluded mastery");}).provisional());
                rewards.captureDeath(w,e,Vec3.ZERO,102,List.of(),"excluded");rewards.detachObserved(w,e);
                release.countDown();excluded.toCompletableFuture().get(3,TimeUnit.SECONDS);rewards.durableFrontier().toCompletableFuture().get(3,TimeUnit.SECONDS);
                assertTrue(store.death(w,e).isEmpty());assertThrows(IllegalStateException.class,()->rewards.attachObserved(w,e,"Wolf_Black",Optional.of(spawn(w,e))));
            }finally{release.countDown();}
        }finally{release.countDown();bundle.service().close();}
    }
    @Test void capacityRejectsBeforeWatermarkMutationAndShutdownCanDrainOnlyOffOwner()throws Exception{
        var hold=new AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(root.resolve("capacity"),ignored->{},at->{if(hold.get()&&at==FileEncounterStore.GroupBoundary.BEFORE_FORCE){entered.countDown();waitFor(release);}},false)){
            var runtime=new PersistentEncounterRuntime(store,(id,reward)->{});var w=UUID.randomUUID();var e=UUID.randomUUID();var a=UUID.randomUUID();
            runtime.attachNative(w,e,"Wolf_Black",Optional.of(spawn(w,e))).toCompletableFuture().get(3,TimeUnit.SECONDS);hold.set(true);
            var receipts=new ArrayList<CompletionStage<Boolean>>();receipts.add(runtime.submitDamage(w,e,a,100,90,100,true,101).durable());assertTrue(entered.await(2,TimeUnit.SECONDS));
            try{
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{
                    for(int i=1;i<64;i++)receipts.add(runtime.submitDamage(w,e,a,100,90,100,true,101+i).durable());
                    assertThrows(FileEncounterStore.CapacityRejected.class,()->runtime.submitDamage(w,e,a,90,0,100,true,999));
                    runtime.detachNative(w,e);
                });
                var close=CompletableFuture.runAsync(runtime::close);assertFalse(close.isDone());release.countDown();close.get(4,TimeUnit.SECONDS);
                for(var receipt:receipts)assertTrue(receipt.toCompletableFuture().get(2,TimeUnit.SECONDS));assertEquals(164,store.load(w,e).orElseThrow().lastObserved());
            }finally{release.countDown();}
        }
    }
    private static void waitFor(CountDownLatch gate){try{if(!gate.await(4,TimeUnit.SECONDS))throw new AssertionError("test force not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
