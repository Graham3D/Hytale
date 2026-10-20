package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11FoundationTest {
    final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
    final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());
    final CompiledProfileResolver resolver=new CompiledProfileResolver();
    CompiledSkillPlan plan(String skill,String... passives){
        var actor=UUID.randomUUID();assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
        for(int i=0;i<passives.length;i++){
            var slot=PassiveSlot.values()[i];assertTrue(bundle.service().equipPassive(actor,slot,new PassiveId(passives[i])).success());
            var result=bundle.service().link(actor,LinkNodeId.valueOf(slot.name()),LinkNodeId.SKILL01);
            assertTrue(result.success(),skill+" / "+passives[i]+": "+result);
        }
        return bundle.service().getPresentationView(actor).plans().get(SkillSlot.SKILL01);
    }
    Stage04SkillProfile effective(String skill,String... passives){return resolver.resolve(profiles.require(skill),plan(skill,passives));}
    boolean accepts(String skill,String passive){return new CompatibilityService().assess(bundle.catalog().skill(new SkillId(skill)).orElseThrow(),bundle.catalog().passive(new PassiveId(passive)).orElseThrow()).accepted();}
    @Test void efficiencyCommitsSeventeenManaInsteadOfTwentyExactlyOnce(){var h=new Stage10SummonTest.Harness();h.link("efficiency",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(83,h.mana);assertEquals(17,h.context.snapshot().resourceCost().amount());assertFalse(h.cast().committed());assertEquals(83,h.mana);}
    @Test void efficiencyAcceptsUpkeepWithoutUpfrontSpend(){assertTrue(accepts("void_beam","efficiency"));var p=plan("void_beam","efficiency");var resources=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().resources();assertEquals(.85,resources.evaluateUpkeep(new ResourceCost(ResourceType.MANA,1),p.kernelModifiers()).amount(),1e-9);}
    @Test void efficiencyNeverCeilsContinuousSlices(){var p=plan("life_drain","efficiency");var resources=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().resources();assertEquals(.2125,resources.evaluateUpkeep(new ResourceCost(ResourceType.MANA,.25),p.kernelModifiers()).amount(),1e-9);}
    @Test void efficiencyRejectsManaguardReservation(){assertFalse(accepts("managuard","efficiency"));}
    @Test void efficiencyRejectsEmanatismReservationDespiteImportedFiniteCostTag(){assertFalse(accepts("emanatism","efficiency"));}
    @Test void efficiencyZeroAndOneFloorRetained(){assertEquals(0,ResourceCost.NONE.modified(.85).amount());assertEquals(1,new ResourceCost(ResourceType.MANA,.1).modified(.85).amount());assertEquals(8,new ResourceCost(ResourceType.STAMINA,9).modified(.85).amount());}
    @Test void projectileRangeChangesOnlyTravelAndDerivedTime(){var authored=profiles.require("fire_bolt").projectile();var p=effective("fire_bolt","long_reach").projectile();assertEquals(30,p.maxDistance());assertEquals(authored.radius(),p.radius());assertEquals(authored.speed(),p.speed());assertEquals(authored.maximumLifetimeSeconds()*1.25,p.maximumLifetimeSeconds(),1e-9);assertEquals(authored.statusSeconds(),p.statusSeconds());}
    @Test void reachNotAppliedTwiceWhenResolvingRepeatedly(){var authored=profiles.require("fire_bolt");var p=plan("fire_bolt","long_reach");var a=resolver.resolve(authored,p);for(int i=0;i<100;i++)assertSame(a,resolver.resolve(authored,p));assertEquals(24,authored.projectile().maxDistance());assertEquals(1,resolver.cachedProfiles());}
    @Test void leapRangeDoesNotIncreaseLandingOrStrikeRadius(){var p=effective("pounce","long_reach");assertEquals(10,p.movement().maxDistance());assertEquals(1.5,p.movement().landingRadius());assertEquals(1.5,p.strike().range());assertEquals(.8,p.movement().maximumDurationSeconds());}
    @Test void placementRangeLeavesTrapRadiusArmingAndStatusAlone(){var a=profiles.require("root_snare").area();var p=effective("root_snare","long_reach").area();assertEquals(20,p.placementRange());assertEquals(a.radius(),p.radius());assertEquals(a.armingSeconds(),p.armingSeconds());assertEquals(a.statusSeconds(),p.statusSeconds());}
    @Test void travelingWaveMaintainsSpeedAndReachesNewEndpoint(){var a=profiles.require("wind_cutter").connection();var p=effective("wind_cutter","long_reach").connection();assertEquals(20,p.range());assertEquals(20,p.speed());assertEquals(1,p.lifetimeSeconds());assertEquals(a.width(),p.width());}
    @Test void reachDoesNotScaleBeamDurationTicksOrUpkeep(){var a=profiles.require("void_beam").connection();var p=effective("void_beam","long_reach").connection();assertEquals(27.5,p.range());assertEquals(a.lifetimeSeconds(),p.lifetimeSeconds());assertEquals(a.intervalSeconds(),p.intervalSeconds());assertEquals(a.upkeepPerSecond(),p.upkeepPerSecond());}
    @Test void wallPlacementNotWallFootprintGetsReach(){var a=profiles.require("wall_of_fire").area();var p=effective("wall_of_fire","long_reach").area();assertEquals(22.5,p.placementRange());assertEquals(a.length(),p.length());assertEquals(a.width(),p.width());}
    @Test void coneReachNotAngleOrInnerStatusRadiusGetsReach(){var a=profiles.require("cold_wave").area();var p=effective("cold_wave","long_reach").area();assertEquals(15,p.radius());assertEquals(a.angleDegrees(),p.angleDegrees());assertEquals(a.innerRadius(),p.innerRadius());}
    @Test void reachRejectsSelfShield(){assertFalse(accepts("managuard","long_reach"));}
    @Test void reachRejectsRadiusOnlyEvenWhenImportedTagSaysRange(){for(String skill:List.of("frost_nova","ground_slam","orbiting_shadow_blades","battle_cry","reaping_storm"))assertFalse(accepts(skill,"long_reach"),skill);}
    @Test void reachAllowsSummonPlacementButNotLeash(){var a=profiles.require("wolf_summon").summon();var p=effective("wolf_summon","long_reach").summon();assertEquals(7.5,p.range());assertEquals(a.leash(),p.leash());assertEquals(a.lifetime(),p.lifetime());}
    @Test void rapidInvocationScalesWindupNotCooldownOrStrikeCadence(){var a=profiles.require("heavy_swing");var p=effective("heavy_swing","rapid_invocation");assertEquals(.36,p.windupSeconds(),1e-9);assertEquals(a.cooldownSeconds(),p.cooldownSeconds());assertEquals(a.strike(),p.strike());}
    @Test void rapidInvocationRejectsInstantSkill(){assertFalse(accepts("quick_slash","rapid_invocation"));}
    @Test void rapidInvocationRejectsReactionWindow(){assertFalse(accepts("riposte","rapid_invocation"));}
    @Test void rapidInvocationCannotAccelerateSkillDelay(){var p=plan("heavy_swing","rapid_invocation","skill_delay");assertEquals(2,p.executionModifiers().delaySeconds());assertEquals(.36,resolver.resolve(profiles.require("heavy_swing"),p).windupSeconds(),1e-9);}
    @Test void rapidInvocationKeepsMinimumAndDoesNotMakeZeroNonzero(){var m=FoundationModifiers.from(List.of(new PassiveId("rapid_invocation")));assertEquals(.05,m.windup(.01));assertEquals(0,m.windup(0));}
    @Test void compiledPlanIsSlotScopedAndHasNoRangeLeak(){var plain=plan("fire_bolt");var modified=plan("fire_bolt","long_reach");assertNotEquals(plain.planHash(),modified.planHash());assertSame(profiles.require("fire_bolt"),resolver.resolve(profiles.require("fire_bolt"),plain));assertEquals(CompiledSkillPlan.CURRENT_SCHEMA,modified.schemaVersion());}
    @Test void mismatchedPlanCannotRewriteAnotherSkill(){assertThrows(IllegalArgumentException.class,()->resolver.resolve(profiles.require("heavy_swing"),plan("fire_bolt","long_reach")));}
    @Test void realExecutionReceivesEffectiveWindupAndReachBeforeCommit(){
        var h=new Stage09SupportRuntimeTest.Harness("heavy_swing"){
            @Override public Equipment equipment(){return new Equipment(new Item("fixture","LONGSWORD",new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_HEAVY"),20d,20d)),null);}
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){assertEquals(3.75,p.strike().range());return Validation.pass();}
            @Override public SkillExecutionResult executeStrike(SkillExecutionContext c){context=c;return SkillExecutionResult.committed("FIXTURE_DISPATCH",0,0);}
        };
        h.link("long_reach",PassiveSlot.PASSIVE01);h.link("rapid_invocation",PassiveSlot.PASSIVE02);h.link("efficiency",PassiveSlot.PASSIVE03);
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertEquals(.36,h.execution.activeWindupSeconds(h.actor).orElseThrow(),1e-9);assertEquals(100,h.mana);
        var result=h.execution.completeWindup(h.actor,h);assertEquals(SkillExecutionResult.Status.COMMITTED,result.status(),result.code());assertEquals(92,h.mana);assertEquals(3.75,h.context.profile().strike().range());
        assertEquals(1.8,h.context.profile().cooldownSeconds());
        assertEquals(h.kernel.cooldowns().calculate(1.8,1,h.context.snapshot().derivedStats().cooldownRecovery(),CompiledSkillPlan.KernelModifiers.NONE).finalSeconds(),h.context.snapshot().cooldownSeconds(),1e-9);
    }
}
