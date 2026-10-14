package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HroMasterEnemiesTest {
    @Test void all27OperatorsRequireExecutionAndSelectionNeverProducesAcceptance(){
        var report=new HroMasterEnemiesReport();
        for(int id=1;id<=27;id++){
            assertFalse(HroMasterEnemiesReport.requiredExecutionEvidence(id).isBlank());
            report.accept(event("ME12_SELECTED_AFFIX","select/"+id,Map.of("affixId","ME-%03d".formatted(id))),"fixture:"+id);
        }
        var effects=(Map<?,?>)report.snapshot(Map.of("synthetic",true),Map.of(),false,0).get("affixReadbackCoverage");assertEquals(27,effects.size());
        effects.values().forEach(value->assertEquals(HroMasterEnemiesReport.Status.BLOCKED,((Map<?,?>)value).get("nativeAcceptance")));
        report.accept(event("ME12_OPERATOR_READBACK","native",Map.of("affixId","ME-014","predicate","NATIVE_EFFECT_PRESENT_AFTER_APPLICATION","status","PASS","present","true")),"fixture:28");
        effects=(Map<?,?>)report.snapshot(Map.of(),Map.of(),false,0).get("affixReadbackCoverage");
        assertEquals(HroMasterEnemiesReport.Status.PASS,((Map<?,?>)effects.get("ME-014")).get("nativeEffectPresenceReadback"));
        effects=(Map<?,?>)report.snapshot(Map.of(),Map.of(),false,1).get("affixReadbackCoverage");
        assertEquals(HroMasterEnemiesReport.Status.BLOCKED,((Map<?,?>)effects.get("ME-014")).get("nativeEffectPresenceReadback"));
    }
    HroEvent event(String boundary,String operation,Map<String,String> facts){return new HroEvent(1,"SYNTHETIC",1,1,1,"PERSISTENCE.RECOVERY","offline-fixture",boundary,operation,"fixture-world",1L,1L,1L,facts);}
    HroEvent ownerReadback(String affix,String operation,String predicate,String status){return new HroEvent(1,"fixture",1,1,1,
            "AFFIX.PROC","MasterEnemies","ME12_OPERATOR_READBACK",operation,"fixture-world",1L,1L,1L,
            Map.of("affixId",affix,"predicate",predicate,"status",status,"nativeActor","actor","generation","1"));}
    @Test void all27NamedReadbacksNeverFabricateCompleteNativeAcceptance(){
        var identified=Map.<String,Object>of("captureBuild",Map.of("rpg.sourceCommit","a".repeat(40),"jarSha256","b".repeat(64)));
        for(int index=1;index<=27;index++){
            String id="ME-%03d".formatted(index);var required=HroMasterEnemiesReport.requiredPredicates(id);
            assertFalse(required.isEmpty(),id);assertEquals(required.size(),Set.copyOf(required).size(),id);
            var report=new HroMasterEnemiesReport();
            for(int p=0;p<required.size();p++)report.accept(ownerReadback(id,"split/"+p,required.get(p),"PASS"),"fixture:"+p);
            var split=(Map<?,?>)((Map<?,?>)report.snapshot(identified,Map.of(),false,0).get("affixReadbackCoverage")).get(id);
            assertEquals(HroMasterEnemiesReport.Status.BLOCKED,split.get("nativeAcceptance"),id);
            assertEquals(required.size(),split.get("observedOperations"),id);
            for(var predicate:required)report.accept(ownerReadback(id,"whole",predicate,"PASS"),"fixture:whole");
            var complete=(Map<?,?>)((Map<?,?>)report.snapshot(identified,Map.of(),false,0).get("affixReadbackCoverage")).get(id);
            assertEquals(HroMasterEnemiesReport.Status.BLOCKED,complete.get("nativeAcceptance"),id);
            assertTrue(((Map<?,?>)complete.get("executionPredicates")).values().stream()
                    .allMatch(status->status==HroMasterEnemiesReport.Status.PASS),id);
            var synthetic=(Map<?,?>)((Map<?,?>)report.snapshot(Map.of("synthetic",true,"captureBuild",identified.get("captureBuild")),Map.of(),false,0)
                    .get("affixReadbackCoverage")).get(id);
            assertEquals(HroMasterEnemiesReport.Status.BLOCKED,synthetic.get("nativeAcceptance"),id);
            var lost=(Map<?,?>)((Map<?,?>)report.snapshot(identified,Map.of(),false,1).get("affixReadbackCoverage")).get(id);
            assertEquals(HroMasterEnemiesReport.Status.BLOCKED,lost.get("nativeAcceptance"),id);
            assertTrue(((Map<?,?>)lost.get("executionPredicates")).values().stream()
                    .allMatch(status->status==HroMasterEnemiesReport.Status.BLOCKED),id);
            report.accept(ownerReadback(id,"whole",required.getFirst(),"FAIL"),"fixture:fail");
            var failed=(Map<?,?>)((Map<?,?>)report.snapshot(identified,Map.of(),false,0).get("affixReadbackCoverage")).get(id);
            assertEquals(HroMasterEnemiesReport.Status.FAIL,failed.get("nativeAcceptance"),id);
        }
    }
    @Test void qaAndUnprovenPublicationDoNotCountAsNaturalV2AndEmptyEffectsAreNotPasses(){
        var report=new HroMasterEnemiesReport();
        report.accept(event("ELITE_PUBLISHED","qa",Map.of("provenance","QA","schemaVersion","2","nativeSourceReceipt","qa-command/fixture")),"fixture:1");
        report.accept(event("ELITE_PUBLISHED","v1",Map.of("provenance","NATURAL","schemaVersion","1","nativeSourceReceipt","native-job/old")),"fixture:2");
        report.accept(event("ELITE_PUBLISHED","v2",Map.of("provenance","NATURAL","schemaVersion","2","nativeSourceReceipt","native-job/new")),"fixture:3");
        report.accept(event("ME12_EFFECT_RESULT","empty",Map.of("affixId","ME-016","requested","10","applied","0","result","EMPTY")),"fixture:4");
        var reportData=report.snapshot(Map.of("synthetic",true),Map.of(),false,0);
        assertEquals(1,((Map<?,?>)reportData.get("denominators")).get("publishedNaturalV2Encounters"));
        assertEquals(HroMasterEnemiesReport.Status.NOT_APPLICABLE,((Map<?,?>)((Map<?,?>)reportData.get("affixReadbackCoverage")).get("ME-016")).get("directResourceReadback"));
        report.accept(event("ME12_EFFECT_RESULT","bad",Map.of("affixId","ME-016","requested","10","applied","11","result","APPLIED")),"fixture:5");
        reportData=report.snapshot(Map.of(),Map.of(),false,0);
        assertEquals(HroMasterEnemiesReport.Status.FAIL,((Map<?,?>)((Map<?,?>)reportData.get("affixReadbackCoverage")).get("ME-016")).get("directResourceReadback"));
    }
    @Test void allFiveStatusesMissingEvidenceLossAndSyntheticExamples()throws Exception{
        var report=new HroMasterEnemiesReport();report.accept(event("ME12_QUANTITY","defeat",Map.of("baseCount","5","finalCount","16")),"synthetic:1");
        report.accept(event("ME12_EXCLUDED","qa",Map.of("reason","QA_ORIGIN_NO_ECONOMY")),"synthetic:2");
        var snapshot=report.snapshot(Map.of("synthetic",true,"source","EXACT_TESTED_COMMIT_IN_FINAL_MANIFEST"),Map.of(),false,0);
        var statuses=(Map<?,?>)snapshot.get("statuses");assertEquals(HroMasterEnemiesReport.Status.PASS,statuses.get("quantityBounds"));
        assertEquals(HroMasterEnemiesReport.Status.NOT_APPLICABLE,statuses.get("excludedActorOpportunity"));
        assertEquals(HroMasterEnemiesReport.Status.NOT_OBSERVED,statuses.get("generatorAvailability"));assertEquals(HroMasterEnemiesReport.Status.BLOCKED,statuses.get("realPassiveSource"));
        assertEquals(HroMasterEnemiesReport.Status.BLOCKED,((Map<?,?>)report.snapshot(Map.of(),Map.of(),false,1).get("statuses")).get("quantityBounds"));
        var folder=Path.of("build/reports/me12/diagnostic-example");Files.createDirectories(folder);
        Files.writeString(folder.resolve("master-enemies.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
        Files.writeString(folder.resolve("master-enemies.md"),HroMasterEnemiesReport.markdown(snapshot));
        report.accept(event("ME12_QUANTITY","invalid",Map.of("baseCount","0","finalCount","1")),"synthetic:3");
        assertEquals(HroMasterEnemiesReport.Status.FAIL,((Map<?,?>)report.snapshot(Map.of(),Map.of(),false,0).get("statuses")).get("quantityBounds"));
        assertEquals(HroMasterEnemiesReport.Status.NOT_OBSERVED,HroMasterEnemiesReport.map("NOT_EXERCISED"));
        assertEquals(HroMasterEnemiesReport.Status.BLOCKED,HroMasterEnemiesReport.map("INCONCLUSIVE"));
    }
    @Test void boundedCaptureFinalizesBothReportsAndProtectsOwner()throws Exception{
        var root=Files.createTempDirectory("me12-hro-test");UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        try(var recorder=new HroRecorder(root,System::nanoTime,()->Map.of("synthetic",true),()->{})){
            HroContracts.IDS.forEach(recorder::registered);recorder.start(owner,world);HroRecorderTest.await(()->recorder.active(owner));
            assertTrue(recorder.stop(UUID.randomUUID()).contains("another player"));assertTrue(recorder.active(owner));
            recorder.observe(owner,"PERSISTENCE.RECOVERY","fixture","ME12_QUANTITY","defeat",world,1L,1L,1L,Map.of("baseCount",1,"finalCount",3,"quantityFactor",3.075,"password","REDACT"));
            recorder.stop(owner);HroRecorderTest.await(()->HroRecorderTest.finalized(recorder.status()));
            try(var dirs=Files.list(root)){var bundle=dirs.filter(Files::isDirectory).findFirst().orElseThrow();
                String json=Files.readString(bundle.resolve("master-enemies.json"));assertTrue(json.contains("quantityFactor"));assertFalse(json.contains("REDACT"));
                assertTrue(Files.readString(bundle.resolve("master-enemies.md")).contains("SYNTHETIC OFFLINE FIXTURE"));
                HroTool.analyze(bundle);
            }
        }
    }
}
