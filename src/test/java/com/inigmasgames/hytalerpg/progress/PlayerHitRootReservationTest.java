package com.inigmasgames.hytalerpg.progress;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayerHitRootReservationTest {
    @TempDir Path directory;

    @Test void rapidHitsOverlappingReservationsAndReloadNeverReuseAnIssuedIdentity(){
        UUID world=UUID.randomUUID(),player=UUID.randomUUID();
        String first,second,projectileA,projectileB;
        try(var store=new FileEncounterStore(directory)){
            var lease=DurableActionRootLease.open(world,player,count->CompletableFuture.completedFuture(
                    store.reservePlayerHitRoots(world,player,count))).toCompletableFuture().join();
            try(lease){
                first=lease.issue();second=lease.issue();
                projectileA=lease.issue();projectileB=lease.issue();
                assertEquals(4,Set.of(first,second,projectileA,projectileB).size());
            }
        }
        try(var reloaded=new FileEncounterStore(directory)){
            var next=reloaded.reservePlayerHitRoots(world,player,64);
            assertEquals(65,next.first());
            assertFalse(Set.of(first,second,projectileA,projectileB).contains(next.id(next.first())));
            assertThrows(IllegalArgumentException.class,()->next.id(next.last()+1));
            assertNotEquals(next.id(next.first()),reloaded.reservePlayerHitRoots(world,UUID.randomUUID(),1).id(1));
        }
    }

    @Test void exhaustedLeaseWaitsForTheSameWriterAndCannotMintAnUnreservedRoot(){
        UUID world=UUID.randomUUID(),player=UUID.randomUUID();
        var refill=new CompletableFuture<FileEncounterStore.PlayerHitRootBlock>();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var lease=DurableActionRootLease.open(world,player,count->calls.getAndIncrement()==0
                ?CompletableFuture.completedFuture(new FileEncounterStore.PlayerHitRootBlock(world,player,1,64))
                :refill).toCompletableFuture().join();
        try(lease){
            for(int i=0;i<64;i++)lease.issue();
            assertThrows(IllegalStateException.class,lease::issue);
            refill.complete(new FileEncounterStore.PlayerHitRootBlock(world,player,65,128));
            assertTrue(lease.issue().endsWith("/65"));
        }
    }
}
