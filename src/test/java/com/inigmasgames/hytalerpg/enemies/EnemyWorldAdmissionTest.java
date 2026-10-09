package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyWorldAdmissionTest {
    @Test void productionNativeReviewIncludesPreRootStagingEvenWithNoSavedBirth(){
        var world=UUID.randomUUID();var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(),List.of())),
                new EnemyPackCapacity(EnemyBalance.canonical()),true);
        gate.begin(world).toCompletableFuture().join();
        assertFalse(gate.admits(world));
        gate.rebindComplete(world,List.of());
        assertTrue(gate.admits(world));
    }
    @Test void emptyWorldOpensOnlyAfterRecoveryAndDuplicateLoadSharesReplay(){
        var world=UUID.randomUUID();var pending=new CompletableFuture<FileEncounterStore.EnemyWorldInventory>();
        var calls=new AtomicInteger();var gate=new EnemyWorldAdmission(id->{calls.incrementAndGet();return pending;},
                new EnemyPackCapacity(EnemyBalance.canonical()));
        var first=gate.begin(world);var second=gate.begin(world);
        assertEquals(1,calls.get());assertFalse(gate.admits(world));
        var reservation=new EnemyPackCapacity.Reservation(world,UUID.randomUUID(),UUID.randomUUID(),1,Vec3.ZERO);
        assertThrows(IllegalStateException.class,()->gate.reserve(reservation));
        pending.complete(new FileEncounterStore.EnemyWorldInventory(List.of(),List.of()));
        assertEquals(first.toCompletableFuture().join(),second.toCompletableFuture().join());
        assertTrue(gate.admits(world));
        assertTrue(gate.reserve(reservation).accepted());
        assertTrue(gate.releaseUnsealed(reservation));
        gate.failClosed(world);
        assertTrue(gate.failed(world));
        assertFalse(gate.admits(world));
        assertThrows(IllegalStateException.class,()->gate.reserve(reservation));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(world,List.of()));
    }

    @Test void savedBirthStaysDormantButBlocksFreshAdmissionUntilWholeRebind(){
        var birth=new EnemyBirthPersistenceTest().plan();var world=birth.world();
        var capacity=new EnemyPackCapacity(EnemyBalance.canonical());
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(birth.pack()))),capacity);
        gate.begin(world).toCompletableFuture().join();
        assertEquals(0,capacity.count(world));assertFalse(gate.admits(world));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(world,List.of()));
        assertFalse(gate.admits(world));
        gate.rebindComplete(world,List.of(birth.encounter()));assertTrue(gate.admits(world));
        assertEquals(0,capacity.count(world));
        assertThrows(IllegalStateException.class,()->gate.releaseTerminal(birth.pack()));
        assertEquals(0,capacity.count(world));
        assertFalse(gate.releaseTerminal(birth.pack().abort("QA_ABORT")));
        assertEquals(0,capacity.count(world));
    }

    @Test void suspendedDurablePackRebindsOneExactActorAndReleasesOnlyAfterLastLoadedMemberLeaves(){
        var birth=new EnemyBirthPersistenceTest().plan();var world=birth.world();
        var suspended=birth.pack().staged().publish().suspend();
        var capacity=new EnemyPackCapacity(EnemyBalance.canonical());
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(suspended))),capacity);
        gate.begin(world).toCompletableFuture().join();
        gate.rebindComplete(world,List.of(birth.encounter()));
        assertEquals(1,gate.status(world).dormantNonterminalProductionPacks());
        assertEquals(0,capacity.count(world));
        var first=birth.actors().getFirst().entityId();var second=birth.actors().getLast().entityId();
        gate.activatePublished(birth,suspended,List.of(first));
        assertEquals(1,capacity.count(world));
        gate.activatePublished(birth,suspended,List.of(second));
        assertEquals(1,capacity.count(world));
        assertEquals(0,gate.status(world).dormantNonterminalProductionPacks());
        gate.memberRemoved(birth,first,"UNLOAD");assertEquals(1,capacity.count(world));
        gate.beginSuspension(birth);
        gate.memberRemoved(birth,second,"UNLOAD");assertEquals(1,capacity.count(world));
        gate.finishSuspension(birth,suspended);assertEquals(0,capacity.count(world));
        assertEquals(1,gate.status(world).dormantNonterminalProductionPacks());
        gate.activatePublished(birth,suspended,List.of(second));
        assertEquals(1,capacity.count(world));
        gate.memberRemoved(birth,second,"REMOVE");assertEquals(0,capacity.count(world));
        assertEquals(1,gate.status(world).dormantNonterminalProductionPacks());
    }

    @Test void failedOrForeignRecoveryCannotOpenAdmission(){
        var world=UUID.randomUUID();
        var failure=new EnemyWorldAdmission(id->CompletableFuture.failedFuture(new IllegalStateException("disk")),
                new EnemyPackCapacity(EnemyBalance.canonical()));
        assertThrows(CompletionException.class,()->failure.begin(world).toCompletableFuture().join());
        assertFalse(failure.admits(world));
        var foreign=new EnemyBirthPersistenceTest().plan();
        var rejected=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(foreign),List.of(foreign.pack()))),
                new EnemyPackCapacity(EnemyBalance.canonical()));
        assertThrows(CompletionException.class,()->rejected.begin(world).toCompletableFuture().join());
        assertFalse(rejected.admits(world));
    }
    @Test void terminalHistoryDoesNotRequireRebindingOrOccupyCapacity(){
        var birth=new EnemyBirthPersistenceTest().plan();var world=birth.world();
        var capacity=new EnemyPackCapacity(EnemyBalance.canonical());
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(birth.pack().abort("QA_ABORT")))),capacity);
        gate.begin(world).toCompletableFuture().join();
        assertTrue(gate.admits(world));assertEquals(0,capacity.count(world));
        gate.rebindComplete(world,List.of());assertTrue(gate.admits(world));
    }
    @Test void compensatedOriginalsHoldFreshAdmissionUntilTheirNativeActorsAreRestored(){
        var root=new EnemyBirthPersistenceTest().root();var birth=root.plan();var world=root.world();
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(birth.pack().abort("BIRTH_COMPENSATED")),
                        List.of(),List.of(EnemyBirthCompensation.of(root)))),new EnemyPackCapacity(EnemyBalance.canonical()));
        gate.begin(world).toCompletableFuture().join();assertFalse(gate.admits(world));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(world,List.of()));
        gate.rebindComplete(world,List.of(birth.encounter()));assertTrue(gate.admits(world));
    }
    @Test void occupiedSuperUniqueAnchorRequiresRecoveryAndLiveAnchorNeedsItsPack(){
        var world=UUID.randomUUID();var template=SuperUniqueTemplates.canonical().requireTemplate("grimgor_the_ashen");
        var idle=new SuperUniqueAnchor(1,UUID.randomUUID(),world,DifficultyId.HELL,Vec3.ZERO,
                template.id(),template.revision(),"verified-placement",template.respawn(),1,0,
                SuperUniqueAnchor.State.IDLE,null,0,null);
        var reserved=idle.reserve(1000);
        var capacity=new EnemyPackCapacity(EnemyBalance.canonical());
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(),List.of(),List.of(reserved))),capacity);
        gate.begin(world).toCompletableFuture().join();assertFalse(gate.admits(world));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(world,List.of()));
        gate.rebindComplete(world,List.of(reserved.encounterId()));assertTrue(gate.admits(world));
        var invalid=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(),List.of(),List.of(reserved.published(reserved.encounterId())))),
                new EnemyPackCapacity(EnemyBalance.canonical()));
        assertThrows(CompletionException.class,()->invalid.begin(world).toCompletableFuture().join());
        assertFalse(invalid.admits(world));
    }
    @Test void liveAnchorResolvesItsPackByEncounterRatherThanPackId(){
        var fixture=new EnemyBirthPlannerTest();
        var seed=fixture.seedFor(EnemyRarity.UNIQUE,DifficultyId.HELL);
        var birth=fixture.planner.plan(fixture.request(seed,DifficultyId.HELL)).plan();
        assertNotNull(birth.pack());
        assertNotEquals(birth.encounter(),birth.pack().packId());
        var template=SuperUniqueTemplates.canonical().requireTemplate("grimgor_the_ashen");
        var live=new SuperUniqueAnchor(1,UUID.randomUUID(),birth.world(),DifficultyId.HELL,birth.pack().anchor(),
                template.id(),template.revision(),"verified-placement",template.respawn(),1,birth.generation(),
                SuperUniqueAnchor.State.LIVE,birth.encounter(),0,null);
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(birth.pack()),List.of(live))),
                new EnemyPackCapacity(EnemyBalance.canonical()));
        gate.begin(birth.world()).toCompletableFuture().join();
        assertFalse(gate.admits(birth.world()));
        gate.rebindComplete(birth.world(),List.of(birth.encounter()));
        assertTrue(gate.admits(birth.world()));
    }
}
