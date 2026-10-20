package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.*;
import com.inigmasgames.hytalerpg.combat.balance.*;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real compiler/kernel/scheduler/registry with an explicitly engine-neutral world port. */
class Stage07MultiplicityTest {
    @Test void barrageVolleyHomingAndPotencyCreateNinePaidOnceShotsAtAuthoredOffsets() {
        var h=new Harness("volley","barrage","homing","potency");assertTrue(h.cast().committed());
        assertEquals(3,h.plans.size());double paid=h.mana;double cooldown=h.kernel.cooldowns().remaining(h.owner,"fire_bolt");
        assertEquals(h.profile.resourceCost(),200-paid,1e-12);h.advance(.179);assertEquals(3,h.plans.size());
        h.origin=new Vec3(2,0,0);h.advance(.18);assertEquals(6,h.plans.size());h.advance(.359);assertEquals(6,h.plans.size());
        h.advance(.36);assertEquals(9,h.plans.size());assertEquals(0,h.service.pendingReleaseCount());
        assertEquals(List.of(0d,.18,.36),h.times);assertEquals(paid,h.mana);assertEquals(1,h.resourceWrites);
        assertTrue(h.kernel.cooldowns().remaining(h.owner,"fire_bolt")<=cooldown);
        assertEquals(1,h.trace.records.stream().filter(r->r.eventType()==RpgTraceEventType.SKILL_COMMITTED).count());
        var first=h.released.getFirst();
        for(var c:h.released) {
            assertEquals(first.rootCastId(),c.rootCastId());assertEquals(first.skillInstanceId(),c.skillInstanceId());
            assertEquals(first.request().correlationId(),c.request().correlationId());assertSame(first.snapshot(),c.snapshot());
            assertEquals(first.target(),c.target());assertEquals(.75*.6*.9*1.15,c.snapshot().modifiers().factor(),1e-12);
        }
        assertEquals(9,h.plans.stream().map(ProjectileExecutionPlan::projectileInstanceId).distinct().count());
        assertEquals(9,h.registry.spent(h.owner,first.rootCastId()));
        assertEquals(Vec3.ZERO,h.plans.getFirst().origin());assertEquals(h.origin,h.plans.get(3).origin());
        assertEquals(0,h.plans.getFirst().generation());assertEquals(1,h.plans.get(3).generation());
    }
    @Test void volleySpreadPreservesElevationAndSharesSnapshotWithoutExtraPerProjectilePayment() {
        var h=new Harness("volley");h.cast();assertEquals(3,h.plans.size());
        var center=h.plans.get(1).velocity().normalized();
        assertTrue(ProjectileContinuation.yaw(center,-12).distanceSquared(h.plans.get(0).velocity().normalized())<1e-24);
        assertTrue(ProjectileContinuation.yaw(center,12).distanceSquared(h.plans.get(2).velocity().normalized())<1e-24);
        assertEquals(.75,h.plans.getFirst().snapshot().modifiers().factor(),1e-12);assertEquals(1,h.resourceWrites);
        h.advance(5);assertEquals(3,h.plans.size());assertEquals(0,h.service.pendingReleaseCount());
    }
    @Test void batchPromisesSurviveEarlyPrimaryTerminationAndAreFullyDrained() {
        var h=new Harness("volley","barrage");h.cast();var root=h.released.getFirst().rootCastId();
        h.endAll();assertEquals(0,h.registry.size());assertEquals(1,h.registry.rootCount());
        h.advance(.18);h.endAll();assertEquals(1,h.registry.rootCount());h.advance(.36);
        assertEquals(9,h.registry.spent(h.owner,root));h.endAll();assertEquals(0,h.registry.rootCount());
    }
    @Test void fullNineShotCapacityIsRejectedBeforeAnyResourceOrCooldownCommit() {
        var h=new Harness("volley","barrage");var seed=ProjectileExecutionPlan.generationZero(Stage07ContinuationTest.context(h.owner),h.owner,
                Vec3.ZERO,Vec3.FORWARD,"fixture",24,0);
        for(int i=0;i<16;i++)h.registry.register(new ProjectileInstance(Stage07ContinuationTest.copyPlan(seed,"occupied-"+i,"p-"+i)));
        assertEquals("OWNER_PROJECTILE_BUDGET",h.cast().code());assertEquals(200,h.mana);assertEquals(0,h.resourceWrites);
        assertEquals(0,h.kernel.cooldowns().remaining(h.owner,"fire_bolt"));assertTrue(h.plans.isEmpty());assertEquals(16,h.registry.size());
    }
    @Test void invalidLaterOriginOrEquipmentCancelsRemainingBatchesWithoutRefundOrReplay() {
        var h=new Harness("volley","barrage");h.cast();double paid=h.mana;h.releaseValid=false;h.advance(.18);
        assertEquals(3,h.plans.size());assertEquals(0,h.service.pendingReleaseCount());h.releaseValid=true;h.advance(2);
        assertEquals(3,h.plans.size());assertEquals(paid,h.mana);h.endAll();assertEquals(0,h.registry.rootCount());
    }
    @Test void deathLogoutAndWorldDrainClearScheduledBatchesAndPendingOnlyRoots() {
        for(String reason:List.of("DEATH","LOGOUT","WORLD_DRAIN")) {
            var h=new Harness("barrage","volley");h.cast();double paid=h.mana;h.endAll();
            h.service.cancel(h.owner,reason);h.registry.removeOwnedBy(h.owner);h.advance(3);
            assertEquals(3,h.plans.size());assertEquals(paid,h.mana);assertEquals(0,h.registry.rootCount());assertEquals(0,h.service.pendingReleaseCount());
        }
    }
    @Test void lateWorldTickDrainsOnlyTheTwoDueBatchesAndCannotRecursivelyRepeat() {
        var h=new Harness("barrage");h.cast();h.advance(.9);assertEquals(3,h.plans.size());
        assertEquals(List.of(0,1,2),h.released.stream().map(SkillExecutionContext::barrageBatch).toList());
        h.advance(1);h.advance(2);assertEquals(3,h.plans.size());assertEquals(1,h.resourceWrites);
        assertThrows(IllegalStateException.class,()->h.released.getFirst().barrageCopy(3));
    }
    @Test void skillDelayMovesBarrageEpochButDoesNotAlterItsSpacingOrPayAgain() {
        var h=new Harness("skill_delay","barrage","volley");assertEquals("RELEASE_PENDING",h.cast().code());
        h.advance(1.99);assertTrue(h.plans.isEmpty());h.advance(2);assertEquals(3,h.plans.size());
        h.advance(2.18);h.advance(2.36);assertEquals(9,h.plans.size());assertEquals(List.of(2d,2.18,2.36),h.times);
        assertEquals(1.35*.6*.75,h.released.getFirst().snapshot().modifiers().factor(),1e-12);assertEquals(1,h.resourceWrites);
    }
    @Test void echoVolleyHasSixCarriersAndExclusiveBarrageIsRejectedByCompiler() {
        var h=new Harness("echo","volley");h.cast();h.advance(.45);assertEquals(6,h.plans.size());
        assertEquals(.75,h.plans.getFirst().snapshot().modifiers().factor(),1e-12);
        assertEquals(.75*.7,h.plans.getLast().snapshot().modifiers().factor(),1e-12);assertEquals(1,h.resourceWrites);
        h.bundle.service().equipPassive(h.owner,PassiveSlot.PASSIVE03,new PassiveId("barrage"));
        assertFalse(h.bundle.service().link(h.owner,LinkNodeId.PASSIVE03,LinkNodeId.SKILL01).success());
    }
    @Test void volleyForkCombinationReservesWholeBatchesAndNeverReplenishesPierce() {
        var h=new Harness("volley","barrage","piercing","fork");h.cast();
        var original=h.registry.ownedBy(h.owner).getFirst();var continuation=new ProjectileContinuation(h.registry);
        for(int i=1;i<=3;i++) {
            var point=new Vec3(0,0,i);original.observe(.02,point);original.acceptTarget("target-"+i);
            var result=continuation.afterEnemy(original,point,Vec3.ZERO,List.of(),i);
            if(i<3)assertEquals(ProjectileContinuation.Action.PIERCE,result.action());
            else {assertEquals(2,result.children().size());assertEquals(11,h.registry.spent(h.owner,original.plan().rootCastId()));
                for(var child:result.children()) {assertEquals(0,child.remaining("PIERCE"));assertEquals(0,child.remaining("FORK"));assertTrue(child.previouslyHit("target-3"));}}
        }
    }
    static final class Harness implements SkillExecutionPort,NativeResourcePort {
        final UUID owner=UUID.randomUUID();final AtomicLong clock=new AtomicLong();
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final Stage01BTestSupport.RecordingTracer trace=(Stage01BTestSupport.RecordingTracer)bundle.tracer();
        final RpgCombatKernel kernel=new RpgCombatKernel(CombatBalanceProfile.loadCanonical(),new CriticalRoller(()->1));
        final ProjectileLifecycleRegistry registry=new ProjectileLifecycleRegistry();final SkillExecutionService service;final Stage04SkillProfile profile;
        final List<ProjectileExecutionPlan> plans=new ArrayList<>();final List<SkillExecutionContext> released=new ArrayList<>();final List<Double> times=new ArrayList<>();
        double mana=200;int resourceWrites;boolean releaseValid=true;Vec3 origin=Vec3.ZERO;
        Harness(String...passives) {
            bundle.service().equipSkill(owner,SkillSlot.SKILL01,new SkillId("fire_bolt"));
            for(int i=0;i<passives.length;i++) {var slot=PassiveSlot.values()[i];bundle.service().equipPassive(owner,slot,new PassiveId(passives[i]));
                assertTrue(bundle.service().link(owner,LinkNodeId.valueOf(slot.name()),LinkNodeId.SKILL01).success());}
            var profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());profile=profiles.require("fire_bolt");
            service=new SkillExecutionService(bundle.service(),profiles,kernel,SkillExecutorRegistry.stage04(),new SkillInstanceLifecycle(),trace,clock::get);
        }
        SkillExecutionResult cast(){return service.request(new SkillExecutionRequest(owner,SkillSlot.SKILL01,"fixture",1,"test-correlation",Vec3.FORWARD),this);}
        void advance(double seconds){clock.set(Math.round(seconds*1e9));service.tickScheduled(owner,this);}
        void endAll(){for(var p:registry.ownedBy(owner)){registry.remove(p);p.terminate("MAX_RANGE",p.flight().lastPosition());}}
        public boolean actorAliveAndUsable(){return true;}
        public Equipment equipment(){String kind=profile.allowedMainHandKinds().stream().sorted().findFirst().orElseThrow();return new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of(kind),20d,20d)),null);}
        public NativeResourcePort resources(){return this;}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){String result=registry.admission(owner,plan.projectileModifiers().rootLaunches(plan.executionModifiers().echoDelaySeconds()>0));return result.equals("PASS")?Validation.pass():Validation.reject(result);}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest r){return new CommittedTarget(new UUID(0,1),origin,new Vec3(0,0,10),Vec3.FORWARD,null);}
        public Validation validateRelease(SkillExecutionContext c){return releaseValid?Validation.pass():Validation.reject("COMMITTED_TARGET_INVALID");}
        public void abandonRelease(SkillExecutionContext c){registry.abandonLaunch(owner,c.rootCastId());}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){
            var batch=ProjectileExecutionPlan.launchBatch(c,owner,origin,c.target()==null?Vec3.FORWARD:c.target().point().subtract(origin).normalized(),"fixture",24,clock.get());
            registry.registerAll(batch.stream().map(ProjectileInstance::new).toList());plans.addAll(batch);released.add(c);times.add(clock.get()/1e9);
            return SkillExecutionResult.committed("PROJECTILE_STARTED",0,0);
        }
        public SkillExecutionResult executeStrike(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeMovement(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeReaction(SkillExecutionContext c){throw new AssertionError();}
        public double current(ResourceType type){return mana;}public double maximum(ResourceType type){return 200;}
        public void setCurrent(ResourceType type,double value){mana=value;resourceWrites++;}
    }
}
