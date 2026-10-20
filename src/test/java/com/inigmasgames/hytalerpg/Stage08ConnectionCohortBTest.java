package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.Stage08ConnectionTest.*;

class Stage08ConnectionCohortBTest {
    @Test void exactFiveProfilesCompleteStage08WithoutExpandingCatalog() {
        var p=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog());assertEquals(35+Stage04SkillProfiles.EXPECTED_STAGE13_PROFILES,p.all().values().stream().filter(profile->profile.support()==null&&profile.summon()==null&&profile.summonAction()==null&&profile.conversion()==null&&profile.cage()==null).count());
        var root=p.require("root_lash");assertEquals(ConnectionProfile.Kind.TETHER,root.connection().kind());assertEquals(12,root.connection().range());
        assertEquals(.7,root.connection().width());assertEquals(.7,root.damageCoefficient());assertEquals(Map.of("ROOT",1.5),root.authoredStatuses());assertEquals(9,root.resourceCost());
        var line=p.require("lightning_bolt");assertEquals(.15,line.windupSeconds());assertEquals(1.45,line.damageCoefficient());assertEquals(24,line.connection().range());
        var chain=p.require("chain_lightning");assertEquals(Stage04SkillProfile.Family.DIRECT_TARGET,chain.family());assertEquals(.08,chain.connection().intervalSeconds());
        assertEquals(List.of(1.25,1d,.8,.65),chain.connection().details().jumpCoefficients());assertEquals(8,chain.connection().details().jumpRadius());
        var orbit=p.require("orbiting_shadow_blades");assertEquals(4,orbit.connection().details().bladeCount());assertEquals(120,orbit.connection().details().degreesPerSecond());
        assertEquals(2.8,orbit.connection().range());assertEquals(.3,orbit.connection().radius());assertEquals(1.1,orbit.connection().originHeight());assertEquals(.75,orbit.connection().details().contactCooldown());
        var drain=p.require("life_drain");assertEquals(0,drain.resourceCost());assertEquals(8,drain.cooldownSeconds());assertEquals(5,drain.connection().upkeepPerSecond());assertEquals(.6,drain.connection().details().healFraction());
    }
    @Test void requiredTargetAbsenceRejectsBeforeCostOrCooldown() {
        for(String skill:List.of("root_lash","chain_lightning","life_drain")) {
            var h=new Harness(skill);assertFalse(h.cast().committed());assertEquals(200,h.mana);assertEquals(0,h.resourceWrites);
            assertTrue(h.kernel.cooldowns().canActivate(h.owner,skill));assertEquals(0,h.runtime.size());
        }
    }
    @Test void rootLashSelectsOneClosestAimedTargetWithoutMovingIt() {
        var h=new Harness("root_lash");var first=enemy(1,0,1.25,4);h.targets.add(enemy(2,0,1.25,6));h.targets.add(first);
        assertTrue(h.cast().committed());assertEquals(List.of(id(1)),h.hits.stream().map(Hit::id).toList());assertEquals(first,h.resolveTarget(id(1)).orElseThrow());
        assertEquals(191,h.mana);assertEquals(0,h.runtime.size());assertEquals(first.bounds().centre(),h.shapes.getFirst().end());
        h.advance(.5);assertEquals(1,h.hits.size());
    }
    @Test void rootLashFullWidthAndLosAreUsedByAcquisition() {
        var h=new Harness("root_lash");h.targets.add(enemy(1,.46,1.25,5));assertFalse(h.cast().committed());
        h.targets.set(0,enemy(1,.44,1.25,5));h.wallZ=4;assertFalse(h.cast().committed());h.wallZ=Double.POSITIVE_INFINITY;assertTrue(h.cast().committed());
    }
    @Test void lightningLineWaitsForCastThenHitsEachTargetOnce() {
        var h=new Harness("lightning_bolt");h.targets.add(enemy(1,0,1.25,5));h.targets.add(enemy(2,0,1.25,12));h.targets.add(enemy(3,0,1.25,20));h.wallZ=15;
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());h.advance(.14);assertEquals(200,h.mana);assertTrue(h.hits.isEmpty());
        h.clock.set(150_000_000L);assertTrue(h.service.completeWindup(h.owner,h).committed());assertEquals(184,h.mana);assertEquals(2,h.hits.size());
        assertTrue(h.hits.stream().allMatch(hit->hit.coefficient()==1.45));assertEquals(0,h.runtime.size());h.advance(.3);assertEquals(2,h.hits.size());
    }
    @Test void interruptedLightningWindupDoesNotCharge() {
        var h=new Harness("lightning_bolt");h.cast();assertTrue(h.service.cancel(h.owner,"NATIVE_DAMAGE_INTERRUPT"));
        assertEquals(200,h.mana);assertTrue(h.hits.isEmpty());assertTrue(h.kernel.cooldowns().canActivate(h.owner,"lightning_bolt"));
    }
    @Test void chainUsesFourDistinctTargetsAtExactOffsetsAndCoefficients() {
        var h=chain();assertTrue(h.cast().committed());assertEquals(1,h.hits.size());h.advance(.079);assertEquals(1,h.hits.size());
        h.advance(.08);assertEquals(2,h.hits.size());h.advance(.16);assertEquals(3,h.hits.size());h.advance(.24);assertEquals(4,h.hits.size());
        assertEquals(List.of(id(1),id(2),id(3),id(4)),h.hits.stream().map(Hit::id).toList());assertEquals(List.of(1.25,1d,.8,.65),h.hits.stream().map(Hit::coefficient).toList());
        assertEquals(176,h.mana);assertEquals(1,h.resourceWrites);assertEquals(List.of("CHAIN_COMPLETE"),h.ends);assertEquals(0,h.runtime.size());
    }
    @Test void chainTieBreakIsStableAndPreviousDeathKeepsLastImpactPoint() {
        var h=new Harness("chain_lightning");h.targets.add(enemy(1,0,1.35,5));h.targets.add(enemy(3,-4,1.35,5));h.targets.add(enemy(2,4,1.35,5));h.cast();
        h.targets.removeIf(t->t.id().equals(id(1)));h.advance(.08);assertEquals(id(2),h.hits.getLast().id());assertEquals(2,h.hits.size());
    }
    @Test void chainDoesNotRevisitOrJumpOutsideRadiusOrThroughWall() {
        var h=new Harness("chain_lightning");h.targets.add(enemy(1,0,1.35,5));h.targets.add(enemy(2,8.11,1.35,5));h.cast();h.advance(.08);
        assertEquals(1,h.hits.size());assertEquals(List.of("CHAIN_NO_VALID_JUMP"),h.ends);
        var blocked=new Harness("chain_lightning");blocked.wallZ=6;blocked.targets.add(enemy(1,0,1.35,5));blocked.targets.add(enemy(2,0,1.35,7));blocked.cast();blocked.advance(.08);assertEquals(1,blocked.hits.size());
    }
    @Test void chainCandidateOverflowRejectsNextWholeJump() {
        var h=chain();h.cast();h.overflow=true;h.advance(.08);assertEquals(1,h.hits.size());assertEquals(List.of("CANDIDATE_BUDGET_REJECTED"),h.ends);assertEquals(176,h.mana);
    }
    @Test void baseChainIsNotPermissionForProjectileChainPassive() {
        for(String passive:List.of("chain","fork","return","piercing")){var h=new Harness("chain_lightning");assertFalse(h.link(passive));}
        for(String passive:List.of("echo","skill_delay")){var h=new Harness("life_drain");assertFalse(h.link(passive));}
        var drain=Stage01BTestSupport.bundle().catalog().skill(new SkillId("life_drain")).orElseThrow();assertFalse(drain.canCrit());
    }
    @Test void fourBladeOffsetsShareOneTargetCooldownNotFourDpsClocks() {
        var h=orbit();h.cast();assertEquals(1,h.hits.size());assertEquals(4,h.shapes.size());
        assertEquals(new Vec3(2.8,1.1,0),h.shapes.getFirst().end());
        for(int tick=1;tick<=200;tick++)h.advance(tick*.05);
        assertEquals(14,h.hits.size());assertEquals(List.of(0,15,30,45,60,75,90,105,120,135,150,165,180,195),h.hits.stream().map(Hit::tick).toList());
        assertEquals(174,h.mana);assertEquals(1,h.resourceWrites);assertEquals(0,h.runtime.size());assertEquals(List.of("ORBIT_EXPIRED"),h.ends);
    }
    @Test void orbitFixedSamplingRemainsStableUnderJitter() {
        var h=orbit();h.cast();for(int n=1;n<=40;n++)h.advance(n*.25);assertEquals(14,h.hits.size());assertEquals(0,h.runtime.size());
    }
    @Test void orbitAngularSpeedAndOwnerTranslationDriveSweptProxies() {
        var h=orbit();h.cast();h.feet=new Vec3(1,0,0);h.advance(.25);var blade=h.shapes.get(h.shapes.size()-4);
        assertEquals(1+2.8*Math.cos(Math.PI/6),blade.end().x(),1e-9);assertEquals(1.4,blade.end().z(),1e-9);assertEquals(.3,blade.radius());
        assertTrue(h.shapes.stream().allMatch(s->s.kind()==ConnectionShape.Kind.CAPSULE));
    }
    @Test void exactSweptSphereBoundsDoNotUseAnExpandedRectangularHitbox() {
        var shape=ConnectionShape.capsule(Vec3.ZERO,new Vec3(0,0,10),.3);
        assertTrue(shape.intersects(bounds(.31,0,5,.01)));assertFalse(shape.intersects(bounds(.321,0,5,.01)));
        assertFalse(shape.intersects(bounds(.23,.23,5,.01)));assertTrue(shape.intersects(bounds(.22,.22,5,.01)));
        assertEquals(25,ConnectionShape.segmentDistanceSquared(Vec3.ZERO,new Vec3(0,0,10),bounds(5,0,5,0)),1e-9);
    }
    @Test void drainDebitsTwentyFractionalSlicesAndConvertsActualLossOnce() {
        var h=drain();h.cast();assertEquals(0,h.resourceWrites);assertTrue(h.kernel.cooldowns().canActivate(h.owner,"life_drain"));
        for(int n=1;n<=20;n++)h.advance(n*.25);
        assertEquals(20,h.hits.size());assertEquals(175,h.mana);assertEquals(20,h.resourceWrites);assertEquals(2.75,h.hits.stream().mapToDouble(Hit::coefficient).sum(),1e-9);
        double wisdom=h.contexts.getFirst().snapshot().derivedStats().healingMultiplier();assertEquals(55*.6*wisdom,h.healRequests.stream().mapToDouble(Double::doubleValue).sum(),1e-9);
        assertEquals(1,h.cooldownEndTraces());assertFalse(h.kernel.cooldowns().canActivate(h.owner,"life_drain"));assertEquals(0,h.runtime.size());
    }
    @Test void drainUsesActualOverkillLimitedLossAndNeverHealsForZeroLoss() {
        var h=drain();h.victimHealth.put(id(1),.5);h.cast();h.advance(.25);h.advance(.5);
        assertEquals(1,h.healRequests.size());assertEquals(.3*h.contexts.getFirst().snapshot().derivedStats().healingMultiplier(),h.healRequests.getFirst(),1e-9);
        var zero=drain();zero.victimHealth.put(id(1),0d);zero.cast();zero.advance(.25);assertTrue(zero.healRequests.isEmpty());assertEquals(50,zero.health);
    }
    @Test void drainTracksOneTargetAndBreaksOnLosRangeOrDespawnBeforePaying() {
        for(String failure:List.of("LOS","RANGE","DESPAWN")){
            var h=drain();h.cast();h.advance(.25);h.aim=new Vec3(1,0,0);h.targets.add(enemy(2,5,1.35,0));
            if(failure.equals("LOS"))h.wallZ=4;else if(failure.equals("RANGE"))h.targets.set(0,enemy(1,0,1.35,19));else h.targets.removeFirst();
            h.advance(.5);assertEquals(1,h.hits.size(),failure);assertEquals(198.75,h.mana,failure);assertEquals(List.of("TETHER_TARGET_INVALID"),h.ends);
        }
    }
    @Test void conversionAppliesHealingWisdomAndIncreasedOnceWithoutSourceDamageBuckets() {
        var service=new HealingCalculationService();var result=service.fromActualDamage(10,.6,1.2,.15);
        assertEquals(6,result.baseHealing());assertEquals(8.28,result.requestedHealing(),1e-9);
        assertEquals(0,service.fromActualDamage(0,.6,1.2,.15).requestedHealing());
        assertThrows(IllegalArgumentException.class,()->service.fromActualDamage(-1,.6,1,0));assertThrows(IllegalArgumentException.class,()->service.fromActualDamage(Double.NaN,.6,1,0));
        assertThrows(IllegalArgumentException.class,()->service.fromActualDamage(1,1.1,1,0));
    }
    @Test void healingCapsAtActualNativeMaximumInPortAndReportsActualGain() {
        var h=drain();h.health=99.9;h.cast();h.advance(.25);assertEquals(100,h.health);assertEquals(.1,h.healTotal,1e-9);
        h.advance(.5);assertEquals(.1,h.healTotal,1e-9);assertEquals(2,h.healRequests.size());
    }
    static String id(int n){return new UUID(0,n).toString();}
    static ConnectionWorldPort.Target enemy(int n,double x,double y,double z){return target(id(n),x,y,z);}
    static Harness chain(){var h=new Harness("chain_lightning");for(int n=1;n<=4;n++)h.targets.add(enemy(n,(n-1)*7,1.35,5));return h;}
    static Harness orbit(){var h=new Harness("orbiting_shadow_blades");h.targets.add(new ConnectionWorldPort.Target(id(1),new AreaGeometry.Bounds(new Vec3(-4,.8,-4),new Vec3(4,1.4,4))));return h;}
    static Harness drain(){var h=new Harness("life_drain");h.targets.add(enemy(1,0,1.35,5));return h;}
}
