package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HroContractsTest {
    private final HroContracts evaluator=new HroContracts(Set.copyOf(HroContracts.IDS));
    private long seq;
    private void event(String contract,String boundary,Map<String,String> facts) {
        evaluator.accept(new HroEvent(1,"fixture",++seq,100,seq,contract,"fixture",boundary,"operation","world",1L,1L,3L,facts),"events/segment-0001.jsonl.gz:"+seq);
    }
    private HroContracts.Outcome outcome(String contract) {
        return (HroContracts.Outcome)((Map<?,?>)evaluator.coverage(false).get(contract)).get("outcome");
    }
    @Test void omittedProductionPreloadBlocksAdmissionAndDoesNotBlameHud() {
        event("PLAYER.ADMISSION","READY_QUEUED",Map.of());
        event("PLAYER.ADMISSION","WAIT",Map.of("reason","CYCLONE_PREPARED_LEASE_UNAVAILABLE"));
        assertEquals(HroContracts.Outcome.BLOCKED,outcome("PLAYER.ADMISSION"));
        assertEquals(HroContracts.Outcome.NOT_EXERCISED,outcome("HUD.PUBLICATION"));
        assertEquals("READY_QUEUED",evaluator.findings(false).getFirst().lastSuccessfulBoundary());
        assertEquals("CYCLONE_PREPARED_LEASE_UNAVAILABLE",evaluator.findings(false).getFirst().firstDivergence());
    }
    @Test void savedSkillsWithoutProjectionAreNotInventedFailure() {
        event("SKILL.ASSIGN","COMPILE_SUCCESS",Map.of());event("SKILL.ASSIGN","SAVE",Map.of());
        event("SKILL.ASSIGN","EQUIP_SKILL_COMMITTED",Map.of());
        assertEquals(HroContracts.Outcome.PASS,outcome("SKILL.ASSIGN"));
        assertEquals(HroContracts.Outcome.NOT_EXERCISED,outcome("SKILL.PROJECTION"));
        event("SKILL.PROJECTION","SAVED_SLOT_OBSERVED",Map.of("logicalSlot","skill01","skillId","blizzard"));
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("SKILL.PROJECTION"));
        var gap = evaluator.findings(false).stream().filter(f -> f.contract().equals("SKILL.PROJECTION")).findFirst().orElseThrow();
        assertEquals("SAVED_SLOT_OBSERVED", gap.lastSuccessfulBoundary());
        assertEquals("SUCCESSOR_NOT_OBSERVED_CAPTURE_BOUNDARY", gap.firstDivergence());
        assertTrue(gap.evidence().endsWith(":4"));
        event("SKILL.PROJECTION","PROJECTION_READBACK",Map.of("result","PROJECTED"));
        assertEquals(HroContracts.Outcome.PASS,outcome("SKILL.PROJECTION"));
    }
    @Test void missingBagRequiresExplicitExpectation() {
        event("INVENTORY.OWNER","CUSTODY_SNAPSHOT",Map.of("componentPresent","false","expectedSpatial","true"));
        assertEquals(HroContracts.Outcome.FAIL,outcome("INVENTORY.OWNER"));
    }
    @Test void nativeFallbackWithoutMigrationExpectationIsGap() {
        event("INVENTORY.OWNER","CUSTODY_SNAPSHOT",Map.of("componentPresent","false"));
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("INVENTORY.OWNER"));
    }
    @Test void inactiveSlotIsOnlyFailureWhenUntranslated() {
        event("INVENTORY.TRANSFER","NATIVE_SLOT_RESOLVED",Map.of("rawNativeSlot","-1","resolvedNativeSlot","0"));
        assertNotEquals(HroContracts.Outcome.FAIL,outcome("INVENTORY.TRANSFER"));
        event("INVENTORY.TRANSFER","NATIVE_SLOT_RESOLVED",Map.of("rawNativeSlot","-1","resolvedNativeSlot","-1"));
        assertEquals(HroContracts.Outcome.FAIL,outcome("INVENTORY.TRANSFER"));
    }
    @Test void staleConnectionCannotCompleteAnEarlierAdmission() {
        event("PLAYER.ADMISSION","READY_QUEUED",Map.of());
        evaluator.accept(new HroEvent(1,"fixture",2,100,2,"PLAYER.ADMISSION","fixture","OWNER_INSTALLED",
                "operation","other-world",2L,1L,3L,Map.of()),"segment:2");
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("PLAYER.ADMISSION"));
    }
    @Test void missingObserverInvalidatesEvenApparentlyPassingReceipt() {
        var absent=new HroContracts(Set.of());
        absent.accept(new HroEvent(1,"fixture",1,1,1,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","op",null,null,null,null,Map.of()),"segment:1");
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,((Map<?,?>)absent.coverage(false).get("HUD.PUBLICATION")).get("outcome"));
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,absent.findings(false).getFirst().outcome());
    }
    @Test void droppedEvidenceInvalidatesEarlierPassAndFailure() {
        event("INVENTORY.OWNER","CUSTODY_SNAPSHOT",Map.of("expectedSpatial","true","componentPresent","false"));
        event("HUD.PUBLICATION","HUD_LAYOUT_READY",Map.of());evaluator.lost(1);
        assertTrue(evaluator.findings(false).stream().allMatch(f->f.outcome()==HroContracts.Outcome.INCONCLUSIVE));
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("HUD.PUBLICATION"));
    }
    @Test void legalUnsupportedSlotsAreBlockedNotRegression() {
        event("SKILL.PROJECTION","PROJECTION",Map.of("result","NATIVE_ABILITY4_UNAVAILABLE"));
        assertEquals(HroContracts.Outcome.BLOCKED,outcome("SKILL.PROJECTION"));
    }
    @Test void dispatchAndRejectionAreNotEffectProof() {
        event("SKILL.EXECUTION","SKILL_COMMITTED",Map.of());event("SKILL.EXECUTION","EXECUTOR_DISPATCH",Map.of());
        assertTrue(evaluator.findings(false).stream().anyMatch(f->f.outcome()==HroContracts.Outcome.PASS));
        event("SKILL.EXECUTION","EXECUTOR_DISPATCH",Map.of());
        assertEquals(HroContracts.Outcome.FAIL,outcome("SKILL.EXECUTION"));
    }
    @Test void rejectionCannotBeDeclaredFreeWithoutDebitEvidence() {
        event("SKILL.EXECUTION","SKILL_ACTIVATION_REJECTED",Map.of());
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("SKILL.EXECUTION"));
        event("SKILL.EXECUTION","RESOURCE_COMMIT",Map.of());
        assertEquals(HroContracts.Outcome.FAIL,outcome("SKILL.EXECUTION"));
    }
    @Test void laterDispatchCannotEraseDuplicateAdmission() {
        event("SKILL.EXECUTION", "SKILL_COMMITTED", Map.of());
        event("SKILL.EXECUTION", "SKILL_COMMITTED", Map.of());
        event("SKILL.EXECUTION", "EXECUTOR_DISPATCH", Map.of());
        assertEquals(HroContracts.Outcome.FAIL, outcome("SKILL.EXECUTION"));
        assertEquals("DUPLICATE_ADMITTED_EXECUTION", evaluator.findings(false).getFirst().firstDivergence());
    }
    @Test void MissingIdentityOnIntermediateReceiptCannotEraseKnownGeneration() {
        event("PLAYER.ADMISSION", "READY_QUEUED", Map.of());
        evaluator.accept(new HroEvent(1,"fixture",2,100,2,"PLAYER.ADMISSION","fixture","WAIT",
                "operation",null,null,null,null,Map.of("reason","PRELOAD_PENDING")),"segment:2");
        evaluator.accept(new HroEvent(1,"fixture",3,100,3,"PLAYER.ADMISSION","fixture","OWNER_INSTALLED",
                "operation","world",2L,1L,3L,Map.of()),"segment:3");
        assertEquals(HroContracts.Outcome.INCONCLUSIVE, outcome("PLAYER.ADMISSION"));
    }
    @Test void allNineHaveMatchedPassingControls() {
        event("BOOT.ARTIFACT","IDENTITY",Map.of("identityVerified","true"));
        event("PLAYER.ADMISSION","OWNER_INSTALLED",Map.of());
        event("SKILL.ASSIGN","COMPILE_SUCCESS",Map.of());event("SKILL.ASSIGN","SAVE",Map.of());event("SKILL.ASSIGN","EQUIP_SKILL_COMMITTED",Map.of());
        event("SKILL.PROJECTION","PROJECTION",Map.of("result","PROJECTED"));
        event("SKILL.EXECUTION","SKILL_COMMITTED",Map.of());event("SKILL.EXECUTION","EXECUTOR_DISPATCH",Map.of());
        event("HUD.PUBLICATION","HUD_LAYOUT_READY",Map.of());
        event("UI.LIFECYCLE","DOCUMENT_CLOSED",Map.of("closedOnce","true","hudRestored","true"));
        event("INVENTORY.OWNER","CUSTODY_SNAPSHOT",Map.of("expectedSpatial","true","componentPresent","true"));
        event("INVENTORY.TRANSFER","SETTLED",Map.of("quantityBefore","3","quantityAfter","3","singleCustodian","true","settled","true"));
        for(String id:HroContracts.IDS)if(!id.equals("SKILL.EXECUTION"))assertEquals(HroContracts.Outcome.PASS,outcome(id),id);
    }
    @Test void expiredAndInterruptedPendingOperationsRemainInconclusive() {
        event("SKILL.EXECUTION","SKILL_ACTIVATION_REQUEST",Map.of());
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("SKILL.EXECUTION"));
        assertTrue(evaluator.findings(true).stream().allMatch(f->f.outcome()==HroContracts.Outcome.INCONCLUSIVE));
    }
    @Test void boundedHighCardinalityBecomesExplicitLoss() {
        for(int i=0;i<2200;i++)evaluator.accept(new HroEvent(1,"fixture",i,1,i,"SKILL.EXECUTION","fixture","SKILL_ACTIVATION_REQUEST",
                "op-"+i,null,null,null,null,Map.of()),"segment:"+i);
        assertEquals(HroContracts.Outcome.INCONCLUSIVE,outcome("SKILL.EXECUTION"));
        assertTrue((long)((Map<?,?>)evaluator.coverage(false).get("SKILL.EXECUTION")).get("losses")>0);
    }
}
