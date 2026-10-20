package com.inigmasgames.hytalerpg.progress;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Genuine halt: never closes/drains on the selected persistence boundary. */
public final class EncounterV2CrashProcess {
    public static void main(String[] args)throws Exception {
        boolean bootstrap=args[1].startsWith("BOOTSTRAP_");
        var target=FileEncounterStore.DurabilityBoundary.valueOf(bootstrap?args[1].substring(10):args[1]);
        var armed=new AtomicBoolean(bootstrap);
        try(var store=FileEncounterStore.durableV2(Path.of(args[0]),b->{if(armed.get()&&b==target)Runtime.getRuntime().halt(73);},b->{},true)){
            var world=new UUID(1,1);var enemy=new UUID(2,2);var actor=new UUID(3,3);
            var spawn=EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
            var ledger=new EncounterContributions();ledger.restore(store.create(spawn));
            for(int first=0;first<1024;first+=64){var values=new ArrayList<EncounterContributions.Snapshot>();for(int i=first;i<first+64;i++){if(!ledger.damage(world,enemy,actor,100,90,100,true,101+i))throw new AssertionError();values.add(ledger.snapshot(world,enemy));}
                try(var lease=store.reserveSubmission(world,Collections.nCopies(64,enemy))){lease.submit(values).toCompletableFuture().get(5,TimeUnit.SECONDS);}
            }
            store.awaitPreparation();armed.set(true);
            if(!ledger.damage(world,enemy,actor,100,90,100,true,1125))throw new AssertionError();
            try(var lease=store.reserveSubmission(world,List.of(enemy))){lease.submit(List.of(ledger.snapshot(world,enemy))).toCompletableFuture().get(5,TimeUnit.SECONDS);}
            store.awaitPreparation();store.awaitCheckpoints();
            throw new AssertionError("Halt boundary not reached: "+target);
        }
    }
}
