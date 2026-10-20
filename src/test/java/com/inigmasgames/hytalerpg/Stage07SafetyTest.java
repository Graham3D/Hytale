package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Engine-neutral geometry/lifetime/budget evidence, not proof of native contacts or visible motion. */
class Stage07SafetyTest {
    private static final Bounds CARRIER=new Bounds(new Vec3(-.1,-.1,-.1),new Vec3(.1,.1,.1));
    @Test void sweepFindsEveryCloselyPackedBodyInDistanceOrder() {
        var entries=new ArrayList<Double>();
        for(double z:List.of(3.0,1.0,2.0))entries.add(ProjectileSweep.contact(Vec3.ZERO,new Vec3(0,0,10),CARRIER,box(0,0,z)).orElseThrow());
        entries.sort(Double::compareTo);
        for(int i=0;i<3;i++)assertEquals(.08+i*.10,entries.get(i),1e-12);
    }
    @Test void sweepUsesThreeDimensionalBoundsAndBothDirections() {
        assertTrue(ProjectileSweep.contact(Vec3.ZERO,new Vec3(0,0,10),CARRIER,box(0,2,5)).isEmpty());
        assertTrue(ProjectileSweep.contact(Vec3.ZERO,new Vec3(0,0,10),CARRIER,box(2,0,5)).isEmpty());
        assertEquals(.48,ProjectileSweep.contact(new Vec3(0,0,10),Vec3.ZERO,CARRIER,box(0,0,5)).orElseThrow(),1e-12);
        assertEquals(0,ProjectileSweep.contact(Vec3.ZERO,Vec3.ZERO,CARRIER,box(0,0,0)).orElseThrow());
        assertTrue(ProjectileSweep.contact(Vec3.ZERO,Vec3.ZERO,CARRIER,box(0,0,1)).isEmpty());
    }
    @Test void rangeClipsBeforeSweepSoPackedTargetsBeyondRemainingPathCannotHit() {
        var end=ProjectileSweep.limit(Vec3.ZERO,new Vec3(0,0,10),2);
        assertEquals(new Vec3(0,0,2),end);
        assertTrue(ProjectileSweep.contact(Vec3.ZERO,end,CARRIER,box(0,0,3)).isEmpty());
        assertTrue(ProjectileSweep.contact(Vec3.ZERO,end,CARRIER,box(0,0,1)).isPresent());
        assertEquals(Vec3.ZERO,ProjectileSweep.limit(Vec3.ZERO,Vec3.FORWARD,0));
        assertThrows(IllegalArgumentException.class,()->ProjectileSweep.limit(Vec3.ZERO,Vec3.FORWARD,Double.NaN));
    }
    @Test void returnCatchClipsAtFirstHalfMeterIntersectionNotAtNearestPointAfterCaster() {
        var from=new Vec3(0,0,5);var to=new Vec3(0,0,-5);
        double t=ProjectileSweep.catchFraction(from,to,Vec3.ZERO).orElseThrow();
        assertEquals(.45,t,1e-12);var caught=from.add(to.subtract(from).multiply(t));
        assertEquals(new Vec3(0,0,.5),caught);
        assertTrue(ProjectileSweep.contact(from,caught,CARRIER,box(0,0,-1)).isEmpty());
        assertTrue(ProjectileSweep.catchFraction(from,to,new Vec3(1,0,0)).isEmpty());
        assertEquals(0,ProjectileSweep.catchFraction(Vec3.ZERO,Vec3.FORWARD,Vec3.ZERO).orElseThrow());
        assertTrue(ProjectileSweep.catchFraction(from,from,Vec3.ZERO).isEmpty());
    }
    @Test void callbacksAndTicksShareOneMonotonicClockAcrossRedirects() {
        var p=new ProjectileInstance(plan(UUID.randomUUID(),"root","p"));long start=p.plan().spawnTimestampNanos();
        assertEquals(.1,p.sampleNativeClock(start+100_000_000).elapsed(),1e-12);
        p.redirect(new Vec3(1,0,0));assertEquals(.1,p.sampleNativeClock(start+100_000_000).elapsed(),1e-12);
        assertEquals(.1,p.sampleNativeClock(start+50_000_000).elapsed(),1e-12);
        assertTrue(p.sampleNativeClock(start+1_000_000_000).expired());
    }
    @Test void returnGetsOnlyItsOwnExplicitLifetimeAndClockContinuesWithoutResettingAncestorAge() {
        var p=new ProjectileInstance(plan(UUID.randomUUID(),"root","p","return"));long start=p.plan().spawnTimestampNanos();
        p.sampleNativeClock(start+500_000_000);p.observe(0,new Vec3(0,0,12));
        assertTrue(p.beginReturn(new Vec3(0,0,12),Vec3.ZERO));
        assertEquals(.5,p.totalSeconds(),1e-12);assertFalse(p.sampleNativeClock(start+1_500_000_000).expired());
        assertTrue(p.sampleNativeClock(start+1_600_000_000).expired());assertEquals(1.6,p.totalSeconds(),1e-12);
    }
    @Test void spentRootBudgetDoesNotResetWhenDerivedChildrenEnd() {
        var registry=new ProjectileLifecycleRegistry();var seed=plan(UUID.randomUUID(),"root","anchor");registry.register(new ProjectileInstance(seed));
        for(int i=1;i<48;i++) {
            var child=new ProjectileInstance(child(seed,"child-"+i,1));registry.register(child);registry.remove(child);
        }
        assertEquals(48,registry.spent(seed.ownerId(),seed.rootCastId()));
        assertEquals("ROOT_SPAWN_EFFECT_BUDGET",assertThrows(IllegalStateException.class,
                ()->registry.register(new ProjectileInstance(child(seed,"overflow",1)))).getMessage());
        assertEquals(1,registry.size());registry.removeOwnedBy(seed.ownerId());assertEquals(0,registry.rootCount());
    }
    @Test void terminatedChildCannotReuseItsIdentityWhileRootLivesAndGenerationFourIsRejected() {
        var registry=new ProjectileLifecycleRegistry();var seed=plan(UUID.randomUUID(),"root","anchor");registry.register(new ProjectileInstance(seed));
        var child=new ProjectileInstance(child(seed,"child",3));registry.register(child);registry.remove(child);
        assertThrows(IllegalStateException.class,()->registry.register(new ProjectileInstance(child(seed,"child",3))));
        assertThrows(IllegalStateException.class,()->registry.register(new ProjectileInstance(child(seed,"too-deep",4))));
        assertEquals(2,registry.spent(seed.ownerId(),seed.rootCastId()));
    }
    @Test void promisedEchoSlotsCountAgainstOwnerCapacityBeforeAnySecondLaunch() {
        var registry=new ProjectileLifecycleRegistry();UUID owner=UUID.randomUUID();var seed=plan(owner,"root","p","echo");
        for(int i=0;i<12;i++)registry.register(new ProjectileInstance(Stage07ContinuationTest.copyPlan(seed,"root-"+i,"p-"+i)));
        assertEquals(12,registry.size());assertEquals("OWNER_PROJECTILE_BUDGET",registry.admission(owner,1));
        registry.removeOwnedBy(owner);assertEquals("PASS",registry.admission(owner,24));assertEquals(0,registry.rootCount());
    }
    @Test void global512CapIncludesOtherOwnersAndNeverEvictsTheirProjectiles() {
        var registry=new ProjectileLifecycleRegistry();int next=0;
        for(int actor=0;actor<22;actor++) {
            UUID owner=UUID.randomUUID();var seed=plan(owner,"r","p");
            for(int slot=0;slot<24&&next<512;slot++,next++)registry.register(new ProjectileInstance(
                    Stage07ContinuationTest.copyPlan(seed,"r-"+next,"p-"+next)));
        }
        assertEquals(512,registry.size());assertEquals("GLOBAL_PROJECTILE_BUDGET",registry.admission(UUID.randomUUID(),1));
        assertEquals(512,registry.rootCount());
    }
    @Test void hitLedgersRemainBoundedAndDisposedInstancesCannotAcceptContacts() {
        var p=new ProjectileInstance(plan(UUID.randomUUID(),"root","p","return"));
        for(int i=0;i<256;i++)assertTrue(p.acceptTarget("npc-"+i));
        assertFalse(p.acceptTarget("overflow"));p.terminate("OWNER_CANCELLED",Vec3.ZERO);assertFalse(p.acceptTarget("after"));
    }
    private static Bounds box(double x,double y,double z) {return new Bounds(new Vec3(x-.1,y-.1,z-.1),new Vec3(x+.1,y+.1,z+.1));}
    private static ProjectileExecutionPlan plan(UUID owner,String root,String id,String... modifiers) {
        var context=Stage07ContinuationTest.context(owner,modifiers);
        return Stage07ContinuationTest.copyPlan(ProjectileExecutionPlan.generationZero(context,owner,Vec3.ZERO,Vec3.FORWARD,"fixture",24,0),root,id);
    }
    private static ProjectileExecutionPlan child(ProjectileExecutionPlan p,String id,int generation) {
        var budget=new HashMap<>(p.remainingContinuationBudgets());budget.put("IS_LAUNCH",0);
        return new ProjectileExecutionPlan(p.rootCastId(),p.skillInstanceId(),id,p.ownerId(),p.skillId(),p.compiledPlanHash(),p.snapshot(),generation,budget,
                0,p.remainingTriggeredSecondaries(),p.spawnTimestampNanos(),p.configId(),p.origin(),p.velocity(),p.radius(),p.maxDistance(),p.maxLifetimeSeconds());
    }
}
