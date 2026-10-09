package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyDisplacementMergeTest {
    @Test void nativeImpulseAlwaysKeepsOwnershipWithoutConvertingForceOrAddingDistance(){
        for(double meters:new double[]{.5,.65,.8}){
            var request=EnemyDisplacementMerge.resolve(true,0,meters);assertTrue(request.preserveNativeImpulse());assertEquals(0,request.horizontalMeters());
            assertEquals(request,EnemyDisplacementMerge.resolve(true,2,meters));
        }
    }
    @Test void comparableDistanceRequestsDeliverOnlyTheirMaximumAndNoAddedVertical(){
        assertEquals(.8,EnemyDisplacementMerge.resolve(false,.5,.8).horizontalMeters());assertEquals(1,EnemyDisplacementMerge.resolve(false,1,.8).horizontalMeters());
        for(double meters:new double[]{.5,.65,.8}){
            var plan=AreaDisplacementPlanner.push(Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,meters,1,true,(p,d)->1,p->true);
            assertEquals(meters,plan.distance(),1e-12);assertEquals(0,plan.destination().y());assertEquals(meters,plan.destination().z(),1e-12);
        }
    }
    @Test void theExistingDistanceSolverClipsAtTerrainAndRejectsControlProtection(){
        var wall=AreaDisplacementPlanner.push(Vec3.ZERO,new Vec3(0,0,-1),Vec3.FORWARD,.5,1,true,
                (point,segment)->Math.clamp((.2-point.z())/segment.z(),0,1),p->true);
        assertEquals(.2,wall.distance(),1e-12);assertEquals("NATIVE_COLLISION",wall.reason());
        assertEquals(0,AreaDisplacementPlanner.push(Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,.8,0,true,(p,d)->1,p->true).distance());
        assertEquals(.4,AreaDisplacementPlanner.push(Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,.8,.5,true,(p,d)->1,p->true).distance(),1e-12);
        assertEquals(0,AreaDisplacementPlanner.push(Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,.8,1,true,(p,d)->1,p->true).distance());
    }
}
