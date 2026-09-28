package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearPlayerReadinessTest {
    static final class Repository implements RpgPlayerStateRepository {
        final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        final AtomicInteger reads=new AtomicInteger();
        public LoadResult load(UUID player) {
            reads.incrementAndGet();entered.countDown();
            try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test deadline");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            return new LoadResult(RpgPlayerState.create(player),false,false,RpgPlayerState.CURRENT_SCHEMA,List.of());
        }
        public void save(RpgPlayerState state) {}
    }
    @Test void coldJoinDefersEquipmentReadsAndResumesAfterTheRealAsyncLoad() throws Exception {
        var repository=new Repository();var catalog=RpgCatalog.loadCanonical();
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        try(var players=new RpgLoadoutService(catalog,repository,graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{})) {
            players.enableNonblockingReads();var equipment=new HytaleGearEquipment(players);var player=UUID.randomUUID();
            var calls=new AtomicInteger();
            java.util.function.Supplier<HytaleGearEquipment.View> read=()->{
                calls.incrementAndGet();var state=players.getPresentationView(player).state();
                return new HytaleGearEquipment.View(state.level,Map.of(),List.of(),GearRequirements.resolve(state.level,Map.of(),List.of()));
            };
            assertNull(equipment.whenReady(player,read));assertEquals(0,repository.reads.get());
            var load=players.preload(player).toCompletableFuture();
            try {
                assertTrue(repository.entered.await(2,TimeUnit.SECONDS));
                // This is the exact read that crashed the R085 world thread.
                assertEquals("PLAYER_PERSISTENCE_NOT_READY",assertThrows(IllegalStateException.class,()->players.getPresentationView(player)).getMessage());
                assertTimeoutPreemptively(Duration.ofSeconds(1),()->{
                    for(int i=0;i<1000;i++)assertNull(equipment.whenReady(player,read));
                });
                assertEquals(0,calls.get());assertFalse(load.isDone());
                assertEquals(GearAffixRuntime.Effects.NONE,equipment.publishedEffects(player));
            } finally {repository.release.countDown();}
            load.get(2,TimeUnit.SECONDS);
            assertNotNull(equipment.whenReady(player,read));assertEquals(1,calls.get());assertEquals(1,repository.reads.get());
        } finally {repository.release.countDown();}
    }
    @Test void readinessGuardDoesNotHideUnrelatedEquipmentFailures() throws Exception {
        var repository=new Repository();repository.release.countDown();var catalog=RpgCatalog.loadCanonical();
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        try(var players=new RpgLoadoutService(catalog,repository,graph,new LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{})) {
            var player=UUID.randomUUID();players.enableNonblockingReads();players.preload(player).toCompletableFuture().get(2,TimeUnit.SECONDS);
            var equipment=new HytaleGearEquipment(players);var failure=new IllegalArgumentException("bad binding");
            assertSame(failure,assertThrows(IllegalArgumentException.class,()->equipment.whenReady(player,()->{throw failure;})));
        }
    }
}
