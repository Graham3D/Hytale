package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.connection.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.links.ValidationCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11GeometryImpactTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void impactAcceptsDeclaredKnockbackAndStaggerDespiteMissingImportedTags(){for(String id:List.of("ground_slam","shield_bash","stone_bolt"))assertTrue(f.accepts(id,"impact_force"),id);}
    @Test void impactCannotInventImpactOnOrdinaryDamageOrRoot(){for(String id:List.of("arcane_bolt","quick_slash","root_snare","root_lash"))assertFalse(f.accepts(id,"impact_force"),id);}
    @Test void impactOnlyScalesAuthoredKnockbackAndDirectMagnitude(){var a=f.profiles.require("stone_bolt");var p=f.effective("stone_bolt","impact_force");assertEquals(2.625,p.projectile().knockbackDistance());assertEquals(1.08,p.projectile().coefficient(),1e-9);assertEquals(a.projectile().radius(),p.projectile().radius());assertEquals(a.projectile().speed(),p.projectile().speed());assertEquals(a.projectile().maxDistance(),p.projectile().maxDistance());assertEquals(a.resourceCost(),p.resourceCost());assertEquals(a.cooldownSeconds(),p.cooldownSeconds());}
    @Test void impactScalesSlamPushNotItsArea(){var a=f.profiles.require("ground_slam");var p=f.effective("ground_slam","impact_force");assertEquals(3.5,p.area().displacement());assertEquals(1.395,p.area().coefficient(),1e-9);assertEquals(a.area().radius(),p.area().radius());assertEquals(a.area().height(),p.area().height());assertEquals(a.area().edgeFalloff(),p.area().edgeFalloff());}
    @Test void impactScalesAuthoredStaggerBeforeResistance(){var p=f.effective("shield_bash","impact_force");assertEquals(1.05,p.strike().statusSeconds(),1e-9);var statuses=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().statuses();var result=statuses.apply(UUID.randomUUID(),RpgStatusType.STAGGER,new ControlProfile(false,false,false,true),p.strike().statusSeconds());assertEquals(.525,result.remainingSeconds(),1e-9);}
    @Test void impactCannotBypassBossOrProtectedControl(){var p=f.effective("shield_bash","impact_force");var statuses=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().statuses();for(var control:List.of(new ControlProfile(false,true,false),new ControlProfile(true,false,false)))assertEquals(StatusService.Outcome.REJECTED,statuses.apply(UUID.randomUUID(),RpgStatusType.STAGGER,control,p.strike().statusSeconds()).outcome());}
    @Test void impactSharesExistingRollingControlResistance(){var duration=f.effective("shield_bash","impact_force").strike().statusSeconds();var statuses=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().statuses();var target=UUID.randomUUID();assertEquals(duration,statuses.apply(target,RpgStatusType.STAGGER,ControlProfile.NORMAL,duration).remainingSeconds());assertEquals(duration*.5,statuses.apply(target,RpgStatusType.STAGGER,ControlProfile.NORMAL,duration).remainingSeconds());assertEquals(duration*.25,statuses.apply(target,RpgStatusType.STAGGER,ControlProfile.NORMAL,duration).remainingSeconds());assertEquals(StatusService.Outcome.REJECTED,statuses.apply(target,RpgStatusType.STAGGER,ControlProfile.NORMAL,duration).outcome());}
    @Test void impactDoesNotIntroduceGlobalKnockbackTag(){assertFalse(f.plan("shield_bash","impact_force").finalTags().contains("APPLIES_KNOCKBACK"));}
    @Test void wideningPositiveAndTwoNegativeFixtures(){assertTrue(f.accepts("wind_cutter","widening"));assertFalse(f.accepts("frost_nova","widening"));assertFalse(f.accepts("fire_bolt","widening"));}
    @Test void focusedChannelPositiveAndTwoNegativeFixtures(){assertTrue(f.accepts("void_beam","focused_channel"));assertFalse(f.accepts("fire_bolt","focused_channel"));assertFalse(f.accepts("frost_nova","focused_channel"));}
    @Test void focusedWorksOnNonChannelledInstantLine(){assertTrue(f.accepts("lightning_bolt","focused_channel"));var a=f.profiles.require("lightning_bolt").connection();var p=f.effective("lightning_bolt","focused_channel").connection();assertFalse(p.channel());assertEquals(.65,p.width(),1e-9);assertEquals(a.coefficient(),p.coefficient());}
    @Test void targetLockWidthDoesNotBecomeAResolvingPrism(){for(String id:List.of("root_lash","life_drain","chain_lightning")){assertFalse(f.accepts(id,"widening"));assertFalse(f.accepts(id,"focused_channel"));}}
    @Test void wideningRetainsReachHeightSpeedLifetimeCostAndCooldown(){var a=f.profiles.require("wind_cutter");var p=f.effective("wind_cutter","widening");assertEquals(1.8,p.connection().width(),1e-9);assertEquals(.95*.85,p.connection().coefficient(),1e-9);assertEquals(a.connection().range(),p.connection().range());assertEquals(a.connection().height(),p.connection().height());assertEquals(a.connection().speed(),p.connection().speed());assertEquals(a.connection().lifetimeSeconds(),p.connection().lifetimeSeconds());assertEquals(a.cooldownSeconds(),p.cooldownSeconds());assertEquals(a.resourceCost(),p.resourceCost());}
    @Test void focusedMagnitudeIsIncreasedNotMore(){var p=f.plan("void_beam","focused_channel","potency");assertEquals(.45,p.kernelModifiers().scalablePayloadIncreased(),1e-9);assertEquals(.52,f.resolver.resolve(f.profiles.require("void_beam"),p).connection().width(),1e-9);}
    @Test void minimumWidthIsAppliedAfterCombinedGeometry(){var geometry=GeometryModifiers.from(List.of(new PassiveId("focused_channel")));assertEquals(.10,geometry.width(.05));assertEquals(.10,geometry.width(.1));assertEquals(.65,geometry.width(1));assertThrows(IllegalArgumentException.class,()->geometry.width(0));}
    @Test void concentrationAndFocusedPreserveCommonWidthFloorAndAdditiveDamage(){var p=f.plan("void_beam","concentration","focused_channel");var result=f.resolver.resolve(f.profiles.require("void_beam"),p);assertEquals(.8*.7*.65,result.connection().width(),1e-9);assertEquals(.6,p.kernelModifiers().scalablePayloadIncreased(),1e-9);}
    @Test void longReachAndWideningTouchIndependentFields(){var p=f.effective("wind_cutter","long_reach","widening");assertEquals(20,p.connection().range());assertEquals(1.8,p.connection().width(),1e-9);assertEquals(2.5,p.connection().height());assertEquals(1,p.connection().lifetimeSeconds());}
    @Test void cachedPlansDoNotMixFocusedAndWidenedProfiles(){var a=f.profiles.require("wind_cutter");var w=f.resolver.resolve(a,f.plan("wind_cutter","widening"));var n=f.resolver.resolve(a,f.plan("wind_cutter","focused_channel"));assertEquals(1.8,w.connection().width(),1e-9);assertEquals(.78,n.connection().width(),1e-9);assertEquals(1.2,a.connection().width());assertEquals(2,f.resolver.cachedProfiles());}
    @Test void conflictingLinkRollsBackGraphInEitherOrder(){
        for(var order:List.of(List.of("widening","focused_channel"),List.of("focused_channel","widening"))){
            var b=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("void_beam"));
            b.service().equipPassive(actor,PassiveSlot.PASSIVE01,new PassiveId(order.getFirst()));b.service().equipPassive(actor,PassiveSlot.PASSIVE02,new PassiveId(order.getLast()));
            assertTrue(b.service().link(actor,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());var before=b.service().getPresentationView(actor);
            var result=b.service().link(actor,LinkNodeId.PASSIVE02,LinkNodeId.SKILL01);assertFalse(result.success());assertEquals(ValidationCode.CONFLICTING_MODIFIER,result.code());
            var after=b.service().getPresentationView(actor);assertEquals(before.plans(),after.plans());assertEquals(before.routes(),after.routes());
            var json=new com.google.gson.Gson();assertEquals(json.toJsonTree(before.state()),json.toJsonTree(after.state()));
        }
    }
    @Test void typedRecordAlsoRefusesConflictingWidthFlags(){assertThrows(IllegalArgumentException.class,()->new GeometryModifiers(false,true,true));}
    @Test void realWaveRuntimeUsesWiderBoundsAndOnlyOnePayment(){
        var narrow=new Stage08ConnectionTest.Harness("wind_cutter");var wide=new Stage08ConnectionTest.Harness("wind_cutter","widening");
        var target=new ConnectionWorldPort.Target("side",new AreaGeometry.Bounds(new Vec3(.8,1,8),new Vec3(.81,1.5,8.1)));
        narrow.targets.add(target);wide.targets.add(target);narrow.cast();wide.cast();narrow.advance(.4);wide.advance(.4);
        assertEquals(0,narrow.hits.size());assertEquals(1,wide.hits.size());assertEquals(.95*.85,wide.hits.getFirst().coefficient(),1e-9);assertEquals(1,wide.resourceWrites);assertEquals(192,wide.mana);
    }
    @Test void focusedBeamRetainsTwentyPaidSlicesAndOneEndCooldown(){
        var h=new Stage08ConnectionTest.Harness("void_beam","focused_channel");h.targets.add(new ConnectionWorldPort.Target("front",new AreaGeometry.Bounds(new Vec3(-.1,1.3,8),new Vec3(.1,1.4,8.1))));h.cast();
        for(int i=1;i<=20;i++)h.advance(i*.25);assertEquals(20,h.hits.size());assertEquals(180,h.mana);assertEquals(20,h.resourceWrites);assertEquals(1,h.cooldownEndTraces());
        assertEquals(1.3,h.contexts.getFirst().snapshot().modifiers().factor(),1e-9);assertEquals(0,h.runtime.size());
    }
    @Test void realSlamRuntimeReceivesResolvedImpactAndKeepsOneRootPayment(){
        var areaRuntime=new AreaRuntime();var port=new Stage06AreaRuntimeTest.FakePort();port.targets=List.of(Stage06AreaRuntimeTest.target("enemy",0,0));
        var h=new Stage09SupportRuntimeTest.Harness("ground_slam"){
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public Equipment equipment(){return new Equipment(new Item("fixture","MACE",new ItemPowerDescriptor("fixture",Set.of("MACE"),20d,null)),null);}
            @Override public SkillExecutionResult executeArea(SkillExecutionContext c){context=c;areaRuntime.start(c,Vec3.ZERO,Vec3.FORWARD,0,1,port);return SkillExecutionResult.committed("FIXTURE_AREA",0,0);}
        };
        h.link("impact_force",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(82,h.mana);assertEquals(1,port.payloads.size());assertEquals(3.5,port.payloads.getFirst().displacement());assertEquals(1.55*.9,port.payloads.getFirst().coefficient(),1e-9);assertFalse(h.cast().committed());assertEquals(82,h.mana);
    }
}
