package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13VictimCoefficientTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @ParameterizedTest @CsvSource({"24.999,2.4","25,1.4","25.001,1.4","100,1.4"})
    void executionThresholdIsStrictlyBelowQuarter(double hp,double coefficient){
        assertEquals(coefficient,1.4*VictimCoefficient.EXECUTION_STRIKE.factor(hp,100,null,null),1e-12);
    }
    @ParameterizedTest @ValueSource(doubles={0,-1,Double.NaN,Double.POSITIVE_INFINITY})
    void missingOrDeadVictimHealthCannotEnterConditionalStrike(double hp){
        assertThrows(IllegalArgumentException.class,()->VictimCoefficient.EXECUTION_STRIKE.factor(hp,100,null,null));
    }
    @Test void backstabRearBoundaryIsInclusive(){
        assertEquals(1.9,.9*VictimCoefficient.BACKSTAB.factor(100,100,Vec3.FORWARD,new Vec3(0,Math.sqrt(3),-1)),1e-12);
        assertEquals(1.9,.9*VictimCoefficient.BACKSTAB.factor(100,100,Vec3.FORWARD,new Vec3(0,0,-1)),1e-12);
        assertEquals(.9,.9*VictimCoefficient.BACKSTAB.factor(100,100,Vec3.FORWARD,new Vec3(0,Math.sqrt(3),-.999)),1e-12);
    }
    @Test void frontAndSideAreOrdinaryHitsNotRejectedBackstabs(){
        for(var position:List.of(Vec3.FORWARD,new Vec3(1,0,0)))assertEquals(1,VictimCoefficient.BACKSTAB.factor(100,100,Vec3.FORWARD,position));
    }
    @Test void missingFacingAndCoincidentPositionsFailClosed(){
        assertThrows(IllegalArgumentException.class,()->VictimCoefficient.BACKSTAB.factor(100,100,null,Vec3.FORWARD));
        assertThrows(IllegalArgumentException.class,()->VictimCoefficient.BACKSTAB.factor(100,100,Vec3.FORWARD,Vec3.ZERO));
    }
    @Test void actualNativeGatherCombinesExecutionerPotencyAndAlreadyRolledCritOnce(){
        var buckets=new ModifierBuckets(List.of(.15),List.of(),List.of(1.38),List.of());
        var condition=new HitConditionModifiers(true,false);
        var seed=new ConditionalDamage(condition,buckets,140*1.5,140*1.5*buckets.factor(),VictimCoefficient.EXECUTION_STRIKE);
        var nativeDamage=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        var info=HytaleConditionalDamage.gather(nativeDamage,24,100,Set.of());
        assertEquals(240*1.5*(1+.15+.35)*1.38,nativeDamage.getAmount(),.0001);
        assertEquals("EXECUTION_STRIKE",info.get("victimCoefficientRule"));
        assertTrue(HytaleConditionalDamage.gather(nativeDamage,1,100,Set.of()).isEmpty());
        var metadata=HytaleDamageAdapter.metadata(nativeDamage);
        assertEquals("root",metadata.rootCastId());assertEquals("instance",metadata.skillInstanceId());assertEquals("correlation",metadata.correlationId());
        assertEquals(24,metadata.targetHealthBefore());
    }
    @Test void facingIsRecheckedForEachActualNativeDamageObject(){
        var seed=new ConditionalDamage(new HitConditionModifiers(false,false),ModifierBuckets.NONE,90,90,VictimCoefficient.BACKSTAB);
        var rear=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        var front=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        HytaleConditionalDamage.gather(rear,100,100,Set.of(),Vec3.FORWARD,new Vec3(0,0,-1));
        HytaleConditionalDamage.gather(front,100,100,Set.of(),new Vec3(0,0,-1),new Vec3(0,0,-1));
        assertEquals(190,rear.getAmount());assertEquals(90,front.getAmount());
    }
    @Test void missingFacingCancelsInsteadOfInventingAHitDirection(){
        var seed=new ConditionalDamage(new HitConditionModifiers(false,false),ModifierBuckets.NONE,90,90,VictimCoefficient.BACKSTAB);
        var nativeDamage=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        assertEquals("LIVE_VICTIM_FACING_UNAVAILABLE",HytaleConditionalDamage.gather(nativeDamage,100,100,Set.of()).get("conditionalGate"));
        assertTrue(nativeDamage.isCancelled());assertFalse(HytaleConditionalDamage.pending(nativeDamage));
    }
    @ParameterizedTest @CsvSource({"execution_strike,LONGSWORD,18,10,1.4,HEAVY","backstab,DAGGER,10,6,.9,LIGHT"})
    void realServicePaysOneCostAndStartsOneCooldown(String skill,String weapon,double cost,double cooldown,double coefficient,String scaling){
        var h=new Stage11ResourcePassivesTest.H(skill);h.weapon=weapon;assertTrue(h.cast().committed());
        assertEquals(100-cost,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
        assertEquals(coefficient,h.last().profile().strike().coefficient());assertEquals(cooldown,h.last().profile().cooldownSeconds());
        assertEquals(scaling,h.last().profile().scaling());h.service.terminate(h.last(),"TEST_COMPLETE");
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(1,h.contexts.size());
    }
    @Test void backstabUsesOneTargetAndItsOwnAuthoredRange(){
        var p=f.profiles.require("backstab");assertEquals(2.4,p.strike().range());assertEquals(1,p.strike().targetCap());
        assertEquals(Stage04SkillProfile.Geometry.ASSIST_CONE,p.strike().geometry());assertEquals(Set.of("DAGGER"),p.allowedMainHandKinds());
        assertEquals(VictimCoefficient.BACKSTAB,f.effective("backstab","potency").strike().details().victimCoefficient());
    }
    @Test void executionGeometryAndConditionSurviveCompilation(){
        var p=f.effective("execution_strike","potency","executioner");assertEquals(3,p.strike().range());assertEquals(110,p.strike().angleDegrees());
        assertEquals(VictimCoefficient.EXECUTION_STRIKE,p.strike().details().victimCoefficient());assertNull(p.area());assertNull(p.support());
    }
    @Test void existingEarlierGatherWriterStillCannotBeOverwritten(){
        var seed=new ConditionalDamage(new HitConditionModifiers(false,false),ModifierBuckets.NONE,140,140,VictimCoefficient.EXECUTION_STRIKE);
        var d=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);d.setAmount(139);
        assertEquals("NATIVE_GATHER_AMOUNT_CHANGED",HytaleConditionalDamage.gather(d,10,100,Set.of()).get("conditionalGate"));assertTrue(d.isCancelled());
    }
    @Test void resolvedFactorIsVictimLocalAndCannotSurviveOnAnotherDamage(){
        var seed=new ConditionalDamage(new HitConditionModifiers(false,false),ModifierBuckets.NONE,140,140,VictimCoefficient.EXECUTION_STRIKE);
        var a=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        var b=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        assertEquals(1,HytaleConditionalDamage.victimFactor(a));
        HytaleConditionalDamage.gather(a,20,100,Set.of());assertEquals(2.4/1.4,HytaleConditionalDamage.victimFactor(a));
        assertEquals(1,HytaleConditionalDamage.victimFactor(b));
        assertNull(com.hypixel.hytale.server.core.modules.entity.damage.Damage.META_REGISTRY.getMetaKeyForCodecKey("InigmasGames:RpgConditionalGather"));
        assertNull(com.hypixel.hytale.server.core.modules.entity.damage.Damage.META_REGISTRY.getMetaKeyForCodecKey("InigmasGames:RpgVictimCoefficient"));
    }
    @Test void cancelledVictimConditionCannotRequestTheStrongerContactAccent(){
        var seed=new ConditionalDamage(new HitConditionModifiers(false,false),ModifierBuckets.NONE,90,90,VictimCoefficient.BACKSTAB);
        var a=Stage11ConditionalDamageTest.damage(seed,HytaleDamageMetadata.Origin.DIRECT);
        HytaleConditionalDamage.gather(a,100,100,Set.of());assertTrue(a.isCancelled());assertEquals(1,HytaleConditionalDamage.victimFactor(a));
    }
}
