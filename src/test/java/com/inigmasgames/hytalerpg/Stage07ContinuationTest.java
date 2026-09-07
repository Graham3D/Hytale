package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage07ContinuationTest {
    @Test void hitThenPierceThenForkThenChainThenReturnGoldenOrderAndFiniteBudgets() {
        var h=new Harness("piercing","fork","chain","return");var p=h.spawn();var order=new ArrayList<String>();
        for(int i=1;i<=3;i++) {
            Vec3 hit=new Vec3(0,0,i);p.observe(.04,hit);assertTrue(h.service.onEnemyContact(p,"target-"+i));order.add("HIT");
            var result=h.continuation.afterEnemy(p,hit,new Vec3(0,0,-2),List.of(),i);order.add(result.action().name());
            if(i<3)assertEquals(ProjectileContinuation.Action.PIERCE,result.action());
            else {
                assertEquals(2,result.children().size());assertEquals(3,h.registry.size());
                h.service.onForwardTermination(p,"FORK_PARENT_CONSUMED",hit);
                for(var child:result.children()) {
                    assertTrue(child.previouslyHit("target-3"));assertEquals(0,child.remaining("FORK"));
                    assertEquals(21,child.plan().maxDistance(),1e-12);assertEquals(3,child.totalDistance(),1e-12);
                    assertEquals(p.plan().rootCastId(),child.plan().rootCastId());assertSame(p.plan().snapshot(),child.plan().snapshot());
                }
                var child=result.children().getFirst();var at=new Vec3(0,0,4);child.observe(.04,at);h.service.onEnemyContact(child,"fourth");
                var choices=List.of(new ProjectileContinuation.Candidate("near",new Vec3(1,0,4),true));
                var chain=h.continuation.afterEnemy(child,at,Vec3.ZERO,choices,4);order.add("HIT");order.add(chain.action().name());
                assertEquals(1,child.remaining("CHAIN"));child.observe(.04,new Vec3(1,0,4));h.service.onEnemyContact(child,"near");
                var returning=h.continuation.afterEnemy(child,new Vec3(1,0,4),Vec3.ZERO,List.of(),5);order.add("HIT");order.add(returning.action().name());
                assertTrue(child.returning());assertTrue(child.acceptTarget("target-3"));assertFalse(child.acceptTarget("target-3"));
                assertEquals(ProjectileContinuation.Action.RETURN_CONTINUE,h.continuation.afterEnemy(child,new Vec3(0,0,3),Vec3.ZERO,choices,6).action());
                assertTrue(child.observe(2,new Vec3(0,0,-24)).expired());
            }
        }
        assertEquals(List.of("HIT","PIERCE","HIT","PIERCE","HIT","FORK","HIT","CHAIN","HIT","RETURN"),order);
        assertEquals(3,h.registry.spent(h.context.request().actorId(),h.context.rootCastId()));
        h.service.cancelOwner(h.context.request().actorId(),"LOGOUT");assertEquals(0,h.registry.size());assertEquals(0,h.registry.rootCount());
    }
    @Test void chainUsesNearestVisibleUnvisitedWithinEightMetersWithStableTieBreak() {
        var h=new Harness("chain");var p=h.spawn();p.acceptTarget("visited");
        var choices=List.of(new ProjectileContinuation.Candidate("visited",new Vec3(0,0,.1),true),
                new ProjectileContinuation.Candidate("occluded",new Vec3(0,0,.2),false),
                new ProjectileContinuation.Candidate("far",new Vec3(0,0,8.01),true),
                new ProjectileContinuation.Candidate("b",new Vec3(2,0,0),true),new ProjectileContinuation.Candidate("a",new Vec3(-2,0,0),true));
        var choice=h.continuation.afterEnemy(p,Vec3.ZERO,new Vec3(0,0,-4),choices,1);
        assertEquals("CHAIN_TARGET_a",choice.reason());assertEquals(new Vec3(-1,0,0),choice.direction());
        p.observe(.1,new Vec3(-2,0,0));assertEquals(22,p.remainingDistance());
        assertEquals(ProjectileContinuation.Action.TERMINATE,h.continuation.afterEnemy(p,new Vec3(-2,0,0),Vec3.ZERO,List.of(),2).action());
    }
    @Test void returnHasSeparateHitLedgerAndOwnBoundedDistanceButDoesNotRearmContinuations() {
        var h=new Harness("return","chain");var p=h.spawn();p.acceptTarget("same");p.observe(.5,new Vec3(0,0,12));
        assertEquals(ProjectileContinuation.Action.RETURN,h.continuation.forwardEnd(p,new Vec3(0,0,12),Vec3.ZERO,"TERRAIN_HIT").action());
        assertTrue(p.acceptTarget("same"));assertFalse(p.acceptTarget("same"));assertEquals(0,p.remaining("RETURN"));
        assertEquals(ProjectileContinuation.Action.TERMINATE,h.continuation.forwardEnd(p,new Vec3(0,0,11),Vec3.ZERO,"TERRAIN_HIT").action());
        var observation=p.observe(1.1,new Vec3(0,0,-12));assertTrue(observation.expired());assertEquals(36,p.totalDistance());
    }
    @Test void echoPromiseKeepsRootBudgetEvenWhenPrimaryTerminatesBeforeEchoLaunch() {
        var h=new Harness("echo","fork");var primary=h.spawn();
        h.service.onForwardTermination(primary,"MAX_RANGE",Vec3.FORWARD);assertEquals(0,h.registry.size());assertEquals(1,h.registry.rootCount());
        var echo=h.context.echoCopy();var plan=h.service.buildPlan(echo,h.context.request().actorId(),Vec3.ZERO,Vec3.FORWARD,"fixture",24,1);
        assertEquals(1,plan.generation());var child=h.service.onProjectileSpawn(plan);assertEquals(2,h.registry.spent(plan.ownerId(),plan.rootCastId()));
        h.service.onForwardTermination(child,"MAX_RANGE",Vec3.FORWARD);assertEquals(0,h.registry.rootCount());
    }
    @Test void abandonedEchoCapacityIsIdempotentlyReleasedAndOwnerCancelDropsPromises() {
        var h=new Harness("echo");var p=h.spawn();h.service.onForwardTermination(p,"MAX_RANGE",Vec3.FORWARD);
        for(int i=0;i<2;i++)h.registry.abandonLaunch(p.plan().ownerId(),p.plan().rootCastId());assertEquals(0,h.registry.rootCount());
        h=new Harness("echo");h.spawn();h.service.cancelOwner(h.context.request().actorId(),"WORLD_DRAIN");
        assertEquals(0,h.registry.size());assertEquals(0,h.registry.rootCount());
    }
    @Test void forkBatchOverflowIsAtomicAndCannotEvictAnotherRoot() {
        var registry=new ProjectileLifecycleRegistry();var owner=UUID.randomUUID();
        var context=context(owner,"fork");var service=new RpgProjectileService(registry);ProjectileInstance first=null;
        for(int i=0;i<23;i++) {
            var plan=service.buildPlan(context,owner,Vec3.ZERO,Vec3.FORWARD,"fixture",24,1);
            plan=copyPlan(plan,"root-"+i,"instance-"+i);var next=service.onProjectileSpawn(plan);if(i==0)first=next;
        }
        var decision=new ProjectileContinuation(registry).afterEnemy(first,Vec3.ZERO,new Vec3(0,0,-1),List.of(),2);
        assertEquals(ProjectileContinuation.Action.TERMINATE,decision.action());assertEquals("OWNER_PROJECTILE_BUDGET",decision.reason());
        assertEquals(23,registry.size());assertEquals(1,registry.spent(owner,first.plan().rootCastId()));
    }
    @Test void firstFourCompatibilityHasPositiveAndNegativeTypedGatesWithoutLineLeaks() {
        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        for(String passive:List.of("piercing","fork","chain","return")) {
            assertTrue(compatibility.assess(catalog.skill(new SkillId("fire_bolt")).orElseThrow(),catalog.passive(new PassiveId(passive)).orElseThrow()).accepted());
            for(String denied:List.of("quick_slash","wind_cutter","chain_lightning"))assertFalse(compatibility.assess(
                    catalog.skill(new SkillId(denied)).orElseThrow(),catalog.passive(new PassiveId(passive)).orElseThrow()).accepted());
        }
    }
    @Test void malformedFlightTimeIsRejectedAndYawPreservesSpeedAndElevation() {
        var flight=new ProjectileFlight(Vec3.ZERO,24,24);assertThrows(IllegalArgumentException.class,()->flight.observe(Double.NaN,Vec3.ZERO));
        var direction=new Vec3(.2,.5,1).normalized();var fork=ProjectileContinuation.yaw(direction,20);
        assertEquals(1,fork.length(),1e-12);assertEquals(direction.y(),fork.y(),1e-12);
    }
    static SkillExecutionContext context(UUID owner,String...passives) {
        var bundle=Stage01BTestSupport.bundle();bundle.service().equipSkill(owner,SkillSlot.SKILL01,new SkillId("fire_bolt"));
        for(int i=0;i<passives.length;i++) {
            bundle.service().equipPassive(owner,PassiveSlot.values()[i],new PassiveId(passives[i]));
            assertTrue(bundle.service().link(owner,LinkNodeId.valueOf(PassiveSlot.values()[i].name()),LinkNodeId.SKILL01).success());
        }
        var seed=Stage06AreaRuntimeTest.context("fire_bolt");var plan=bundle.service().getPresentationView(owner).plans().get(SkillSlot.SKILL01);
        var s=seed.snapshot();var snap=new CombatSnapshot(seed.rootCastId(),seed.skillInstanceId(),owner,s.rawAttributes(),s.effectiveAttributes(),s.derivedStats(),
                s.itemId(),s.weaponClass(),s.basePowerSource(),s.basePower(),plan.planHash(),s.skillCoefficient(),s.criticalChance(),s.criticalMultiplier(),s.modifiers(),s.resourceCost(),s.cooldownSeconds(),s.statusModifiers());
        return new SkillExecutionContext(new SkillExecutionRequest(owner,SkillSlot.SKILL01,"fixture",7,"fixture-correlation",Vec3.FORWARD),seed.rootCastId(),seed.skillInstanceId(),seed.profile(),plan,snap,null);
    }
    static ProjectileExecutionPlan copyPlan(ProjectileExecutionPlan p,String root,String id) {
        var s=p.snapshot();var snapshot=new CombatSnapshot(root,p.skillInstanceId(),p.ownerId(),s.rawAttributes(),s.effectiveAttributes(),s.derivedStats(),
                s.itemId(),s.weaponClass(),s.basePowerSource(),s.basePower(),p.compiledPlanHash(),s.skillCoefficient(),s.criticalChance(),s.criticalMultiplier(),s.modifiers(),s.resourceCost(),s.cooldownSeconds(),s.statusModifiers());
        return new ProjectileExecutionPlan(root,p.skillInstanceId(),id,p.ownerId(),p.skillId(),p.compiledPlanHash(),snapshot,p.generation(),p.remainingContinuationBudgets(),
                p.remainingSpawnedEffects(),p.remainingTriggeredSecondaries(),p.spawnTimestampNanos(),p.configId(),p.origin(),p.velocity(),p.radius(),p.maxDistance(),p.maxLifetimeSeconds());
    }
    private static class Harness {
        final SkillExecutionContext context;final ProjectileLifecycleRegistry registry=new ProjectileLifecycleRegistry();
        final RpgProjectileService service=new RpgProjectileService(registry);final ProjectileContinuation continuation=new ProjectileContinuation(registry);
        Harness(String...passives){context=context(UUID.randomUUID(),passives);}
        ProjectileInstance spawn(){return service.onProjectileSpawn(service.buildPlan(context,context.request().actorId(),Vec3.ZERO,Vec3.FORWARD,"fixture",24,1));}
    }
}
