package com.inigmasgames.persistentnpcs.training.cli;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.training.TrainingMode;
import com.inigmasgames.persistentnpcs.training.curation.CurationPolicy;
import com.inigmasgames.persistentnpcs.training.curation.DeterministicCurationEngine.CurationResult;
import com.inigmasgames.persistentnpcs.training.dataset.Block2FixtureCatalog;
import com.inigmasgames.persistentnpcs.training.dataset.ContaminationChecker;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetAssembler;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetSplit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ReviewApproval;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetFreezer;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetPolicy;
import com.inigmasgames.persistentnpcs.training.dataset.LicenseManifests;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.registry.TrainingArtifactRegistries;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline D4/D5 operator entrypoint. It has no production runtime registration. */
public final class Block2Cli {
    private Block2Cli() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "usage: <curate-fixture|freeze-fixture|report> <projectRoot> <offlineRoot> <activeSaveRoot>");
        }
        if (TrainingMode.OFF.permitsModelMutation()) throw new IllegalStateException(
                "TrainingMode.OFF invariant violated");
        String command = args[0];
        Path projectRoot = Path.of(args[1]).toAbsolutePath().normalize();
        ArtifactRoot root = new ArtifactRoot(Path.of(args[2]), Path.of(args[3]));
        root.initialize();
        TrainingArtifactRegistries registries = new TrainingArtifactRegistries(root);
        registries.initialize();
        if (command.equals("report")) {
            report(root); return;
        }
        CurationPolicy curationPolicy = CurationPolicy.defaultOffline();
        Block2FixtureCatalog catalog = new Block2FixtureCatalog(projectRoot, curationPolicy);
        List<CurationResult> results = catalog.curatePositiveFixtures();
        writeD4Evidence(root, results, curationPolicy);
        long accepted = results.stream().filter(CurationResult::accepted).count();
        if (accepted != results.size()) throw new IllegalStateException(
                "D4 gate failed: accepted " + accepted + " of " + results.size() + " fixtures; "
                        + results.stream().filter(value -> !value.accepted())
                                .map(value -> value.example().sourceProvenance().scenarioId()
                                        + "=" + value.reasonCodes()).toList());
        if (command.equals("curate-fixture")) {
            System.out.println("D4_GATE_PASS accepted=" + accepted
                    + " evidence=" + root.resolve("reports", "d4-gate-evidence.json"));
            return;
        }
        if (!command.equals("freeze-fixture")) throw new IllegalArgumentException(
                "unknown command: " + command);

        DatasetPolicy datasetPolicy = DatasetPolicy.defaultOffline();
        String protectedSource = CanonicalJson.sha256("block2-protected-fixture-sources-v1");
        var connected = ContaminationChecker.manifest("connected-fixture-v1",
                DatasetSplit.CONNECTED, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                List.of("Connected validation: can the NPC see the newly spawned beacon?"),
                protectedSource);
        var canary = ContaminationChecker.manifest("canary-fixture-v1",
                DatasetSplit.CANARY, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                List.of("Canary validation: report the rotating codeword without guessing."),
                protectedSource);
        String approvalEvidence = CanonicalJson.sha256(results.stream()
                .map(CurationResult::verdicts).toList());
        ReviewApproval approval = new ReviewApproval("project-fixture-authority",
                "bounded D4/D5 project-owned fixture corpus only", true,
                approvalEvidence, Instant.parse("2026-09-03T00:00:00Z"));
        DatasetAssembler.DatasetBuild build = new DatasetAssembler(datasetPolicy).assemble(
                results.stream().map(CurationResult::example).toList(),
                List.of(connected, canary), List.of(approval));
        var frozen = new DatasetFreezer(root, registries).freeze(build,
                LicenseManifests.projectFixtureOnly(), curationPolicy,
                catalog.sourceRegistryHash(results), "NO_GIT_REPOSITORY");
        System.out.println("D5_FREEZE_PASS dataset=" + frozen.manifest().datasetId().value()
                + " hash=" + frozen.manifest().canonicalSha256() + " path="
                + frozen.directory() + " created=" + frozen.created()
                + " idempotent=" + frozen.idempotent());
    }

    private static void writeD4Evidence(ArtifactRoot root, List<CurationResult> results,
            CurationPolicy policy) {
        Map<String, Object> evidence = Map.of(
                "schemaVersion", 1,
                "gate", "D4_DETERMINISTIC_CURATION",
                "policySha256", policy.policyHash(),
                "fixtureCount", results.size(),
                "acceptedCount", results.stream().filter(CurationResult::accepted).count(),
                "rejectedCount", results.stream().filter(value -> !value.accepted()).count(),
                "exampleIds", results.stream().map(value ->
                        value.example().exampleId().value()).sorted().toList(),
                "verdictSha256", CanonicalJson.sha256(results.stream()
                        .map(CurationResult::verdicts).toList()));
        JsonFiles.writeAtomic(root.resolve("reports", "d4-gate-evidence.json"), evidence);
    }

    private static void report(ArtifactRoot root) throws Exception {
        Path datasets = root.resolve("datasets");
        List<Path> manifests;
        try (var paths = Files.walk(datasets)) {
            manifests = paths.filter(path -> path.getFileName().toString().equals("manifest.json"))
                    .sorted().toList();
        }
        if (manifests.isEmpty()) {
            System.out.println("No frozen datasets."); return;
        }
        for (Path manifest : manifests) {
            System.out.println(manifest + "\n" + Files.readString(manifest,
                    StandardCharsets.UTF_8));
        }
    }
}
