package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HroMasterEnemiesTest {
    HroEvent event(String boundary,String operation,Map<String,String> facts){return new HroEvent(1,"SYNTHETIC",1,1,1,"PERSISTENCE.RECOVERY","offline-fixture",boundary,operation,"fixture-world",1L,1L,1L,facts);}
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
