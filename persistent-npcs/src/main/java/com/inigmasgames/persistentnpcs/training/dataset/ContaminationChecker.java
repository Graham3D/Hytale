package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.CanonicalDatasetRow;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ContaminationAudit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ContaminationIssue;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ContaminationKind;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetSplit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ProtectedSetManifest;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local-only protected-set comparison. No remote judge or training provider is called. */
public final class ContaminationChecker {
    public ContaminationAudit audit(List<CanonicalDatasetRow> rows,
            List<ProtectedSetManifest> protectedSets, DatasetPolicy policy) {
        List<ContaminationIssue> issues = new ArrayList<>();
        int checked = 0;
        for (CanonicalDatasetRow row : rows) {
            if (row.split() != DatasetSplit.TRAIN) continue;
            checked++;
            for (ProtectedSetManifest set : protectedSets) {
                compare(row, set, policy, issues);
            }
        }
        AuditSeed seed = new AuditSeed(DatasetNormalization.FUZZY_ALGORITHM,
                policy.fuzzyDuplicateThreshold(), List.copyOf(issues), checked);
        return new ContaminationAudit(1, DatasetNormalization.FUZZY_ALGORITHM,
                policy.fuzzyDuplicateThreshold(), issues, checked,
                CanonicalJson.sha256(seed));
    }

    public static ProtectedSetManifest manifest(String id, DatasetSplit split,
            Set<String> exact, Set<String> normalized, Set<String> entityNormalized,
            Set<String> families, Set<String> ancestors, List<String> prompts,
            String sourceHash) {
        var seed = new ProtectedSeed(id, split, sorted(exact), sorted(normalized),
                sorted(entityNormalized), sorted(families), sorted(ancestors), prompts,
                sourceHash == null ? "" : sourceHash);
        return new ProtectedSetManifest(1, id, split, exact, normalized,
                entityNormalized, families, ancestors, prompts,
                sourceHash == null ? "" : sourceHash, CanonicalJson.sha256(seed));
    }

    private static void compare(CanonicalDatasetRow row, ProtectedSetManifest set,
            DatasetPolicy policy, List<ContaminationIssue> issues) {
        if (set.exactInputHashes().contains(row.inputSha256())) {
            issue(row, set, ContaminationKind.EXACT, 1.0,
                    "PROTECTED_EXACT_CONTAMINATION", row.inputSha256(), issues);
        }
        if (set.normalizedInputFingerprints().contains(row.normalizedInputFingerprint())) {
            issue(row, set, ContaminationKind.NORMALIZED, 1.0,
                    "PROTECTED_NORMALIZED_CONTAMINATION",
                    row.normalizedInputFingerprint(), issues);
        }
        if (set.entityNormalizedInputFingerprints()
                .contains(row.entityNormalizedInputFingerprint())) {
            issue(row, set, ContaminationKind.ENTITY_NORMALIZED, 1.0,
                    "PROTECTED_ENTITY_CONTAMINATION",
                    row.entityNormalizedInputFingerprint(), issues);
        }
        if (set.semanticFamilyIds().contains(row.semanticFamilyId().value())) {
            issue(row, set, ContaminationKind.SEMANTIC_FAMILY, 1.0,
                    "PROTECTED_FAMILY_CONTAMINATION", row.semanticFamilyId().value(), issues);
        }
        if (!row.generationAncestorId().isBlank()
                && set.generationAncestorIds().contains(row.generationAncestorId())) {
            issue(row, set, ContaminationKind.GENERATION_ANCESTRY, 1.0,
                    "PROTECTED_ANCESTRY_CONTAMINATION", row.generationAncestorId(), issues);
        }
        String input = DatasetNormalization.exactInput(row.example());
        for (String prompt : set.prompts()) {
            double score = DatasetNormalization.fuzzySimilarity(input, prompt);
            if (score >= policy.fuzzyDuplicateThreshold()) {
                issue(row, set, ContaminationKind.FUZZY, score,
                        "PROTECTED_FUZZY_CONTAMINATION",
                        DatasetNormalization.normalizedFingerprint(prompt), issues);
            }
        }
    }

    private static void issue(CanonicalDatasetRow row, ProtectedSetManifest set,
            ContaminationKind kind, double similarity, String reason, String fingerprint,
            List<ContaminationIssue> output) {
        boolean duplicate = output.stream().anyMatch(existing ->
                existing.rowId().equals(row.rowId().value())
                        && existing.protectedSetId().equals(set.protectedSetId())
                        && existing.kind() == kind);
        if (!duplicate) output.add(new ContaminationIssue(row.rowId().value(),
                set.protectedSetId(), kind, similarity, reason, fingerprint));
    }

    private record AuditSeed(String algorithm, double threshold,
            List<ContaminationIssue> issues, int checked) { }
    private static List<String> sorted(Set<String> values) {
        return values == null ? List.of() : values.stream().sorted().toList();
    }
    private record ProtectedSeed(String id, DatasetSplit split, List<String> exact,
            List<String> normalized, List<String> entityNormalized, List<String> families,
            List<String> ancestors, List<String> prompts, String sourceHash) { }
}
