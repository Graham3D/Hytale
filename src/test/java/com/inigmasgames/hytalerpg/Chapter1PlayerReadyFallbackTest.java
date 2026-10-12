package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.execution.hytale.HytalePlayerPersistenceReady;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Pre-connect completion must arm the production owner installer without a ready packet. */
class Chapter1PlayerReadyFallbackTest {
    @Test void preparedPlayerIsPendingBeforeWorldEntryAndOldConnectionCannotClearNewOne() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var bundle=Stage01BTestSupport.bundle();
        try(var loadouts=bundle.service();var ready=new HytalePlayerPersistenceReady(loadouts,
                (store,ref,player,entity,stats)->true)) {
            UUID id=UUID.randomUUID(), world=UUID.randomUUID();
            var first=new PlayerRef(null,id,"first","en-US",null,null);
            ready.preConnect(first,world).get(5,TimeUnit.SECONDS);
            assertEquals(1,ready.preparationStatus().get("ownerInstallPending"));
            var replacement=new PlayerRef(null,id,"second","en-US",null,null);
            ready.preConnect(replacement,world).get(5,TimeUnit.SECONDS);
            ready.detach(first);
            assertEquals(1,ready.preparationStatus().get("ownerInstallPending"));
            ready.detach(replacement);
            assertEquals(0,ready.preparationStatus().get("ownerInstallPending"));
        }
    }
}
