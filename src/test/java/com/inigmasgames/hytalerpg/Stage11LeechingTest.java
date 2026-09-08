package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11LeechingTest {
    static class H implements NativeResourcePort {
        final UUID actor=UUID.randomUUID();final RpgCombatKernel kernel=RpgCombatKernel.createProduction();
        final RootLeechBudget budget=new RootLeechBudget(actor,"root");
        double mana=0,stamina=0,max=100;int writes;boolean fail,uncertain,failRead;
        void arm(ResourceType type){budget.initialize(type,kernel.resources().spendableMaximum(actor,type,this));}
        RootLeechBudget.Recovery hit(double before,double after){return hit(new RootLeechBudget.HitReceipt(before,after,false,true,false));}
        RootLeechBudget.Recovery hit(RootLeechBudget.HitReceipt receipt){return kernel.resources().recoverLeech(budget,receipt,this);}
        public double current(ResourceType t){if(failRead)throw new IllegalStateException("fixture read fail");return t==ResourceType.MANA?mana:stamina;}
        public double maximum(ResourceType t){return max;}
        public void setCurrent(ResourceType t,double amount){writes++;if(fail)throw new IllegalStateException("fixture write fail");if(t==ResourceType.MANA)mana=amount;else stamina=amount;if(uncertain)throw new IllegalStateException("fixture write happened but completion unknown");}
    }
    @Test void leechRequiresDamageAndDeclaredManaOrStamina(){var f=new Stage11FoundationTest();assertTrue(f.accepts("fire_bolt","leeching"));assertTrue(f.accepts("quick_slash","leeching"));assertFalse(f.accepts("emanatism","leeching"));assertFalse(f.accepts("minor_heal","leeching"));}
    @Test void threePercentUsesObservedLossNotAuthoredOrNativeDamageAmount(){var h=new H();h.arm(ResourceType.MANA);var r=h.hit(100,70);assertEquals(.9,r.restored(),1e-9);assertEquals(30,r.healthLost());assertEquals(.9,h.mana,1e-9);assertEquals(0,h.stamina);}
    @Test void staminaDeclarationNeverCreditsMana(){var h=new H();h.arm(ResourceType.STAMINA);h.hit(100,0);assertEquals(3,h.stamina);assertEquals(0,h.mana);}
    @Test void shieldsCancelledAndImmuneHitsGiveNothing(){var h=new H();h.arm(ResourceType.MANA);assertEquals(0,h.hit(100,100).restored());assertEquals(0,h.hit(new RootLeechBudget.HitReceipt(100,50,true,true,false)).restored());assertEquals(0,h.writes);}
    @Test void reflectionAndFriendlyDamageGiveNothing(){var h=new H();h.arm(ResourceType.MANA);assertEquals(0,h.hit(new RootLeechBudget.HitReceipt(100,0,false,false,false)).restored());assertEquals(0,h.hit(new RootLeechBudget.HitReceipt(100,0,false,true,true)).restored());assertEquals(0,h.writes);}
    @Test void overkillCannotIncreaseActualLossBeyondStartingHealth(){var h=new H();h.arm(ResourceType.MANA);assertEquals(.3,h.hit(10,-1000).restored(),1e-9);}
    @Test void deadTargetsHealingAndMissingHealthNeverCredit(){var h=new H();h.arm(ResourceType.MANA);for(double[] pair:new double[][]{{0,-100},{100,110},{Double.NaN,0},{100,Double.NaN},{Double.POSITIVE_INFINITY,1}})assertEquals(0,h.hit(pair[0],pair[1]).restored());assertEquals(0,h.writes);}
    @Test void allHitsAcrossOneRootShareEightPercentCap(){var h=new H();h.arm(ResourceType.MANA);for(int i=0;i<1000;i++)h.hit(1000,0);assertEquals(8,h.mana,1e-9);assertEquals(8,h.budget.restored(),1e-9);assertEquals("ROOT_CAP_REACHED",h.hit(100,0).gate());}
    @Test void duplicateReceiptCannotRestoreTwice(){var h=new H();h.arm(ResourceType.MANA);var receipt=new RootLeechBudget.HitReceipt(100,0,false,true,false);assertEquals(3,h.hit(receipt).restored());assertEquals("DUPLICATE_RECEIPT",h.hit(receipt).gate());assertEquals(3,h.mana);}
    @Test void separateRootsHaveSeparateButNotResettableCaps(){var h=new H();h.arm(ResourceType.MANA);h.hit(1000,0);assertThrows(IllegalStateException.class,()->h.arm(ResourceType.MANA));var other=new RootLeechBudget(h.actor,"another");other.initialize(ResourceType.MANA,100);h.kernel.resources().recoverLeech(other,new RootLeechBudget.HitReceipt(1000,0,false,true,false),h);assertEquals(16,h.mana);assertFalse(other.owns(UUID.randomUUID(),"another"));}
    @Test void rootCapUsesSpendableManaNotReservedCapacity(){var h=new H();h.mana=100;h.kernel.reservations().addPercentage(h.actor,"aura",.5,h);h.mana=0;h.arm(ResourceType.MANA);h.hit(1000,0);assertEquals(4,h.mana);assertEquals(4,h.budget.cap());}
    @Test void loweringCapacityConstrainsRemainingReturnWithoutRemovingResource(){var h=new H();h.arm(ResourceType.MANA);h.hit(100,0);h.max=50;assertEquals(1,h.hit(100,0).restored());assertEquals(4,h.mana);h.max=20;assertEquals(0,h.hit(100,0).restored());assertEquals(4,h.mana);}
    @Test void raisingCapacityDoesNotExpandAlreadyCommittedRootBudget(){var h=new H();h.arm(ResourceType.MANA);h.max=1000;h.hit(1000,0);assertEquals(8,h.mana);assertEquals(8,h.budget.cap());}
    @Test void fullPoolDoesNotConsumePotentialReturnOrStoreDamageForLater(){var h=new H();h.arm(ResourceType.MANA);h.mana=100;var old=new RootLeechBudget.HitReceipt(100,0,false,true,false);assertEquals("RESOURCE_FULL",h.hit(old).gate());h.mana=0;assertEquals("DUPLICATE_RECEIPT",h.hit(old).gate());assertEquals(0,h.mana);assertEquals(3,h.hit(100,0).restored());}
    @Test void partialMissingResourceConsumesOnlyActualCredit(){var h=new H();h.arm(ResourceType.MANA);h.mana=99;assertEquals(1,h.hit(100,0).restored());h.mana=0;assertEquals(7,h.hit(1000,0).restored());assertEquals(8,h.budget.restored());}
    @Test void nativeFailureDisablesRootWithoutRetryEvenIfWriteMayHaveHappened(){for(boolean applied:new boolean[]{false,true}){var h=new H();h.arm(ResourceType.MANA);h.fail=!applied;h.uncertain=applied;assertEquals("NATIVE_CREDIT_FAILED_NO_RETRY",h.hit(100,0).gate());h.fail=false;h.uncertain=false;assertEquals("UNAVAILABLE_BUDGET",h.hit(100,0).gate());assertEquals(1,h.writes);}}
    @Test void missingNativeResourceCannotThrowThroughAlreadyAppliedDamage(){var h=new H();h.arm(ResourceType.MANA);h.failRead=true;assertEquals("NATIVE_RESOURCE_READ_FAILED_NO_RETRY",h.hit(100,0).gate());h.failRead=false;assertEquals("UNAVAILABLE_BUDGET",h.hit(100,0).gate());}
    @Test void nativeFloatTargetAlwaysRoundsDownInsideAllowance(){for(float before:new float[]{0,1,20,99.99f,10000,1e10f})for(double amount:new double[]{.00001,.03,.1,.9,3,8}){float after=RpgResourceService.nativeCreditTarget(before,amount,Double.MAX_VALUE);assertTrue(after>=before);assertTrue((double)after-before<=amount+1e-12);}}
    @Test void nativeFloatCapNeverOverfillsOrRemovesExistingResource(){assertTrue(RpgResourceService.nativeCreditTarget(10,100,15.123)<=15.123);assertEquals(20,RpgResourceService.nativeCreditTarget(20,1,10));assertEquals(1e10f,RpgResourceService.nativeCreditTarget(1e10f,.01,2e10));}
    @Test void unarmedAndInvalidBudgetsCannotBecomeHealthLeech(){var h=new H();assertEquals("UNAVAILABLE_BUDGET",h.hit(100,0).gate());assertThrows(IllegalArgumentException.class,()->h.arm(ResourceType.HEALTH));assertThrows(IllegalArgumentException.class,()->h.budget.initialize(ResourceType.MANA,Double.NaN));assertEquals(0,h.writes);}
    @Test void sharedCastCapturesResourceAndCapBeforeEffectDispatch(){var h=new Stage11ResourcePassivesTest.H("fire_bolt");h.weapon="STAFF";h.link("leeching",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(ResourceType.MANA,h.last().leechBudget().resource());assertEquals(8,h.last().leechBudget().cap());assertEquals(92,h.current(ResourceType.MANA));}
    @Test void echoAndSnapshotCopiesShareRootLedgerRatherThanResettingCap(){var h=new Stage11ResourcePassivesTest.H("fire_bolt");h.weapon="STAFF";h.link("leeching",PassiveSlot.PASSIVE01);h.link("echo",PassiveSlot.PASSIVE02);h.cast();var root=h.last();var echo=root.echoCopy();assertSame(root.leechBudget(),echo.leechBudget());assertSame(root.leechBudget(),root.withSnapshot(root.snapshot().withMagnitudeFactor(.7)).leechBudget());h.current.put(ResourceType.MANA,0d);h.kernel.resources().recoverLeech(root.leechBudget(),new RootLeechBudget.HitReceipt(100,0,false,true,false),h);h.kernel.resources().recoverLeech(echo.leechBudget(),new RootLeechBudget.HitReceipt(1000,0,false,true,false),h);assertEquals(8,h.current(ResourceType.MANA));}
    @Test void barrageDescendantsCannotMultiplyRootCap(){var h=new Stage11ResourcePassivesTest.H("fire_bolt");h.weapon="STAFF";h.link("leeching",PassiveSlot.PASSIVE01);h.link("barrage",PassiveSlot.PASSIVE02);h.cast();assertSame(h.last().leechBudget(),h.last().barrageCopy(1).leechBudget());assertSame(h.last().leechBudget(),h.last().barrageCopy(2).leechBudget());}
    @Test void passiveCannotPairWithLifebloodInEitherLinkOrder(){for(var passives:List.of(List.of("lifeblood","leeching"),List.of("leeching","lifeblood"))){var h=new Stage11ResourcePassivesTest.H("quick_slash");h.link(passives.getFirst(),PassiveSlot.PASSIVE01);assertTrue(h.b.service().equipPassive(h.actor,PassiveSlot.PASSIVE02,new PassiveId(passives.getLast())).success());assertFalse(h.b.service().link(h.actor,LinkNodeId.PASSIVE02,LinkNodeId.SKILL01).success());}}
    @Test void foreignRootLedgerCannotBeAttachedToContext(){var h=new Stage11ResourcePassivesTest.H("quick_slash");h.cast();var c=h.last();assertThrows(IllegalArgumentException.class,()->new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),c.target(),false,0,new RootLeechBudget(UUID.randomUUID(),"foreign")));}
    @Test void periodicReceiptIsNotSuppressedByGenericNoProcRule(){var h=new H();h.arm(ResourceType.MANA);for(int i=0;i<4;i++)h.hit(100,50);assertEquals(6,h.mana);assertEquals(2,h.hit(1000,0).restored());}
}
