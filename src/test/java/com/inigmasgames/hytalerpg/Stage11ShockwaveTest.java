package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.strike.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.Stage11StrikeSecondaryTest.target;

class Stage11ShockwaveTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    final Stage11StrikeSecondaryTest n=new Stage11StrikeSecondaryTest();
    static class P extends Stage11StrikeSecondaryTest.P {
        final Map<String,AreaGeometry.Bounds> boxes=new HashMap<>();AreaGeometry visual;
        @Override public AreaGeometry.Bounds bounds(StrikeGeometryService.Candidate<String> t){return boxes.getOrDefault(t.stableId(),new AreaGeometry.Bounds(t.position(),t.position()));}
        @Override public void presentShockwave(AreaGeometry geometry){visual=geometry;}
    }
    Stage11ResourcePassivesTest.H h(String skill,String...passives){
        var h=new Stage11ResourcePassivesTest.H(skill){@Override public Equipment equipment(){return new Equipment(new Item("weapon","LONGSWORD",new ItemPowerDescriptor("weapon",Set.of("LONGSWORD"),20d,20d)),new Item("shield","SHIELD",new ItemPowerDescriptor("shield",Set.of("SHIELD"),20d,20d)));}};
        for(int i=0;i<passives.length;i++)h.link(passives[i],PassiveSlot.values()[i]);var r=h.cast();if(r.status()==SkillExecutionResult.Status.PENDING)r=h.service.completeWindup(h.actor,h);assertEquals(SkillExecutionResult.Status.COMMITTED,r.status());return h;
    }
    List<StrikeSecondaryRuntime.Hit<String>> hit(double resolved,double unit){return List.of(new StrikeSecondaryRuntime.Hit<>(target("primary",0,0,1),resolved,7,false,unit));}
    @Test void shockwavePositiveAndNonStrikeNegativeGates(){assertTrue(f.accepts("heavy_swing","shockwave"));assertTrue(f.accepts("shield_bash","shockwave"));assertFalse(f.accepts("frost_bolt","shockwave"));assertFalse(f.accepts("managuard","shockwave"));assertFalse(f.accepts("riposte","shockwave"));}
    @Test void burstTagsAreComponentLocalNotAConversionOrParentCapabilityLeak(){var p=f.plan("shield_bash","shockwave","expanded_radius");assertEquals("STRIKE",p.finalFamily());assertFalse(p.finalTags().contains("BURST"));assertFalse(p.finalTags().contains("HAS_RADIUS"));assertTrue(p.strikes().introducedTags().containsAll(Set.of("BURST","AREA","DAMAGE","HAS_RADIUS")));assertTrue(p.radiusOnlyOnShockwave());}
    @Test void radiusRequiresIntroducedShockwaveAndDoesNotAcceptPlainCleave(){assertFalse(f.accepts("shield_bash","expanded_radius"));var p=f.plan("shield_bash","shockwave","expanded_radius");assertTrue(p.executionModifiers().expandedRadius());var h=h("shield_bash","cleaving_edge");h.b.service().equipPassive(h.actor,PassiveSlot.PASSIVE02,new PassiveId("expanded_radius"));assertFalse(h.b.service().link(h.actor,LinkNodeId.PASSIVE02,LinkNodeId.SKILL01).success());}
    @Test void secondaryOnlyConcentrationLeavesParentGeometryAndIncreasedDamageUntouched(){var h=h("shield_bash","shockwave","concentration","potency");assertTrue(h.last().compiledPlan().concentrationOnlyOnSecondary());assertEquals(1.15,h.last().snapshot().modifiers().factor(),1e-9);assertEquals(60,h.last().profile().strike().angleDegrees());assertEquals(.7,h.last().profile().strike().coefficient());}
    @Test void secondaryOnlyRadiusLeavesParentDamageAndReachUntouched(){var h=h("shield_bash","shockwave","expanded_radius");assertEquals(1,h.last().snapshot().modifiers().factor(),1e-9);assertEquals(2.2,h.last().profile().strike().range());assertEquals(60,h.last().profile().strike().angleDegrees());assertEquals(8,h.last().snapshot().resourceCost().amount());}
    @Test void fortyPercentUsesResolvedPremitigationNotHealthLoss(){var h=h("quick_slash","shockwave");var p=new P();p.candidates=List.of(target("primary",0,0,1),target("other",1,0,1));assertEquals(2,n.run(h.last(),hit(100,100),p));for(var d:p.delivered)assertEquals(40,d.resolved());assertEquals(1,h.last().effects().triggered());assertEquals(2,h.last().effects().spawned());}
    @Test void shockwaveMayHitPrimaryButCannotDuplicateSameTargetWithinBurst(){var h=h("quick_slash","shockwave");var p=new P();var t=target("primary",0,0,1);p.candidates=List.of(t,t,t);n.run(h.last(),hit(100,100),p);assertEquals(1,p.delivered.size());assertEquals("primary",p.delivered.getFirst().target());}
    @Test void firstSuccessfulContactChosenAfterCancelledOrZeroLossContact(){var h=h("quick_slash","shockwave");var p=new P();p.candidates=List.of(target("other",0,0,2));var hits=List.of(new StrikeSecondaryRuntime.Hit<>(target("cancelled",0,0,1),999,20,true,999),new StrikeSecondaryRuntime.Hit<>(target("blocked",0,0,1),999,0,false,999),hit(50,50).getFirst(),hit(90,90).getFirst());n.run(h.last(),hits,p);assertEquals(20,p.delivered.getFirst().resolved());}
    @Test void missedInitialHitCanTriggerOnLaterAuthoredRootHitExactlyOnce(){var h=h("quick_slash","shockwave");var p=new P();p.candidates=List.of(target("other",0,0,2));assertEquals(0,n.run(h.last(),List.of(),p));assertEquals(1,new StrikeSecondaryRuntime().afterPrimary(h.last(),1,Vec3.ZERO,Vec3.FORWARD,hit(100,100),p));assertEquals(0,new StrikeSecondaryRuntime().afterPrimary(h.last(),2,Vec3.ZERO,Vec3.FORWARD,hit(100,100),p));}
    @Test void shockwaveNotTriggeredByMultistrikeOrOtherSecondary(){var h=h("shield_bash","shockwave","multistrike","cleaving_edge");var p=new P();p.candidates=List.of(target("other",0,0,2));assertEquals(0,n.run(h.last().multistrikeCopy(1),hit(100,100),p));assertEquals(0,n.run(h.last().secondaryCopy("cleaving_edge",1,.6),hit(100,100),p));assertEquals(0,h.last().effects().triggered());}
    @Test void cylinderIncludesLargeBoundsOutsideRadiusButNotTargetsAboveHeight(){var h=h("quick_slash","shockwave");var p=new P();p.candidates=List.of(target("large",4,0,1),target("high",0,3.1,1),target("outside",0,0,4.1));p.boxes.put("large",new AreaGeometry.Bounds(new Vec3(2.8,0,0),new Vec3(5,2,2)));n.run(h.last(),hit(100,100),p);assertEquals(List.of("large"),p.delivered.stream().map(Stage11StrikeSecondaryTest.P.Delivery::target).toList());assertEquals(3,p.visual.height());assertEquals(3,p.visual.radius());}
    @Test void concentrationAndRadiusUseMatchingActualBurstGeometry(){var h=h("shield_bash","shockwave","expanded_radius","concentration");var p=new P();p.candidates=List.of(target("other",0,0,2));n.run(h.last(),hit(100,100),p);assertEquals(3*1.25*.7,p.visual.radius(),1e-9);assertEquals(3,p.visual.height());assertEquals(46.8,p.delivered.getFirst().resolved(),1e-9);}
    @Test void additiveConcentrationDoesNotBecomeThirtyPercentMoreOnPotency(){var p=f.plan("shield_bash","shockwave","concentration","potency");assertEquals(58,StrikeSecondaryRuntime.shockwaveAmount(p,hit(115,100).getFirst()),1e-9);assertNotEquals(115*.4*1.3,StrikeSecondaryRuntime.shockwaveAmount(p,hit(115,100).getFirst()),1e-9);}
    @Test void increasedUnitRetainsExistingCritMoreLessButNotIncreasedBucket(){var buckets=new ModifierBuckets(List.of(.15),List.of(),List.of(1.35),List.of(.1));var result=new DamageCalculationService.Result(100,1,100,100,buckets.factor(),100*buckets.factor(),true,200*buckets.factor());assertEquals(200*1.35*.9,result.increasedUnit(buckets,2),1e-9);var p=f.plan("shield_bash","shockwave","concentration","potency");assertEquals(200*1.35*.9*1.45*.4,StrikeSecondaryRuntime.shockwaveAmount(p,hit(result.preMitigationDamage(),result.increasedUnit(buckets,2)).getFirst()),1e-9);}
    @Test void originalAreaConcentrationAlreadyAppliedIsNotAddedTwice(){var h=h("quick_slash","shockwave","concentration","potency");assertFalse(h.last().compiledPlan().concentrationOnlyOnSecondary());assertEquals(1.45,h.last().snapshot().modifiers().factor(),1e-9);assertEquals(58,StrikeSecondaryRuntime.shockwaveAmount(h.last().compiledPlan(),hit(145,100).getFirst()),1e-9);}
    @Test void conditionalPrimaryIncreasedRemainsInResolvedAmountWithoutAnotherVictimCheck(){var p=f.plan("shield_bash","shockwave","concentration","executioner","potency");assertEquals((150+30)*.4,StrikeSecondaryRuntime.shockwaveAmount(p,hit(150,100).getFirst()),1e-9);}
    @Test void missingResolvedAdditiveUnitFailsClosedOnlyWhenNeeded(){var p=f.plan("shield_bash","shockwave","concentration");assertThrows(IllegalArgumentException.class,()->StrikeSecondaryRuntime.shockwaveAmount(p,hit(100,Double.NaN).getFirst()));assertEquals(40,StrikeSecondaryRuntime.shockwaveAmount(f.plan("quick_slash","shockwave"),hit(100,Double.NaN).getFirst()));assertThrows(IllegalArgumentException.class,()->StrikeSecondaryRuntime.shockwaveAmount(p,hit(Double.POSITIVE_INFINITY,100).getFirst()));}
    @Test void shockwaveHasOneResourceCooldownAndSharedRootLeechBudget(){var h=h("quick_slash","shockwave","leeching");var p=new P();p.candidates=List.of(target("other",0,0,2));n.run(h.last(),hit(100,100),p);assertEquals(95,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);var c=p.delivered.getFirst().child();assertSame(h.last().effects(),c.effects());assertSame(h.last().leechBudget(),c.leechBudget());assertEquals("shockwave",c.secondaryKind());assertTrue(c.derivedRelease());}
    @Test void shockwaveTargetCountAndSpatialOverflowBounded(){
        var h=h("quick_slash","shockwave");var rejected=new ArrayList<String>();
        var p=new P(){@Override public void rejected(String effect,String reason){rejected.add(reason);}};
        for(int i=0;i<64;i++)p.candidates.add(target("enemy"+i,0,0,2));
        assertEquals(64,n.run(h.last(),hit(100,100),p));assertEquals(64,p.delivered.size());assertEquals(1,h.last().effects().triggered());
        for(int size:List.of(65,100,257)){
            while(p.candidates.size()<size)p.candidates.add(target("enemy"+p.candidates.size(),0,0,2));
            p.delivered.clear();rejected.clear();var other=h("quick_slash","shockwave");
            assertEquals(0,n.run(other.last(),hit(100,100),p));assertTrue(p.delivered.isEmpty());
            assertEquals(List.of("STRIKE_SECONDARY_QUERY_OVERFLOW"),rejected);
        }
    }
    @Test void shockwaveConsumesSharedSecondaryBudgetBeforeNativeDispatch(){var h=h("quick_slash","shockwave","cleaving_edge");for(int i=0;i<16;i++)h.last().effects().claim("prior"+i,1,true);var p=new P();p.candidates=List.of(target("other",0,0,2));assertEquals(0,n.run(h.last(),hit(100,100),p));assertTrue(p.delivered.isEmpty());}
    @Test void uncertainBurstDamageCannotRepeatItsOnceOnlyController(){var h=h("quick_slash","shockwave");var p=new P();p.candidates=List.of(target("other",0,0,2));p.fail=true;assertThrows(IllegalStateException.class,()->n.run(h.last(),hit(100,100),p));assertEquals(0,n.run(h.last(),hit(100,100),p));assertEquals(1,p.delivered.size());assertEquals(1,h.last().effects().triggered());}
    @Test void introducedCleaveConcentrationLeavesSingleTargetParentAlone(){var h=h("shield_bash","cleaving_edge","concentration","potency");assertTrue(h.last().compiledPlan().concentrationOnlyOnSecondary());assertEquals(1.15,h.last().snapshot().modifiers().factor(),1e-9);assertEquals(60,h.last().profile().strike().angleDegrees());var p=new P();p.candidates=List.of(target("other",0,0,2));n.run(h.last(),hit(115,100),p);assertEquals(1.45*.6,p.delivered.getFirst().child().snapshot().modifiers().factor(),1e-9);}
    @Test void introducedAreaConcentrationDoesNotBuffPhantomNonAreaPacket(){var h=h("shield_bash","shockwave","phantom_reach","concentration");var p=new P();p.candidates=List.of(target("other",0,0,2));n.run(h.last(),hit(100,100),p);assertEquals(60,p.delivered.stream().filter(d->d.child().secondaryKind().equals("phantom_reach")).findFirst().orElseThrow().resolved());assertEquals(52,p.delivered.stream().filter(d->d.child().secondaryKind().equals("shockwave")).findFirst().orElseThrow().resolved());}
    @Test void cleaveAndPhantomNowUseActualTargetBoundsInsteadOfPointCentres(){for(String passive:List.of("cleaving_edge","phantom_reach")){var h=h("quick_slash",passive);var p=new P();p.candidates=List.of(target("large",4,0,2));p.boxes.put("large",new AreaGeometry.Bounds(new Vec3(.1,0,1.5),new Vec3(4.5,2.5,2.5)));assertEquals(1,n.run(h.last(),hit(100,100),p));}}
    @Test void introducedRadiusLinkRemovalRollsBackUntilDependentModifierUnlinked(){var h=h("shield_bash","shockwave","expanded_radius");var before=h.b.service().getPresentationView(h.actor).state().linkEdges();var edge=before.stream().filter(e->e.sourceNodeId()==LinkNodeId.PASSIVE01).findFirst().orElseThrow().edgeId();var result=h.b.service().unlink(h.actor,edge);assertFalse(result.success());assertEquals(before,h.b.service().getPresentationView(h.actor).state().linkEdges());}
}
