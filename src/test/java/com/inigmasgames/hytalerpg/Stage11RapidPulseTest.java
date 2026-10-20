package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11RapidPulseTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void acceptsAuthoredPulsesAcrossAreasChannelsOrbsAndAuras(){
        for(String id:List.of("poison_cloud","vortex","earthquake","blizzard","wall_of_fire","void_beam","life_drain","ball_lightning","chilling_aura","reaping_storm"))assertTrue(f.accepts(id,"rapid_pulse"),id);
    }
    @Test void rejectsFlightContactsAndOneOffSequencesRatherThanChangingTheirSamplingClock(){
        for(String id:List.of("snipe","quick_slash","fire_bolt","orbiting_shadow_blades","chain_lightning","avalanche","void_cataclysm","root_snare","wolf_summon","thorns_aura"))assertFalse(f.accepts(id,"rapid_pulse"),id);
    }
    @Test void auraHasIndependentDamageAndChillClocksWithoutUpkeepDiscount(){
        var a=f.profiles.require("chilling_aura").support();var p=f.effective("chilling_aura","rapid_pulse").support();
        assertEquals(.7,p.damageInterval(),1e-9);assertEquals(1.05,p.chillInterval(),1e-9);assertEquals(a.coefficient(),p.coefficient());assertEquals(a.durationSeconds(),p.durationSeconds());assertEquals(a.upkeepPerSecond(),p.upkeepPerSecond());assertEquals(a.radius(),p.radius());
    }
    @Test void integratedAreaCoefficientPreservesOldPulseBeforeApplyingEightyPercent(){
        var a=f.profiles.require("poison_cloud").area();var p=f.effective("poison_cloud","rapid_pulse").area();
        assertEquals(.175,p.intervalSeconds(),1e-9);assertEquals(a.coefficient()*a.intervalSeconds(),p.coefficient()*p.intervalSeconds(),1e-9);
        assertEquals(8,p.lifetimeSeconds());assertEquals(45,p.perTargetHitCap());assertEquals(a.statusSeconds(),p.statusSeconds());
    }
    @Test void discreteGroundPulsesRetainImmediateFirstPulseAndShortenOnlyIntervals(){
        var a=f.profiles.require("earthquake").area();var p=f.effective("earthquake","rapid_pulse").area();
        assertEquals(6,p.impactCount());assertEquals(6,p.perTargetHitCap());assertEquals(.7875,p.intervalSeconds(),1e-9);assertEquals(a.coefficient(),p.coefficient());assertEquals(.32,p.statusSeconds(),1e-9);assertEquals(a.firstImpactSeconds(),p.firstImpactSeconds());
    }
    @Test void blizzardWarningAndVictimHitIntervalRemainAuthoritative(){
        var p=f.effective("blizzard","rapid_pulse").area();assertEquals(23,p.impactCount());assertEquals(.35,p.intervalSeconds(),1e-9);assertEquals(.25,p.warningSeconds());assertEquals(.75,p.targetIntervalSeconds());assertEquals(2,p.impactRadius());assertEquals(8,p.lifetimeSeconds());
    }
    @Test void lingeringIsResolvedBeforePulseCountWithBoundedSpawnBudget(){
        var plan=f.plan("blizzard","rapid_pulse","lingering");var p=f.resolver.resolve(f.profiles.require("blizzard"),plan).area();
        assertEquals(32,p.impactCount());assertEquals(32,p.perTargetHitCap());assertTrue(p.impactCount()+1<=plan.safetyBudgets().maxSpawnedEffects());assertEquals(11.2,p.lifetimeSeconds(),1e-9);
    }
    @Test void profileCacheDoesNotLeakPulseIntervalIntoPlainOrGeometryOnlyPlans(){
        var a=f.profiles.require("poison_cloud");var p=f.resolver.resolve(a,f.plan("poison_cloud","rapid_pulse"));assertEquals(.175,p.area().intervalSeconds(),1e-9);
        assertSame(a,f.resolver.resolve(a,f.plan("poison_cloud")));assertEquals(.25,f.effective("poison_cloud","concentration").area().intervalSeconds());
    }
    @Test void fiveIntegerChillPulsesGrantFourAndDuplicatesGrantNothing(){
        var ledger=new ChillPulseLedger();int stacks=0;for(int i=1;i<=5;i++){stacks+=ledger.grant("a",i,1);assertEquals(0,ledger.grant("a",i,1));}
        assertEquals(4,stacks);assertEquals(0,ledger.grant("a",1,1));assertEquals(1,ledger.size());
    }
    @Test void newcomerCannotInheritAnotherVictimsFractionalCredit(){
        var ledger=new ChillPulseLedger();assertEquals(0,ledger.grant("old",1,1));assertEquals(1,ledger.grant("old",2,1));assertEquals(0,ledger.grant("new",2,1));
        assertEquals(1,ledger.grant("new",3,1));
    }
    @Test void multiStackPulseHasExactFourFifthsTotalWithoutFractionalNativeStatuses(){
        var ledger=new ChillPulseLedger();int stacks=0;for(int i=0;i<5;i++){int count=ledger.grant("a",i,2);assertTrue(count>=1&&count<=2);stacks+=count;}assertEquals(8,stacks);
    }
    @Test void fractionalLedgerIsBoundedPerEffectAndRefusesMalformedPayload(){
        var ledger=new ChillPulseLedger();for(int i=0;i<256;i++)ledger.grant("t"+i,0,1);assertThrows(IllegalStateException.class,()->ledger.grant("overflow",0,1));
        assertEquals(256,ledger.size());assertThrows(IllegalArgumentException.class,()->ledger.grant("t0",1,0));assertThrows(IllegalArgumentException.class,()->ledger.grant("t0",1,6));
    }
    @Test void poisonHasFortyFiveFullPulsesNoTailAndOnePayment(){
        var h=area("poison_cloud");h.targets=List.of(Stage06AreaRuntimeTest.target("a",0,0));assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());
        for(int i=1;i<=45;i++)h.step(i*.175);h.step(8);h.step(8);
        assertEquals(45,h.payloads.size());assertEquals(0,h.areaRuntime.size());assertEquals(0,h.budget.size());assertEquals(76,h.mana);assertEquals(1,h.resourceWrites);
        assertEquals(2.7,h.payloads.stream().mapToDouble(p->p.coefficient()*.8).sum(),1e-9);
        assertEquals(.8,h.hitContexts.getFirst().snapshot().modifiers().factor());assertEquals(1,h.context.snapshot().modifiers().factor());
    }
    @Test void allPulseContextsPreserveOriginalRootInstanceAndCorrelation(){
        var h=area("poison_cloud");h.targets=List.of(Stage06AreaRuntimeTest.target("a",0,0));h.cast();h.step(.7);
        for(var c:h.hitContexts){assertEquals(h.context.rootCastId(),c.rootCastId());assertEquals(h.context.skillInstanceId(),c.skillInstanceId());assertEquals(h.context.request().correlationId(),c.request().correlationId());}
        assertEquals(4,h.hitContexts.size());assertSame(h.hitContexts.getFirst(),h.hitContexts.getLast());
    }
    @Test void pulseAndMobileComposeWithoutAnAuraOrExtraCost(){
        var h=area("poison_cloud");h.link("mobile_domain",PassiveSlot.PASSIVE02);h.cast();h.anchor=new Vec3(20,0,0);h.targets=List.of(Stage06AreaRuntimeTest.target("a",20,0));h.step(.175);
        assertEquals(h.anchor,h.payloads.getFirst().origin());assertEquals(.64,h.hitContexts.getFirst().snapshot().modifiers().factor(),1e-9);assertEquals(1,h.resourceWrites);assertEquals(0,h.runtime.auraCount());
    }
    @Test void vortexScalesPerPulsePullRatherThanRenormalizingItsRate(){
        var h=area("vortex");h.targets=List.of(Stage06AreaRuntimeTest.target("a",3,0));h.cast();h.step(.175);
        assertEquals(.3,h.payloads.getFirst().pull(),1e-9);assertEquals(1.5,h.payloads.getFirst().pullCoreRadius());assertEquals(.8,h.hitContexts.getFirst().snapshot().modifiers().factor());
    }
    @Test void earthquakeUsesSixExistingSpatialImpactsNotSixActivations(){
        var h=area("earthquake");h.targets=List.of(Stage06AreaRuntimeTest.target("a",0,0));h.cast();for(int i=1;i<=18;i++)h.step(i*.25);
        assertEquals(6,h.payloads.size());assertEquals(.32,h.payloads.getFirst().statusSeconds(),1e-9);assertEquals(1,h.resourceWrites);assertEquals(70,h.mana);assertEquals(0,h.areaRuntime.size());
    }
    @Test void finiteAuraHasElevenReducedPulsesButExactlyEightSecondsUpkeep(){
        var h=new AuraHarness("reaping_storm");h.link("rapid_pulse",PassiveSlot.PASSIVE01);h.cast();h.advance(8.2);
        assertEquals(11,h.damagePulses.size());assertEquals(32,h.slices.size());assertEquals(8,h.slices.stream().mapToDouble(Double::doubleValue).sum());assertEquals(28,h.mana,1e-8);
        assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());assertEquals(.8,h.pulseContexts.getFirst().snapshot().modifiers().factor());
    }
    @Test void chillingAuraUsesFractionalPerVictimCreditAndIndependentDamage(){
        var h=new AuraHarness("chilling_aura");h.link("rapid_pulse",PassiveSlot.PASSIVE01);h.cast();h.advance(5.3); // Next retained 100ms owner tick observes the 5.25s authored pulse.
        assertEquals(5,h.chillPulses.size(),"time="+h.now+" mana="+h.mana+" auras="+h.runtime.auraCount()+" profile="+h.context.profile().support()+" events="+h.events);assertEquals(4,h.chillRecipients.stream().mapToInt(List::size).sum());assertEquals(7,h.damagePulses.size());assertEquals(1,h.context.snapshot().modifiers().factor());
        assertEquals(.8,h.pulseContexts.getFirst().snapshot().modifiers().factor());
    }
    @Test void duplicateAuraTickCannotGrantExtraChillOrCharge(){
        var h=new AuraHarness("chilling_aura");h.link("rapid_pulse",PassiveSlot.PASSIVE01);h.cast();h.advance(2.1);double mana=h.mana;int pulses=h.pulseContexts.size();h.tick(2.1);h.tick(2.1);
        assertEquals(mana,h.mana);assertEquals(pulses,h.pulseContexts.size());assertEquals(1,h.chillRecipients.stream().mapToInt(List::size).sum());
    }
    @Test void beamPaysTailTimeWithoutAnExtraDamagePulseOrSecondCooldown(){
        var h=new Stage08ConnectionTest.Harness("void_beam","rapid_pulse");h.targets.add(Stage08ConnectionTest.target("a",0,1.35,5));h.cast();
        for(int i=1;i<=20;i++)h.advance(i*.25);
        assertEquals(28,h.hits.size());assertEquals(29,h.resourceWrites);assertEquals(180,h.mana,1e-8);assertEquals(1,h.cooldownEndTraces());assertEquals(0,h.runtime.size());
        assertEquals(.8,h.hitContexts.getFirst().snapshot().modifiers().factor());assertTrue(h.order.indexOf("pay1")<h.order.indexOf("hit1"));assertFalse(h.order.contains("hit29"));
    }
    @Test void unaffordableChannelTailDoesNotInventAFreeFinalPulse(){
        var h=new Stage08ConnectionTest.Harness("void_beam","rapid_pulse");h.mana=19.600001;h.targets.add(Stage08ConnectionTest.target("a",0,1.35,5));h.cast();for(int i=1;i<=20;i++)h.advance(i*.25);
        assertEquals(28,h.hits.size());assertEquals(28,h.resourceWrites);assertEquals(List.of("INSUFFICIENT_UPKEEP"),h.ends);assertEquals(1,h.cooldownEndTraces());
    }
    @Test void orbChangesPulseTimingNotMovementSpeedOrLife(){
        var a=f.profiles.require("ball_lightning").connection();var p=f.effective("ball_lightning","rapid_pulse").connection();assertEquals(a.speed(),p.speed());assertEquals(a.lifetimeSeconds(),p.lifetimeSeconds());assertEquals(a.range(),p.range());assertEquals(a.coefficient(),p.coefficient());assertEquals(a.intervalSeconds()*.7,p.intervalSeconds());
    }
    @Test void insufficientAuraUpkeepAndOwnerLagStillCleanWithoutBackfilling(){
        var h=new AuraHarness("reaping_storm");h.link("rapid_pulse",PassiveSlot.PASSIVE01);h.cast();h.tick(20);assertEquals(0,h.runtime.auraCount());assertTrue(h.pulseContexts.isEmpty());assertEquals(59,h.mana);
    }
    @Test void lifeDrainRetainsActualDamageBasedHealingAndRate(){
        var h=new Stage08ConnectionTest.Harness("life_drain","rapid_pulse");h.targets.add(Stage08ConnectionTest.target(UUID.randomUUID().toString(),0,1.35,5));h.cast();
        double duration=h.contexts.getFirst().profile().connection().lifetimeSeconds();for(int i=1;i<=Math.round(duration/.25);i++)h.advance(i*.25);
        int count=(int)Math.floor(duration/h.contexts.getFirst().profile().connection().intervalSeconds()+1e-9);assertEquals(count,h.hits.size());assertEquals(count,h.healRequests.size());assertEquals(.8,h.hitContexts.getFirst().snapshot().modifiers().factor());assertEquals(1,h.cooldownEndTraces());
    }
    @Test void blizzardUsesWeightedChillAfterExistingVictimIntervalGate(){
        var h=area("blizzard");h.targets=List.of(new com.inigmasgames.hytalerpg.execution.area.AreaWorldPort.Target("large",
                new com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds(new Vec3(-10,0,-10),new Vec3(10,3,10)),false));
        h.cast();for(int i=1;i<=165;i++)h.step(i*.05);
        assertFalse(h.payloads.isEmpty());assertTrue(h.payloads.size()<23);assertEquals((h.payloads.size()*4)/5,
                h.payloads.stream().filter(p->p.status().equals("CHILL")).mapToInt(p->p.chillStacks()).sum());
        assertEquals(0,h.areaRuntime.size());assertEquals(0,h.budget.size());assertEquals(1,h.resourceWrites);
    }

    static Stage11MobileDomainTest.Harness area(String id){var h=new Stage11MobileDomainTest.Harness(id);h.link("rapid_pulse",PassiveSlot.PASSIVE01);return h;}
    static final class AuraHarness extends Stage09AuraRuntimeTest.Harness {
        final List<SkillExecutionContext> pulseContexts=new ArrayList<>();final List<List<UUID>> chillRecipients=new ArrayList<>();
        AuraHarness(String id){super(id);}
        @Override public void auraPulse(SkillExecutionContext c,List<UUID> targets,int tick,boolean chill){pulseContexts.add(c);(chill?chillPulses:damagePulses).add(tick);if(chill)chillRecipients.add(List.copyOf(targets));}
    }
}
