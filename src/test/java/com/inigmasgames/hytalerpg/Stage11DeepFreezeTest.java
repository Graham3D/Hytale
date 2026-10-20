package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.ChillPulseLedger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11DeepFreezeTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void acceptsNativeChillComponentsAndRejectsNoChill(){
        for(String id:List.of("frost_bolt","frost_nova","cold_wave","blizzard","comet","avalanche","chilling_aura"))assertTrue(f.accepts(id,"deep_freeze"),id);
        for(String id:List.of("arcane_bolt","fire_bolt","minor_heal"))assertFalse(f.accepts(id,"deep_freeze"),id);
    }
    @Test void directPenaltyLeavesControlTimingFlightCostAndOffensiveSnapshotAlone(){
        var a=f.profiles.require("frost_bolt");var p=f.effective("frost_bolt","deep_freeze");assertEquals(a.projectile().coefficient()*.9,p.projectile().coefficient(),1e-9);
        assertEquals(a.projectile().statusSeconds(),p.projectile().statusSeconds());assertEquals(a.projectile().speed(),p.projectile().speed());assertEquals(a.resourceCost(),p.resourceCost());assertEquals(a.cooldownSeconds(),p.cooldownSeconds());assertEquals(0,f.plan("frost_bolt","deep_freeze").kernelModifiers().scalablePayloadIncreased());
    }
    @Test void areaPenaltyDoesNotAddStacksInTheProfileWhereChildrenCouldMultiplyIt(){
        var a=f.profiles.require("frost_nova").area();var p=f.effective("frost_nova","deep_freeze").area();assertEquals(a.coefficient()*.9,p.coefficient(),1e-9);assertEquals(2,p.chillStacks());assertEquals(a.radius(),p.radius());
    }
    @Test void chillingAuraPeriodicDamageAndItsClocksAreNotDirectHits(){
        var a=f.profiles.require("chilling_aura");var p=f.effective("chilling_aura","deep_freeze");assertEquals(a,p);assertTrue(f.plan("chilling_aura","deep_freeze").controls().deepFreeze());
    }
    @Test void cacheAndPlanHashesSeparateDeepFreezeFromPlain(){
        var a=f.profiles.require("frost_bolt");var plain=f.plan("frost_bolt");var deep=f.plan("frost_bolt","deep_freeze");assertNotEquals(plain.planHash(),deep.planHash());assertSame(a,f.resolver.resolve(a,plain));assertNotEquals(a,f.resolver.resolve(a,deep));
    }
    @Test void oneSuccessfulApplicationAddsOneBonusStack(){var h=new Harness();var result=h.hit("root",1,true);assertEquals("BONUS_APPLIED",result.bonusGate());assertEquals(2,result.results().size());assertEquals(2,h.stacks());assertEquals(1,h.status.retainedChillBonusCount());}
    @Test void unlinkedApplicationIsUnchangedAndHasNoBonusMemory(){var h=new Harness();assertEquals("NOT_LINKED",h.hit("root",2,false).bonusGate());assertEquals(2,h.stacks());assertEquals(0,h.status.retainedChillBonusCount());}
    @Test void forkChildrenShareTheRootTargetBonusCooldown(){
        var h=new Harness();h.hit("root",1,true);var second=h.hit("root",1,true);assertEquals("ROOT_TARGET_ONE_SECOND_ICD",second.bonusGate());assertEquals(3,h.stacks());assertEquals(1,second.results().size());
    }
    @Test void bonusBecomesAvailableExactlyAtOneSecond(){
        var h=new Harness();h.hit("root",1,true);h.now=999_999_999L;assertEquals("ROOT_TARGET_ONE_SECOND_ICD",h.hit("root",1,true).bonusGate());h.now=1_000_000_000L;
        assertEquals("BONUS_APPLIED",h.hit("root",1,true).bonusGate());assertTrue(h.status.inspect(h.victim).active().containsKey(RpgStatusType.FROZEN));assertEquals(0,h.stacks());
    }
    @Test void differentRootHasItsOwnPaidActivationOpportunity(){var h=new Harness();h.hit("first",1,true);assertEquals("BONUS_APPLIED",h.hit("second",1,true).bonusGate());assertEquals(4,h.stacks());}
    @Test void differentVictimDoesNotConsumeAnotherVictimsOpportunity(){var h=new Harness();h.hit("root",1,true);var other=UUID.randomUUID();assertEquals("BONUS_APPLIED",h.status.applyChill(h.owner,"root",other,ControlProfile.NORMAL,1,true).bonusGate());assertEquals(2,h.status.retainedChillBonusCount());}
    @Test void differentCasterIsNotBlockedByAnotherCastersRootLabel(){var h=new Harness();h.hit("root",1,true);assertEquals("BONUS_APPLIED",h.status.applyChill(UUID.randomUUID(),"root",h.victim,ControlProfile.NORMAL,1,true).bonusGate());assertEquals(4,h.stacks());}
    @Test void thresholdConsumesWholeApplicationWithoutPostFreezeChill(){
        var h=new Harness();h.hit("seed",4,false);var result=h.hit("root",2,true);assertEquals("BASE_THRESHOLD",result.bonusGate());assertEquals(1,result.results().size());assertEquals(RpgStatusType.FROZEN,result.results().getLast().type());assertEquals(0,h.stacks());assertEquals(0,h.status.retainedChillBonusCount());
    }
    @Test void activeFrozenCannotBeRefreshedByExtraChill(){
        var h=new Harness();h.hit("seed",5,false);h.now=1_000_000_000L;assertEquals("FROZEN_ACTIVE",h.hit("root",1,true).bonusGate());assertEquals(1,h.status.inspect(h.victim).active().get(RpgStatusType.FROZEN).remainingSeconds());assertEquals(0,h.stacks());
    }
    @Test void frozenImmunityStillHoldsAtFourAndRequiresLaterLegalApplication(){
        var h=new Harness();h.hit("seed",5,false);h.now=2_100_000_000L;h.hit("a",2,true);h.hit("b",1,false);assertEquals(4,h.stacks());assertEquals("BASE_REJECTED",h.hit("c",1,true).bonusGate());
        h.now=5_100_000_000L;assertEquals("BASE_THRESHOLD",h.hit("d",1,true).bonusGate());assertTrue(h.status.inspect(h.victim).active().containsKey(RpgStatusType.FROZEN));
    }
    @Test void protectedTargetCannotConsumeOrReceiveBonus(){var h=new Harness();h.control=new ControlProfile(true,false,false);assertEquals("BASE_REJECTED",h.hit("root",1,true).bonusGate());assertEquals(0,h.stacks());assertEquals(0,h.status.retainedChillBonusCount());}
    @Test void bossThresholdRemainsSlowNotFrozen(){
        var h=new Harness();h.control=new ControlProfile(false,true,false);h.hit("a",2,true);h.hit("b",2,true);
        assertFalse(h.status.inspect(h.victim).active().containsKey(RpgStatusType.FROZEN));assertTrue(h.status.inspect(h.victim).active().containsKey(RpgStatusType.FROZEN_SUBSTITUTE_SLOW));
    }
    @Test void eliteFrozenDurationIsStillReducedByControlPolicy(){var h=new Harness();h.control=new ControlProfile(false,false,false,true);h.hit("a",2,true);h.hit("b",1,true);assertEquals(1,h.status.inspect(h.victim).active().get(RpgStatusType.FROZEN).remainingSeconds());}
    @Test void noBonusWhenRapidPulseHasNotEarnedAWholeBaseStack(){
        var h=new Harness();var ledger=new ChillPulseLedger();int first=ledger.grant("victim",1,1);assertEquals(0,first);assertEquals(0,h.status.retainedChillBonusCount());
        int second=ledger.grant("victim",2,1);h.hit("auraRoot",second,true);assertEquals(2,h.stacks());
    }
    @Test void ownerBudgetRejectsOnlyBonusNotAuthoredChill(){
        var h=new Harness();for(int i=0;i<256;i++)h.status.applyChill(h.owner,"root",new UUID(1,i),ControlProfile.NORMAL,1,true);
        assertEquals("CHILL_BONUS_BUDGET",h.hit("root",1,true).bonusGate());assertEquals(1,h.stacks());assertEquals(256,h.status.retainedChillBonusCount());
    }
    @Test void globalBudgetIsBoundedAndExpiredEntriesAreReclaimed(){
        var h=new Harness();for(int owner=0;owner<16;owner++)for(int i=0;i<256;i++)h.status.applyChill(new UUID(2,owner),"root",new UUID(owner+10,i),ControlProfile.NORMAL,1,true);
        assertEquals(4096,h.status.retainedChillBonusCount());assertEquals("CHILL_BONUS_BUDGET",h.hit("root",1,true).bonusGate());h.now=1_000_000_000L;assertEquals(0,h.status.retainedChillBonusCount());assertEquals("BONUS_APPLIED",h.hit("root",1,true).bonusGate());
    }
    @Test void ownerTeardownDoesNotRemoveOtherOwnersVictimStatus(){
        var h=new Harness();h.hit("a",1,true);var other=UUID.randomUUID();h.status.applyChill(other,"b",h.victim,ControlProfile.NORMAL,1,true);h.status.forgetSource(h.owner);
        assertEquals(1,h.status.retainedChillBonusCount());assertEquals(4,h.stacks());h.status.forget(h.victim);assertEquals(0,h.status.retainedChillBonusCount());assertEquals(0,h.stacks());
    }
    @Test void malformedSourceOrStackCountRejected(){var h=new Harness();assertThrows(IllegalArgumentException.class,()->h.hit("",1,true));assertThrows(IllegalArgumentException.class,()->h.hit("root",0,true));assertThrows(IllegalArgumentException.class,()->h.hit("root",6,true));assertEquals(0,h.status.retainedChillBonusCount());}
    static final class Harness {
        final UUID owner=UUID.randomUUID(),victim=UUID.randomUUID();long now;ControlProfile control=ControlProfile.NORMAL;
        final StatusService status=new StatusService(CombatBalanceProfile.loadCanonical(),()->now);
        StatusService.ChillApplication hit(String root,int stacks,boolean deep){return status.applyChill(owner,root,victim,control,stacks,deep);}
        int stacks(){var view=status.inspect(victim).active().get(RpgStatusType.CHILL);return view==null?0:view.stacks();}
    }
}
