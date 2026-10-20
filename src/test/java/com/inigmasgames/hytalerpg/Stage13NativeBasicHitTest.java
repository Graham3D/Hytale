package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.execution.strike.FinisherLedger;
import com.inigmasgames.hytalerpg.execution.RootEffectBudget;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13NativeBasicHitTest {
    @ParameterizedTest @CsvSource({"false,4","true,12"})
    void realRecoveryRequiresHealthLossAndRestoresExactlyOneNormalOrChargedFraction(boolean charged,double amount){
        var resources=RpgCombatKernel.createProduction().resources();var port=new Port();var hit=new RootWeaponHit(UUID.randomUUID());
        assertFalse(resources.recoverHostileWeaponHit(hit,port).applied());assertEquals(0,port.writes);
        assertTrue(hit.observe(100,90,false,true,charged));
        var recovery=resources.recoverHostileWeaponHit(hit,port);assertTrue(recovery.applied());
        assertEquals(amount,recovery.manaRecovered());assertEquals(amount,recovery.staminaRecovered());assertEquals(2,port.writes);
        assertFalse(resources.recoverHostileWeaponHit(hit,port).applied());assertEquals(2,port.writes);
    }
    @Test void sameRootCannotRecoverAgainForAnotherVictimOrDamageComponent(){
        var hit=new RootWeaponHit(UUID.randomUUID());assertTrue(hit.observe(100,98,false,true,false));
        assertFalse(hit.observe(200,100,false,true,true));assertFalse(hit.charged());
    }
    @ParameterizedTest @CsvSource({"100,100,false,true","100,90,true,true","100,90,false,false","0,0,false,true","100,-1,false,true"})
    void blockedCancelledFriendlyAndInvalidHitsDoNotCreateReceipts(double before,double after,boolean cancelled,boolean hostile){
        var hit=new RootWeaponHit(UUID.randomUUID());assertFalse(hit.observe(before,after,cancelled,hostile,false));assertFalse(hit.claimRecovery());
        assertTrue(hit.observe(10,0,false,true,false)); // A later genuinely damaging native contact may qualify.
    }
    @Test void missingHealthCannotBeReplacedByReportedDamageAmount(){
        var hit=new RootWeaponHit(UUID.randomUUID());assertFalse(hit.observe(Double.NaN,90,false,true,false));
        assertFalse(hit.observe(100,Double.NaN,false,true,false));assertFalse(hit.claimRecovery());
    }
    @Test void newRootHasIndependentRecoveryIdentity(){
        var actor=UUID.randomUUID();var a=new RootWeaponHit(actor);var b=new RootWeaponHit(actor);assertNotEquals(a.id(),b.id());
        a.observe(10,9,false,true,false);b.observe(10,9,false,true,true);assertTrue(a.claimRecovery());assertTrue(b.claimRecovery());
    }
    @Test void partialRecoveryFailureCannotRetryTheFirstWrite(){
        var port=new Port();port.failStamina=true;var service=RpgCombatKernel.createProduction().resources();var root=new RootWeaponHit(UUID.randomUUID());root.observe(10,9,false,true,false);
        assertThrows(IllegalStateException.class,()->service.recoverHostileWeaponHit(root,port));assertEquals(24,port.values.get(ResourceType.MANA));
        port.failStamina=false;assertFalse(service.recoverHostileWeaponHit(root,port).applied());assertEquals(24,port.values.get(ResourceType.MANA));
    }
    @Test void depletedFixtureAndNativeFloatCreditUseObservedDeltas(){
        float initial=12.123456f;float credited=RpgResourceService.nativeCreditTarget(initial,4,100);
        assertTrue(credited-initial<=4);assertTrue(credited>initial);
    }
    @Test void threeRootsWithinInclusiveFourSecondsGrantOnePersistentToken(){
        var ledger=new FinisherLedger();var actor=UUID.randomUUID();ledger.observedRoot(actor,0);ledger.observedRoot(actor,2);ledger.observedRoot(actor,4);
        assertEquals(3,ledger.pips(actor,100));assertTrue(ledger.consume(actor));assertFalse(ledger.consume(actor));assertEquals(0,ledger.pips(actor,100));
    }
    @Test void rollingWindowIsNotALifetimeHitCount(){
        var ledger=new FinisherLedger();var actor=UUID.randomUUID();ledger.observedRoot(actor,0);ledger.observedRoot(actor,2);ledger.observedRoot(actor,4.001);
        assertEquals(2,ledger.pips(actor,4.001));assertFalse(ledger.consume(actor));assertEquals(0,ledger.pips(actor,9));
    }
    @Test void tokenDoesNotBankExtraTokensFromHitsWhileReady(){
        var ledger=new FinisherLedger();var actor=UUID.randomUUID();for(int i=0;i<20;i++)ledger.observedRoot(actor,i*.1);
        assertTrue(ledger.consume(actor));assertFalse(ledger.consume(actor));assertEquals(0,ledger.pips(actor,2));
    }
    @Test void ownerAndTerminalCleanupSeparateComboState(){
        var ledger=new FinisherLedger();var actor=UUID.randomUUID();for(int i=0;i<3;i++)ledger.observedRoot(actor,i);
        assertEquals(0,ledger.pips(UUID.randomUUID(),2));ledger.forget(actor);assertFalse(ledger.consume(actor));
    }
    @Test void backwardsOrNonfiniteNativeClockIsRejected(){
        var ledger=new FinisherLedger();var actor=UUID.randomUUID();ledger.observedRoot(actor,2);
        assertThrows(IllegalArgumentException.class,()->ledger.observedRoot(actor,1));assertThrows(IllegalArgumentException.class,()->ledger.observedRoot(actor,Double.NaN));
    }
    @Test void finisherConsumesAtRealServiceReleaseEvenOnMiss(){
        var h=finisher();grant(h,0);assertEquals(3,h.service.finisherPips(h.actor));
        assertTrue(h.cast().committed());assertEquals(0,h.service.finisherPips(h.actor));assertEquals(1.5,h.last().effects().finisherFactor());
        assertEquals(88,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);assertEquals(0,h.last().effects().triggered());
    }
    @Test void rejectedPaymentDoesNotConsumeFinisher(){
        var h=finisher();grant(h,0);h.current.put(ResourceType.STAMINA,0d);assertFalse(h.cast().committed());assertEquals(3,h.service.finisherPips(h.actor));
    }
    @Test void irreversibleDispatchFailureConsumesTokenAndRetainsPayment(){
        var h=finisher();grant(h,0);h.failDispatch=true;assertTrue(h.cast().committed());assertEquals(0,h.service.finisherPips(h.actor));
        assertEquals(88,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
    }
    @Test void delayedReleaseConsumesOnReleaseRatherThanCommit(){
        var h=finisher();h.link("skill_delay",PassiveSlot.PASSIVE01);grant(h,0);var result=h.cast();assertTrue(result.committed(),result.toString());assertEquals(3,h.service.finisherPips(h.actor));
        h.time=3;h.service.tickScheduled(h.actor,h);assertEquals(0,h.service.finisherPips(h.actor));assertEquals(1.5,h.last().effects().finisherFactor());
    }
    @Test void repeatedReleaseSharesResolvedScalarWithoutConsumingAnotherToken(){
        var root=new RootEffectBudget(UUID.randomUUID(),"root");int[] claims={0};root.resolveFinisher(()->{claims[0]++;return true;});
        root.resolveFinisher(()->{claims[0]++;return false;});assertEquals(1,claims[0]);assertEquals(1.5,root.finisherFactor());
    }
    private static Stage11ResourcePassivesTest.H finisher(){var h=new Stage11ResourcePassivesTest.H("finishing_strike");h.weapon="LONGSWORD";return h;}
    private static void grant(Stage11ResourcePassivesTest.H h,double time){for(int i=0;i<3;i++)h.service.observeNativeBasicRootHit(h.actor,time);}
    static class Port implements NativeResourcePort {
        final Map<ResourceType,Double> values=new EnumMap<>(Map.of(ResourceType.MANA,20d,ResourceType.STAMINA,20d));int writes;boolean failStamina;
        public double current(ResourceType type){return values.get(type);}
        public double maximum(ResourceType type){return 100;}
        public void setCurrent(ResourceType type,double value){if(failStamina&&type==ResourceType.STAMINA)throw new IllegalStateException("native write failed");values.put(type,value);writes++;}
    }
}
