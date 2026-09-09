package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.input.NativeAbilityProjectionService;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the real projection service/native containers; not connected-client rendering proof. */
class Stage13NativeAbilityReadinessTest {
    static final class Repository implements RpgPlayerStateRepository {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger reads = new AtomicInteger();
        volatile boolean hold;
        public LoadResult load(UUID player) {
            reads.incrementAndGet();
            if (hold) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test deadline"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return new LoadResult(RpgPlayerState.create(player), false, false, RpgPlayerState.CURRENT_SCHEMA, List.of());
        }
        public void save(RpgPlayerState state) { }
    }
    private RpgLoadoutService loadouts(Repository repository) {
        var catalog = RpgCatalog.loadCanonical();
        var compatibility = new CompatibilityService();
        var graph = new RpgLinkGraphService(catalog, compatibility);
        var service = new RpgLoadoutService(catalog, repository, graph,
                new LinkCompiler(catalog, graph, compatibility), new OwnershipEntitlementPolicy(true), ignored -> {});
        service.enableNonblockingReads();
        return service;
    }
    @Test void tickCannotInstallEvenWhenPersistenceIsAlreadyReady() throws Exception {
        var repository = new Repository();
        try (var loadouts = loadouts(repository)) {
            var player = UUID.randomUUID(); loadouts.preload(player).toCompletableFuture().get(2, TimeUnit.SECONDS);
            var slots = new InventoryComponent.AbilitySlots(InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
            try (var projection = new NativeAbilityProjectionService(loadouts, new Stage04SkillProfiles(List.of()), ignored -> {}, loadouts::ready)) {
                projection.tick(player, slots);
                assertTrue(projection.status(player, true).contains("NO_ACTIVE_WORLD_SESSION"));
                projection.install(player, slots);
                assertTrue(projection.status(player, true).contains("skill01 -> EMPTY"));
                projection.detach(player, "TEST"); projection.tick(player, slots);
                assertTrue(projection.status(player, true).contains("NO_ACTIVE_WORLD_SESSION"));
            }
        }
    }
    @Test void coldJoinAndHeldLoadDoNotReadOrBlockWorldTick() throws Exception {
        var repository = new Repository(); repository.hold = true;
        try (var loadouts = loadouts(repository)) {
            var player = UUID.randomUUID(); var slots = new InventoryComponent.AbilitySlots(InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
            try (var projection = new NativeAbilityProjectionService(loadouts, new Stage04SkillProfiles(List.of()), ignored -> {}, loadouts::ready)) {
                projection.tick(player, slots); projection.install(player, slots); projection.onLoadoutMutation(player);
                assertEquals(0, repository.reads.get());
                var loaded = loadouts.preload(player).toCompletableFuture();
                try {
                    assertTrue(repository.entered.await(1, TimeUnit.SECONDS));
                    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                        for (int i = 0; i < 100; i++) { projection.tick(player, slots); projection.install(player, slots); }
                        assertTrue(projection.status(player, true).contains("NO_ACTIVE_WORLD_SESSION"));
                    });
                    assertFalse(loaded.isDone());
                } finally { repository.release.countDown(); }
                loaded.get(2, TimeUnit.SECONDS);
                projection.install(player, slots);
                assertTrue(projection.status(player, true).contains("skill02 -> EMPTY"));
                assertEquals(1, repository.reads.get());
            } finally { repository.release.countDown(); }
        }
    }
    @Test void readinessLossPausesExistingSessionAndContainerReplacementRepairsAfterReady() throws Exception {
        var repository = new Repository();
        try (var loadouts = loadouts(repository)) {
            var player = UUID.randomUUID(); loadouts.preload(player).toCompletableFuture().get(2, TimeUnit.SECONDS);
            var ready = new AtomicBoolean(true); var traces = new AtomicInteger();
            var original = new InventoryComponent.AbilitySlots(InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
            var replacement = new InventoryComponent.AbilitySlots(InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
            try (var projection = new NativeAbilityProjectionService(loadouts, new Stage04SkillProfiles(List.of()), ignored -> traces.incrementAndGet(), id -> ready.get() && loadouts.ready(id))) {
                projection.install(player, original); int installed = traces.get(); assertTrue(installed > 0);
                ready.set(false); projection.tick(player, replacement); projection.onLoadoutMutation(player);
                assertTrue(projection.status(player, true).contains("PLAYER_PERSISTENCE_NOT_READY"));
                assertEquals(installed, traces.get());
                ready.set(true); projection.tick(player, replacement);
                assertTrue(traces.get() > installed);
                assertTrue(projection.status(player, true).contains("@" + Integer.toHexString(System.identityHashCode(replacement.getInventory()))));
            }
        }
    }
}
