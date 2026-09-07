package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage07SteeringTest {
    @Test void forkInheritsLastBounceTimeAndRemainingRicochetCredits() {
        var p=projectile("ricochet","fork");var registry=new ProjectileLifecycleRegistry();registry.register(p);
        p.observe(.1,new Vec3(0,0,2));assertTrue(p.bounce(new Vec3(0,0,-1)));p.observe(.01,new Vec3(0,0,1.8));
        var fork=new ProjectileContinuation(registry).afterEnemy(p,new Vec3(0,0,1.8),Vec3.ZERO,List.of(),0);
        assertEquals(2,fork.children().size());
        for(var child:fork.children()) {assertEquals(1,child.remaining("RICOCHET"));assertFalse(child.bounceIntervalReady());
            child.observe(.04,child.flight().lastPosition());assertTrue(child.bounceIntervalReady());}
    }
    @Test void fourCohortBCompatibilityGatesRejectNonProjectileFamilies() {
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var gate=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        for(String id:List.of("ricochet","volley","barrage","homing")) {
            var passive=catalog.passive(new com.inigmasgames.hytalerpg.domain.PassiveId(id)).orElseThrow();
            assertTrue(gate.assess(catalog.skill(new com.inigmasgames.hytalerpg.domain.SkillId("fire_bolt")).orElseThrow(),passive).accepted());
            for(String skill:List.of("frost_nova","minor_heal","lightning_bolt"))assertFalse(gate.assess(
                    catalog.skill(new com.inigmasgames.hytalerpg.domain.SkillId(skill)).orElseThrow(),passive).accepted());
        }
    }
    @Test void twoRealNormalReflectionsPreserveSpeedThenReturnBeginsWithoutResettingCredits() {
        var p=projectile("ricochet","return");var registry=new ProjectileLifecycleRegistry();registry.register(p);var controller=new ProjectileContinuation(registry);
        p.observe(.1,new Vec3(0,0,2));var first=controller.afterTerrain(p,new Vec3(0,0,2),Vec3.ZERO,new Vec3(0,0,-1));
        assertEquals(ProjectileContinuation.Action.RICOCHET,first.action());assertEquals(new Vec3(0,0,-1),first.direction());assertEquals(1,p.remaining("RICOCHET"));
        p.observe(.05,new Vec3(0,0,1));var second=controller.afterTerrain(p,new Vec3(0,0,1),Vec3.ZERO,Vec3.FORWARD);
        assertEquals(ProjectileContinuation.Action.RICOCHET,second.action());assertEquals(1,second.direction().length());assertEquals(0,p.remaining("RICOCHET"));
        p.observe(.1,new Vec3(0,0,3));assertEquals(ProjectileContinuation.Action.RETURN,controller.afterTerrain(p,new Vec3(0,0,3),Vec3.ZERO,new Vec3(0,0,-1)).action());
        assertEquals(0,p.remaining("RICOCHET"));assertEquals(ProjectileContinuation.Action.TERMINATE,controller.afterTerrain(p,new Vec3(0,0,2),Vec3.ZERO,Vec3.FORWARD).action());
    }
    @Test void tooRapidBounceOrInvalidNormalFailsClosedWithoutEarlyReturnOrPhasing() {
        var p=projectile("ricochet","return");var controller=new ProjectileContinuation(new ProjectileLifecycleRegistry());
        p.observe(.1,Vec3.FORWARD);assertEquals(ProjectileContinuation.Action.RICOCHET,controller.afterTerrain(p,Vec3.FORWARD,Vec3.ZERO,new Vec3(0,0,-1)).action());
        p.observe(.049,new Vec3(0,0,.9));assertEquals("RICOCHET_MIN_INTERVAL",controller.afterTerrain(p,new Vec3(0,0,.9),Vec3.ZERO,Vec3.FORWARD).reason());
        assertFalse(p.returning());assertEquals(1,p.remaining("RICOCHET"));
        p=projectile("ricochet","return");assertEquals("INVALID_TERRAIN_NORMAL",controller.afterTerrain(p,Vec3.FORWARD,Vec3.ZERO,Vec3.ZERO).reason());
    }
    @Test void reflectionUsesObliqueNormalAndDoesNotConsumeEnemyChainCredits() {
        var p=projectile("ricochet","chain");Vec3 normal=new Vec3(-1,0,-1).normalized();assertTrue(p.bounce(normal));
        assertEquals(-1,p.direction().x(),1e-12);assertEquals(0,p.direction().z(),1e-12);assertEquals(2,p.remaining("CHAIN"));
        assertEquals(1,p.direction().length(),1e-12);assertEquals(24,p.plan().velocity().length());
    }
    @Test void homingReacquiresAtTenHertzWithinRangeConeAndLosUsingStableTieBreak() {
        var h=new ProjectileHoming();var calls=new AtomicInteger();
        var targets=List.of(target("occluded",0,1,false),target("behind",0,-1,true),target("outside-cone",4,1,true),
                target("too-far",0,8.01,true),target("b",1,4,true),target("a",-1,4,true));
        java.util.function.Supplier<List<ProjectileHoming.Target>> query=()->{calls.incrementAndGet();return targets;};
        java.util.function.Function<String,Optional<ProjectileHoming.Target>> live=id->targets.stream().filter(t->t.id().equals(id)).findFirst();
        var first=h.update(0,Vec3.ZERO,Vec3.FORWARD,query,live);assertEquals("a",first.targetId());assertEquals(Vec3.FORWARD,first.direction());
        h.update(.099,Vec3.ZERO,Vec3.FORWARD,query,live);assertEquals(1,calls.get());h.update(.1,Vec3.ZERO,Vec3.FORWARD,query,live);assertEquals(2,calls.get());
    }
    @Test void homingTurnIsCappedAt120DegreesPerSecondAndNeverTurnsThroughOcclusion() {
        var h=new ProjectileHoming();var target=target("one",4,4,true);var list=List.of(target);
        h.update(0,Vec3.ZERO,Vec3.FORWARD,()->list,id->Optional.of(target));
        var next=h.update(.05,Vec3.ZERO,Vec3.FORWARD,()->list,id->Optional.of(target));
        assertEquals(6,Math.toDegrees(Math.acos(next.direction().z())),1e-9);assertEquals(1,next.direction().length(),1e-12);
        var occluded=h.update(.06,Vec3.ZERO,next.direction(),()->list,id->Optional.of(target("one",4,4,false)));
        assertEquals(next.direction(),occluded.direction());assertNull(occluded.targetId());
    }
    @Test void antiparallelAndVerticalTurnsStayFiniteNormalizedAndAngleBounded() {
        for(Vec3 direction:List.of(Vec3.FORWARD,new Vec3(.2,1,.1).normalized())) {
            var turn=ProjectileHoming.turn(direction,direction.multiply(-1),Math.toRadians(12));assertEquals(1,turn.length(),1e-12);
            double dot=turn.x()*direction.x()+turn.y()*direction.y()+turn.z()*direction.z();assertEquals(12,Math.toDegrees(Math.acos(dot)),1e-9);
        }
        assertThrows(IllegalArgumentException.class,()->ProjectileHoming.turn(Vec3.FORWARD,Vec3.FORWARD,Double.NaN));
    }
    @Test void homingRejectsOverflowRatherThanPickingFromTruncatedTargets() {
        var h=new ProjectileHoming();var targets=new ArrayList<ProjectileHoming.Target>();for(int i=0;i<65;i++)targets.add(target("t"+i,0,1,true));
        assertThrows(IllegalStateException.class,()->h.update(0,Vec3.ZERO,Vec3.FORWARD,()->targets,id->Optional.empty()));
        assertEquals(Vec3.FORWARD,new ProjectileHoming().update(0,Vec3.ZERO,Vec3.FORWARD,List::of,id->Optional.empty()).direction());
    }
    private static ProjectileHoming.Target target(String id,double x,double z,boolean visible){return new ProjectileHoming.Target(id,new Vec3(x,0,z),visible);}
    private static ProjectileInstance projectile(String...modifiers){var context=Stage07ContinuationTest.context(UUID.randomUUID(),modifiers);
        return new ProjectileInstance(ProjectileExecutionPlan.generationZero(context,context.request().actorId(),Vec3.ZERO,Vec3.FORWARD,"fixture",24,0));}
}
