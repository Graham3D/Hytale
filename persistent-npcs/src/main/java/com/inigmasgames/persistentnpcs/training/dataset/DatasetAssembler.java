package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.curation.CurationContracts;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ContaminationMetadata;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ReviewState;
import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.CanonicalDatasetRow;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ContaminationAudit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.CoverageReport;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetSplit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetState;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DedupDecision;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ProtectedSetManifest;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ReviewApproval;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** D5 admission, deduplication, semantic grouping, split assignment, and coverage. */
public final class DatasetAssembler {
    private final DatasetPolicy policy;
    private final SemanticFamilyAssigner families = new SemanticFamilyAssigner();
    private final ContaminationChecker contaminationChecker = new ContaminationChecker();

    public DatasetAssembler(DatasetPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    public DatasetBuild assemble(List<DistillationExample> source,
            List<ProtectedSetManifest> protectedSets, List<ReviewApproval> approvals) {
        List<String> blockers = new ArrayList<>();
        List<DistillationExample> admissible = new ArrayList<>();
        for (DistillationExample example : source == null ? List.<DistillationExample>of() : source) {
            if (example.reviewState() == ReviewState.REJECTED) {
                blockers.add("REJECTED_ROW:" + example.exampleId().value());
            } else if (example.reviewState() != ReviewState.ORACLE_ACCEPTED
                    && example.reviewState() != ReviewState.HUMAN_ACCEPTED) {
                blockers.add("UNREVIEWED_ROW:" + example.exampleId().value());
            } else {
                admissible.add(example);
            }
        }
        if (admissible.isEmpty()) blockers.add("DATASET_POLICY_BLOCK:empty");

        List<DedupDecision> dedup = new ArrayList<>();
        Map<String, PendingRow> exact = new LinkedHashMap<>();
        for (DistillationExample example : admissible.stream()
                .sorted(Comparator.comparing(e -> e.exampleId().value())).toList()) {
            String input = DatasetNormalization.exactInput(example);
            String exactKey = CanonicalJson.sha256(Map.of(
                    "input", DatasetNormalization.canonicalText(input),
                    "target", example.epistemicTarget().canonicalSha256(),
                    "response", DatasetNormalization.canonicalText(example.chosenResponse())));
            PendingRow retained = exact.get(exactKey);
            if (retained == null) {
                exact.put(exactKey, PendingRow.of(example, input, families.assign(example)));
            } else {
                retained.merge(example);
                dedup.add(new DedupDecision(retained.example.exampleId().value(),
                        List.of(example.exampleId().value()), "canonical-exact-v1", 1.0,
                        "EXACT_DUPLICATE_MERGED"));
            }
        }

        List<PendingRow> pending = new ArrayList<>(exact.values());
        boolean[] removed = new boolean[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            if (removed[i]) continue;
            for (int j = i + 1; j < pending.size(); j++) {
                if (removed[j]) continue;
                PendingRow left = pending.get(i), right = pending.get(j);
                double score = DatasetNormalization.fuzzySimilarity(left.input, right.input);
                boolean compatible = left.family.equals(right.family)
                        && left.example.epistemicTarget().canonicalSha256().equals(
                                right.example.epistemicTarget().canonicalSha256())
                        && DatasetNormalization.normalizedFingerprint(left.example.chosenResponse())
                                .equals(DatasetNormalization.normalizedFingerprint(
                                        right.example.chosenResponse()));
                if (score >= policy.fuzzyDuplicateThreshold() && compatible) {
                    left.merge(right);
                    removed[j] = true;
                    dedup.add(new DedupDecision(left.example.exampleId().value(),
                            List.of(right.example.exampleId().value()),
                            policy.fuzzyAlgorithm(), score, "FUZZY_DUPLICATE_MERGED"));
                } else if (score >= policy.fuzzyReviewThreshold()) {
                    blockers.add("AMBIGUOUS_FUZZY_COLLISION:"
                            + left.example.exampleId().value() + ":"
                            + right.example.exampleId().value());
                }
            }
        }
        List<PendingRow> retained = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) if (!removed[i]) retained.add(pending.get(i));

        Map<ArtifactIds.SemanticFamilyId, DatasetSplit> splitByFamily = assignSplits(retained,
                blockers);
        List<CanonicalDatasetRow> rows = new ArrayList<>();
        for (PendingRow pendingRow : retained) {
            DatasetSplit split = splitByFamily.get(pendingRow.family);
            if (split == DatasetSplit.CONNECTED || split == DatasetSplit.CANARY) {
                // Protected runtime cases remain manifest references only.
                continue;
            }
            rows.add(materialize(pendingRow, split, ContaminationMetadata.pending()));
        }
        List<ProtectedSetManifest> protectedCopy = List.copyOf(protectedSets == null
                ? List.of() : protectedSets);
        ContaminationAudit audit = contaminationChecker.audit(rows, protectedCopy, policy);
        audit.issues().forEach(issue -> blockers.add(issue.reasonCode() + ":" + issue.rowId()));
        List<CanonicalDatasetRow> audited = rows.stream().map(row -> {
            List<String> rowChecks = audit.issues().stream()
                    .filter(issue -> issue.rowId().equals(row.rowId().value()))
                    .map(issue -> issue.kind().name()).toList();
            List<String> refs = audit.issues().stream()
                    .filter(issue -> issue.rowId().equals(row.rowId().value()))
                    .map(issue -> issue.protectedSetId()).distinct().toList();
            return materialize(PendingRow.from(row), row.split(),
                    new ContaminationMetadata(true, !rowChecks.isEmpty(), rowChecks, refs));
        }).toList();

        CoverageReport coverage = coverage(audited);
        boolean approval = approvals != null && approvals.stream().anyMatch(ReviewApproval::approved);
        if (!approval) blockers.add("DATASET_POLICY_BLOCK:human-approval-required");
        DatasetState state = blockers.isEmpty() ? DatasetState.APPROVED
                : DatasetState.REVIEW_REQUIRED;
        return new DatasetBuild(List.copyOf(audited), List.copyOf(dedup), audit, coverage,
                protectedCopy, List.copyOf(approvals == null ? List.of() : approvals),
                state, List.copyOf(blockers), policy);
    }

    private Map<ArtifactIds.SemanticFamilyId, DatasetSplit> assignSplits(
            List<PendingRow> rows, List<String> blockers) {
        Map<ArtifactIds.SemanticFamilyId, DatasetSplit> assigned = new LinkedHashMap<>();
        Map<ArtifactIds.SemanticFamilyId, List<PendingRow>> grouped = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(row -> row.family,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (var entry : grouped.entrySet()) {
            DatasetSplit requested = null;
            for (PendingRow row : entry.getValue()) {
                DatasetSplit rowRequest = parseSplit(row.example.semanticMetadata()
                        .requestedProtectedSplit());
                if (policy.profileHoldoutIds().contains(
                        row.example.semanticMetadata().profileId())) {
                    rowRequest = DatasetSplit.TEST;
                }
                if (rowRequest == null) continue;
                if (requested != null && requested != rowRequest) {
                    blockers.add("SEMANTIC_FAMILY_LEAKAGE:" + entry.getKey().value());
                } else requested = rowRequest;
            }
            if (requested != null) assigned.put(entry.getKey(), requested);
        }

        List<ArtifactIds.SemanticFamilyId> unassigned = grouped.keySet().stream()
                .filter(family -> !assigned.containsKey(family))
                .sorted(Comparator.comparing(family -> CanonicalJson.sha256(
                        policy.splitSeed() + "\u0000" + family.value()))).toList();
        long fixedOrdinary = assigned.values().stream().filter(split ->
                split == DatasetSplit.TRAIN || split == DatasetSplit.DEV
                        || split == DatasetSplit.TEST).count();
        int ordinaryFamilies = (int) fixedOrdinary + unassigned.size();
        int desiredDev = ordinaryFamilies < 3 ? 0 : Math.max(1,
                (int) Math.round(ordinaryFamilies * policy.devPercent() / 100.0));
        int desiredTest = ordinaryFamilies < 3 ? 0 : Math.max(1,
                (int) Math.round(ordinaryFamilies * policy.testPercent() / 100.0));
        int existingDev = (int) assigned.values().stream()
                .filter(split -> split == DatasetSplit.DEV).count();
        int existingTest = (int) assigned.values().stream()
                .filter(split -> split == DatasetSplit.TEST).count();
        int needDev = Math.max(0, desiredDev - existingDev);
        int needTest = Math.max(0, desiredTest - existingTest);
        for (int index = 0; index < unassigned.size(); index++) {
            DatasetSplit split = index < needDev ? DatasetSplit.DEV
                    : index < needDev + needTest ? DatasetSplit.TEST
                    : DatasetSplit.TRAIN;
            assigned.put(unassigned.get(index), split);
        }

        for (PendingRow row : rows) {
            DatasetSplit requested = parseSplit(row.example.semanticMetadata()
                    .requestedProtectedSplit());
            if (policy.profileHoldoutIds().contains(row.example.semanticMetadata().profileId())) {
                requested = DatasetSplit.TEST;
            }
            if (requested != null && assigned.get(row.family) != requested) {
                blockers.add("SEMANTIC_FAMILY_LEAKAGE:" + row.family.value());
            }
        }
        for (String holdout : policy.profileHoldoutIds()) {
            boolean present = rows.stream().anyMatch(row ->
                    row.example.semanticMetadata().profileId().equals(holdout));
            if (!present) blockers.add("PROFILE_HOLDOUT_VIOLATION:" + holdout);
        }
        return assigned;
    }

    private static DatasetSplit parseSplit(String value) {
        if (value == null || value.isBlank()) return null;
        try { return DatasetSplit.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static CanonicalDatasetRow materialize(PendingRow pending, DatasetSplit split,
            ContaminationMetadata contamination) {
        String inputSha = CanonicalJson.sha256(DatasetNormalization.canonicalText(pending.input));
        String semanticSha = pending.example.epistemicTarget().canonicalSha256();
        String responseSha = CanonicalJson.sha256(DatasetNormalization.canonicalText(
                pending.example.chosenResponse()));
        RowSeed seed = new RowSeed(inputSha, semanticSha, responseSha,
                pending.family.value(), split, pending.example.targetSource().name());
        String rowSha = CanonicalJson.sha256(seed);
        ArtifactIds.DatasetRowId rowId = ArtifactIds.row(seed);
        return new CanonicalDatasetRow(1, rowId, pending.example.exampleId(), pending.family,
                split, pending.example, List.copyOf(pending.provenance), inputSha, semanticSha,
                responseSha, rowSha, DatasetNormalization.normalizedFingerprint(pending.input),
                DatasetNormalization.entityNormalizedFingerprint(pending.input,
                        pending.example.semanticMetadata().entityValues()),
                pending.example.semanticMetadata().generationAncestorId(), contamination);
    }

    private static CoverageReport coverage(List<CanonicalDatasetRow> rows) {
        Map<String, Map<String, Long>> counts = new TreeMap<>();
        Map<String, Map<String, Long>> tokens = new TreeMap<>();
        long totalTokens = 0;
        for (CanonicalDatasetRow row : rows) {
            int weight = DatasetNormalization.approximateTokens(
                    DatasetNormalization.exactInput(row.example()))
                    + DatasetNormalization.approximateTokens(row.example().chosenResponse());
            totalTokens += weight;
            String temporal = row.example().epistemicTarget().requiredPropositions().stream()
                    .findFirst().map(p -> p.temporalCategory().name()).orElse("NONE");
            String evidenceClass = row.example().epistemicTarget().requiredPropositions().stream()
                    .findFirst().map(p -> p.sourceKind().name()).orElse("NONE");
            String memoryType = evidenceClass.contains("MEMORY")
                    || evidenceClass.equals("CONVERSATION_WORKSPACE") ? evidenceClass : "NONE";
            String relationship = row.example().taskType()
                    == CurationContracts.TaskType.RELATIONSHIP ? "EXPLICIT" : "NONE";
            String uncertainty = switch (row.example().epistemicTarget().answerability()) {
                case UNKNOWN, PARTIALLY_KNOWN, CONFLICTED, INFERRED,
                        NEEDS_CURRENT_PERCEPTION -> "UNCERTAINTY";
                case WITHHELD -> "REFUSAL";
                default -> "DIRECT";
            };
            String teacher = row.example().teacherIdentity() == null ? "NONE"
                    : row.example().teacherIdentity().sourceId();
            addBoth(counts, tokens, "taskType", row.example().taskType().name(), weight);
            addBoth(counts, tokens, "answerability",
                    row.example().epistemicTarget().answerability().name(), weight);
            addBoth(counts, tokens, "targetSource", row.example().targetSource().name(), weight);
            addBoth(counts, tokens, "evidenceSourceClass", evidenceClass, weight);
            addBoth(counts, tokens, "temporalCategory", temporal, weight);
            addBoth(counts, tokens, "actionOutcome",
                    row.example().epistemicTarget().actionTruth().name(), weight);
            addBoth(counts, tokens, "memoryType", memoryType, weight);
            addBoth(counts, tokens, "relationshipStance", relationship, weight);
            addBoth(counts, tokens, "uncertaintyRefusal", uncertainty, weight);
            addBoth(counts, tokens, "archetype",
                    empty(row.example().semanticMetadata().archetype()), weight);
            addBoth(counts, tokens, "paraphraseTemplate",
                    empty(row.example().semanticMetadata().paraphraseTemplateId()), weight);
            addBoth(counts, tokens, "teacherSource", teacher, weight);
            addBoth(counts, tokens, "failureSignature",
                    empty(row.example().semanticMetadata().failureSignature()), weight);
            addBoth(counts, tokens, "split", row.split().name(), weight);
        }
        List<String> warnings = new ArrayList<>();
        for (String required : List.of("taskType", "answerability", "targetSource",
                "evidenceSourceClass", "temporalCategory", "actionOutcome", "memoryType",
                "relationshipStance", "uncertaintyRefusal", "archetype",
                "paraphraseTemplate", "teacherSource", "failureSignature", "split")) {
            if (!counts.containsKey(required) || counts.get(required).isEmpty()) {
                warnings.add("COVERAGE_GAP:" + required);
            }
        }
        CoverageSeed seed = new CoverageSeed(counts, tokens, warnings, rows.size(), totalTokens);
        return new CoverageReport(1, counts, tokens, warnings, rows.size(), totalTokens,
                CanonicalJson.sha256(seed));
    }

    private static void add(Map<String, Map<String, Long>> map, String dimension,
            String value, long amount) {
        map.computeIfAbsent(dimension, ignored -> new TreeMap<>())
                .merge(value, amount, Long::sum);
    }
    private static void addBoth(Map<String, Map<String, Long>> counts,
            Map<String, Map<String, Long>> tokens, String dimension, String value,
            long tokenWeight) {
        add(counts, dimension, value, 1); add(tokens, dimension, value, tokenWeight);
    }
    private static String empty(String value) { return value == null || value.isBlank()
            ? "UNSPECIFIED" : value; }

    private static final class PendingRow {
        private final DistillationExample example;
        private final String input;
        private final ArtifactIds.SemanticFamilyId family;
        private final List<com.inigmasgames.persistentnpcs.training.corpus
                .DistillationCorpusCandidate.SourceProvenance> provenance = new ArrayList<>();
        private PendingRow(DistillationExample example, String input,
                ArtifactIds.SemanticFamilyId family) {
            this.example = example; this.input = input; this.family = family;
            provenance.add(example.sourceProvenance());
        }
        static PendingRow of(DistillationExample example, String input,
                ArtifactIds.SemanticFamilyId family) {
            return new PendingRow(example, input, family);
        }
        static PendingRow from(CanonicalDatasetRow row) {
            PendingRow value = new PendingRow(row.example(),
                    DatasetNormalization.exactInput(row.example()), row.semanticFamilyId());
            value.provenance.clear(); value.provenance.addAll(row.provenance()); return value;
        }
        void merge(DistillationExample duplicate) { provenance.add(duplicate.sourceProvenance()); }
        void merge(PendingRow duplicate) { provenance.addAll(duplicate.provenance); }
    }

    private record RowSeed(String inputSha256, String semanticTargetSha256,
            String responseSha256, String familyId, DatasetSplit split,
            String targetSource) { }
    private record CoverageSeed(Map<String, Map<String, Long>> counts,
            Map<String, Map<String, Long>> tokens, List<String> warnings,
            int rows, long approximateTokens) { }

    public record DatasetBuild(List<CanonicalDatasetRow> rows,
            List<DedupDecision> dedupDecisions, ContaminationAudit contaminationAudit,
            CoverageReport coverage, List<ProtectedSetManifest> protectedSets,
            List<ReviewApproval> approvals, DatasetState state,
            List<String> blockers, DatasetPolicy policy) {
        public DatasetBuild {
            rows = List.copyOf(rows); dedupDecisions = List.copyOf(dedupDecisions);
            protectedSets = List.copyOf(protectedSets); approvals = List.copyOf(approvals);
            blockers = List.copyOf(blockers);
        }
        public void requireApproved() {
            if (state != DatasetState.APPROVED || !blockers.isEmpty()
                    || !contaminationAudit.clean()) {
                throw new IllegalStateException("FREEZE_GATE_FAILED: "
                        + String.join(", ", blockers));
            }
        }
    }
}
