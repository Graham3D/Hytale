package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.cooldown.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class Stage11SecondWindTest {
    @TempDir Path temp;
    static class H extends Stage09CooldownPersistenceTest.Harness {
        RpgCooldownService.Spend spend(){return cd.spendCharge(actor,"skill",2,10,1.3,0,CompiledSkillPlan.KernelModifiers.NONE);}
        int charges(int capacity){return cd.availableCharges(actor,"skill",capacity);}
    }
    @Test void twoChargesNotThreeAndBothPersist(){var h=new H();assertEquals(2,h.charges(2));h.spend();assertEquals(1,h.charges(2));h.spend();assertEquals(0,h.charges(2));assertThrows(IllegalStateException.class,h::spend);assertEquals(13,h.saved.get("skill").remainingWork());assertEquals(13,h.saved.get("skill").queued().getFirst().remainingWork());}
    @Test void rechargeIsSerialNotParallel(){var h=new H();h.spend();h.spend();h.now=13_000_000_000L;assertEquals(1,h.charges(2));assertEquals(13,h.cd.remaining(h.actor,"skill"));h.now=26_000_000_000L;assertEquals(2,h.charges(2));}
    @Test void elapsedTimeCarriesToSecondQueueEntryWithoutAFrameDelay(){var h=new H();h.spend();h.spend();h.now=15_000_000_000L;assertEquals(1,h.charges(2));assertEquals(11,h.cd.remaining(h.actor,"skill"));}
    @Test void swappingCapacityDoesNotRefillSpentCharges(){var h=new H();h.spend();h.spend();for(int i=0;i<100;i++){assertEquals(0,h.charges(1));assertEquals(0,h.charges(2));}h.now=13_000_000_000L;assertEquals(0,h.charges(1));assertEquals(1,h.charges(2));}
    @Test void enablingSecondWindDoesNotRewriteAlreadyRunningNormalCooldown(){var h=new H();h.start();h.spend();assertEquals(10,h.saved.get("skill").remainingWork());assertEquals(13,h.saved.get("skill").queued().getFirst().remainingWork());h.now=10_000_000_000L;assertEquals(13,h.cd.remaining(h.actor,"skill"));}
    @Test void reconnectRestoresBothDebtsWithoutOfflineCredit(){var h=new H();h.spend();h.spend();h.now=2_000_000_000L;h.cd.detach(h.actor);h.now=200_000_000_000L;assertEquals(0,h.charges(2));assertEquals(11,h.cd.remaining(h.actor,"skill"));assertEquals(13,h.cd.snapshot(h.actor).get("skill").queued().getFirst().remainingWork());}
    @Test void newServiceRestoresQueueAfterProcessRestart(){var h=new H();h.spend();h.spend();h.now=5_000_000_000L;h.cd.detach(h.actor);var restored=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->900_000_000_000L);restored.bindPersistence(h);assertEquals(8,restored.remaining(h.actor,"skill"));assertEquals(0,restored.availableCharges(h.actor,"skill",2));}
    @Test void failedSecondSpendSavePreservesFirstDebtAndAvailableCharge(){var h=new H();h.spend();h.fail=true;assertThrows(IllegalStateException.class,h::spend);assertEquals(1,h.charges(2));assertTrue(h.saved.get("skill").queued().isEmpty());}
    @Test void failedSecondExecutionRefundsOnlyItsOwnCharge(){var h=new H();h.spend();h.now=1_000_000_000L;var second=h.spend();assertTrue(h.cd.refundCharge(second));assertEquals(1,h.charges(2));assertEquals(12,h.cd.remaining(h.actor,"skill"));assertTrue(h.saved.get("skill").queued().isEmpty());assertFalse(h.cd.refundCharge(second));}
    @Test void olderRefundCannotEraseNewerSpend(){var h=new H();var first=h.spend();h.spend();assertFalse(h.cd.refundCharge(first));assertEquals(0,h.charges(2));}
    @Test void failedRefundSaveDoesNotPublishFreeCharge(){var h=new H();h.spend();var second=h.spend();h.fail=true;assertThrows(IllegalStateException.class,()->h.cd.refundCharge(second));assertEquals(0,h.charges(2));}
    @Test void independentSkillsDoNotShareDebt(){var h=new H();h.spend();h.spend();assertEquals(2,h.cd.availableCharges(h.actor,"another",2));}
    @Test void auraRateAndItsExpiryApplyAcrossSerialBoundary(){var h=new H();h.cd.spendCharge(h.actor,"skill",2,.1,1,0,CompiledSkillPlan.KernelModifiers.NONE);h.cd.spendCharge(h.actor,"skill",2,1,1,0,CompiledSkillPlan.KernelModifiers.NONE);h.cd.setAuraRate(h.actor,.5,1,.25);h.now=500_000_000L;assertEquals(1,h.charges(2));assertEquals(.625,h.cd.remaining(h.actor,"skill"),1e-9);}
    @Test void malformedQueuesRejectBeforePublication(){assertThrows(IllegalArgumentException.class,()->new SavedCooldown(0,0,List.of(new SavedCooldown.Queued(1,0))));assertThrows(IllegalArgumentException.class,()->new SavedCooldown(1,0,List.of(new SavedCooldown.Queued(1,0),new SavedCooldown.Queued(1,0))));assertThrows(IllegalArgumentException.class,()->new SavedCooldown.Queued(Double.NaN,0));}
    @Test void unsupportedCapacityCannotGrantExtraCharges(){var h=new H();assertThrows(IllegalArgumentException.class,()->h.charges(3));assertThrows(IllegalArgumentException.class,()->h.charges(0));}
    @Test void compatibilityAcceptsQuickstepAndRejectsAuraAndChannel(){var f=new Stage11FoundationTest();assertTrue(f.accepts("quickstep","second_wind"));assertFalse(f.accepts("emanatism","second_wind"));assertFalse(f.accepts("void_beam","second_wind"));}
    @Test void playerSchemaFiveMigratesDebtWithoutRefillingOrLosingLoadout(){
        var state=RpgPlayerState.create(UUID.randomUUID());state.equippedSkills[0]="quickstep";state.cooldowns.put("quickstep",new SavedCooldown(3,.2));var gson=new com.google.gson.Gson();var json=gson.toJsonTree(state).getAsJsonObject();json.addProperty("schemaVersion",5);json.getAsJsonObject("cooldowns").getAsJsonObject("quickstep").remove("queued");
        var migration=new RpgStateMigrator().migrate(json);var restored=gson.fromJson(migration.state(),RpgPlayerState.class);restored.normalizeShape();assertEquals(6,restored.schemaVersion);assertEquals(3,restored.cooldowns.get("quickstep").remainingWork());assertTrue(restored.cooldowns.get("quickstep").queued().isEmpty());assertEquals("quickstep",restored.equippedSkills[0]);
    }
    @Test void realRepositoryRoundTripsSerialQueue(){var state=RpgPlayerState.create(UUID.randomUUID());state.cooldowns.put("quickstep",new SavedCooldown(1,.2,List.of(new SavedCooldown.Queued(3,.1))));var repo=new FileRpgPlayerStateRepository(temp);repo.save(state);assertEquals(state.cooldowns,repo.load(state.playerUuid()).state().cooldowns);}
    @Test void schemaSixMissingQueueIsCorruptionNotARefill(){var state=RpgPlayerState.create(UUID.randomUUID());state.cooldowns.put("quickstep",new SavedCooldown(3,0));var json=new com.google.gson.Gson().toJsonTree(state).getAsJsonObject();json.getAsJsonObject("cooldowns").getAsJsonObject("quickstep").remove("queued");var repo=new FileRpgPlayerStateRepository(temp);assertDoesNotThrow(()->java.nio.file.Files.writeString(repo.path(state.playerUuid()),json.toString()));assertThrows(IllegalStateException.class,()->repo.load(state.playerUuid()));}
    @Test void realSkillServicePaysEachMovementChargeAndThirdCastRejects(){
        var h=new Stage09SupportRuntimeTest.Harness("quickstep"){
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public SkillExecutionResult executeMovement(SkillExecutionContext c){context=c;return SkillExecutionResult.committed("FIXTURE_MOVEMENT",0,4);}
        };
        h.link("second_wind",PassiveSlot.PASSIVE01);
        assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());h.execution.terminate(h.context,"FIRST_MOVEMENT_DONE");
        assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());h.execution.terminate(h.context,"SECOND_MOVEMENT_DONE");
        assertEquals("COOLDOWN_ACTIVE",h.cast().code());assertEquals(84,h.mana);assertEquals(1,h.kernel.cooldowns().snapshot(h.actor).get("quickstep").queued().size());
        assertEquals(h.kernel.cooldowns().calculate(2.5,1.3,h.context.snapshot().derivedStats().cooldownRecovery(),CompiledSkillPlan.KernelModifiers.NONE).finalSeconds(),h.context.snapshot().cooldownSeconds(),1e-9);
    }
    @Test void realFailedSecondDispatchCannotClearFirstChargeDebt(){
        var h=new Stage09SupportRuntimeTest.Harness("quickstep"){
            int casts;
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public SkillExecutionResult executeMovement(SkillExecutionContext c){context=c;if(++casts==2)throw new IllegalStateException("NO_EFFECT_CREATED");return SkillExecutionResult.committed("FIXTURE_MOVEMENT",0,4);}
        };
        h.link("second_wind",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());h.execution.terminate(h.context,"FIRST_MOVEMENT_DONE");
        assertEquals(SkillExecutionResult.Status.TERMINATED,h.cast().status());assertEquals(92,h.mana);assertEquals(1,h.kernel.cooldowns().availableCharges(h.actor,"quickstep",2));assertTrue(h.kernel.cooldowns().remaining(h.actor,"quickstep")>0);
    }
    @Test void migrationThroughRepositoryRetainsRecoverableOriginalSchemaFiveFile(){
        var state=RpgPlayerState.create(UUID.randomUUID());state.cooldowns.put("quickstep",new SavedCooldown(2,0));var json=new com.google.gson.Gson().toJsonTree(state).getAsJsonObject();json.addProperty("schemaVersion",5);json.getAsJsonObject("cooldowns").getAsJsonObject("quickstep").remove("queued");
        var repo=new FileRpgPlayerStateRepository(temp);assertDoesNotThrow(()->java.nio.file.Files.writeString(repo.path(state.playerUuid()),json.toString()));var loaded=repo.load(state.playerUuid());assertTrue(loaded.migrated());assertEquals(2,loaded.state().cooldowns.get("quickstep").remainingWork());
        assertDoesNotThrow(()->assertEquals(json.toString(),java.nio.file.Files.readString(repo.path(state.playerUuid()).resolveSibling(state.playerUuid()+".json.bak"))));
    }
}
