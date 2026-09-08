package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11MagnitudeDurationTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void overchargeAddsIncreasedNotSeparateMoreBucket(){var p=f.plan("fire_bolt","potency","overcharge");assertEquals(.4,p.kernelModifiers().scalablePayloadIncreased(),1e-9);assertEquals(1.2,p.kernelModifiers().resourceCostMultiplier());}
    @Test void overchargePaidBeforeDispatchAndCombinesWithEfficiency(){var h=new Stage10SummonTest.Harness();h.link("overcharge",PassiveSlot.PASSIVE01);h.link("efficiency",PassiveSlot.PASSIVE02);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(79,h.mana);assertEquals(1.25,h.context.snapshot().modifiers().factor(),1e-9);}
    @Test void overchargeUsesRealUpkeepMultiplier(){var p=f.plan("void_beam","overcharge");var r=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().resources();assertEquals(1.2,r.evaluateUpkeep(new ResourceCost(ResourceType.MANA,1),p.kernelModifiers()).amount(),1e-9);}
    @Test void overchargeRejectsPureReservationBarrier(){assertFalse(f.accepts("managuard","overcharge"));}
    @Test void overchargeRejectsPureReservationUtility(){assertFalse(f.accepts("pedanticism","overcharge"));}
    @Test void overchargeRejectsMovementWithoutScalablePayload(){assertFalse(f.accepts("quickstep","overcharge"));}
    @Test void concentrationUsesSingleIncreasedBucketWithPotency(){var p=f.plan("ground_slam","potency","concentration");assertEquals(.45,p.kernelModifiers().scalablePayloadIncreased(),1e-9);assertEquals(1,p.kernelModifiers().resourceCostMultiplier());}
    @Test void concentrationShrinksRadialFootprintNotHeightOrKnockback(){var a=f.profiles.require("ground_slam").area();var p=f.effective("ground_slam","concentration").area();assertEquals(3.5,p.radius());assertEquals(a.height(),p.height());assertEquals(a.displacement(),p.displacement());}
    @Test void concentrationShrinksConeAngleNotReach(){var a=f.profiles.require("cold_wave").area();var p=f.effective("cold_wave","concentration").area();assertEquals(49,p.angleDegrees(),1e-9);assertEquals(a.radius(),p.radius());assertEquals(a.innerRadius(),p.innerRadius());}
    @Test void concentrationShrinksWallWidthNotPlacementOrLength(){var a=f.profiles.require("wall_of_fire").area();var p=f.effective("wall_of_fire","concentration").area();assertEquals(1.4,p.width(),1e-9);assertEquals(a.length(),p.length());assertEquals(a.placementRange(),p.placementRange());}
    @Test void concentrationShrinksBeamWidthNotHeightOrRange(){var a=f.profiles.require("void_beam").connection();var p=f.effective("void_beam","concentration").connection();assertEquals(.56,p.width(),1e-9);assertEquals(a.height(),p.height());assertEquals(a.range(),p.range());}
    @Test void concentrationRejectsProjectileCollisionRadius(){assertFalse(f.accepts("frost_bolt","concentration"));}
    @Test void concentrationRejectsSingleTargetUtility(){assertFalse(f.accepts("taunt","concentration"));}
    @Test void concentrationAndExpandedRadiusComposeOnceAtRuntime(){var p=f.plan("ground_slam","concentration","expanded_radius");var area=f.resolver.resolve(f.profiles.require("ground_slam"),p).area();assertEquals(4.375,area.radius()*p.executionModifiers().radiusFactor(),1e-9);assertEquals(.3,p.kernelModifiers().scalablePayloadIncreased());}
    @Test void concentrationChangesOrbitRadiusNotBladeCollisionRadius(){var a=f.profiles.require("orbiting_shadow_blades").connection();var p=f.effective("orbiting_shadow_blades","concentration").connection();assertEquals(a.range()*.7,p.range(),1e-9);assertEquals(a.radius(),p.radius());}
    @Test void lingeringExtendsRealSummonLeaseAndPaysOnce(){var h=new Stage10SummonTest.Harness();h.link("lingering",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(77,h.mana);assertEquals(28,h.leases.getFirst().expires(),1e-9);assertEquals(1,h.leases.getFirst().interval());assertFalse(h.cast().committed());assertEquals(77,h.mana);}
    @Test void lingeringCombinesWithEmpowermentWithoutRewritingPower(){var h=new Stage10SummonTest.Harness();h.link("lingering",PassiveSlot.PASSIVE01);h.link("minion_empowerment",PassiveSlot.PASSIVE02);h.cast();assertEquals(21,h.leases.getFirst().expires(),1e-9);assertEquals(.55*1.3,h.leases.getFirst().coefficient(),1e-9);}
    @Test void lingeringExtendsTrapLifetimeNotArmingOrRootControl(){var a=f.profiles.require("root_snare").area();var p=f.effective("root_snare","lingering").area();assertEquals(21,p.lifetimeSeconds());assertEquals(a.armingSeconds(),p.armingSeconds());assertEquals(a.statusSeconds(),p.statusSeconds());assertEquals(1,p.perTargetHitCap());}
    @Test void lingeringExtendsPeriodicZoneWithoutStoppingDamageAtOldScheduleCap(){var a=f.profiles.require("wall_of_fire").area();var p=f.effective("wall_of_fire","lingering").area();assertEquals(11.2,p.lifetimeSeconds(),1e-9);assertEquals(45,p.perTargetHitCap());assertEquals(a.intervalSeconds(),p.intervalSeconds());assertEquals(a.coefficient(),p.coefficient());}
    @Test void lingeringPreservesExplicitBombardmentHitCapsWarningsAndCadence(){var a=f.profiles.require("avalanche").area();var p=f.effective("avalanche","lingering").area();assertEquals(8.4,p.lifetimeSeconds(),1e-9);assertEquals(8,p.impactCount());assertEquals(3,p.perTargetHitCap());assertEquals(a.intervalSeconds(),p.intervalSeconds());assertEquals(a.warningSeconds(),p.warningSeconds());assertEquals(a.statusSeconds(),p.statusSeconds());}
    @Test void lingeringDoesNotScaleOverheadWarningOrWindup(){var a=f.profiles.require("meteor");var p=f.effective("meteor","lingering");assertEquals(a.windupSeconds(),p.windupSeconds());assertEquals(a.area().warningSeconds(),p.area().warningSeconds());assertEquals(a.area().descentSeconds(),p.area().descentSeconds());assertEquals(5.6,p.area().statusSeconds(),1e-9);}
    @Test void lingeringExtendsBurnNotProjectileFlightOrDps(){var a=f.profiles.require("fire_bolt").projectile();var p=f.effective("fire_bolt","lingering").projectile();assertEquals(5.6,p.statusSeconds(),1e-9);assertEquals(a.maximumLifetimeSeconds(),p.maximumLifetimeSeconds());assertEquals(a.periodicCoefficient(),p.periodicCoefficient());assertEquals(a.periodicIntervalSeconds(),p.periodicIntervalSeconds());}
    @Test void lingeringExtendsFiniteShieldWithoutChangingCapacity(){var a=f.profiles.require("spirit_shield").support();var p=f.effective("spirit_shield","lingering").support();assertEquals(a.durationSeconds()*1.4,p.durationSeconds(),1e-9);assertEquals(a.coefficient(),p.coefficient());}
    @Test void lingeringExtendsFiniteAuraAndUpkeepButNotItsPulseClocks(){var p=f.plan("reaping_storm","lingering");var a=f.profiles.require("reaping_storm").support();var r=f.resolver.resolve(f.profiles.require("reaping_storm"),p).support();assertEquals(a.durationSeconds()*1.4,r.durationSeconds(),1e-9);assertEquals(a.damageInterval(),r.damageInterval());assertEquals(a.upkeepPerSecond(),r.upkeepPerSecond());assertEquals(1.15,p.kernelModifiers().resourceCostMultiplier());}
    @Test void lingeringRejectsIndefiniteAura(){assertFalse(f.accepts("emanatism","lingering"));}
    @Test void lingeringRejectsFlightOnlyAndCrowdControlOnlyDespiteImportedDurationTags(){for(String s:java.util.List.of("arcane_bolt","wind_cutter","frost_bolt","riposte","intimidate","dominate","comet"))assertFalse(f.accepts(s,"lingering"),s);}
    @Test void lingeringRejectsChannelMaximumRatherThanExtendingActiveInputState(){assertFalse(f.accepts("void_beam","lingering"));assertFalse(f.accepts("life_drain","lingering"));}
    @Test void newComponentRecordParticipatesInVersionedHash(){var a=f.plan("wolf_summon");var b=f.plan("wolf_summon","lingering");assertEquals(11,b.schemaVersion());assertNotEquals(a.planHash(),b.planHash());}
    @Test void componentAdmissionDoesNotLeakNewGlobalTags(){assertFalse(f.plan("void_beam","concentration").finalTags().contains("HAS_AREA_GEOMETRY"));}
    @Test void realFiniteAreaRuntimeDeliversTheExtendedFractionalTailAndReleasesOwnership(){
        var areaRuntime=new com.inigmasgames.hytalerpg.execution.area.AreaRuntime();var port=new Stage06AreaRuntimeTest.FakePort();
        port.targets=java.util.List.of(Stage06AreaRuntimeTest.target("enemy",0,0));
        var h=new Stage09SupportRuntimeTest.Harness("wall_of_fire"){
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public SkillExecutionResult executeArea(SkillExecutionContext c){context=c;areaRuntime.start(c,com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO,com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,0,1,port);return SkillExecutionResult.committed("FIXTURE_AREA",0,0);}
        };
        h.link("lingering",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(72,h.mana);
        for(int tick=1;tick<=45;tick++)areaRuntime.tick(h.actor,tick*.25,port);
        assertEquals(45,port.payloads.size());assertEquals(.45*11.2,port.payloads.stream().mapToDouble(p->p.coefficient()).sum(),1e-9);assertEquals(0,areaRuntime.size());assertEquals(0,areaRuntime.retainedRootCount());
    }
    @Test void lingeringBurnFeedsExistingPeriodicRuntimeWithoutIntegerTailInflation(){
        var p=f.effective("fire_bolt","lingering").projectile();var runtime=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime<String,String>();
        var source=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source(java.util.UUID.randomUUID(),"fire_bolt",java.util.UUID.randomUUID(),com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BURN);
        var hits=new java.util.ArrayList<Double>();
        var port=new com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Port<String,String>(){
            public boolean tick(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source s,String c,String t,int index,double coefficient,double seconds){hits.add(coefficient);return true;}
            public void changed(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source s,String t,com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.View v){}
        };
        runtime.apply(source,"same-root","target",p.periodicCoefficient()/p.periodicIntervalSeconds(),20,p.statusSeconds(),1,1,0,port);
        for(int tick=1;tick<=24;tick++)runtime.tick(source.owner(),tick*.25,port);
        assertEquals(6,hits.size());assertEquals(.56,hits.stream().mapToDouble(Double::doubleValue).sum(),1e-9);assertEquals(.06,hits.getLast(),1e-9);assertEquals(0,runtime.size());
    }
    @Test void concentrationRuntimeFiltersAnEnemyOutsideNewFootprint(){
        var old=Stage06AreaRuntimeTest.context("ground_slam");var plan=f.plan("ground_slam","concentration");var p=f.resolver.resolve(old.profile(),plan);
        var context=new SkillExecutionContext(old.request(),old.rootCastId(),old.skillInstanceId(),p,plan,old.snapshot(),old.equipment(),old.target(),false);
        var runtime=new com.inigmasgames.hytalerpg.execution.area.AreaRuntime();var port=new Stage06AreaRuntimeTest.FakePort();port.targets=java.util.List.of(Stage06AreaRuntimeTest.target("inside",3,0),Stage06AreaRuntimeTest.target("outside",4,0));
        runtime.start(context,com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO,com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,0,1,port);
        assertEquals(java.util.List.of("inside"),port.hitIds);assertEquals(0,runtime.size());
    }
}
