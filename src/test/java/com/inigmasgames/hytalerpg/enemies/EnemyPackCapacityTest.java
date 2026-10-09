package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.concurrent.*;
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
    @Test void recoveryCountsSuspendedPacksAndGrandfathersExistingReservations(){
        var planner=new EnemyBirthPlannerTest();var original=planner.planner.plan(planner.request(planner.seedFor(EnemyRarity.CHAMPION),com.inigmasgames.hytalerpg.difficulty.DifficultyId.NORMAL)).plan().pack();
        var suspended=original.staged().publish().suspend();capacity.restore(suspended.worldId(),List.of(suspended));
        assertEquals(1,capacity.count(suspended.worldId()));
        capacity.unload(suspended.worldId());
        var records=new ArrayList<EnemyPackRecord>();for(int i=0;i<4;i++)records.add(new EnemyPackRecord(1,EnemyBirthPlannerTest.id("recovery/"+i),original.worldId(),original.encounterId(),1,
                original.state(),null,Vec3.ZERO,original.birthRoster(),null,Set.of(),Map.of(),false,false,"recovery/"+i,null));
        capacity.restore(original.worldId(),records);assertEquals(4,capacity.count(original.worldId()));
        var fresh=new EnemyPackCapacity.Reservation(original.worldId(),UUID.randomUUID(),UUID.randomUUID(),2,Vec3.ZERO);
        assertEquals(EnemyPackCapacity.Gate.CELL_LIMIT,capacity.reserve(fresh).gate());
        capacity.unload(original.worldId());
        capacity.restore(original.worldId(),List.of(original.abort("native failure")));assertEquals(0,capacity.count(original.worldId()));
    }
}
