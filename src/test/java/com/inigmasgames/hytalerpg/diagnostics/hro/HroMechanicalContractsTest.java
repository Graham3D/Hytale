package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.diagnostics.hro.HroContracts.Outcome.*;

class HroMechanicalContractsTest {
    private final HroContracts contracts=new HroContracts(Set.copyOf(HroContracts.IDS));
    private long sequence;
    static Map<String,String> facts(String... pairs){var f=new HashMap<String,String>();for(int i=0;i<pairs.length;i+=2)f.put(pairs[i],pairs[i+1]);return f;}
    private void event(String contract,String boundary,String... pairs){contracts.accept(new HroEvent(1,"fixture",++sequence,100,sequence,
            contract,"owner",boundary,"operation","world",1L,null,null,facts(pairs)),"events/segment-0001.jsonl.gz:"+sequence);}
    private HroContracts.Outcome outcome(String id){return (HroContracts.Outcome)((Map<?,?>)contracts.coverage(false).get(id)).get("outcome");}
    private void modifier(String applied){event("AFFIX.MODIFIER","CONTRIBUTION","affixId","WA-001","itemId","item","equipmentRevision","r1",
            "units","PERCENT_POINTS_TO_FRACTION","bucket","INCREASED","value","25","applied",applied);}
    private void proc(boolean selected){event("AFFIX.PROC","ROLL","affixId","WA-135","itemId","item","targetId","target","equipmentRevision","r1","rootId","root","selected",String.valueOf(selected));}
    @Test void outcomeFromDifferentTargetCannotPass(){proc(true);event("AFFIX.PROC","OUTCOME","targetId","other","result","APPLIED","proof","NATIVE_READBACK");assertEquals(INCONCLUSIVE,outcome("AFFIX.PROC"));}
    private void debit(String before,String absorbed,String remaining,String requested,String overflow,boolean duplicate){
        event("CYCLONE.ACCOUNTING","DEBIT","generation","g1","receiptId","hit1","before",before,"absorbed",absorbed,"remaining",remaining,
                "requested",requested,"damageRemaining",overflow,"duplicate",String.valueOf(duplicate),"durable","true");}
    @Test void seventeenVersionedContracts(){assertEquals(17,HroContracts.IDS.size());assertEquals(Set.copyOf(HroContracts.IDS),HroContracts.ANCHORS.keySet());}
    @Test void manaBeforeLifeUsesConfirmedSpend(){event("CYCLONE.ACCOUNTING","MANA_BEFORE_LIFE","before","100","after","75","manaSpent","25","intended","40","uncertain","false");assertEquals(PASS,outcome("CYCLONE.ACCOUNTING"));}
    @Test void uncertainManaCannotPass(){event("CYCLONE.ACCOUNTING","MANA_BEFORE_LIFE","before","100","after","75","manaSpent","25","intended","40","uncertain","true");assertEquals(INCONCLUSIVE,outcome("CYCLONE.ACCOUNTING"));}
    @Test void sharedReceiptWithoutChildIdentityCannotPass(){event("CYCLONE.ACCOUNTING","SHARED_DEBIT","absorbed","25");assertEquals(INCONCLUSIVE,outcome("CYCLONE.ACCOUNTING"));}
    @Test void missingAcceptedDamageCannotPassNativeHealth(){event("COMBAT.RECONCILE","NATIVE_RESULT","healthBefore","100","healthAfter","75","accepted","NaN","postAbsorption","25","reconciliation","MATCH");assertEquals(INCONCLUSIVE,outcome("COMBAT.RECONCILE"));}
    @Test void authoredRollIsNotContribution(){event("AFFIX.MODIFIER","ROLLED_VALUE","affixId","WA-001","value","25");assertEquals(INCONCLUSIVE,outcome("AFFIX.MODIFIER"));}
    @Test void actualPercentageBoundaryPasses(){modifier("0.25");assertEquals(PASS,outcome("AFFIX.MODIFIER"));}
    @Test void percentWholeNumberRegressionFails(){modifier("25");assertEquals(FAIL,outcome("AFFIX.MODIFIER"));}
    @Test void mismatchWithoutSourceIsNotFailure(){event("AFFIX.MODIFIER","CONTRIBUTION","value","25","applied","25");assertEquals(INCONCLUSIVE,outcome("AFFIX.MODIFIER"));}
    @Test void immunityBlocksSelectedProc(){proc(true);event("AFFIX.PROC","OUTCOME","result","IMMUNE","proof","OWNER_IMMUNITY_DECISION");assertEquals(BLOCKED,outcome("AFFIX.PROC"));}
    @Test void nativeEffectReadbackPasses(){proc(true);event("AFFIX.PROC","OUTCOME","result","APPLIED","proof","NATIVE_READBACK");assertEquals(PASS,outcome("AFFIX.PROC"));}
    @Test void ownerAdmissionWithoutReadbackCannotPass(){proc(true);event("AFFIX.PROC","OUTCOME","result","APPLIED");assertEquals(INCONCLUSIVE,outcome("AFFIX.PROC"));}
    @Test void existingRollMissIsLegal(){proc(false);assertEquals(NOT_APPLICABLE,outcome("AFFIX.PROC"));}
    @Test void selectedMissingSettledEffectFails(){proc(true);event("AFFIX.PROC","OUTCOME","result","MISSING","settled","true","proof","NATIVE_READBACK");assertEquals(FAIL,outcome("AFFIX.PROC"));}
    @Test void pendingSelectedEffectIsInconclusive(){proc(true);assertEquals(INCONCLUSIVE,outcome("AFFIX.PROC"));}
    @Test void healthMatchUsesExistingReceipt(){event("COMBAT.RECONCILE","NATIVE_RESULT","healthBefore","100","healthAfter","80","accepted","25","postAbsorption","20","reconciliation","MATCH");assertEquals(PASS,outcome("COMBAT.RECONCILE"));}
    @Test void nativeHealthDivergenceFailsAtApply(){event("COMBAT.RECONCILE","NATIVE_RESULT","healthBefore","100","healthAfter","90","accepted","25","postAbsorption","20","reconciliation","MISMATCH");assertEquals(FAIL,outcome("COMBAT.RECONCILE"));assertEquals("NATIVE_APPLY_HEALTH_DIVERGENCE",contracts.findings(false).getFirst().firstDivergence());}
    @Test void barrierAndOverflowConserve(){debit("50","50","0","80","30",false);assertEquals(PASS,outcome("CYCLONE.ACCOUNTING"));}
    @Test void barrierRefillFails(){debit("50","30","50","80","50",false);assertEquals(FAIL,outcome("CYCLONE.ACCOUNTING"));}
    @Test void duplicateReceiptCannotAbsorbAgain(){debit("50","10","40","80","70",true);assertEquals(FAIL,outcome("CYCLONE.ACCOUNTING"));}
    @Test void duplicateNoOpIsLegal(){debit("50","0","50","80","80",true);assertEquals(NOT_APPLICABLE,outcome("CYCLONE.ACCOUNTING"));}
    @Test void duplicatePaymentFails(){for(int i=0;i<2;i++)event("CYCLONE.ACCOUNTING","PAYMENT","generation","g","receiptId","mana-1");assertEquals(FAIL,outcome("CYCLONE.ACCOUNTING"));}
    @Test void correctReflectionUsesActualAbsorption(){event("CYCLONE.ACCOUNTING","REFLECTION","absorbed","25","requestedReflection","5","reflectionFactor","0.2");assertEquals(PASS,outcome("CYCLONE.ACCOUNTING"));}
    @Test void reflectionUsingIncomingInsteadOfAbsorbedFails(){event("CYCLONE.ACCOUNTING","REFLECTION","absorbed","25","requestedReflection","20","reflectionFactor","0.2");assertEquals(FAIL,outcome("CYCLONE.ACCOUNTING"));}
    @Test void naturalNormalIsLegal(){event("SPAWN.ADMISSION","RARITY_DECISION","provenance","NATURAL","encounter","e","rarity","NORMAL");assertEquals(NOT_APPLICABLE,outcome("SPAWN.ADMISSION"));}
    @Test void closedWorldBlocks(){event("SPAWN.ADMISSION","ADMISSION_CLOSED","provenance","NATURAL","encounter","e","reason","WORLD_ADMISSION_CLOSED");assertEquals(BLOCKED,outcome("SPAWN.ADMISSION"));}
    @Test void acceptedUnpublishedPlanRequiresSettledEvidence(){event("SPAWN.PROMOTION","PLAN_ACCEPTED","provenance","NATURAL","encounter","e");assertEquals(INCONCLUSIVE,outcome("SPAWN.PROMOTION"));event("SPAWN.PROMOTION","SETTLED","provenance","NATURAL","encounter","e","published","false");assertEquals(FAIL,outcome("SPAWN.PROMOTION"));}
    @Test void planPublicationPasses(){event("SPAWN.PROMOTION","PLAN_ACCEPTED","provenance","QA","encounter","e");event("SPAWN.PROMOTION","ELITE_PUBLISHED","provenance","QA","encounter","e");assertEquals(PASS,outcome("SPAWN.PROMOTION"));}
    @Test void matchingDurableRevisionRecovers(){event("PERSISTENCE.RECOVERY","SAVED","durableSubject","s","schema","2","revision","8","fingerprint","same");event("PERSISTENCE.RECOVERY","LOADED","durableSubject","s","schema","2","revision","8","fingerprint","same");assertEquals(PASS,outcome("PERSISTENCE.RECOVERY"));}
    @Test void reloadWithoutEarlierEvidenceIsInconclusive(){event("PERSISTENCE.RECOVERY","LOADED","durableSubject","s","schema","2","revision","8","fingerprint","same");assertEquals(INCONCLUSIVE,outcome("PERSISTENCE.RECOVERY"));}
    @Test void durableContentMismatchAtSameRevisionFails(){event("PERSISTENCE.RECOVERY","SAVED","durableSubject","s","schema","2","revision","8","fingerprint","a");event("PERSISTENCE.RECOVERY","LOADED","durableSubject","s","schema","2","revision","8","fingerprint","b");assertEquals(FAIL,outcome("PERSISTENCE.RECOVERY"));}
    @Test void interveningRevisionIsNotGuessedRegression(){event("PERSISTENCE.RECOVERY","SAVED","durableSubject","s","schema","2","revision","8","fingerprint","a");event("PERSISTENCE.RECOVERY","LOADED","durableSubject","s","schema","2","revision","9","fingerprint","b");assertEquals(INCONCLUSIVE,outcome("PERSISTENCE.RECOVERY"));}
    @Test void partialCaptureInvalidatesFailure(){modifier("25");contracts.lost(1);assertEquals(INCONCLUSIVE,outcome("AFFIX.MODIFIER"));assertEquals(INCONCLUSIVE,contracts.findings(false).getFirst().outcome());}
    @Test void interruptionInvalidatesPassingFinding(){modifier(".25");assertEquals(INCONCLUSIVE,contracts.findings(true).getFirst().outcome());}
    @Test void missingRegistrationCannotPass(){var c=new HroContracts(Set.of());c.accept(new HroEvent(1,"r",1,1,1,"GEAR.EQUIPPED","p","EQUIPMENT","o",null,null,null,null,facts("itemId","i","equipmentRevision","r","accepted","true")),"e:1");assertEquals(INCONCLUSIVE,c.findings(false).getFirst().outcome());}
    @Test void firstSupportedDivergenceIsSticky(){modifier("25");modifier(".25");assertEquals(FAIL,outcome("AFFIX.MODIFIER"));assertTrue(contracts.findings(false).getFirst().evidence().endsWith(":1"));}
    @Test void zeroElitesAndQaAreNotNaturalFrequencyFailure(){var report=new HroMechanicalReport();for(int i=0;i<20;i++)report.accept(new HroEvent(1,"r",i,1,1,"SPAWN.ADMISSION","p","RARITY_DECISION","e"+i,null,null,null,null,facts("rarity","NORMAL","provenance",i==0?"QA":"NATURAL")),"e:"+i);var view=report.snapshot(false);assertEquals("INCONCLUSIVE",view.get("frequencyOutcome"));assertEquals(Map.of("QA/NORMAL",1L,"NATURAL/NORMAL",19L),view.get("spawnDecisionCounts"));assertEquals(com.inigmasgames.hytalerpg.gear.GearCatalog.load().rollableAffixes().size(),((Map<?,?>)view.get("affixes")).size());}
}
