package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.links.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** No client/packet/physics rendering claim: compiler + kernel + finite secondary ownership fixtures. */
class Stage07SecondaryTest {
    @Test void accelerantCompilesSpeedDistanceLifetimeWithoutMagnitudeChange() {
        var h=new Stage07MultiplicityTest.Harness("accelerant");h.cast();var p=h.plans.getFirst();
        assertEquals(33.6,p.velocity().length(),1e-12);assertEquals(28.8,p.maxDistance(),1e-12);
        assertEquals(28.8/33.6,p.maxLifetimeSeconds(),1e-12);assertEquals(1,p.snapshot().modifiers().factor());
    }
    @Test void ballisticsUsesAdditiveIncreasedBucketAndCombinesWithVolleySpeedAndDistance() {
        var h=new Stage07MultiplicityTest.Harness("accelerant","ballistics","potency","volley");h.cast();
        for(var p:h.plans) {assertEquals(24*1.4*.65,p.velocity().length(),1e-12);assertEquals(28.8,p.maxDistance(),1e-12);
            assertEquals(p.maxDistance()/p.velocity().length(),p.maxLifetimeSeconds(),1e-12);
            assertEquals(1.45*.75,p.snapshot().modifiers().factor(),1e-12);}
        assertEquals(1,h.resourceWrites);
    }
    @Test void independentLifetimeCapWinsAndGravityHasDefensiveRuntimeRejection() {
        var context=Stage07ContinuationTest.context(UUID.randomUUID(),"accelerant","ballistics");
        var capped=projectileProfile(context,0,.2);var p=plan(capped,0);
        assertEquals(.2,p.maxLifetimeSeconds());assertEquals(.2,capped.profile().projectile().maximumLifetimeSeconds());
        var gravity=projectileProfile(context,9.81,3);
        assertEquals("BALLISTICS_GRAVITY_UNSUPPORTED",assertThrows(IllegalStateException.class,()->plan(gravity,0)).getMessage());
    }
    @Test void ballisticsRejectsAuthoredGravityCatalogWhileAllFourAcceptValidProjectileAndRejectLines() {
        var catalog=RpgCatalog.loadCanonical();var gate=new CompatibilityService();
        for(String id:List.of("accelerant","ballistics","shrapnel","splinterburst")) {
            var passive=catalog.passive(new PassiveId(id)).orElseThrow();
            assertTrue(gate.assess(catalog.skill(new SkillId("fire_bolt")).orElseThrow(),passive).accepted());
            for(String denied:List.of("quick_slash","void_beam","lightning_bolt","minor_heal"))
                assertFalse(gate.assess(catalog.skill(new SkillId(denied)).orElseThrow(),passive).accepted());
        }
        for(String id:List.of("explosive_flask","bomb_toss"))assertEquals(ValidationCode.UNSUPPORTED_BUILD,
                gate.assess(catalog.skill(new SkillId(id)).orElseThrow(),catalog.passive(new PassiveId("ballistics")).orElseThrow()).code());
    }
    @Test void shrapnelRequiresPositiveAuthoritativeLossAndTriggersOnlyOnceAcrossBothLegs() {
        var f=new Fixture("shrapnel","return");var p=f.spawn();
        for(double loss:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertTrue(f.hit(p,loss).burst().isEmpty());
        assertEquals(1,p.remaining("SHRAPNEL"));p.acceptTarget("direct");var burst=f.hit(p,1).burst().orElseThrow();
        assertTrue(burst.acceptTarget("direct"));assertFalse(burst.acceptTarget("direct"));assertFalse(burst.canProc());
        assertEquals(.5,burst.coefficientFactor());assertEquals(2.5,burst.geometry().radius());assertEquals(3,burst.geometry().height());
        p.beginReturn(new Vec3(0,0,5),Vec3.ZERO);assertTrue(p.acceptTarget("direct"));assertTrue(f.hit(p,1).burst().isEmpty());
        assertEquals(2,f.registry.spent(f.owner(),p.plan().rootCastId()));assertEquals(1,f.registry.triggered(f.owner(),p.plan().rootCastId()));
    }
    @Test void introducedRadiusNeverEnlargesOrReducesThePrimaryAndMultipliesSecondaryExactlyOnce() {
        var h=new Stage07MultiplicityTest.Harness("shrapnel","expanded_radius","ballistics","potency","volley","homing");h.cast();
        var c=h.released.getFirst();var p=h.registry.ownedBy(h.owner).getFirst();
        assertTrue(c.compiledPlan().radiusOnlyOnShrapnel());assertFalse(c.compiledPlan().finalTags().contains("HAS_RADIUS"));
        assertEquals(h.profile.projectile().radius(),p.plan().radius());assertEquals(1.45*.75*.9,p.plan().snapshot().modifiers().factor(),1e-12);
        var burst=new ProjectileSecondaryEffects(h.registry).afterDamage(p,c.compiledPlan(),Vec3.ZERO,1).burst().orElseThrow();
        assertEquals(3.125,burst.geometry().radius());assertEquals(.45,burst.coefficientFactor(),1e-12);
        assertEquals(1.45*.75*.9*.5*.9,p.plan().snapshot().withMagnitudeFactor(burst.coefficientFactor()).modifiers().factor(),1e-12);
        assertTrue(c.compiledPlan().geometryModifiers().stream().anyMatch(s->s.startsWith("SHRAPNEL_")));
    }
    @Test void componentIntroductionIsOrderIndependentButNeedsShrapnelOnTheSameSkill() {
        var catalog=RpgCatalog.loadCanonical();var gate=new CompatibilityService();var skill=catalog.skill(new SkillId("fire_bolt")).orElseThrow();
        var radius=catalog.passive(new PassiveId("expanded_radius")).orElseThrow();var shrapnel=catalog.passive(new PassiveId("shrapnel")).orElseThrow();
        assertFalse(gate.assess(skill,radius).accepted());assertTrue(gate.assess(skill,radius,List.of(radius,shrapnel)).accepted());
        assertTrue(gate.assess(skill,radius,List.of(shrapnel,radius)).accepted());
        assertFalse(gate.assess(catalog.skill(new SkillId("quick_slash")).orElseThrow(),radius,List.of(shrapnel)).accepted());
        var h=new Stage07MultiplicityTest.Harness("shrapnel","expanded_radius");
        h.bundle.service().equipSkill(h.owner,SkillSlot.SKILL02,new SkillId("frost_bolt"));
        assertFalse(h.bundle.service().link(h.owner,LinkNodeId.PASSIVE02,LinkNodeId.SKILL02).success());
        assertTrue(h.bundle.service().getPresentationView(h.owner).plans().get(SkillSlot.SKILL01).radiusOnlyOnShrapnel());
        int saves=h.bundle.repository().saves;
        assertFalse(h.bundle.service().unlinkSource(h.owner,LinkNodeId.PASSIVE01).success());
        assertFalse(h.bundle.service().unequipPassive(h.owner,PassiveSlot.PASSIVE01).success());
        assertEquals(saves,h.bundle.repository().saves);
        assertTrue(h.bundle.service().unlinkSource(h.owner,LinkNodeId.PASSIVE02).success());
        assertTrue(h.bundle.service().unequipPassive(h.owner,PassiveSlot.PASSIVE01).success());
    }
    @Test void burstTargetLedgerIsIndependentFiniteAndRejectsBlankIDs() {
        var f=new Fixture("shrapnel");var burst=f.hit(f.spawn(),1).burst().orElseThrow();
        assertFalse(burst.acceptTarget(null));assertFalse(burst.acceptTarget(""));
        for(int i=0;i<64;i++)assertTrue(burst.acceptTarget("victim-"+i));assertFalse(burst.acceptTarget("overflow"));
        assertTrue(burst.geometry().intersects(new com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds(new Vec3(2.4,-.1,-.1),new Vec3(3,.1,.1))));
        assertFalse(burst.geometry().intersects(new com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds(new Vec3(0,2,0),new Vec3(.1,3,.1))));
    }
    @Test void splinterburstCreatesThreeHalfRangeScaledChildrenAndNeverCopiesItself() {
        var f=new Fixture("splinterburst","piercing","chain","return","shrapnel");var p=f.spawn();p.spend("PIERCE");p.acceptTarget("parent-victim");
        var result=f.terminal(p,ProjectileSecondaryEffects.TerminalCause.TERRAIN);assertEquals(ProjectileContinuation.Action.SPLINTERBURST,result.action());
        assertEquals(3,result.children().size());assertEquals(4,f.registry.size());assertEquals(3,f.registry.triggered(f.owner(),p.plan().rootCastId()));
        for(int i=0;i<3;i++) {var child=result.children().get(i);assertEquals(1,child.plan().generation());assertEquals(12,child.plan().maxDistance());
            assertEquals(.5,child.plan().maxLifetimeSeconds());assertEquals(.35,child.plan().snapshot().modifiers().factor(),1e-12);
            assertEquals(p.plan().snapshot().resourceCost(),child.plan().snapshot().resourceCost());
            assertEquals(1,child.remaining("PIERCE"));assertEquals(2,child.remaining("CHAIN"));assertEquals(1,child.remaining("RETURN"));
            assertEquals(0,child.remaining("SPLINTERBURST"));assertFalse(child.previouslyHit("parent-victim"));
            assertTrue(ProjectileContinuation.yaw(p.direction(),(i-1)*25).distanceSquared(child.direction())<1e-24);
            assertTrue(f.terminal(child,ProjectileSecondaryEffects.TerminalCause.RANGE).children().isEmpty());
        }
        assertTrue(f.terminal(p,ProjectileSecondaryEffects.TerminalCause.TERRAIN).children().isEmpty());
    }
    @Test void returnDefersBurstAndTerminalChildrenDoNotRestoreConsumedReturnCredit() {
        var f=new Fixture("splinterburst","return");var p=f.spawn();p.observe(.25,new Vec3(0,0,6));
        var next=new ProjectileContinuation(f.registry).forwardEnd(p,new Vec3(0,0,6),Vec3.ZERO,"MAX_RANGE");
        assertEquals(ProjectileContinuation.Action.RETURN,next.action());assertEquals(1,p.remaining("SPLINTERBURST"));
        var result=f.terminal(p,ProjectileSecondaryEffects.TerminalCause.RETURN_CAUGHT);
        for(var child:result.children()){assertEquals(0,child.remaining("RETURN"));assertEquals(12,child.originalMaxDistance());}
    }
    @Test void forkRetainsOriginalDistanceAndIndependentCapForTerminalHalfRangeChildren() {
        var f=new Fixture("fork","splinterburst","shrapnel");var p=f.spawn();p.observe(.9,new Vec3(0,0,21));
        f.hit(p,1);var fork=new ProjectileContinuation(f.registry).afterEnemy(p,new Vec3(0,0,21),Vec3.ZERO,List.of(),1);
        var child=fork.children().getFirst();assertEquals(3,child.plan().maxDistance());assertEquals(24,child.originalMaxDistance());
        assertEquals(1,child.remaining("SHRAPNEL"));assertTrue(f.hit(child,1).burst().isPresent());
        var splinter=f.terminal(child,ProjectileSecondaryEffects.TerminalCause.RANGE);
        assertEquals(3,splinter.children().size());for(var next:splinter.children()){assertEquals(12,next.plan().maxDistance());assertEquals(.5,next.plan().maxLifetimeSeconds());}
    }
    @Test void cancelledUnloadedAndBudgetRejectedCarriersCannotBurst() {
        for(var cause:List.of(ProjectileSecondaryEffects.TerminalCause.CANCELLED,ProjectileSecondaryEffects.TerminalCause.WORLD_UNLOAD,ProjectileSecondaryEffects.TerminalCause.BUDGET_REJECTED)) {
            var f=new Fixture("splinterburst");var p=f.spawn();assertTrue(f.terminal(p,cause).children().isEmpty());assertEquals(1,f.registry.size());
            f.registry.remove(p);assertTrue(f.terminal(p,ProjectileSecondaryEffects.TerminalCause.RANGE).children().isEmpty());assertEquals(0,f.registry.rootCount());
        }
    }
    @Test void ownerCapacityRejectionIsAtomicAndCannotBurstLaterAfterCapacityFrees() {
        var f=new Fixture("splinterburst");var p=f.spawn();
        for(int i=0;i<21;i++)f.registry.register(new ProjectileInstance(Stage07ContinuationTest.copyPlan(p.plan(),"other-"+i,"other-"+i)));
        var rejected=f.terminal(p,ProjectileSecondaryEffects.TerminalCause.RANGE);assertEquals("OWNER_PROJECTILE_BUDGET",rejected.reason());
        assertEquals(22,f.registry.size());assertEquals(1,f.registry.spent(f.owner(),p.plan().rootCastId()));assertEquals(0,f.registry.triggered(f.owner(),p.plan().rootCastId()));
        for(var other:f.registry.ownedBy(f.owner()))if(other!=p)f.registry.remove(other);
        assertTrue(f.terminal(p,ProjectileSecondaryEffects.TerminalCause.RANGE).children().isEmpty());
    }
    @Test void triggeredSecondaryBudgetIsSharedWithInstantBurstsAndHasNoPartialThreeChildAdmission() {
        var f=new Fixture("splinterburst","shrapnel");var p=f.spawn();
        for(int i=0;i<14;i++)assertEquals("PASS",f.registry.reserveSecondary(p,"fixture-"+i));
        assertEquals("ROOT_TRIGGERED_SECONDARY_BUDGET",f.terminal(p,ProjectileSecondaryEffects.TerminalCause.RANGE).reason());
        assertEquals(1,f.registry.size());assertEquals(15,f.registry.spent(f.owner(),p.plan().rootCastId()));
        assertTrue(f.hit(p,1).burst().isPresent());assertEquals("PASS",f.registry.reserveSecondary(p,"last"));
        assertEquals("ROOT_TRIGGERED_SECONDARY_BUDGET",f.registry.reserveSecondary(p,"overflow"));
    }
    @Test void sourceSnapshotIsImmutableAndSecondaryContextPreservesAllExecutionIdentity() {
        var f=new Fixture("splinterburst");var p=f.spawn();var child=f.terminal(p,ProjectileSecondaryEffects.TerminalCause.ENEMY).children().getFirst();
        var c=f.context.withSnapshot(child.plan().snapshot());assertSame(f.context.request(),c.request());assertEquals(f.context.rootCastId(),c.rootCastId());
        assertEquals(f.context.skillInstanceId(),c.skillInstanceId());assertSame(f.context.target(),c.target());assertEquals(1,f.context.snapshot().modifiers().factor());
        assertEquals(.35,c.snapshot().modifiers().factor(),1e-12);
    }
    @Test void generationThreeCanHitButCannotCreateAnyNewSecondaryGeneration() {
        var f=new Fixture("splinterburst","shrapnel");var anchor=f.spawn();var p=anchor.plan();var budgets=new HashMap<>(p.remainingContinuationBudgets());budgets.put("IS_LAUNCH",0);
        var deep=new ProjectileInstance(new ProjectileExecutionPlan(p.rootCastId(),p.skillInstanceId(),"deep",p.ownerId(),p.skillId(),p.compiledPlanHash(),p.snapshot(),3,
                budgets,0,16,0,p.configId(),p.origin(),p.velocity(),p.radius(),p.maxDistance(),p.maxLifetimeSeconds()));f.registry.register(deep);
        assertTrue(deep.acceptTarget("enemy"));assertEquals("MAX_GENERATION",f.hit(deep,1).reason());
        assertEquals("MAX_GENERATION",f.terminal(deep,ProjectileSecondaryEffects.TerminalCause.ENEMY).reason());assertEquals(2,f.registry.size());
    }
    @Test void damageMetadataRoundtripRetainsUnchangedRootIdsAndNonProccingSecondaryIdentity() {
        var metadata=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(UUID.randomUUID(),"root","skill","correlation",10,50,"carrier/shrapnel",false);
        var gson=new Gson();var copy=gson.fromJson(gson.toJson(metadata),com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.class);
        assertEquals(metadata,copy);assertFalse(copy.canProc());assertEquals("carrier/shrapnel",copy.effectInstanceId());
        var original=new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata(metadata.actorId(),"root","skill","correlation",10,50);
        assertTrue(original.canProc());assertEquals("skill",original.effectInstanceId());
    }
    @Test void goldenDelayEchoForkChainReturnPreservesPaidRootAndSnapshotAcrossBothWaves() {
        var h=new Stage07MultiplicityTest.Harness("skill_delay","echo","fork","chain","return");h.cast();assertTrue(h.plans.isEmpty());h.advance(2);
        var first=h.released.getFirst();assertEquals(1.35,first.snapshot().modifiers().factor(),1e-12);double paid=h.mana;
        for(double release:List.of(2d,2.45)) {
            if(release>2)h.advance(release);var original=h.registry.ownedBy(h.owner).getFirst();
            original.observe(.1,new Vec3(0,0,2));original.acceptTarget("original-target");
            var continuations=new ProjectileContinuation(h.registry);var fork=continuations.afterEnemy(original,new Vec3(0,0,2),Vec3.ZERO,List.of(),1);
            assertEquals(2,fork.children().size());h.registry.remove(original);
            for(var child:fork.children()) {
                var chained=continuations.afterEnemy(child,new Vec3(0,0,3),Vec3.ZERO,List.of(new ProjectileContinuation.Candidate("next",new Vec3(0,0,4),true)),2);
                assertEquals(ProjectileContinuation.Action.CHAIN,chained.action());child.acceptTarget("next");
                assertEquals(ProjectileContinuation.Action.RETURN,continuations.afterEnemy(child,new Vec3(0,0,4),Vec3.ZERO,List.of(),3).action());
                assertTrue(child.acceptTarget("original-target"));assertEquals(first.rootCastId(),child.plan().rootCastId());
                assertTrue(child.observe(2,Vec3.ZERO).expired());h.registry.remove(child);
            }
        }
        assertEquals(1,h.resourceWrites);assertEquals(paid,h.mana);assertEquals(0,h.registry.rootCount());assertEquals(0,h.service.pendingReleaseCount());
        assertEquals(first.request().correlationId(),h.released.getLast().request().correlationId());
        assertEquals(1.35*.7,h.released.getLast().snapshot().modifiers().factor(),1e-12);
    }
    private static SkillExecutionContext projectileProfile(SkillExecutionContext context,double gravity,double cap) {
        var gson=new Gson();var tree=gson.toJsonTree(context.profile()).getAsJsonObject();
        tree.getAsJsonObject("projectile").addProperty("gravity",gravity);tree.getAsJsonObject("projectile").addProperty("independentLifetimeSeconds",cap);
        return new SkillExecutionContext(context.request(),context.rootCastId(),context.skillInstanceId(),gson.fromJson(tree,Stage04SkillProfile.class),
                context.compiledPlan(),context.snapshot(),context.equipment());
    }
    private static ProjectileExecutionPlan plan(SkillExecutionContext context,long now) {
        return ProjectileExecutionPlan.generationZero(context,context.request().actorId(),Vec3.ZERO,Vec3.FORWARD,"fixture",24,now);
    }
    private static final class Fixture {
        final SkillExecutionContext context;final ProjectileLifecycleRegistry registry=new ProjectileLifecycleRegistry();
        final ProjectileSecondaryEffects effects=new ProjectileSecondaryEffects(registry);
        Fixture(String...passives){context=Stage07ContinuationTest.context(UUID.randomUUID(),passives);}
        UUID owner(){return context.request().actorId();}
        ProjectileInstance spawn(){var p=new ProjectileInstance(plan(context,0));registry.register(p);return p;}
        ProjectileSecondaryEffects.BurstResult hit(ProjectileInstance p,double loss){return effects.afterDamage(p,context.compiledPlan(),Vec3.ZERO,loss);}
        ProjectileContinuation.Decision terminal(ProjectileInstance p,ProjectileSecondaryEffects.TerminalCause cause){return effects.terminal(p,new Vec3(0,0,5),cause,2);}
    }
}
