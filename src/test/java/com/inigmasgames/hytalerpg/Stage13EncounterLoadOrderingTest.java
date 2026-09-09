package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic worker ordering; not a connected native performance claim. */
class Stage13EncounterLoadOrderingTest {
    @TempDir Path directory;
    private EnemyRewardRegistry.Spawn spawn(UUID world,UUID enemy){return EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    private static void hold(CountDownLatch gate){try{if(!gate.await(4,TimeUnit.SECONDS))throw new AssertionError("Test gate was not released");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}

    @Test void queuedLoadDoesNotAcquireLaterUnrelatedReceiptAsPredecessor()throws Exception{queuedLoad(false);}
    @Test void queuedRoleMismatchExcludesWithoutAcquiringLaterUnrelatedReceipt()throws Exception{queuedLoad(true);}
    private void queuedLoad(boolean mismatch)throws Exception{
        var blockCreate=new AtomicBoolean();var blockAck=new AtomicBoolean();
        var createEntered=new CountDownLatch(1);var createRelease=new CountDownLatch(1);
        var ackEntered=new CountDownLatch(1);var ackRelease=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(directory,at->{if(blockCreate.get()&&at==FileEncounterStore.Boundary.AFTER_CONTEXT){createEntered.countDown();hold(createRelease);}},ignored->{},at->{if(blockAck.get()&&at==FileEncounterStore.GroupBoundary.BEFORE_ACKNOWLEDGEMENTS){ackEntered.countDown();hold(ackRelease);}},false);
            var runtime=new PersistentEncounterRuntime(store,(player,reward)->{})){
            var world=UUID.randomUUID();var writer=UUID.randomUUID();var loading=UUID.randomUUID();var blocker=UUID.randomUUID();var actor=UUID.randomUUID();
            store.create(spawn(world,loading));
            assertTrue(runtime.attachNative(world,writer,"Wolf_Black",Optional.of(spawn(world,writer))).toCompletableFuture().get(2,TimeUnit.SECONDS));
            blockCreate.set(true);
            var first=runtime.attachNative(world,blocker,"Wolf_Black",Optional.of(spawn(world,blocker)));
            try{
                assertTrue(createEntered.await(2,TimeUnit.SECONDS));
                var load=runtime.attachNative(world,loading,mismatch?"Changed_Role":"Wolf_Black",Optional.empty());
                blockAck.set(true);
                var later=runtime.submitDamage(world,writer,actor,100,90,100,true,101);
                assertFalse(load.toCompletableFuture().isDone());
                blockCreate.set(false);createRelease.countDown();
                assertTrue(ackEntered.await(2,TimeUnit.SECONDS));
                assertTrue(first.toCompletableFuture().get(1,TimeUnit.SECONDS));
                // The later physical write has happened, but its covering receipt is held.
                // Neither load nor its role-mismatch tombstone may wait on that new tail.
                assertEquals(!mismatch,load.toCompletableFuture().get(1,TimeUnit.SECONDS));
                assertFalse(later.durable().toCompletableFuture().isDone());
                assertFalse(runtime.unavailable());assertEquals(!mismatch,runtime.contains(world,loading));
                ackRelease.countDown();assertTrue(later.durable().toCompletableFuture().get(2,TimeUnit.SECONDS));
                assertEquals(mismatch,store.load(world,loading).orElseThrow().disqualified());
            }finally{blockCreate.set(false);createRelease.countDown();ackRelease.countDown();}
        }
    }

    @Test void loadStillWaitsForEarlierAcceptedContributionAndRestoresItsWatermark()throws Exception{
        var blockAck=new AtomicBoolean();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=FileEncounterStore.durableV2(directory,ignored->{},at->{if(blockAck.get()&&at==FileEncounterStore.GroupBoundary.BEFORE_ACKNOWLEDGEMENTS){entered.countDown();hold(release);}},false);
            var runtime=new PersistentEncounterRuntime(store,(player,reward)->{})){
            var world=UUID.randomUUID();var enemy=UUID.randomUUID();var actor=UUID.randomUUID();
            var ledger=new EncounterContributions();assertTrue(ledger.restore(store.create(spawn(world,enemy))));
            assertTrue(ledger.damage(world,enemy,actor,100,90,100,true,101));blockAck.set(true);
            try(var lease=store.reserveSubmission(world,List.of(enemy))){
                var earlier=lease.submit(List.of(ledger.snapshot(world,enemy)));
                try{
                    assertTrue(entered.await(2,TimeUnit.SECONDS));
                    var loaded=runtime.attachNative(world,enemy,"Wolf_Black",Optional.empty());
                    assertFalse(earlier.toCompletableFuture().isDone());assertFalse(loaded.toCompletableFuture().isDone());
                    blockAck.set(false);release.countDown();earlier.toCompletableFuture().get(2,TimeUnit.SECONDS);
                    assertTrue(loaded.toCompletableFuture().get(2,TimeUnit.SECONDS));
                    assertEquals(List.of(actor),runtime.provisionalContributors(world,enemy));
                    assertTrue(runtime.submitDamage(world,enemy,actor,90,80,100,true,102).durable().toCompletableFuture().get(2,TimeUnit.SECONDS));
                    var captured=runtime.prepareDeathNative(world,enemy,com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO,103).toCompletableFuture().get(2,TimeUnit.SECONDS).orElseThrow().snapshot();
                    assertEquals(101,captured.firstCombat());assertEquals(102,captured.lastObserved());
                    assertEquals(.8,captured.lowestHealthFraction(),1e-9);
                    assertEquals(captured,store.load(world,enemy).orElseThrow());
                }finally{blockAck.set(false);release.countDown();}
            }
        }
    }
}
