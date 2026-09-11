package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.connection.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.damage.MaxHealthDamageCap;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.Stage08ConnectionCohortBTest.*;

class Stage13SupportTetherTest {
    static class Healing extends Stage08ConnectionTest.Harness {
        boolean held=true,los=true;final Set<String> hostile=new HashSet<>();
        final Map<String,Double> hp=new HashMap<>();final List<Double> coefficients=new ArrayList<>();
        final List<String> recipients=new ArrayList<>();final FiniteSupportEffects overflowEffects=new FiniteSupportEffects();
        Healing(String...passives){super("healing_beam",passives);targets.add(enemy(1,0,1.35,5));}
        public boolean held(SkillExecutionContext c){return held;}
        public Query queryFriendly(ConnectionShape shape,int cap){return new Query(targets.stream().filter(t->resolveFriendly(t.id()).isPresent()&&shape.intersects(t.bounds())).toList(),overflow);}
        public Optional<Target> resolveFriendly(String id){return targets.stream().filter(t->t.id().equals(id)&&!id.equals(owner.toString())&&!hostile.contains(id)&&hp.getOrDefault(id,10d)>0).findFirst();}
        public boolean injured(Target t){return hp.getOrDefault(t.id(),10d)<100;}
        public boolean lineOfSight(Vec3 from,Target t){return los;}
        public double heal(SkillExecutionContext c,Target t,int tick,double coefficient){
            coefficients.add(coefficient);recipients.add(t.id());order.add("heal"+tick);
            double before=hp.getOrDefault(t.id(),10d),requested=SupportMagnitude.tetherHealing(c,coefficient,before,100);
            double after=Math.min(100,before+requested);hp.put(t.id(),after);
            overflowEffects.healingResolved(c,UUID.fromString(t.id()),requested,before,after,100,clock.get()/1e9);
            return after-before;
        }
        void stepTo(double end){double begin=clock.get()/1e9;while(begin+.05<end-1e-8){begin+=.05;advance(begin);}advance(end);}
    }
    @Test void baselineFourPulsesAndFourManaPerSecond(){
        var h=new Healing();assertTrue(h.cast().committed());h.stepTo(1);
        assertEquals(4,h.coefficients.size());assertTrue(h.coefficients.stream().allMatch(c->Math.abs(c-.1125)<1e-12));
        assertEquals(196,h.mana,1e-8);assertEquals(10+20*.45*h.contexts.getFirst().snapshot().derivedStats().healingMultiplier(),h.hp.get(id(1)),1e-8);
        assertEquals(1,h.contexts.size());assertEquals(1,h.runtime.size());assertTrue(h.hits.isEmpty());
    }
    @Test void rapidPulseChangesOnlyPulseCadenceAndEffectNotManaPerSecond(){
        var h=new Healing("rapid_pulse");assertTrue(h.cast().committed());h.stepTo(1);
        assertEquals(5,h.coefficients.size());assertEquals(196,h.mana,1e-8);
        assertEquals(10+5*20*.1125*.8*h.contexts.getFirst().snapshot().derivedStats().healingMultiplier(),h.hp.get(id(1)),1e-8);
    }
    @Test void lockedTargetDoesNotRequireContinuousCrosshair(){var h=new Healing();h.cast();h.aim=new Vec3(1,0,0);h.stepTo(.5);assertEquals(List.of(id(1),id(1)),h.recipients);}
    @Test void releaseImmediatelyRemovesChannelAndLocksReentry(){var h=new Healing();h.cast();h.stepTo(.25);h.held=false;h.advance(.26);assertEquals(0,h.runtime.size());assertEquals(List.of("CHANNEL_INPUT_RELEASED"),h.ends);assertEquals(1,h.coefficients.size());assertFalse(h.kernel.cooldowns().canActivate(h.owner,"healing_beam"));assertEquals(.25,h.kernel.cooldowns().calculate(.25,1,0,null).finalSeconds());}
    @Test void enemyAndSelfAcquisitionRejected(){
        var enemy=new Healing();enemy.hostile.add(id(1));assertFalse(enemy.cast().committed());assertEquals(200,enemy.mana);
        var self=new Healing();self.targets.clear();self.targets.add(Stage08ConnectionTest.target(self.owner.toString(),0,1.35,5));assertFalse(self.cast().committed());
    }
    @Test void noInitialLosRejects(){var h=new Healing();h.los=false;assertFalse(h.cast().committed());assertEquals(0,h.runtime.size());}
    @Test void grace149ContinuesAnd150Ends(){var h=new Healing();h.cast();h.los=false;h.advance(.01);h.stepTo(1.50);assertEquals(1,h.runtime.size());assertEquals(6,h.coefficients.size());h.advance(1.51);assertEquals(0,h.runtime.size());assertEquals(List.of("TETHER_LOS_GRACE_EXPIRED"),h.ends);}
    @Test void restoredLosResetsGrace(){var h=new Healing();h.cast();h.los=false;h.advance(.01);h.stepTo(1.4);h.los=true;h.advance(1.41);h.los=false;h.advance(1.42);h.stepTo(2.91);assertEquals(1,h.runtime.size());h.advance(2.92);assertEquals(0,h.runtime.size());}
    @Test void rangeHasNoGrace(){var h=new Healing();h.cast();h.targets.set(0,enemy(1,0,1.35,19));h.advance(.01);assertEquals(List.of("TETHER_RANGE_BROKEN"),h.ends);assertEquals(200,h.mana);}
    @Test void resourceExhaustionBeforeUnpaidHeal(){var h=new Healing();h.cast();h.mana=.5;h.advance(.25);assertTrue(h.coefficients.isEmpty());assertEquals(.5,h.mana);assertEquals(List.of("INSUFFICIENT_UPKEEP"),h.ends);}
    @Test void invalidActorWorldAllianceAndDespawnEndBeforeMutation(){
        for(String reason:List.of("ACTOR_NOT_USABLE","WORLD_CHANGED","LOGOUT")){var h=new Healing();h.cast();h.validation=reason;h.advance(.25);assertEquals(List.of(reason),h.ends);assertTrue(h.coefficients.isEmpty());}
        var h=new Healing();h.cast();h.hostile.add(id(1));h.advance(.25);assertEquals(List.of("TETHER_TARGET_INVALID"),h.ends);
        var gone=new Healing();gone.cast();gone.targets.clear();gone.advance(.25);assertEquals(0,gone.runtime.size());
    }
    @Test void duplicateTimesCannotDoubleHealOrKeepPresentingAfterEnd(){var h=new Healing();h.cast();h.advance(.25);h.advance(.25);assertEquals(1,h.coefficients.size());h.held=false;h.advance(.26);int visuals=h.shapes.size();h.advance(.5);assertEquals(visuals,h.shapes.size());assertEquals(0,h.capacity.size());}
    @Test void channelIsNotArtificiallyFinite(){var h=new Healing();h.mana=10000;h.cast();for(int n=1;n<=130;n++)h.advance(n);assertEquals(1,h.runtime.size());assertEquals(1,h.contexts.size());}
    @Test void pulseCompositionHasFiveDistinctTerminalRecipients(){
        var h=new Healing("arc","fork","chain");for(int i=2;i<=9;i++)h.targets.add(enemy(i,i-1,1.35,5));h.cast();h.advance(.25);
        assertEquals(6,h.recipients.size());assertEquals(6,new HashSet<>(h.recipients).size());
        assertEquals(List.of(1d,.6,.45,.45,.7,.49),h.coefficients.stream().map(v->v/.1125).toList());assertEquals(199,h.mana);assertEquals(1,h.runtime.size());
    }
    @Test void eachModifierHasItsOwnExactCoefficients(){for(String passive:List.of("arc","fork","chain")){
        var h=new Healing(passive);for(int i=2;i<5;i++)h.targets.add(enemy(i,i,1.35,5));h.cast();h.advance(.25);
        assertEquals(passive.equals("arc")?2:3,h.recipients.size());
        var expected=passive.equals("arc")?List.of(1d,.6):passive.equals("fork")?List.of(1d,.45,.45):List.of(1d,.7,.49);
        for(int i=0;i<expected.size();i++)assertEquals(expected.get(i)*.1125,h.coefficients.get(i),1e-12);
    }}
    @Test void secondaryEligibilityRequiresInjuredFriendlyAndNoPrimaryRepeat(){var h=new Healing("arc","fork","chain");h.targets.add(enemy(2,2,1.35,5));h.hp.put(id(2),100d);h.targets.add(enemy(3,3,1.35,5));h.hostile.add(id(3));h.cast();h.advance(.25);assertEquals(List.of(id(1)),h.recipients);}
    @Test void triageOverflowAndPayloadModifiersArePerRecipient(){
        var h=new Healing("arc","potency","overcharge","triage","overflow");h.targets.add(enemy(2,1,1.35,5));h.hp.put(id(1),99d);h.hp.put(id(2),99.5d);h.cast();h.advance(.25);
        assertEquals(2,h.overflowEffects.size());assertEquals(100,h.hp.get(id(1)));assertEquals(100,h.hp.get(id(2)));assertEquals(198.8,h.mana,1e-8);
        var c=h.contexts.getFirst();assertEquals(20*.1125*1.4*c.snapshot().derivedStats().healingMultiplier(),SupportMagnitude.tetherHealing(c,.1125,99,100),1e-8);
        assertTrue(SupportMagnitude.tetherHealing(c,.1125,10,100)>SupportMagnitude.tetherHealing(c,.1125,99,100));
    }
    @Test void compatibilityKeepsChannelAndWidthExclusions(){
        var catalog=Stage01BTestSupport.bundle().catalog();var service=new com.inigmasgames.hytalerpg.links.CompatibilityService();var skill=catalog.skill(new SkillId("healing_beam")).orElseThrow();
        for(String id:List.of("potency","efficiency","long_reach","overcharge","rapid_pulse","overflow","triage","arc","fork","chain"))assertTrue(service.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
        for(String id:List.of("widening","focused_channel","second_wind","echo","retaliation","critical_trigger","kill_trigger","skill_delay","lifeblood","lingering"))assertFalse(service.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
    }
    @Test void capIsPerHitAndNeverRaisesSmallHits(){var cap=new MaxHealthDamageCap(.1);assertEquals(10,cap.apply(80,100,MaxHealthDamageCap.Origin.DIRECT_HOSTILE));assertEquals(7,cap.apply(7,100,MaxHealthDamageCap.Origin.DIRECT_HOSTILE));double sum=0;for(int n=0;n<5;n++)sum+=cap.apply(80,100,MaxHealthDamageCap.Origin.DIRECT_HOSTILE);assertEquals(50,sum);assertEquals(20,cap.apply(80,200,MaxHealthDamageCap.Origin.DIRECT_HOSTILE));}
    @Test void periodicCostsReflectionEnvironmentAreNotCapped(){var cap=new MaxHealthDamageCap(.1);for(var origin:MaxHealthDamageCap.Origin.values())if(origin!=MaxHealthDamageCap.Origin.DIRECT_HOSTILE)assertEquals(80,cap.apply(80,100,origin),origin.name());}
    @Test void blessingProfileIsUtilitySpellbookOnlyAndNonScalable(){var b=Stage01BTestSupport.bundle();var p=Stage04SkillProfiles.loadCanonical(b.catalog()).require("blessing_of_protection");assertEquals(Set.of("SPELLBOOK"),p.allowedMainHandKinds());assertEquals(30,p.resourceCost());assertEquals(30,p.cooldownSeconds());assertEquals(30,p.support().durationSeconds());assertEquals(.1,p.support().coefficient());assertTrue(p.support().allyTarget());assertFalse(new com.inigmasgames.hytalerpg.links.CompatibilityService().assess(b.catalog().skill(new SkillId(p.skillId())).orElseThrow(),b.catalog().passive(new PassiveId("potency")).orElseThrow()).accepted());}
    @Test void blessingUsesFiniteRuntimeAndExpiresAtThirtySeconds(){
        var h=new Stage09FiniteSupportTest.Harness("blessing_of_protection");assertTrue(h.cast().committed());
        assertEquals(70,h.mana);var effects=h.runtime.finite();
        assertEquals(.1,effects.forTarget(h.world,h.actor,29.999).getFirst().damageCap().orElseThrow().maximumHealthFraction());
        assertTrue(effects.forTarget(h.world,h.actor,30).isEmpty());
    }
    @Test void identicalBlessingsDoNotCompoundAndCapPrecedesShield(){
        var h=new Stage09FiniteSupportTest.Harness("blessing_of_protection");h.cast();var effects=h.runtime.finite();
        effects.apply(h.context,List.of(h.actor),30,0);assertEquals(1,effects.size());
        double damage=effects.forTarget(h.world,h.actor,0).getFirst().damageCap().orElseThrow().apply(80,100,MaxHealthDamageCap.Origin.DIRECT_HOSTILE);
        var shield=new Stage09FiniteSupportTest.Harness("spirit_shield");shield.cast();
        var before=shield.runtime.finite().forTarget(shield.world,shield.actor,0).getFirst().shieldRemaining();
        var hit=shield.runtime.finite().shieldHit(shield.world,shield.actor,damage,true,0,(effect,amount)->false);
        assertEquals(Math.max(0,10-before),hit.remainder(),1e-9);
        assertEquals(Math.min(10,before),hit.allocations().stream().mapToDouble(FiniteSupportEffects.Absorption::amount).sum(),1e-9);
    }
    @Test void secondariesHaveDeterministicTieBreakLosAndHopRange(){
        var primary=enemy(1,0,0,0);var list=List.of(enemy(4,20,0,0),enemy(3,-8,0,0),enemy(2,8,0,0),enemy(5,16,0,0));
        var targets=new TetherContinuations.Targets(){
            public ConnectionWorldPort.Query nearby(Vec3 p,double r,int cap){return new ConnectionWorldPort.Query(list,false);}
            public boolean eligible(ConnectionWorldPort.Target t){return true;}
            public boolean lineOfSight(Vec3 p,ConnectionWorldPort.Target t){return !t.id().equals(id(3));}
        };
        var result=TetherContinuations.select(primary,new TetherContinuations.Modifiers(false,false,true),targets);
        assertEquals(List.of(id(2),id(5)),result.stream().map(p->p.recipient().id()).toList());
        assertTrue(result.stream().allMatch(TetherContinuations.Payload::noTetherFanout));
        assertThrows(IllegalArgumentException.class,()->new TetherContinuations.Payload(primary,list.getFirst(),.6,"ARC",false));
    }
    @Test void continuationOverflowRejectsBeforePulsePaymentOrHeal(){
        var h=new Healing("arc");h.cast();h.overflow=true;h.advance(.25);
        assertTrue(h.coefficients.isEmpty());assertEquals(200,h.mana);assertEquals(0,h.runtime.size());
    }
    @Test void catchupPulsesReevaluateInjuredSecondaryRecipients(){
        var h=new Healing("arc");h.targets.add(enemy(2,1,1.35,5));h.targets.add(enemy(3,2,1.35,5));h.hp.put(id(2),99.9);h.cast();h.advance(.5);
        assertEquals(List.of(id(1),id(2),id(1),id(3)),h.recipients);assertEquals(198,h.mana,1e-9);
    }
}
