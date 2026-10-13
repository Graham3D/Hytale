package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deliverable example: synthetic evidence only; never a capture of the owner's character. */
class HroExampleBundleTest {
    @Test void producesStageTwoFailureAndMatchedControlEvidence()throws Exception {
        Path reports=Path.of("build/reports/hro").toAbsolutePath();
        Path directory=Files.createTempDirectory(Files.createDirectories(reports),"stage2-example-");
        String run="synthetic-stage2-example";
        try(var bundle=new HroBundle(directory,Map.of("schemaVersion",1,"runId",run,"state","RECORDING",
                "captureBuild",Map.of("source","SYNTHETIC_FIXTURE_NOT_LIVE_GAMEPLAY")),Set.copyOf(HroContracts.IDS))) {
            bundle.event(event(run,1,"AFFIX.MODIFIER","CONTRIBUTION","wrong-units",HroMechanicalContractsTest.facts(
                    "affixId","WA-001","itemId","item","equipmentRevision","r1","value","25","applied","25","units","PERCENT_POINTS_TO_FRACTION","bucket","INCREASED")));
            bundle.event(event(run,2,"AFFIX.MODIFIER","CONTRIBUTION","correct-units",HroMechanicalContractsTest.facts(
                    "affixId","WA-001","itemId","item","equipmentRevision","r1","value","25","applied",".25","units","PERCENT_POINTS_TO_FRACTION","bucket","INCREASED")));
            bundle.event(event(run,3,"SPAWN.ADMISSION","RARITY_DECISION","natural",Map.of("provenance","NATURAL","encounter","e","rarity","NORMAL")));
            bundle.event(event(run,4,"PERSISTENCE.RECOVERY","LOADED","missing-prior",Map.of("durableSubject","opaque","schema","2","revision","8","fingerprint","hash")));
            bundle.finish(0,"STOPPED",Map.of("qualification","SYNTHETIC_EXAMPLE_NO_TIMING_CLAIM"));
        }
        HroTool.analyze(directory);String findings=Files.readString(directory.resolve("findings.json"));
        assertTrue(findings.contains("PERCENT_UNIT_BOUNDARY"));assertTrue(findings.contains("EARLIER_DURABLE_EVIDENCE_MISSING"));
        assertTrue(Files.readString(directory.resolve("mechanical.json")).contains("INDEPENDENT_TRIAL_MODEL_NOT_ESTABLISHED"));
        Files.writeString(reports.resolve("stage2-example-path.txt"),directory.toString());
    }
    @Test void producesReadableExampleWithFirstMissingBoundary() throws Exception {
        Path reports = Path.of("build/reports/hro").toAbsolutePath();
        Path directory = Files.createTempDirectory(Files.createDirectories(reports), "example-");
        String run = "synthetic-stage1-example";
        var manifest = Map.<String,Object>of("schemaVersion", 1, "runId", run, "state", "RECORDING",
                "captureBuild", Map.of("source", "SYNTHETIC_FIXTURE_NOT_LIVE_GAMEPLAY"));
        try (HroBundle bundle = new HroBundle(directory, manifest, Set.copyOf(HroContracts.IDS))) {
            bundle.event(event(run, 1, "BOOT.ARTIFACT", "IDENTITY", "boot", Map.of("preloadRegistration", "false")));
            bundle.event(event(run, 2, "PLAYER.ADMISSION", "READY_QUEUED", "admission", Map.of()));
            bundle.event(event(run, 3, "PLAYER.ADMISSION", "WAIT", "admission", Map.of("reason", "CYCLONE_PREPARED_LEASE_UNAVAILABLE")));
            bundle.event(event(run, 4, "SKILL.PROJECTION", "SAVED_SLOT_OBSERVED", "slot-1", Map.of("skillId", "blizzard")));
            bundle.event(event(run, 5, "INVENTORY.OWNER", "CUSTODY_SNAPSHOT", "bag", Map.of("expectedSpatial", "true", "componentPresent", "false")));
            bundle.finish(0, "STOPPED", Map.of("qualification", "SYNTHETIC_EXAMPLE_NO_TIMING_CLAIM"));
        }
        HroTool.analyze(directory);
        assertTrue(Files.readString(directory.resolve("findings.json")).contains("SUCCESSOR_NOT_OBSERVED_CAPTURE_BOUNDARY"));
        assertTrue(Files.readString(directory.resolve("analysis.json")).contains("SYNTHETIC_FIXTURE_NOT_LIVE_GAMEPLAY"));
        Files.writeString(reports.resolve("example-path.txt"), directory.toString());
    }
    private static HroEvent event(String run, long seq, String contract, String boundary, String operation, Map<String,String> facts) {
        return new HroEvent(1, run, seq, 1_000 + seq, seq, contract, "synthetic-fixture", boundary,
                operation, "fixture-world", 1L, 1L, 1L, facts);
    }
}
