package com.inigmasgames.hytalerpg.progress;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual abrupt process termination; no close, hooks, executor drain or mocked persistence. */
public final class EncounterGroupCrashProcess {
    public static void main(String[] args)throws Exception{
        var target=FileEncounterStore.GroupBoundary.valueOf(args[1]);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new AtomicBoolean();
        var cpEntered=new CountDownLatch(1);var cpRelease=new CountDownLatch(1);
        var store=new FileEncounterStore(Path.of(args[0]),b->{},b->{},b->{
            if(b==FileEncounterStore.GroupBoundary.BEFORE_DEQUEUE&&first.compareAndSet(false,true)){entered.countDown();await(release);}
            if(b==target){if(target==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED){cpEntered.countDown();await(cpRelease);}Runtime.getRuntime().halt(73);}
        });
        var world=new UUID(1,1);var enemy=new UUID(2,2);var other=new UUID(2,3);var actor=new UUID(3,3);
        var registry=EnemyRewardRegistry.load();var runtime=new PersistentEncounterRuntime(store,(id,reward)->{throw new AssertionError("No death");});
        for(var id:List.of(enemy,other)){
            var spawn=registry.classify(world,id,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
            if(!runtime.attach(world,id,"Wolf_Black",Optional.of(spawn)))throw new AssertionError("No spawn");
        }
        var receipts=new ArrayList<CompletionStage<Boolean>>();
        receipts.add(runtime.submitDamage(world,enemy,actor,100,90,100,true,101).durable());await(entered);
        for(int i=1;i<64;i++)receipts.add(runtime.submitDamage(world,enemy,actor,100,90,100,true,101+i).durable());
        if(target==FileEncounterStore.GroupBoundary.ANOTHER_GROUP_QUEUED)for(int i=0;i<64;i++)receipts.add(runtime.submitDamage(world,other,actor,100,90,100,true,101+i).durable());
        release.countDown();for(var receipt:receipts)if(!receipt.toCompletableFuture().get(10,TimeUnit.SECONDS))throw new AssertionError("Not accepted");
        if(target==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED){
            Thread.ofPlatform().start(store::checkpoint);await(cpEntered);
            if(!runtime.submitDamage(world,enemy,actor,100,90,100,true,165).durable().toCompletableFuture().get(10,TimeUnit.SECONDS))throw new AssertionError("No successor commit");
            cpRelease.countDown();Thread.sleep(10000);
        }
        throw new AssertionError("Boundary not reached");
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Child barrier timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}}
}
