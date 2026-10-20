package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.support.SupportMagnitude;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11ConditionalDamageTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    final HitConditionModifiers both=new HitConditionModifiers(true,true);
    @Test void executionerCanonicalPositiveAndTwoNegativeFixtures(){assertTrue(f.accepts("snipe","executioner"));assertFalse(f.accepts("minor_heal","executioner"));assertFalse(f.accepts("emanatism","executioner"));}
    @Test void opportunistCanonicalPositiveAndTwoNegativeFixtures(){assertTrue(f.accepts("frost_bolt","opportunist"));assertFalse(f.accepts("minor_heal","opportunist"));assertFalse(f.accepts("emanatism","opportunist"));}
    @Test void executionerThresholdStrictlyBelowThirtyPercent(){assertEquals(0,both.increased(30,100,Set.of()));assertEquals(.35,both.increased(29.999,100,Set.of()));assertEquals(0,both.increased(31,100,Set.of()));}
    @Test void invalidOrDeadHealthCannotQualifyExecutioner(){for(double hp:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1})assertEquals(0,both.increased(hp,100,Set.of()));assertEquals(0,both.increased(1,0,Set.of()));}
    @Test void allFourActualControlsQualifyButNeverStack(){for(String status:List.of("ROOT","FROZEN","STUN","FEAR"))assertEquals(.25,both.increased(100,100,Set.of(status)));assertEquals(.25,both.increased(100,100,Set.of("ROOT","FROZEN","STUN","FEAR")));}
    @Test void slowChillTauntAndStaggerLabelAreNotActualNamedControl(){assertEquals(0,both.increased(100,100,Set.of("FROZEN_SUBSTITUTE_SLOW","CHILL","TAUNT","STAGGER")));}
    @Test void bossFrozenSubstituteDoesNotQualify(){var statuses=com.inigmasgames.hytalerpg.combat.RpgCombatKernel.createProduction().statuses();var victim=UUID.randomUUID();statuses.apply(victim,RpgStatusType.FROZEN,new ControlProfile(false,true,false));assertEquals(0,both.increased(100,100,statuses.inspect(victim).active().keySet().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())));}
    @Test void conditionsAreNotBakedIntoKernelCommitModifiers(){var p=f.plan("fire_bolt","executioner","opportunist");assertEquals(0,p.kernelModifiers().scalablePayloadIncreased());assertTrue(p.hitConditions().active());assertNotEquals(f.plan("fire_bolt").planHash(),p.planHash());}
    @Test void pureGatherAddsToExistingIncreasedNotAsMore(){var seed=seed(new ModifierBuckets(List.of(.15),List.of(),List.of(),List.of()),100);assertEquals(175,seed.amount(seed.increased(20,100,Set.of("ROOT"))),1e-9);}
    @Test void moreLessAndReducedStayInTheirOwnBuckets(){var b=new ModifierBuckets(List.of(.15),List.of(.1),List.of(1.2),List.of(.25));var seed=seed(b,100);assertEquals(100*(1+.15-.1+.35+.25)*1.2*.75,seed.amount(.60),1e-9);}
    @Test void zeroOriginalBucketCanBecomePositiveWithoutDivision(){var seed=seed(new ModifierBuckets(List.of(),List.of(1.1),List.of(),List.of()),100);assertEquals(0,seed.expectedAmount());assertEquals(25,seed.amount(.35),1e-9);}
    @Test void noConditionalPlanDoesNotAttachAnInput(){assertNull(ConditionalDamage.calculated(new HitConditionModifiers(false,false),ModifierBuckets.NONE,new DamageCalculationService.Result(100,1,100,100,1,100,false,100),1.5));}
    @Test void actualNativeDamageMetadataIsSingleUseAndPreservesAllIds(){
        var d=damage(seed(ModifierBuckets.NONE,100),HytaleDamageMetadata.Origin.DIRECT);var info=HytaleConditionalDamage.gather(d,20,100,Set.of("ROOT"));
        assertEquals(160,d.getAmount(),1e-6);assertEquals(.60,(double)info.get("targetConditionalIncreased"),1e-9);assertFalse(HytaleConditionalDamage.pending(d));
        assertTrue(HytaleConditionalDamage.gather(d,1,100,Set.of("FEAR")).isEmpty());assertEquals(160,d.getAmount(),1e-6);
        var m=HytaleDamageAdapter.metadata(d);assertEquals("root",m.rootCastId());assertEquals("instance",m.skillInstanceId());assertEquals("correlation",m.correlationId());assertEquals("effect",m.effectInstanceId());assertEquals(20,m.targetHealthBefore());assertEquals(160,m.preMitigationDamage(),1e-6);
    }
    @Test void healthIsReadPerHitNotWhenInputWasCreated(){var seed=seed(ModifierBuckets.NONE,100);var a=damage(seed,HytaleDamageMetadata.Origin.DIRECT);var b=damage(seed,HytaleDamageMetadata.Origin.DIRECT);HytaleConditionalDamage.gather(a,70,100,Set.of());HytaleConditionalDamage.gather(b,20,100,Set.of());assertEquals(100,a.getAmount());assertEquals(135,b.getAmount(),1e-6);}
    @Test void statusIsReadPerHitAndMayExpireBetweenTicks(){var seed=seed(ModifierBuckets.NONE,10);var a=damage(seed,HytaleDamageMetadata.Origin.PERIODIC);var b=damage(seed,HytaleDamageMetadata.Origin.PERIODIC);HytaleConditionalDamage.gather(a,100,100,Set.of("ROOT"));HytaleConditionalDamage.gather(b,100,100,Set.of());assertEquals(12.5,a.getAmount());assertEquals(10,b.getAmount());assertFalse(HytaleDamageAdapter.metadata(a).canProc());}
    @Test void cancelledDamageNeverResurrects(){var d=damage(seed(ModifierBuckets.NONE,100),HytaleDamageMetadata.Origin.DIRECT);d.setCancelled(true);HytaleConditionalDamage.gather(d,1,100,Set.of("ROOT"));assertTrue(d.isCancelled());assertEquals(100,d.getAmount());}
    @Test void redirectedDamageIsNeverOffensivelyScaled(){var d=damage(seed(ModifierBuckets.NONE,100),HytaleDamageMetadata.Origin.REDIRECTED);HytaleConditionalDamage.gather(d,1,100,Set.of("ROOT"));assertEquals(100,d.getAmount());assertTrue(HytaleDamageAdapter.metadata(d).noCredit());}
    @Test void reflectedOwnPayloadCanScaleWithoutEnablingReflectionRecursion(){var d=damage(seed(ModifierBuckets.NONE,100),HytaleDamageMetadata.Origin.REFLECTED);HytaleConditionalDamage.gather(d,1,100,Set.of());assertEquals(135,d.getAmount(),1e-6);var m=HytaleDamageAdapter.metadata(d);assertFalse(m.canProc());assertTrue(m.noLeech());assertTrue(m.noCredit());}
    @Test void unexpectedNativeGatherWriterFailsClosedInsteadOfBeingOverwritten(){var d=damage(seed(ModifierBuckets.NONE,100),HytaleDamageMetadata.Origin.DIRECT);d.setAmount(50);var details=HytaleConditionalDamage.gather(d,1,100,Set.of());assertTrue(d.isCancelled());assertEquals(50,d.getAmount());assertEquals("NATIVE_GATHER_AMOUNT_CHANGED",details.get("conditionalGate"));}
    @Test void nativeFloatNarrowingDoesNotProduceFalseMismatch(){var d=damage(seed(ModifierBuckets.NONE,100.123456789),HytaleDamageMetadata.Origin.DIRECT);var details=HytaleConditionalDamage.gather(d,1,100,Set.of());assertFalse(d.isCancelled());assertEquals("RESOLVED",details.get("conditionalGate"));}
    @Test void noSecondCriticalRollAtGather(){
        var result=new DamageCalculationService.Result(100,1,100,100,1,100,true,150);var seed=ConditionalDamage.calculated(both,ModifierBuckets.NONE,result,1.5);
        var d=damage(seed,HytaleDamageMetadata.Origin.DIRECT);HytaleConditionalDamage.gather(d,20,100,Set.of());assertEquals(202.5,d.getAmount());
    }
    @Test void reflectionSeedRetainsThornsCoefficientAndBeneficialMultipliers(){
        var h=new Stage09SupportRuntimeTest.Harness("thorns_aura");h.link("executioner",PassiveSlot.PASSIVE01);h.link("potency",PassiveSlot.PASSIVE02);h.cast();
        var c=h.context;double raw=100*c.profile().support().coefficient()*c.compiledPlan().supportModifiers().beneficialFactor();
        var seed=SupportMagnitude.reflectionConditional(c,100,raw*c.snapshot().modifiers().factor());assertNotNull(seed);assertEquals(raw*1.5,seed.amount(.35),1e-9);
    }
    @Test void malformedInputsCannotEnterGather(){assertThrows(IllegalArgumentException.class,()->new ConditionalDamage(both,ModifierBuckets.NONE,Double.NaN,1));assertThrows(IllegalArgumentException.class,()->new ConditionalDamage(both,ModifierBuckets.NONE,1,-1));}
    ConditionalDamage seed(ModifierBuckets buckets,double raw){return new ConditionalDamage(both,buckets,raw,raw*buckets.factor());}
    static Damage damage(ConditionalDamage seed,HytaleDamageMetadata.Origin origin){
        var damage=new Damage(Damage.NULL_SOURCE,0,(float)seed.expectedAmount());
        var metadata=new HytaleDamageMetadata(new UUID(1,1),"root","instance","correlation",seed.expectedAmount(),90,"effect",origin==HytaleDamageMetadata.Origin.DIRECT,origin);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(metadata));HytaleConditionalDamage.attach(damage,seed);return damage;
    }
}
