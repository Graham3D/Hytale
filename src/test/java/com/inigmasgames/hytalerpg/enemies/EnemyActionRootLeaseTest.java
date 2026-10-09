package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyActionRootLeaseTest {
    @Test void actionAcceptanceNeverWaitsForIoAndNeverReusesThePreviousBlock(){
        var world=UUID.randomUUID();var actor=UUID.randomUUID();
        var refill=new CompletableFuture<EnemyActionRootBlock>();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var lease=EnemyActionRootLease.open(world,actor,7,count->{
            assertEquals(64,count);
            return calls.getAndIncrement()==0?CompletableFuture.completedFuture(new EnemyActionRootBlock(world,actor,7,1,64)):refill;
        }).toCompletableFuture().join();
        try(lease){
            for(int i=1;i<=64;i++)assertEquals("me.action/"+world+"/"+actor+"/7/"+i,lease.issue());
            assertEquals(2,calls.get());
            assertThrows(IllegalStateException.class,lease::issue);
            refill.complete(new EnemyActionRootBlock(world,actor,7,65,128));
            assertEquals("me.action/"+world+"/"+actor+"/7/65",lease.issue());
        }
        assertThrows(IllegalStateException.class,lease::issue);
    }
    @Test void wrongGenerationCannotSupplyAnAcceptedActionRoot(){
        var world=UUID.randomUUID();var actor=UUID.randomUUID();
        assertThrows(CompletionException.class,()->EnemyActionRootLease.open(world,actor,3,
                count->CompletableFuture.completedFuture(new EnemyActionRootBlock(world,actor,4,1,64)))
                .toCompletableFuture().join());
    }
}
