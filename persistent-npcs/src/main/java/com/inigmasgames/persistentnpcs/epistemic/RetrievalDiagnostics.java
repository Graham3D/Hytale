package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.Map;

/** Compact E5 trace payload for reconstructing bounded retrieval without dumping sessions. */
public record RetrievalDiagnostics(List<String> queryExpansionTerms, String timeConstraint,
        int candidateCount, Map<String, String> rankingComponents,
        List<String> selectedEvidenceIds, List<String> rejectedLowConfidence,
        String factSessionSource, String readBoundary,
        List<String> workspaceTopics, List<String> workspaceReferents,
        List<String> openCommitments) {
    public RetrievalDiagnostics {
        queryExpansionTerms = List.copyOf(queryExpansionTerms == null ? List.of()
                : queryExpansionTerms);
        timeConstraint = clean(timeConstraint);
        rankingComponents = Map.copyOf(rankingComponents == null ? Map.of()
                : rankingComponents);
        selectedEvidenceIds = List.copyOf(selectedEvidenceIds == null ? List.of()
                : selectedEvidenceIds);
        rejectedLowConfidence = List.copyOf(rejectedLowConfidence == null ? List.of()
                : rejectedLowConfidence);
        factSessionSource = clean(factSessionSource);
        readBoundary = clean(readBoundary);
        workspaceTopics = List.copyOf(workspaceTopics == null ? List.of() : workspaceTopics);
        workspaceReferents = List.copyOf(workspaceReferents == null ? List.of()
                : workspaceReferents);
        openCommitments = List.copyOf(openCommitments == null ? List.of() : openCommitments);
        candidateCount = Math.max(0, candidateCount);
    }
    public static RetrievalDiagnostics empty() {
        return new RetrievalDiagnostics(List.of(), "UNBOUNDED", 0, Map.of(), List.of(),
                List.of(), "", "RAM_ONLY", List.of(), List.of(), List.of());
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
