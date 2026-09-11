package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage10CageGateTest {
    private final Stage04SkillProfile profile=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog()).require("bone_cage");
    static class Harness extends Stage10SummonTest.Harness{
        int preflights,targets,dispatches;
        Harness(){super("bone_cage");}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){preflights++;return Validation.pass();}
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest r){targets++;throw new AssertionError("Disabled cage cannot query targets");}
        @Override public SkillExecutionResult executeArea(SkillExecutionContext c){dispatches++;throw new AssertionError("Disabled cage cannot create effects");}
    }
    @Test void canonicalGeometryAndCostsAreRetainedWithoutClaimingCollider(){var c=profile.cage();assertEquals(28,profile.resourceCost());assertEquals(18,profile.cooldownSeconds());assertEquals(18,c.range());assertEquals(4,c.radius());assertEquals(12,c.segments());assertEquals(2.5,c.height());assertEquals(.25,c.thickness());assertEquals(5,c.duration());assertEquals(.6,c.coefficient());assertEquals(1,c.rootSeconds());}
    @Test void allTwelveSegmentsUseAuthoritativeRadiusHeightAndThickness(){var c=profile.cage();var origin=new Vec3(12,3,-4);var edges=c.boundary(origin);assertEquals(12,edges.size());for(var edge:edges){assertEquals(4,edge.start().subtract(origin).length(),1e-9);assertEquals(2.5,edge.height());assertEquals(.25,edge.thickness());assertEquals(3,edge.start().y());}}
    @Test void segmentsCloseWithEqualThirtyDegreeSpacing(){var edges=profile.cage().boundary(Vec3.ZERO);double width=8*Math.sin(Math.PI/12);for(int i=0;i<12;i++){var a=edges.get(i);var b=edges.get((i+1)%12);assertEquals(0,a.end().subtract(b.start()).length(),1e-9);assertEquals(width,a.end().subtract(a.start()).length(),1e-9);}}
    @Test void gateRejectsBeforeAnyNativePreflightCostCooldownTargetOrDispatch(){var h=new Harness();var result=h.cast();assertEquals(SkillExecutionResult.Status.REJECTED,result.status());assertEquals(SelectiveCageProfile.BLOCKED_BOUNDARY,result.code());assertEquals(100,h.mana);assertTrue(h.kernel.cooldowns().canActivate(h.actor,"bone_cage"));assertEquals(0,h.preflights+h.targets+h.dispatches);}
    @Test void repeatsCannotBypassGateOrAccumulateWork(){var h=new Harness();for(int i=0;i<20;i++)assertEquals(SelectiveCageProfile.BLOCKED_BOUNDARY,h.cast().code());assertEquals(100,h.mana);assertEquals(0,h.dispatches);}
    @Test void skillDelayCannotArmAnUnsafeCage(){var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);assertEquals(SelectiveCageProfile.BLOCKED_BOUNDARY,h.cast().code());h.now=20;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.dispatches);assertEquals(100,h.mana);}
    @Test void potencyDoesNotBypassSafetyGate(){var h=new Harness();h.link("potency",PassiveSlot.PASSIVE01);assertEquals(SelectiveCageProfile.BLOCKED_BOUNDARY,h.cast().code());assertEquals(0,h.dispatches);}
    @Test void noNativeColliderOrAreaComponentIsPackagedAsAWorkingFallback(){assertNull(profile.area());assertNull(profile.summon());assertNull(profile.conversion());assertEquals(Stage04SkillProfile.Family.WALL,profile.family());}
    @Test void malformedGeometryRejected(){assertThrows(IllegalArgumentException.class,()->new SelectiveCageProfile(18,4,11,2.5,.25,5,.6,1));assertThrows(IllegalArgumentException.class,()->new SelectiveCageProfile(18,Double.NaN,12,2.5,.25,5,.6,1));}
    @Test void finalStage10InventoryIsExactlyNineNotAnExtraCanonicalSkill(){
        var profiles=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog());
        var stage10=Set.of("consume_minion","simulacrum","corpse_burst","wolf_summon","bone_cage","revive_fallen","summon_void_crawlers","brood_call","dominate");
        assertEquals(9,Stage04SkillProfiles.EXPECTED_STAGE10_PROFILES);assertEquals(9,stage10.size());
        for(var id:stage10)assertTrue(profiles.supports(id),id);
        // The original 60 profiles remain; Stage13 explicitly adds the previously absent catalog records.
        assertEquals(60,profiles.all().size()-Stage04SkillProfiles.EXPECTED_STAGE13_PROFILES-2); // V adds two, not a Stage10 summon.
        assertEquals(89,Stage01BTestSupport.bundle().catalog().skills().size());
    }
}
