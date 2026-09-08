package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** No connected/native hit or presentation claims: shared production ledger and execution fixtures. */
class Stage09FinalSupportPassivesTest {
    @TempDir Path temp;
    @Test void finalPassiveCompatibilityUsesActualPayloads(){
        for(String[] p:new String[][]{{"triage","minor_heal"},{"triage","life_drain"},{"shared_aegis","managuard"},
                {"shared_aegis","spirit_shield"},{"reflective_ward","spirit_shield"},{"reflective_ward","managuard"}})assertTrue(compatible(p[0],p[1]));
        for(String[] p:new String[][]{{"triage","emanatism"},{"shared_aegis","frost_nova"},{"reflective_ward","reflective_hide"},
                {"shared_aegis","shield_bash"},{"reflective_ward","shield_bash"},{"shared_aegis","guard"},
                {"reflective_ward","guard"},{"shared_aegis","bone_cage"},{"reflective_ward","bone_cage"}})assertFalse(compatible(p[0],p[1]),Arrays.toString(p));
    }
    @Test void triageIsStrictlyBelowThirtyFivePercent(){
        var below=heal(34.999,"triage");assertEquals(34.999+20.6*1.35,below.health,1e-9);
        var boundary=heal(35,"triage");assertEquals(55.6,boundary.health,1e-9);
    }
    @Test void triageAddsToPotencyRatherThanMultiplyingIt(){
        var h=heal(10,"triage","potency");assertEquals(10+20.6*1.5,h.health,1e-9);assertEquals(88,h.mana);
    }
    @Test void echoRechecksTriageAtItsOwnResolution(){
        var h=heal(10,"triage","echo");assertEquals(10+20.6*1.35,h.health,1e-9);h.tick(.45);
        assertEquals(10+20.6*1.35+20.6*.7,h.health,1e-9);assertEquals(88,h.mana);
    }
    @Test void triageNeverAmplifiesTheDrainFraction(){
        var mods=SupportModifiers.from(List.of(new PassiveId("triage")));var healing=new com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService()
                .fromActualDamage(10,.6,1.03,.15+mods.healingIncreased(20,100));
        assertEquals(.6,healing.fraction());assertEquals(6*1.03*1.5,healing.requestedHealing(),1e-9);
    }
    @Test void triageRejectsInvalidHealthObservations(){
        assertThrows(IllegalArgumentException.class,()->SupportModifiers.NONE.healingIncreased(Double.NaN,100));
        assertThrows(IllegalArgumentException.class,()->SupportModifiers.NONE.healingIncreased(101,100));
    }
    @Test void wardReducesSpiritShieldCapacityOnce(){
        var h=shield("reflective_ward","potency");h.cast();assertEquals(30.9*1.15*.8,h.shield(),1e-9);assertEquals(82,h.mana);
    }
    @Test void wardAndConservationComposeOnActualManaguardReservation(){
        var h=guard("reflective_ward","conservation");h.cast();assertEquals(40,h.reserved);assertEquals(28.8,h.saved.managuard().lastValidatedCapacity(),1e-9);
        assertEquals(11.2,h.hit(40),1e-9);
    }
    @Test void wardUsesActuallyConsumedCapacityNotIncomingDamageOrCurrentHealthLoss(){
        var h=shield("reflective_ward");h.cast();var hit=h.runtime.finite().shieldHit(h.world,h.actor,100,false,0,(e,a)->false);
        assertEquals(24.72,hit.absorbed(),1e-9);assertEquals(24.72*.2,SupportMagnitude.wardReflection(h.context,hit.absorbed()),1e-9);
        assertEquals(0,SupportMagnitude.wardReflection(h.context,0));assertThrows(IllegalArgumentException.class,()->SupportMagnitude.wardReflection(h.context,Double.NaN));
    }
    @Test void sharedSpiritShieldHasHalfCreatedCapacityAndSameExpiry(){
        var h=shield("shared_aegis","reflective_ward");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        var child=h.runtime.finite().forTarget(h.world,ally,0).getFirst();assertEquals(SupportProfile.Kind.SHARED_SHIELD,child.kind());
        assertEquals(30.9*.8*.5,child.shieldRemaining(),1e-9);assertEquals(8,child.ends());assertEquals(h.context.rootCastId(),child.rootCastId());
        assertEquals(h.context.skillInstanceId(),child.skillInstanceId());assertEquals(h.context.request().correlationId(),child.correlationId());
    }
    @Test void sharedChildNeverRedirectsOrSharesRecursively(){
        var h=shield("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        assertTrue(h.runtime.finite().shareCreatedShield(h.context,UUID.randomUUID(),0).isEmpty());
        var hit=h.runtime.finite().shieldHit(h.world,ally,30,true,0,(e,a)->{throw new AssertionError();});assertEquals(0,hit.redirected());
        assertTrue(h.runtime.finite().shareCreatedShield(h.context,ally,0).isEmpty());
    }
    @Test void allyTargetedSpiritShieldDoesNotCreateASecondAllyShield(){
        var h=shield("shared_aegis");h.target=UUID.randomUUID();h.members.add(h.target);h.members.add(UUID.randomUUID());h.cast();assertEquals(1,h.runtime.finite().size());
    }
    @Test void noEligibleAllyMeansNoDerivedShield(){var h=shield("shared_aegis");h.cast();assertEquals(1,h.runtime.finite().size());}
    @Test void managuardShareChoosesOneRecipientAndPreservesDeficitWhenItChanges(){
        var h=guard("shared_aegis");var first=UUID.randomUUID();var second=UUID.randomUUID();h.members.add(first);h.members.add(second);h.cast();
        assertEquals(1,h.runtime.sharedGuards(h.world,first,0).size());assertTrue(h.runtime.sharedGuards(h.world,second,0).isEmpty());
        assertEquals(0,sharedHit(h,first,10).remainder());assertEquals(10,h.saved.managuard().sharedDeficit());
        h.members.remove(first);h.tick(.1);assertTrue(h.runtime.sharedGuards(h.world,first,.1).isEmpty());
        assertEquals(15,h.runtime.sharedGuards(h.world,second,.1).getFirst().shieldRemaining());assertEquals(50,h.mana);
    }
    @Test void ownerDamageReducesSharedCeilingWithoutRestoringAlreadyConsumedAllyShield(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();sharedHit(h,ally,10);h.hit(40);
        assertEquals(5,h.runtime.sharedGuards(h.world,ally,0).getFirst().shieldRemaining());assertEquals(20,h.saved.managuard().sharedDeficit());
    }
    @Test void sharedDamageDoesNotConsumeTheOwnersSeparateSelfShield(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();assertEquals(15,sharedHit(h,ally,40).remainder());
        assertEquals(0,h.saved.managuard().deficit());assertEquals(25,h.saved.managuard().sharedDeficit());assertEquals(0,h.hit(50));
    }
    @Test void sharedDeficitRechargeUsesTheSameObservedDelayAndHalfCapacityRate(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();h.runtime.hostileDamage(h.actor,0);sharedHit(h,ally,20);
        h.advance(6);assertEquals(20,h.saved.managuard().sharedDeficit(),1e-8);h.advance(8);assertEquals(15,h.runtime.state(h.actor).managuard().sharedDeficit(),1e-8);
    }
    @Test void staleSharedMembershipCannotAbsorbAfterLeaseExpiry(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();var offered=h.runtime.sharedGuards(h.world,ally,0).getFirst();
        assertTrue(h.runtime.sharedGuards(h.world,ally,.26).isEmpty());assertEquals(10,h.runtime.absorbShared(offered,10,.26,h).remainder());
    }
    @Test void failedDurableSharedAbsorptionGrantsNoShield(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();h.failSave=true;
        assertThrows(IllegalStateException.class,()->sharedHit(h,ally,10));assertEquals(0,h.saved.managuard().sharedDeficit());
    }
    @Test void lowerAndReRaiseCapacityCannotEraseSharedDeficit(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(50,1).absorbShared(20,50).ledger();
        ledger=ledger.validateCapacity(10,1);assertEquals(0,ledger.sharedCurrent(10));ledger=ledger.validateCapacity(50,1);assertEquals(5,ledger.sharedCurrent(50));
    }
    @Test void toggleCannotCreateFreshSharedCapacity(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();sharedHit(h,ally,20);h.advance(3);h.runtime.stopActive(h.actor,"managuard",h);
        h.advance(6);h.runtime.execute(h.context,6,h);assertEquals(5,h.runtime.sharedGuards(h.world,ally,6).getFirst().shieldRemaining(),1e-8);assertEquals(0,h.mana);
    }
    @Test void schemaFourMigrationDerivesSharedDeficitWithoutTouchingOldLoadout(){
        var state=RpgPlayerState.create(UUID.randomUUID());state.support=state.support.guard(new ManaguardLedger(30,50,50));
        var json=new Gson().toJsonTree(state).getAsJsonObject();json.addProperty("schemaVersion",4);json.getAsJsonObject("support").getAsJsonObject("managuard").remove("sharedDeficit");
        var result=new RpgStateMigrator().migrate(json);assertTrue(result.migrated());assertEquals(RpgPlayerState.CURRENT_SCHEMA,result.targetVersion());
        var loaded=new Gson().fromJson(result.state(),RpgPlayerState.class);assertEquals(30,loaded.support.managuard().deficit());assertEquals(15,loaded.support.managuard().sharedDeficit());
        assertEquals(json.get("equippedSkills"),result.state().get("equippedSkills"));
    }
    @Test void savedSharedDeficitSurvivesRestartWithoutAnActiveRecipient(){
        var state=RpgPlayerState.create(UUID.randomUUID());state.support=state.support.guard(ManaguardLedger.INITIAL.validateCapacity(50,1).absorbShared(17,50).ledger());
        var repo=new FileRpgPlayerStateRepository(temp);repo.save(state);var loaded=repo.load(state.playerUuid()).state();
        assertEquals(17,loaded.support.managuard().sharedDeficit());assertEquals(8,loaded.support.managuard().sharedCurrent(50));
        assertEquals(state.support,loaded.support);
    }
    @Test void malformedSharedDeficitIsRejected(){
        assertThrows(IllegalArgumentException.class,()->new ManaguardLedger(0,50,50,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new ManaguardLedger(0,50,50,-1));
    }
    @Test void finiteSecondaryBudgetIsSharedWithTheDerivedAllyAndReportsOnlyFirstRejection(){
        var h=shield("shared_aegis","reflective_ward");var ally=UUID.randomUUID();h.members.add(ally);h.cast();var effect=h.runtime.finite().forTarget(h.world,ally,0).getFirst();
        for(int i=0;i<15;i++)assertEquals(1,h.runtime.claimSupportSecondary(effect,0));assertEquals(-1,h.runtime.claimSupportSecondary(effect,0));
        for(int i=0;i<100;i++)assertEquals(0,h.runtime.claimSupportSecondary(effect,0));h.runtime.finite().expire(8);assertEquals(0,h.runtime.finite().secondaryRootCount());
    }
    @Test void auraWardSecondaryBudgetRenewsWithoutAccumulatingUnusedTokens(){
        var h=guard("shared_aegis","reflective_ward");var ally=UUID.randomUUID();h.members.add(ally);h.cast();var effect=h.runtime.sharedGuards(h.world,ally,0).getFirst();
        for(int i=0;i<8;i++)assertEquals(1,h.runtime.claimSupportSecondary(effect,0));assertEquals(-1,h.runtime.claimSupportSecondary(effect,0));assertEquals(0,h.runtime.claimSupportSecondary(effect,0));
        h.advance(1);for(int i=0;i<8;i++)assertEquals(1,h.runtime.claimSupportSecondary(effect,1));assertEquals(-1,h.runtime.claimSupportSecondary(effect,1));
    }
    @Test void endingManaguardRemovesSharedRecipientsAndReservationsWithoutRefund(){
        var h=guard("shared_aegis");var ally=UUID.randomUUID();h.members.add(ally);h.cast();sharedHit(h,ally,12);h.runtime.detach(h.actor,"LOGOUT",h);
        assertTrue(h.runtime.sharedGuards(h.world,ally,0).isEmpty());assertEquals(0,h.budget.size());assertEquals(0,h.reserved);assertEquals(50,h.mana);assertEquals(12,h.saved.managuard().sharedDeficit());
    }
    private static SupportRuntime.GuardHit sharedHit(Stage09SupportRuntimeTest.Harness h,UUID ally,double amount){return h.runtime.absorbShared(h.runtime.sharedGuards(h.world,ally,h.now).getFirst(),amount,h.now,h);}
    private static Stage09AuraRuntimeTest.Harness guard(String...ids){var h=new Stage09AuraRuntimeTest.Harness("managuard");for(int i=0;i<ids.length;i++)h.link(ids[i],PassiveSlot.values()[i]);return h;}
    private static Stage09BarrierSupportTest.Harness shield(String...ids){var h=new Stage09BarrierSupportTest.Harness("spirit_shield");for(int i=0;i<ids.length;i++)h.link(ids[i],PassiveSlot.values()[i]);return h;}
    private static Stage09SupportRuntimeTest.Harness heal(double hp,String...ids){var h=new Stage09SupportRuntimeTest.Harness("minor_heal");h.health=hp;for(int i=0;i<ids.length;i++)h.link(ids[i],PassiveSlot.values()[i]);assertTrue(h.cast().committed());return h;}
    private static boolean compatible(String passive,String skill){var c=Stage01BTestSupport.bundle().catalog();return new CompatibilityService().assess(c.skill(new SkillId(skill)).orElseThrow(),c.passive(new PassiveId(passive)).orElseThrow()).accepted();}
}
