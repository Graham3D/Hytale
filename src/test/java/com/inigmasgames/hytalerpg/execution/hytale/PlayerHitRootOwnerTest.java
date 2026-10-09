package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayerHitRootOwnerTest {
    @TempDir Path directory;

    @Test void staleDisconnectCannotCloseAnewConnectionsDurableLease(){
        UUID world=UUID.randomUUID(),player=UUID.randomUUID();
        Object oldConnection=new Object(),newConnection=new Object();
        try(var store=new FileEncounterStore(directory);
            var owner=new PlayerHitRootOwner((w,p,count)->CompletableFuture.completedFuture(
                    store.reservePlayerHitRoots(w,p,count)))){
            owner.open(world,player,oldConnection).toCompletableFuture().join();
            String old=owner.issue(world,player);
            owner.open(world,player,newConnection).toCompletableFuture().join();
            owner.detach(player,oldConnection);
            String next=owner.issue(world,player);
            assertNotNull(next);
            assertNotEquals(old,next);
            owner.detach(player,newConnection);
            assertNull(owner.issue(world,player));
        }
    }
}
