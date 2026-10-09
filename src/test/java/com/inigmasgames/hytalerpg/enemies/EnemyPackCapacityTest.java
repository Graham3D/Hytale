package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyPackCapacityTest {
    final EnemyPackCapacity capacity=new EnemyPackCapacity(EnemyBalance.canonical());
    final UUID world=UUID.randomUUID();
    EnemyPackCapacity.Reservation reservation(int n,double x,double z){return new EnemyPackCapacity.Reservation(world,EnemyBirthPlannerTest.id("encounter/"+n),EnemyBirthPlannerTest.id("pack/"+n),1,new Vec3(x,0,z));}
    @Test void concurrentReservationsCountBeforePublicationAndCannotOversubscribeACell() throws Exception{
        try(var executor=Executors.newFixedThreadPool(8)){
            var tasks=new ArrayList<Callable<EnemyPackCapacity.Admission>>();for(int i=0;i<100;i++){int id=i;tasks.add(()->capacity.reserve(reservation(id,0,0)));}
            int admitted=0;for(var future:executor.invokeAll(tasks))if(future.get().accepted())admitted++;
            assertEquals(3,admitted);assertEquals(3,capacity.count(world));assertEquals(3,capacity.count(world,Vec3.ZERO));
        }
    }
    @Test void cellsUseHorizontalFloorCoordinatesAndWorldLimitsStillApply(){
        for(int i=0;i<3;i++)assertTrue(capacity.reserve(reservation(i,-.001,-.001)).accepted());
        assertEquals(EnemyPackCapacity.Gate.CELL_LIMIT,capacity.reserve(reservation(3,-64,-64)).gate());
        for(int i=3;i<12;i++)assertTrue(capacity.reserve(reservation(i,(i-3)/3*64,0)).accepted());
        assertEquals(EnemyPackCapacity.Gate.WORLD_LIMIT,capacity.reserve(reservation(12,256,0)).gate());
        var another=new EnemyPackCapacity.Reservation(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,Vec3.ZERO);
        assertTrue(capacity.reserve(another).accepted());
    }
    @Test void duplicateBirthAndStaleReleaseRemainSafeAcrossThousandCycles(){
        for(int i=0;i<1000;i++){
            var first=reservation(i,0,0);assertTrue(capacity.reserve(first).accepted());
            assertEquals(EnemyPackCapacity.Gate.ALREADY_RESERVED,capacity.reserve(first).gate());assertEquals(1,capacity.count(world));
            var newer=new EnemyPackCapacity.Reservation(world,first.encounter(),first.pack(),2,first.anchor());
            assertThrows(IllegalArgumentException.class,()->capacity.reserve(newer));
            assertTrue(capacity.release(first));assertTrue(capacity.reserve(newer).accepted());
            assertFalse(capacity.release(first));assertEquals(1,capacity.count(world));
            capacity.unload(world);assertEquals(0,capacity.count(world));
        }
    }
    @Test void recoveryKeepsSuspendedPacksDormantAndReactivatesExactLoadedMembers(){
        var planner=new EnemyBirthPlannerTest();var original=planner.planner.plan(planner.request(planner.seedFor(EnemyRarity.CHAMPION),com.inigmasgames.hytalerpg.difficulty.DifficultyId.NORMAL)).plan().pack();
        var suspended=original.staged().publish().suspend();capacity.restore(suspended.worldId(),List.of(suspended));
        assertEquals(0,capacity.count(suspended.worldId()));
        capacity.unload(suspended.worldId());
        var records=new ArrayList<EnemyPackRecord>();for(int i=0;i<4;i++)records.add(new EnemyPackRecord(1,EnemyBirthPlannerTest.id("recovery/"+i),original.worldId(),EnemyBirthPlannerTest.id("recovery-encounter/"+i),1,
                original.state(),null,Vec3.ZERO,original.birthRoster(),null,Set.of(),Map.of(),false,false,"recovery/"+i,null));
        capacity.restore(original.worldId(),records);assertEquals(0,capacity.count(original.worldId()));
        var fresh=new EnemyPackCapacity.Reservation(original.worldId(),UUID.randomUUID(),UUID.randomUUID(),2,Vec3.ZERO);
        assertTrue(capacity.reserve(fresh).accepted());
        assertEquals(1,capacity.count(original.worldId()));
        assertTrue(capacity.release(fresh));
        capacity.unload(original.worldId());
        capacity.restore(original.worldId(),List.of(original.abort("native failure")));assertEquals(0,capacity.count(original.worldId()));
    }
    @Test void sixMemberPackHasOneLeaseAndLastUnloadFreesOnlyVolatileCapacity(){
        var pack=reservation(100,0,0);var actors=new ArrayList<UUID>();
        for(int i=0;i<6;i++)actors.add(UUID.randomUUID());
        assertTrue(capacity.reserve(pack).accepted());
        assertTrue(capacity.activate(pack,actors));
        assertEquals(1,capacity.metrics(world).loadedActiveProductionPacks());
        assertEquals(0,capacity.metrics(world).pendingNewBirths());
        for(int i=0;i<5;i++){
            assertFalse(capacity.removeActor(pack,actors.get(i)));
            assertEquals(1,capacity.count(world));
        }
        capacity.beginTransition(pack);
        assertFalse(capacity.removeActor(pack,actors.get(5)));
        assertEquals(1,capacity.count(world));
        assertTrue(capacity.endTransition(pack));
        assertEquals(0,capacity.count(world));
        assertFalse(capacity.removeActor(pack,actors.get(5)));
        for(var actor:actors)capacity.activate(pack,List.of(actor));
        assertEquals(1,capacity.count(world));
        for(var actor:actors)capacity.removeActor(pack,actor);
        assertEquals(0,capacity.count(world));
    }
    @Test void incumbentRebindGrandfathersWorldAndCellOverflowAndBlocksNewBirths(){
        var incumbents=new ArrayList<EnemyPackCapacity.Reservation>();
        for(int i=0;i<12;i++){
            var pack=reservation(i,(i/3)*64,0);incumbents.add(pack);
            assertTrue(capacity.activate(pack,List.of(UUID.randomUUID())));
        }
        var returning=reservation(12,0,0);
        assertTrue(capacity.activate(returning,List.of(UUID.randomUUID())));
        var snapshot=capacity.metrics(world);
        assertEquals(13,snapshot.loadedActiveProductionPacks());
        assertEquals(1,snapshot.grandfatheredOverCapPacks());
        assertEquals(4,snapshot.activeBy64mCell().get("0,0"));
        assertEquals(EnemyPackCapacity.Gate.WORLD_LIMIT,capacity.reserve(reservation(13,512,0)).gate());
        capacity.release(incumbents.get(3));capacity.release(incumbents.get(4));
        assertEquals(EnemyPackCapacity.Gate.CELL_LIMIT,capacity.reserve(reservation(14,0,0)).gate());
        assertTrue(capacity.reserve(reservation(15,512,0)).accepted());
        assertEquals(1,capacity.metrics(world).pendingNewBirths());
    }
    @Test void r239RecoveryShapeDoesNotTurnNineSavedProductionPacksIntoNineSlots(){
        var fixture=new EnemyBirthPlannerTest();var base=fixture.planner.plan(fixture.request(
                fixture.seedFor(EnemyRarity.CHAMPION),com.inigmasgames.hytalerpg.difficulty.DifficultyId.NORMAL)).plan().pack();
        var saved=new ArrayList<EnemyPackRecord>();
        for(int i=0;i<12;i++)saved.add(new EnemyPackRecord(1,EnemyBirthPlannerTest.id("old-pack/"+i),world,
                EnemyBirthPlannerTest.id("old-encounter/"+i),1,
                i<11?EnemyPackRecord.State.SUSPENDED:base.state(),
                i<11?EnemyPackRecord.State.RELEASED:null,new Vec3(i*64,0,0),
                base.birthRoster(),null,Set.of(),Map.of(),i<11,false,"old-job/"+i,null));
        // The three historical QA records remain in the durable inventory, never the production lease index.
        capacity.restore(world,saved.subList(3,12));
        assertEquals(0,capacity.count(world));
        var loaded=EnemyPackCapacity.Reservation.of(saved.get(11));
        assertTrue(capacity.activate(loaded,List.of(UUID.randomUUID())));
        assertEquals(1,capacity.metrics(world).loadedActiveProductionPacks());
        assertEquals(0,capacity.metrics(world).pendingNewBirths());
        assertTrue(capacity.reserve(reservation(13,832,0)).accepted());
        assertEquals(2,capacity.count(world));
    }
    @Test void configReloadGrandfathersIncumbentsAndTraversalDoesNotAccumulateClaims(){
        var original=EnemyBalance.canonical().promotion();
        var current=new AtomicReference<>(original);
        var index=new EnemyPackCapacity(current::get);
        var first=reservation(1000,0,0);var second=reservation(1001,64,0);
        assertTrue(index.activate(first,List.of(UUID.randomUUID())));
        assertTrue(index.activate(second,List.of(UUID.randomUUID())));
        current.set(new EnemyBalance.Promotion(original.weightsByDifficulty(),original.championMinimum(),
                original.championMaximum(),original.minionMinimum(),original.minionMaximum(),
                original.maximumMembers(),1,original.cellLimit(),original.cellMeters(),original.leashMeters(),
                original.minimumDistance(),original.maximumDistance()));
        assertEquals(1,index.metrics(world).grandfatheredOverCapPacks());
        assertEquals(EnemyPackCapacity.Gate.WORLD_LIMIT,index.reserve(reservation(1002,128,0)).gate());
        index.release(first);index.release(second);
        for(int i=0;i<100;i++){
            var next=reservation(2000+i,i*64,0);var actor=UUID.randomUUID();
            assertTrue(index.reserve(next).accepted());index.activate(next,List.of(actor));
            assertTrue(index.removeActor(next,actor));
            assertEquals(0,index.count(world));
        }
    }
}
