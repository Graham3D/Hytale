package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.Stage06AreaRuntimeTest.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage06CohortCTest {
    @Test void vortexIntegratesDamageAndBoundedPullRequestsAtQuarterSecond() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("vortex");port.targets=List.of(target("inside",3,0));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        for(int i=1;i<=24;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(24,port.payloads.size());assertEquals(2.1,port.payloads.stream().mapToDouble(AreaWorldPort.Payload::coefficient).sum(),1e-12);
        assertTrue(port.payloads.stream().allMatch(p->p.pull()==.375 && p.pullCoreRadius()==1.5 && !p.pullBeforeDamage() && p.periodic()));
        assertEquals(0,runtime.size());
    }
    @Test void pullUsesNativeCollisionSupportControlAndNeverMovesVerticallyOrPastCore() {
        var start=new Vec3(0,4,5);var centre=new Vec3(0,0,0);
        assertEquals("AIRBORNE",AreaPullPlanner.plan(start,centre,3,0,1,false,(p,d)->1,p->true).reason());
        assertEquals("CONTROL_RESISTANT",AreaPullPlanner.plan(start,centre,3,0,0,true,(p,d)->1,p->true).reason());
        var elite=AreaPullPlanner.plan(start,centre,3,0,.5,true,(p,d)->1,p->true);assertEquals(1.5,elite.distance());assertEquals(4,elite.destination().y());
        var core=AreaPullPlanner.plan(new Vec3(0,4,2),centre,3,1.5,1,true,(p,d)->1,p->true);assertEquals(.5,core.distance());
        var wall=AreaPullPlanner.plan(start,centre,3,0,1,true,(p,d)->.5,p->true);assertEquals(.125,wall.distance());assertEquals("NATIVE_COLLISION",wall.reason());
        var cliff=AreaPullPlanner.plan(start,centre,3,0,1,true,(p,d)->1,p->p.z()>=4.5);assertEquals(.5,cliff.distance());assertEquals("NO_SUPPORTED_PATH",cliff.reason());
    }
    @Test void earthquakeHasFourDiscretePulsesNotFrameRateDependentDamage() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("earthquake");port.targets=List.of(target("inside",3,0));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);assertEquals(1,port.payloads.size());
        for(int i=1;i<=36;i++)runtime.tick(context.request().actorId(),i*.125,port);
        assertEquals(4,port.payloads.size());assertEquals(2.2,port.payloads.stream().mapToDouble(AreaWorldPort.Payload::coefficient).sum(),1e-12);
        assertTrue(port.payloads.stream().allMatch(p->p.status().equals("STAGGER") && p.statusSeconds()==.4 && !p.periodic()));
        assertEquals(0,runtime.size());
    }
    @Test void meteorUsesOneDisjointDamageTierAndSeparateBurnRadiusAfterFullWarning() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("meteor");
        port.targets=List.of(target("inner",2,0),target("inner-burn-only",2.5,0),target("outer",4,0));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        for(int i=1;i<24;i++)runtime.tick(context.request().actorId(),i*.05,port);
        assertTrue(port.payloads.isEmpty());assertEquals(12,port.descending.getFirst().y(),1e-9);
        assertEquals(1,port.descending.getLast().y(),1e-9);
        runtime.tick(context.request().actorId(),1.2,port);
        assertEquals(List.of(2.5,1.6,1.6),port.payloads.stream().map(AreaWorldPort.Payload::coefficient).toList());
        assertEquals(List.of(8d,8d,4d),port.payloads.stream().map(AreaWorldPort.Payload::statusSeconds).toList());
        assertEquals(0,runtime.size());
    }
    @Test void cometKeepsFiveAndThreeChillTiersDisjoint() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("comet");
        port.targets=List.of(target("inner",2,0),target("outer",3,0));runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.tick(context.request().actorId(),1.29,port);assertTrue(port.payloads.isEmpty());
        runtime.tick(context.request().actorId(),1.3,port);
        assertEquals(List.of(2.35,1.5),port.payloads.stream().map(AreaWorldPort.Payload::coefficient).toList());
        assertEquals(List.of(5,3),port.payloads.stream().map(AreaWorldPort.Payload::chillStacks).toList());
    }
    @Test void overheadRejectsRoofAndChangedGroundInsteadOfMovingWarning() {
        for(boolean roof:new boolean[]{true,false}) {
            var runtime=new AreaRuntime();var port=new FakePort();var context=context("meteor");port.targets=List.of(target("inner",1,0));
            runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
            port.roof=roof;port.ground=roof;runtime.tick(context.request().actorId(),1.2,port);
            assertTrue(port.payloads.isEmpty());assertEquals(0,runtime.size());
        }
    }
    @Test void avalanchePreservesAuthoredFirstHalfSecondAndAlternatesIceStoneWithRootCap() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("avalanche");port.targets=List.of(huge());
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.tick(context.request().actorId(),.25,port);runtime.tick(context.request().actorId(),.49,port);assertTrue(port.payloads.isEmpty());
        runtime.tick(context.request().actorId(),.5,port);assertEquals(1,port.payloads.size());
        for(int i=3;i<=24;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(3,port.payloads.size());assertEquals(List.of("COLD","EARTH","COLD"),port.payloads.stream().map(AreaWorldPort.Payload::element).toList());
        assertEquals(List.of("CHILL","STAGGER","CHILL"),port.payloads.stream().map(AreaWorldPort.Payload::status).toList());
        assertEquals(.5,port.payloads.get(1).statusSeconds());assertEquals(0,runtime.size());
    }
    @Test void voidHasFiveSubImpactCapAndOneIndependentFinalBlastWithPullBeforeDamage() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("void_cataclysm");port.targets=List.of(huge());
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);assertEquals(1,port.payloads.size());
        for(int i=1;i<=32;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(6,port.payloads.size());var finale=port.payloads.getLast();
        assertEquals(2,finale.coefficient());assertEquals(3,finale.pull());assertTrue(finale.pullBeforeDamage());
        assertEquals(8,finale.impactIndex());assertEquals(0,runtime.size());
        runtime.tick(context.request().actorId(),9,port);assertEquals(6,port.payloads.size());
    }
    @Test void voidFinalBlastCanHitAnEnemyMissedByEverySubImpactAndCoreAddsNoPacket() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("void_cataclysm");
        port.targets=List.of(target("edge",10,0));runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        for(int i=1;i<=32;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(1,port.payloads.size());assertEquals(2,port.payloads.getFirst().coefficient());
    }
    private static AreaWorldPort.Target huge() {
        return new AreaWorldPort.Target("huge",new AreaGeometry.Bounds(new Vec3(-11,0,-11),new Vec3(11,2,11)),false);
    }
}
