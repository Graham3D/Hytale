package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.movement.ValidatedTravel;
import com.inigmasgames.hytalerpg.execution.reaction.ReactionWindowService;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11ReactionMovementTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void reversalChangesOnlyAuthoredWindowAndRetaliationBucket(){
        var p=f.plan("riposte","reversal","potency");var a=f.profiles.require("riposte");var e=f.resolver.resolve(a,p);
        assertEquals(1.04,e.reaction().windowSeconds(),1e-9);assertEquals(a.strike(),e.strike());assertEquals(a.reaction().qualifyingSignals(),e.reaction().qualifyingSignals());
        assertEquals(a.cooldownSeconds(),e.cooldownSeconds());assertEquals(a.resourceCost(),e.resourceCost());assertEquals(.40,p.kernelModifiers().scalablePayloadIncreased(),1e-9);
    }
    @Test void reversalCannotCreateOrdinaryStrikeWindow(){assertFalse(f.accepts("quick_slash","reversal"));}
    @Test void reversalCannotExtendAnActiveShield(){assertFalse(f.accepts("managuard","reversal"));}
    @Test void extendedWindowUsesExistingOneShotReactionAndSameIds(){
        var h=new H("riposte","reversal");var c=h.castContext();var now=new AtomicLong();var windows=new ReactionWindowService(now::get);
        assertTrue(windows.arm(h.actor,c,c.profile().reaction().windowSeconds()));now.set(900_000_000);
        assertTrue(windows.trigger(h.actor,"WRONG_SIGNAL","event").isEmpty());
        assertSame(c,windows.trigger(h.actor,"HYTALE_DAMAGE_BLOCKED","event").orElseThrow());assertTrue(windows.trigger(h.actor,"HYTALE_DAMAGE_BLOCKED","event").isEmpty());
        assertEquals("stage09",c.request().correlationId());assertEquals(c.rootCastId(),c.snapshot().rootCastId());assertEquals(c.skillInstanceId(),c.snapshot().skillInstanceId());
        assertEquals(100-c.snapshot().resourceCost().amount(),h.mana);assertEquals(.25,c.snapshot().modifiers().increased().stream().mapToDouble(Double::doubleValue).sum(),1e-9);
    }
    @Test void extensionDoesNotPermitActivationAtOrBeyondExpiry(){
        var h=new H("riposte","reversal");var c=h.castContext();var now=new AtomicLong();var windows=new ReactionWindowService(now::get);
        windows.arm(h.actor,c,1.04);now.set(1_040_000_000);assertTrue(windows.trigger(h.actor,"HYTALE_DAMAGE_BLOCKED","event").isEmpty());assertSame(c,windows.expire(h.actor).orElseThrow());
    }
    @Test void reactionCancelRemovesExtendedWindow(){var h=new H("riposte","reversal");var c=h.castContext();var windows=new ReactionWindowService(()->0);windows.arm(h.actor,c,1.04);windows.cancel(h.actor);assertTrue(windows.trigger(h.actor,"HYTALE_DAMAGE_BLOCKED","event").isEmpty());}
    @Test void momentumAcceptsPounceAndCanonicalChargeDespiteImportedConjunction(){assertTrue(f.accepts("pounce","momentum"));assertTrue(f.accepts("charge","momentum"));assertTrue(f.plan("pounce","momentum").foundationModifiers().momentum());}
    @Test void momentumRejectsProjectileAndNonDamagingTravel(){assertFalse(f.accepts("quick_shot","momentum"));assertFalse(f.accepts("quickstep","momentum"));}
    @Test void momentumCapabilityIsNotLeakedGlobally(){assertFalse(f.plan("pounce","momentum").finalTags().contains("DAMAGING_CHARGE"));}
    @Test void requestedTenMetersButObservedTwoGrantsOnlyTenPercent(){var c=new H("pounce","momentum").castContext();var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,new Vec3(0,0,10),new Vec3(0,0,2),.1);assertEquals(2,t.meters());assertEquals(.10,t.increased(c));}
    @Test void collisionBlockedSegmentAndZeroTravelGiveNoBonus(){var c=new H("pounce","momentum").castContext();var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,.1);assertEquals(0,t.increased(c));assertSame(c,t.impact(c));}
    @Test void acceptedThreeDimensionalPathNotEndpointDistance(){var t=new ValidatedTravel(Vec3.ZERO);var a=new Vec3(0,3,0);t.observe(Vec3.ZERO,a,a,.1);t.observe(a,Vec3.ZERO,Vec3.ZERO,.1);assertEquals(6,t.meters());}
    @Test void accumulatedPathIsBoundedAtTenMeters(){var c=new H("pounce","momentum").castContext();var t=new ValidatedTravel(Vec3.ZERO);var a=new Vec3(0,0,8);var b=new Vec3(0,0,16);t.observe(Vec3.ZERO,a,a,.1);t.observe(a,b,b,.1);assertEquals(10,t.meters());assertEquals(.5,t.increased(c));}
    @Test void externalTeleportInvalidatesAllMomentumButDoesNotMutateContext(){var c=new H("pounce","momentum").castContext();var t=new ValidatedTravel(Vec3.ZERO);var a=new Vec3(0,0,2);t.observe(Vec3.ZERO,a,a,.1);var teleport=new Vec3(50,0,0);t.observe(teleport,teleport,teleport,.1);assertFalse(t.valid());assertSame(c,t.impact(c));}
    @Test void UnapprovedOvershootCannotCountAsMovement(){var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,new Vec3(0,0,8),.1);assertFalse(t.valid());assertEquals(0,t.meters());}
    @Test void sidewaysNativeDisplacementCannotCountAsApprovedTravel(){var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,new Vec3(1,0,0),.1);assertFalse(t.valid());}
    @Test void zeroTimeTeleportCannotQualify(){var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,0);assertFalse(t.valid());}
    @Test void zeroTimeUnmovedTickDoesNotEraseEarlierTravel(){var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,.1);t.observe(Vec3.FORWARD,Vec3.FORWARD,Vec3.FORWARD,0);assertTrue(t.valid());assertEquals(1,t.meters());}
    @Test void plainMovementNeverReceivesMomentumEvenIfTravelWasMeasured(){var c=new H("pounce").castContext();var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,.1);assertSame(c,t.impact(c));}
    @Test void dynamicBonusAddsOnceAtImpactWithoutAnotherPaymentOrCooldown(){
        var h=new H("pounce","momentum","potency");var c=h.castContext();var t=new ValidatedTravel(Vec3.ZERO);var end=new Vec3(0,0,4);t.observe(Vec3.ZERO,end,end,.25);
        var impact=t.impact(c);assertEquals(c.snapshot().modifiers().factor()+.2,impact.snapshot().modifiers().factor(),1e-9);
        assertEquals(c.snapshot().resourceCost(),impact.snapshot().resourceCost());assertEquals(c.snapshot().cooldownSeconds(),impact.snapshot().cooldownSeconds());
        assertEquals(c.rootCastId(),impact.rootCastId());assertEquals(c.skillInstanceId(),impact.skillInstanceId());assertEquals(c.request(),impact.request());
        assertEquals(impact,t.impact(c));assertNotEquals(c.snapshot().modifiers(),impact.snapshot().modifiers());assertEquals(100-c.snapshot().resourceCost().amount(),h.mana);
    }
    @Test void newCastLedgerDoesNotInheritPriorTravel(){var t=new ValidatedTravel(Vec3.ZERO);t.observe(Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,.1);assertEquals(0,new ValidatedTravel(Vec3.FORWARD).meters());}
    @Test void movementBonusIsNotAppliedToTheCommitSnapshotBeforeImpact(){var c=new H("pounce","momentum").castContext();assertEquals(1,c.snapshot().modifiers().factor());assertEquals(0,c.compiledPlan().kernelModifiers().scalablePayloadIncreased());}
    static class H extends Stage09SupportRuntimeTest.Harness {
        H(String skill,String... passives){super(skill);for(int i=0;i<passives.length;i++)link(passives[i],PassiveSlot.values()[i]);}
        SkillExecutionContext castContext(){var r=cast();assertEquals(SkillExecutionResult.Status.COMMITTED,r.status(),r.code());return context;}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
        @Override public Equipment equipment(){return new Equipment(new Item("fixture","SWORD",new ItemPowerDescriptor("fixture",Set.of("SWORD"),20d,null)),null);}
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext c){context=c;return SkillExecutionResult.committed("FIXTURE_REACTION",0,0);}
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext c){context=c;return SkillExecutionResult.committed("FIXTURE_MOVEMENT",0,0);}
    }
}
