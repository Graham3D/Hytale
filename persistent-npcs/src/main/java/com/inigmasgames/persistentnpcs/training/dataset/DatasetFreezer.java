package com.inigmasgames.persistentnpcs.training.dataset;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts;
import com.inigmasgames.persistentnpcs.training.curation.CurationPolicy;
import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetAssembler.DatasetBuild;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.CanonicalDatasetRow;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetManifest;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetSplit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetState;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.LicenseManifest;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.registry.TrainingArtifactRegistries;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One-way D5 freeze. Existing identical content is idempotent; changed content gets a new ID. */
public final class DatasetFreezer {
    private final ArtifactRoot root;
    private final TrainingArtifactRegistries registries;

    public DatasetFreezer(ArtifactRoot root, TrainingArtifactRegistries registries) {
        this.root = java.util.Objects.requireNonNull(root);
        this.registries = java.util.Objects.requireNonNull(registries);
    }

    public FreezeResult freeze(DatasetBuild build, LicenseManifest license,
            CurationPolicy curationPolicy, String sourceRegistrySha256, String gitState) {
        build.requireApproved();
        if (curationPolicy == null) throw new IllegalStateException("DATASET_POLICY_BLOCK");
        String curationPolicySha256 = curationPolicy.policyHash();
        if (license == null || !license.approvedForTraining()) {
            throw new IllegalStateException("LICENSE_POLICY_BLOCK");
        }
        requireHash(curationPolicySha256); requireHash(sourceRegistrySha256);
        List<CanonicalDatasetRow> frozenRows = build.rows().stream()
                .map(DatasetFreezer::freezeRow).toList();
        DatasetSeed seed = new DatasetSeed(frozenRows.stream()
                .map(CanonicalDatasetRow::rowSha256).sorted().toList(),
                curationPolicySha256, build.policy().dedupPolicyHash(),
                build.policy().splitPolicyHash(), build.policy().contaminationPolicyHash(),
                build.coverage().canonicalSha256(), license.canonicalSha256(),
                build.protectedSets().stream().map(value -> value.canonicalSha256()).sorted().toList(),
                sourceRegistrySha256,
                frozenRows.getFirst().example().productionInput().baseModel().contentId(),
                frozenRows.getFirst().example().productionInput().promptTemplate().contentId());
        String datasetHash = CanonicalJson.sha256(seed);
        ArtifactIds.DatasetId datasetId = ArtifactIds.dataset(seed);
        ArtifactIds.DatasetVersionId versionId = ArtifactIds.datasetVersion(Map.of(
                "datasetId", datasetId.value(), "schemaVersion", 1));
        Path directory = root.resolve("datasets", datasetId.value());
        Path manifestPath = directory.resolve("manifest.json");
        if (Files.exists(directory)) {
            if (!Files.isRegularFile(manifestPath)) throw new IllegalStateException(
                    "existing dataset directory is incomplete: " + directory);
            DatasetManifest existing = JsonFiles.read(manifestPath, DatasetManifest.class);
            if (!existing.canonicalSha256().equals(datasetHash)) {
                throw new IllegalStateException("immutable dataset identity collision: "
                        + datasetId.value());
            }
            return new FreezeResult(existing, directory, false, true);
        }

        Instant now = Instant.now();
        EnumMap<DatasetSplit, List<String>> rowIds = new EnumMap<>(DatasetSplit.class);
        for (DatasetSplit split : DatasetSplit.values()) rowIds.put(split, new ArrayList<>());
        frozenRows.forEach(row -> rowIds.get(row.split()).add(row.rowId().value()));
        EnumMap<DatasetSplit, List<String>> protectedRefs = new EnumMap<>(DatasetSplit.class);
        for (DatasetSplit split : DatasetSplit.values()) protectedRefs.put(split, new ArrayList<>());
        build.protectedSets().forEach(value -> protectedRefs.get(value.split())
                .add(value.protectedSetId()));
        var teachers = frozenRows.stream().map(row -> row.example().teacherIdentity())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(value -> value.contentId(),
                        value -> value, (left, right) -> left, LinkedHashMap::new))
                .values().stream().toList();
        DatasetManifest manifest = new DatasetManifest(DatasetManifest.SCHEMA_VERSION,
                datasetId, versionId, DatasetState.FROZEN, datasetHash, sourceRegistrySha256,
                curationPolicySha256, build.policy().dedupPolicyHash(),
                build.policy().splitPolicyHash(), build.policy().contaminationPolicyHash(),
                build.coverage().canonicalSha256(), license.canonicalSha256(),
                seed.baseModelContentId(), seed.promptTemplateContentId(), rowIds,
                protectedRefs, build.dedupDecisions(), teachers, build.approvals(),
                gitState == null ? "" : gitState.strip(), now, now);
        try {
            createLayout(directory);
            writeJsonlNew(directory.resolve("canonical/examples.jsonl"), frozenRows);
            for (DatasetSplit split : List.of(DatasetSplit.TRAIN, DatasetSplit.DEV,
                    DatasetSplit.TEST, DatasetSplit.CHALLENGE)) {
                List<CanonicalDatasetRow> splitRows = frozenRows.stream()
                        .filter(row -> row.split() == split).toList();
                writeTrainerView(directory.resolve(split.name().toLowerCase()
                        + "/sft.jsonl"), splitRows);
            }
            JsonFiles.writeAtomic(directory.resolve("coverage.json"), build.coverage());
            JsonFiles.writeAtomic(directory.resolve("contamination-audit.json"),
                    build.contaminationAudit());
            JsonFiles.writeAtomic(directory.resolve("licenses/manifest.json"), license);
            JsonFiles.writeAtomic(directory.resolve("policies/curation-policy.json"),
                    curationPolicy);
            JsonFiles.writeAtomic(directory.resolve("policies/dataset-policy.json"),
                    build.policy());
            for (var protectedSet : build.protectedSets()) {
                JsonFiles.writeAtomic(directory.resolve("protected-sets/"
                        + protectedSet.protectedSetId() + ".json"), protectedSet);
            }
            // Manifest is written last; its presence is the local commit marker.
            JsonFiles.writeAtomic(manifestPath, manifest);
            registries.datasets().append(datasetId.value(), manifest);
            return new FreezeResult(manifest, directory, true, false);
        } catch (IOException exception) {
            throw new UncheckedIOException("dataset freeze failed before commit marker", exception);
        }
    }

    private static CanonicalDatasetRow freezeRow(CanonicalDatasetRow row) {
        DistillationExample source = row.example();
        DistillationExample frozen = new DistillationExample(source.schemaVersion(),
                source.exampleId(), source.taskType(), source.targetSource(),
                source.sourceProvenance(), source.productionInput(), source.epistemicTarget(),
                source.chosenResponse(), source.publicCritique(), source.requiredPropositionIds(),
                source.forbiddenPropositionIds(), source.oracleVerdicts(), source.teacherIdentity(),
                CurationContracts.ReviewState.FROZEN, source.semanticMetadata(),
                row.semanticFamilyId().value(), row.split().name(), row.contamination(),
                source.artifactHashes(), source.negativeEvidence(), source.createdAt());
        FrozenRowPayload payload = new FrozenRowPayload(row.schemaVersion(), row.exampleId(),
                row.semanticFamilyId(), row.split(), frozen, row.provenance(), row.inputSha256(),
                row.semanticTargetSha256(), row.responseSha256(),
                row.normalizedInputFingerprint(), row.entityNormalizedInputFingerprint(),
                row.generationAncestorId(), row.contamination());
        return new CanonicalDatasetRow(row.schemaVersion(), ArtifactIds.row(payload),
                row.exampleId(), row.semanticFamilyId(), row.split(), frozen, row.provenance(),
                row.inputSha256(), row.semanticTargetSha256(), row.responseSha256(),
                CanonicalJson.sha256(payload), row.normalizedInputFingerprint(),
                row.entityNormalizedInputFingerprint(), row.generationAncestorId(),
                row.contamination());
    }

    /** Verifies the complete frozen row payload without trusting its stored ID or hash. */
    public static boolean hasValidCanonicalRowHash(CanonicalDatasetRow row) {
        if (row == null || row.example().reviewState() != CurationContracts.ReviewState.FROZEN) {
            return false;
        }
        FrozenRowPayload payload = new FrozenRowPayload(row.schemaVersion(), row.exampleId(),
                row.semanticFamilyId(), row.split(), row.example(), row.provenance(),
                row.inputSha256(), row.semanticTargetSha256(), row.responseSha256(),
                row.normalizedInputFingerprint(), row.entityNormalizedInputFingerprint(),
                row.generationAncestorId(), row.contamination());
        return row.rowSha256().equals(CanonicalJson.sha256(payload))
                && row.rowId().equals(ArtifactIds.row(payload));
    }

    private static void createLayout(Path directory) throws IOException {
        Files.createDirectories(directory.resolve("canonical"));
        Files.createDirectories(directory.resolve("train"));
        Files.createDirectories(directory.resolve("dev"));
        Files.createDirectories(directory.resolve("test"));
        Files.createDirectories(directory.resolve("challenge"));
        Files.createDirectories(directory.resolve("licenses"));
        Files.createDirectories(directory.resolve("policies"));
        Files.createDirectories(directory.resolve("protected-sets"));
    }

    private static void writeJsonlNew(Path path, List<CanonicalDatasetRow> rows)
            throws IOException {
        String body = rows.stream().sorted(java.util.Comparator.comparing(row ->
                        row.rowId().value())).map(CanonicalJson::serialize)
                .collect(java.util.stream.Collectors.joining("\n"));
        if (!body.isBlank()) body += "\n";
        Files.writeString(path, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    /** Trainer view intentionally exposes only the exact messages and chosen response. */
    private static void writeTrainerView(Path path, List<CanonicalDatasetRow> rows)
            throws IOException {
        String body = rows.stream().sorted(java.util.Comparator.comparing(row ->
                        row.rowId().value())).map(row -> {
                    JsonObject object = new JsonObject();
                    object.add("messages", JsonFiles.GSON.toJsonTree(
                            row.example().productionInput().messages()));
                    object.addProperty("chosenResponse", row.example().chosenResponse());
                    return CanonicalJson.serialize(object);
                }).collect(java.util.stream.Collectors.joining("\n"));
        if (!body.isBlank()) body += "\n";
        Files.writeString(path, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 required");
        }
    }
    private record DatasetSeed(List<String> rowHashes, String curationPolicySha256,
            String dedupPolicySha256, String splitPolicySha256,
            String contaminationPolicySha256, String coverageSha256,
            String licenseManifestSha256, List<String> protectedSetHashes,
            String sourceRegistrySha256, String baseModelContentId,
            String promptTemplateContentId) { }

    /** Canonical row commitment. rowId/rowSha256 are deliberately excluded as self references. */
    private record FrozenRowPayload(int schemaVersion, ArtifactIds.ExampleId exampleId,
            ArtifactIds.SemanticFamilyId semanticFamilyId, DatasetSplit split,
            DistillationExample example,
            List<com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate
                    .SourceProvenance> provenance,
            String inputSha256, String semanticTargetSha256, String responseSha256,
            String normalizedInputFingerprint, String entityNormalizedInputFingerprint,
            String generationAncestorId,
            CurationContracts.ContaminationMetadata contamination) { }

    public record FreezeResult(DatasetManifest manifest, Path directory,
            boolean created, boolean idempotent) { }
}
