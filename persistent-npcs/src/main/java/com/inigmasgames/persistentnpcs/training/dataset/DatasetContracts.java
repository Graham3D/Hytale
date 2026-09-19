package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ContaminationMetadata;
import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherContracts;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable D5 contracts. CONNECTED/CANARY are references, never trainer rows. */
public final class DatasetContracts {
    private DatasetContracts() { }

    public enum DatasetSplit { TRAIN, DEV, TEST, CHALLENGE, CONNECTED, CANARY }
    public enum DatasetState { DRAFT, CURATING, REVIEW_REQUIRED, APPROVED, FROZEN, RETIRED }
    public enum ContaminationKind {
        EXACT, NORMALIZED, ENTITY_NORMALIZED, SEMANTIC_FAMILY, GENERATION_ANCESTRY, FUZZY
    }

    public record CanonicalDatasetRow(int schemaVersion, ArtifactIds.DatasetRowId rowId,
            ArtifactIds.ExampleId exampleId, ArtifactIds.SemanticFamilyId semanticFamilyId,
            DatasetSplit split, DistillationExample example,
            List<DistillationCorpusCandidate.SourceProvenance> provenance,
            String inputSha256, String semanticTargetSha256, String responseSha256,
            String rowSha256, String normalizedInputFingerprint,
            String entityNormalizedInputFingerprint, String generationAncestorId,
            ContaminationMetadata contamination) {
        public static final int SCHEMA_VERSION = 1;
        public CanonicalDatasetRow {
            if (schemaVersion != SCHEMA_VERSION || rowId == null || exampleId == null
                    || semanticFamilyId == null || split == null || example == null
                    || contamination == null) throw new IllegalArgumentException(
                            "complete canonical dataset row required");
            provenance = List.copyOf(provenance == null ? List.of() : provenance);
            requireHash(inputSha256); requireHash(semanticTargetSha256);
            requireHash(responseSha256); requireHash(rowSha256);
            normalizedInputFingerprint = clean(normalizedInputFingerprint);
            entityNormalizedInputFingerprint = clean(entityNormalizedInputFingerprint);
            generationAncestorId = clean(generationAncestorId);
        }
    }

    public record DedupDecision(String retainedExampleId, List<String> mergedExampleIds,
            String algorithm, double similarity, String reasonCode) {
        public DedupDecision {
            retainedExampleId = clean(retainedExampleId);
            mergedExampleIds = List.copyOf(mergedExampleIds == null ? List.of()
                    : mergedExampleIds);
            algorithm = clean(algorithm); reasonCode = clean(reasonCode);
        }
    }

    public record ContaminationIssue(String rowId, String protectedSetId,
            ContaminationKind kind, double similarity, String reasonCode,
            String evidenceFingerprint) {
        public ContaminationIssue {
            rowId = clean(rowId); protectedSetId = clean(protectedSetId);
            if (kind == null) throw new IllegalArgumentException("contamination kind required");
            reasonCode = clean(reasonCode); evidenceFingerprint = clean(evidenceFingerprint);
        }
    }

    public record ContaminationAudit(int schemaVersion, String algorithmVersion,
            double fuzzyThreshold, List<ContaminationIssue> issues,
            int rowsChecked, String canonicalSha256) {
        public ContaminationAudit {
            if (schemaVersion != 1 || algorithmVersion == null || algorithmVersion.isBlank()) {
                throw new IllegalArgumentException("versioned contamination audit required");
            }
            issues = List.copyOf(issues == null ? List.of() : issues);
            requireHash(canonicalSha256);
        }
        public boolean clean() { return issues.isEmpty(); }
    }

    public record ProtectedSetManifest(int schemaVersion, String protectedSetId,
            DatasetSplit split, Set<String> exactInputHashes,
            Set<String> normalizedInputFingerprints,
            Set<String> entityNormalizedInputFingerprints,
            Set<String> semanticFamilyIds, Set<String> generationAncestorIds,
            List<String> prompts, String sourceArtifactHash, String canonicalSha256) {
        public ProtectedSetManifest {
            if (schemaVersion != 1 || protectedSetId == null || protectedSetId.isBlank()
                    || split == null || split == DatasetSplit.TRAIN) {
                throw new IllegalArgumentException("protected non-training set required");
            }
            exactInputHashes = sorted(exactInputHashes);
            normalizedInputFingerprints = sorted(normalizedInputFingerprints);
            entityNormalizedInputFingerprints = sorted(entityNormalizedInputFingerprints);
            semanticFamilyIds = sorted(semanticFamilyIds);
            generationAncestorIds = sorted(generationAncestorIds);
            prompts = List.copyOf(prompts == null ? List.of() : prompts);
            sourceArtifactHash = clean(sourceArtifactHash); requireHash(canonicalSha256);
        }
    }

    public record CoverageReport(int schemaVersion,
            Map<String, Map<String, Long>> rowCounts,
            Map<String, Map<String, Long>> tokenWeightedCounts,
            List<String> warnings, long totalRows, long approximateTokens,
            String canonicalSha256) {
        public CoverageReport {
            if (schemaVersion != 1 || totalRows < 0 || approximateTokens < 0) {
                throw new IllegalArgumentException("valid coverage report required");
            }
            rowCounts = immutableNested(rowCounts);
            tokenWeightedCounts = immutableNested(tokenWeightedCounts);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            requireHash(canonicalSha256);
        }
    }

    public record LicenseManifest(int schemaVersion, String licenseManifestId,
            Set<String> allowedLicenseIds, Set<String> prohibitedUses,
            String decisionBasis, boolean approvedForTraining,
            String canonicalSha256) {
        public LicenseManifest {
            if (schemaVersion != 1 || licenseManifestId == null || licenseManifestId.isBlank()) {
                throw new IllegalArgumentException("license manifest required");
            }
            allowedLicenseIds = sorted(allowedLicenseIds);
            prohibitedUses = sorted(prohibitedUses);
            decisionBasis = clean(decisionBasis); requireHash(canonicalSha256);
        }
    }

    public record ReviewApproval(String reviewer, String scope, boolean approved,
            String evidenceHash, Instant reviewedAt) {
        public ReviewApproval {
            reviewer = clean(reviewer); scope = clean(scope);
            requireHash(evidenceHash);
            if (reviewedAt == null) throw new IllegalArgumentException("review time required");
        }
    }

    public record DatasetManifest(int schemaVersion, ArtifactIds.DatasetId datasetId,
            ArtifactIds.DatasetVersionId versionId, DatasetState state,
            String canonicalSha256, String sourceRegistrySha256,
            String curationPolicySha256, String dedupPolicySha256,
            String splitPolicySha256, String contaminationPolicySha256,
            String coverageSha256, String licenseManifestSha256,
            String baseModelContentId, String promptTemplateContentId,
            Map<DatasetSplit, List<String>> rowIdsBySplit,
            Map<DatasetSplit, List<String>> protectedSetReferences,
            List<DedupDecision> dedupDecisions,
            List<TeacherContracts.TeacherIdentity> teacherSnapshots,
            List<ReviewApproval> approvals, String gitState,
            Instant createdAt, Instant frozenAt) {
        public static final int SCHEMA_VERSION = 1;
        public DatasetManifest {
            if (schemaVersion != SCHEMA_VERSION || datasetId == null || versionId == null
                    || state == null || createdAt == null) throw new IllegalArgumentException(
                            "complete dataset manifest required");
            requireHash(canonicalSha256); requireHash(sourceRegistrySha256);
            requireHash(curationPolicySha256); requireHash(dedupPolicySha256);
            requireHash(splitPolicySha256); requireHash(contaminationPolicySha256);
            requireHash(coverageSha256); requireHash(licenseManifestSha256);
            baseModelContentId = clean(baseModelContentId);
            promptTemplateContentId = clean(promptTemplateContentId);
            rowIdsBySplit = immutableSplitMap(rowIdsBySplit);
            protectedSetReferences = immutableSplitMap(protectedSetReferences);
            dedupDecisions = List.copyOf(dedupDecisions == null ? List.of()
                    : dedupDecisions);
            teacherSnapshots = List.copyOf(teacherSnapshots == null ? List.of()
                    : teacherSnapshots);
            approvals = List.copyOf(approvals == null ? List.of() : approvals);
            gitState = clean(gitState);
            if (state == DatasetState.FROZEN && frozenAt == null) {
                throw new IllegalArgumentException("frozen manifest requires frozenAt");
            }
        }
    }

    private static Map<String, Map<String, Long>> immutableNested(
            Map<String, Map<String, Long>> input) {
        if (input == null) return Map.of();
        java.util.TreeMap<String, Map<String, Long>> copy = new java.util.TreeMap<>();
        input.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
        return Map.copyOf(copy);
    }
    private static Map<DatasetSplit, List<String>> immutableSplitMap(
            Map<DatasetSplit, List<String>> input) {
        if (input == null) return Map.of();
        java.util.EnumMap<DatasetSplit, List<String>> copy =
                new java.util.EnumMap<>(DatasetSplit.class);
        input.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static Set<String> sorted(Set<String> values) {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                values == null ? Set.of() : values));
    }
    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 required");
        }
    }
}
