package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11SourceDotTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void combustionPositiveAndTwoNegativeFixtures(){assertTrue(f.accepts("fire_bolt","combustion"));assertFalse(f.accepts("spark","combustion"));assertFalse(f.accepts("frost_bolt","combustion"));}
    @Test void virulenceCanonicalFixturesRemainContentCompatibilityNotProfileCoverage(){assertTrue(f.accepts("venom_spray","virulence"));assertTrue(f.accepts("poison_cloud","virulence"));assertFalse(f.accepts("fireball","virulence"));assertFalse(f.accepts("frost_bolt","virulence"));}
    @Test void concentratedVenomRequiresActualStackablePoison(){assertTrue(f.accepts("poison_cloud","concentrated_venom"));assertFalse(f.accepts("frost_bolt","concentrated_venom"));assertFalse(f.accepts("vortex","concentrated_venom"));}
    @Test void fireBoltPenaltyDoesNotEnterBurnCoefficientOrCrit(){
        var a=f.profiles.require("fire_bolt");var p=f.effective("fire_bolt","combustion");var op=f.plan("fire_bolt","combustion").dots();
        assertEquals(a.projectile().coefficient()*.85,p.projectile().coefficient(),1e-9);assertEquals(a.projectile().periodicCoefficient(),p.projectile().periodicCoefficient());
        assertEquals(5,p.projectile().statusSeconds());assertEquals(5,p.projectile().periodicTicks());assertEquals(.15,op.application(Kind.BURN,.1).coefficientPerSecond(),1e-9);
        assertEquals(a.resourceCost(),p.resourceCost());assertEquals(a.cooldownSeconds(),p.cooldownSeconds());assertEquals(a.projectile().speed(),p.projectile().speed());
    }
    @Test void combustionDoesNotDiscountContinuousWallDamageAsADirectHit(){
        var a=f.profiles.require("wall_of_fire").area();var p=f.effective("wall_of_fire","combustion").area();assertEquals(a.coefficient(),p.coefficient());assertEquals(6.25,p.statusSeconds());assertEquals(a.lifetimeSeconds(),p.lifetimeSeconds());assertEquals(a.intervalSeconds(),p.intervalSeconds());
    }
    @Test void combustionAndLingeringComposeDurationOnce(){
        var p=f.effective("fire_bolt","lingering","combustion");assertEquals(7,p.projectile().statusSeconds(),1e-9);assertEquals(7,p.projectile().periodicTicks());assertEquals(.15,f.plan("fire_bolt","lingering","combustion").dots().application(Kind.BURN,.1).coefficientPerSecond(),1e-9);
    }
    @Test void virulenceExtendsStatusNotZoneLifeAndAddsTwoAtTheSameCap(){
        var a=f.profiles.require("poison_cloud");var p=f.effective("poison_cloud","virulence");var op=f.plan("poison_cloud","virulence").dots().application(Kind.POISON,.06);
        assertEquals(10.4,p.area().statusSeconds());assertEquals(a.area().lifetimeSeconds(),p.area().lifetimeSeconds());assertEquals(a.area().coefficient(),p.area().coefficient());assertEquals(.06,op.coefficientPerSecond());assertEquals(2,op.addedStacks());assertEquals(3,op.sourceCap());
    }
    @Test void concentratedVenomChangesOnlyItsPoisonDpsAndSourceCap(){
        var a=f.profiles.require("poison_cloud");var p=f.effective("poison_cloud","concentrated_venom");var op=f.plan("poison_cloud","concentrated_venom").dots().application(Kind.POISON,.06);
        assertEquals(a,p);assertEquals(.105,op.coefficientPerSecond(),1e-9);assertEquals(1,op.addedStacks());assertEquals(1,op.sourceCap());
    }
    @Test void poisonCombinedOperatorsAreOrderIndependentAndVirulenceCannotRaiseCap(){
        var a=f.plan("poison_cloud","virulence","concentrated_venom");var b=f.plan("poison_cloud","concentrated_venom","virulence");assertEquals(a.dots(),b.dots());
        var op=a.dots().application(Kind.POISON,.06);assertEquals(2,op.addedStacks());assertEquals(1,op.sourceCap());assertEquals(.105,op.coefficientPerSecond(),1e-9);
    }
    @Test void modifiersCannotLeakFromBurnToPoisonOrViceVersa(){
        assertEquals(.06,f.plan("fire_bolt","combustion").dots().application(Kind.POISON,.06).coefficientPerSecond());
        assertEquals(.1,f.plan("poison_cloud","virulence","concentrated_venom").dots().application(Kind.BURN,.1).coefficientPerSecond());
    }
    @Test void cachedProfilesDistinguishStatusOperatorsAndLeaveAuthoredDataImmutable(){
        var a=f.profiles.require("poison_cloud");var plan=f.plan("poison_cloud","virulence");var p=f.resolver.resolve(a,plan);assertSame(p,f.resolver.resolve(a,plan));assertEquals(8,a.area().statusSeconds());assertEquals(10.4,p.area().statusSeconds());
        assertEquals(8,f.effective("poison_cloud","concentrated_venom").area().statusSeconds());assertNotEquals(plan.planHash(),f.plan("poison_cloud","concentrated_venom").planHash());
    }
    @Test void malformedDotApplicationRejectedBeforeRuntime(){var op=f.plan("fire_bolt","combustion").dots();assertThrows(IllegalArgumentException.class,()->op.application(Kind.BURN,Double.NaN));assertThrows(IllegalArgumentException.class,()->op.application(Kind.BURN,0));}
    @Test void combustionBurnHasFiveTicksAndIndependentMagnitude(){
        var h=new Harness();var s=h.source("fire_bolt",Kind.BURN);var p=f.effective("fire_bolt","combustion").projectile();var op=f.plan("fire_bolt","combustion").dots().application(Kind.BURN,p.periodicCoefficient()/p.periodicIntervalSeconds());
        h.apply(s,"burn",op,p.statusSeconds(),20,0);h.advance(s.owner(),5);assertEquals(5,h.hits.size());assertEquals(.75,h.sum(),1e-9);assertEquals(0,h.runtime.size());
    }
    @Test void wallBurnRetainsProportionalFinalQuarterTick(){
        var h=new Harness();var s=h.source("wall_of_fire",Kind.BURN);var op=f.plan("wall_of_fire","combustion").dots().application(Kind.BURN,.1);
        h.apply(s,"burn",op,f.effective("wall_of_fire","combustion").area().statusSeconds(),20,0);h.advance(s.owner(),6.25);assertEquals(7,h.hits.size());assertEquals(.15*.25,h.hits.getLast().coefficient(),1e-9);assertEquals(.9375,h.sum(),1e-9);
    }
    @Test void virulenceAddsStacksWithoutRetroactiveAccrual(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);var op=f.plan("poison_cloud","virulence").dots().application(Kind.POISON,.06);
        h.apply(s,"poison",op,10.4,20,0);assertEquals(2,h.view(s).stacks());h.apply(s,"poison",op,10.4,20,.5);assertEquals(3,h.view(s).stacks());h.runtime.tick(s.owner(),1,h);assertEquals(.15,h.sum(),1e-9);
    }
    @Test void combinedPoisonCannotExceedOneStackAcrossRepeatedApplications(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);var op=f.plan("poison_cloud","virulence","concentrated_venom").dots().application(Kind.POISON,.06);
        for(int i=0;i<8;i++){h.apply(s,"con",op,10.4,35,i*.1);assertEquals(1,h.view(s).stacks());assertEquals(1,h.view(s).sourceCap());}
        h.runtime.tick(s.owner(),1,h);assertEquals(.105,h.sum(),1e-9);
    }
    @Test void unrelatedSkillsOfSameCasterKeepTheirOwnCaps(){
        var h=new Harness();var a=h.source("poison_cloud",Kind.POISON);var b=new Source(a.owner(),"venom_spray",a.victim(),Kind.POISON);
        h.apply(a,"con",new DotModifiers.Application(.105,2,1),8,35,0);h.apply(b,"plain",new DotModifiers.Application(.06,3,3),6,20,0);
        assertEquals(1,h.view(a).stacks());assertEquals(3,h.view(b).stacks());assertEquals(4,h.runtime.view(a.victim(),Kind.POISON,0).stacks());
    }
    @Test void sameSkillOfOtherCasterIsNotReduced(){
        var h=new Harness();var a=h.source("poison_cloud",Kind.POISON);var b=new Source(UUID.randomUUID(),a.skill(),a.victim(),Kind.POISON);
        h.apply(a,"con",new DotModifiers.Application(.105,2,1),8,35,0);h.apply(b,"other",new DotModifiers.Application(.06,3,3),8,20,0);
        assertEquals(1,h.view(a).sourceCap());assertEquals(3,h.view(b).sourceCap());h.runtime.cancel(a.owner(),.1,h);assertTrue(h.runtime.sourceView(a,.1).isEmpty());assertEquals(3,h.view(b).stacks());
    }
    @Test void removingConcentratedVenomCannotGrowTheRetainedStrongerPackage(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);h.apply(s,"con",new DotModifiers.Application(.105,1,1),8,35,0);
        h.apply(s,"plain",new DotModifiers.Application(.06,3,3),8,20,.5);assertEquals(1,h.view(s).stacks());assertEquals(1,h.view(s).sourceCap());assertEquals(.105,h.view(s).coefficientPerSecond(),1e-9);
        h.runtime.tick(s.owner(),1,h);assertEquals(.105,h.sum(),1e-9);assertTrue(h.hits.stream().allMatch(hit->hit.context().equals("con")));
    }
    @Test void genuinelyStrongerReplacementCarriesItsOwnCapAndDoesNotRewritePastTime(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);h.apply(s,"con",new DotModifiers.Application(.105,1,1),8,35,0);
        h.apply(s,"strongPlain",new DotModifiers.Application(.06,1,3),8,50,.5);assertEquals(2,h.view(s).stacks());assertEquals(3,h.view(s).sourceCap());
        h.apply(s,"strongPlain",new DotModifiers.Application(.06,1,3),8,50,.75);h.runtime.tick(s.owner(),1,h);
        assertEquals(List.of("con","strongPlain"),h.hits.stream().map(Hit::context).toList());assertEquals(.0525,h.hits.getFirst().coefficient(),1e-9);assertEquals(.075,h.hits.getLast().coefficient(),1e-9);
    }
    @Test void strongerConcentratedReplacementShrinksOnlyFutureStackAccrual(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);h.apply(s,"plain",new DotModifiers.Application(.06,3,3),8,20,0);
        h.apply(s,"con",new DotModifiers.Application(.105,2,1),8,35,.5);assertEquals(1,h.view(s).stacks());h.runtime.tick(s.owner(),1,h);
        assertEquals(.09,h.hits.getFirst().coefficient(),1e-9);assertEquals(.0525,h.hits.getLast().coefficient(),1e-9);
    }
    @Test void victimTotalCapStillTwelveWithConcentratedSources(){
        var h=new Harness();UUID victim=UUID.randomUUID();for(int i=0;i<4;i++)h.apply(new Source(new UUID(0,i+1),"poison_cloud",victim,Kind.POISON),"plain"+i,new DotModifiers.Application(.06,3,3),8,20,0);
        var con=new Source(new UUID(0,5),"poison_cloud",victim,Kind.POISON);h.apply(con,"con",new DotModifiers.Application(.105,2,1),8,35,0);
        assertEquals(12,h.runtime.view(victim,Kind.POISON,0).stacks());assertEquals(1,h.view(con).stacks());assertEquals(1,h.view(con).sourceCap());
    }
    @Test void expiredStrongPackageCannotLendMagnitudeOrCapToNewCast(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);h.apply(s,"con",new DotModifiers.Application(.105,1,1),.5,35,0);
        h.apply(s,"plain",new DotModifiers.Application(.06,3,3),8,20,.75);assertEquals(3,h.view(s).stacks());assertEquals(3,h.view(s).sourceCap());assertEquals(.06,h.view(s).coefficientPerSecond());
    }
    @Test void nativeRejectionOrFailureCleansWithoutReplayingEarnedDamage(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);h.apply(s,"con",new DotModifiers.Application(.105,1,1),8,35,0);h.accept=false;h.runtime.tick(s.owner(),1,h);assertEquals(0,h.runtime.size());h.runtime.tick(s.owner(),1,h);assertEquals(1,h.hits.size());
        h.apply(s,"con",new DotModifiers.Application(.105,1,1),8,35,2);h.runtime.tick(s.owner(),5,h);assertEquals(0,h.runtime.size());assertEquals(1,h.hits.size());
    }
    @Test void pureStatusRuntimeDoesNotScaleDurationOrCoefficientAgain(){
        var h=new Harness();var s=h.source("poison_cloud",Kind.POISON);var plan=f.plan("poison_cloud","virulence","concentrated_venom","lingering");var p=f.resolver.resolve(f.profiles.require("poison_cloud"),plan);
        h.apply(s,"captured",plan.dots().application(Kind.POISON,.06),p.area().statusSeconds(),35,0);assertEquals(14.56,h.view(s).remainingSeconds(),1e-9);assertEquals(.105,h.view(s).coefficientPerSecond(),1e-9);
    }
    @Test void realCommitKeepsPaymentAndOffensiveSnapshotSeparateFromHitPenalty(){
        var h=new Stage09SupportRuntimeTest.Harness("fire_bolt"){
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public SkillExecutionResult executeProjectile(SkillExecutionContext c){context=c;return SkillExecutionResult.committed("FIXTURE_PROJECTILE",0,0);}
        };
        h.link("combustion",PassiveSlot.PASSIVE01);h.link("potency",PassiveSlot.PASSIVE02);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());
        assertEquals(100-h.profile.resourceCost(),h.mana);var c=h.context;assertEquals(1.15,c.snapshot().modifiers().factor(),1e-9);
        assertEquals(h.profile.projectile().coefficient()*.85,c.snapshot().skillCoefficient(),1e-9);
        var payload=c.compiledPlan().dots().application(Kind.BURN,.1);
        var result=h.kernel.damage().calculate(com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService.Request.periodic(
                c.snapshot().basePower(),c.snapshot().effectiveAttributes().get(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.INT),payload.coefficientPerSecond(),c.snapshot().modifiers(),1,2));
        assertFalse(result.critical());assertEquals(20*1.03*.15*1.15,result.preMitigationDamage(),1e-9);assertEquals(5,c.snapshot().statusModifiers().get("BURN"));
    }

    record Hit(String context,double coefficient,double seconds){}
    static class Harness implements PeriodicStatusRuntime.Port<String,String>{
        final PeriodicStatusRuntime<String,String> runtime=new PeriodicStatusRuntime<>();final List<Hit> hits=new ArrayList<>();boolean accept=true;
        Source source(String skill,Kind kind){return new Source(UUID.randomUUID(),skill,UUID.randomUUID(),kind);}
        void apply(Source source,String context,DotModifiers.Application op,double duration,double strength,double now){assertTrue(Set.of("APPLIED","REFRESHED").contains(runtime.apply(source,context,"target",op.coefficientPerSecond(),strength,duration,op.addedStacks(),op.sourceCap(),now,this)));}
        PackageView view(Source source){return runtime.sourceView(source,0).orElseThrow();}
        void advance(UUID owner,double end){for(int i=1;i<=Math.ceil(end*4);i++)runtime.tick(owner,Math.min(end,i*.25),this);}
        double sum(){return hits.stream().mapToDouble(Hit::coefficient).sum();}
        public boolean tick(Source s,String c,String t,int index,double coefficient,double seconds){hits.add(new Hit(c,coefficient,seconds));return accept;}
        public void changed(Source s,String t,View aggregate){}
    }
}
