package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.UUID;

public record EvidencePacket(int schemaVersion, UUID responseId, UUID npcStableId,
        UUID playerId, UUID worldId, String queryKind, String requestedSubject,
        String requestedPredicate, List<EvidenceRef> supporting,
        List<EvidenceRef> contradicting, List<EvidenceRef> contextual,
        List<String> unknownSlots, EvidenceSufficiency sufficiency,
        List<String> restrictions, List<String> omittedExistingEvidence,
        int prunedCount, List<String> prunedReasons,
        List<EvidenceSourceKind> provenanceSummary, int estimatedTokens,
        long retrievalMicros, boolean budgetExhausted,
        RetrievalDiagnostics retrievalDiagnostics) {
    public static final int SCHEMA_VERSION = 2;
    public EvidencePacket {
        if (schemaVersion < 1) throw new IllegalArgumentException("schema version required");
        queryKind = clean(queryKind); requestedSubject = clean(requestedSubject);
        requestedPredicate = clean(requestedPredicate);
        supporting = List.copyOf(supporting == null ? List.of() : supporting);
        contradicting = List.copyOf(contradicting == null ? List.of() : contradicting);
        contextual = List.copyOf(contextual == null ? List.of() : contextual);
        unknownSlots = List.copyOf(unknownSlots == null ? List.of() : unknownSlots);
        restrictions = List.copyOf(restrictions == null ? List.of() : restrictions);
        omittedExistingEvidence = List.copyOf(omittedExistingEvidence == null
                ? List.of() : omittedExistingEvidence);
        sufficiency = sufficiency == null ? EvidenceSufficiency.UNIMPLEMENTED : sufficiency;
        prunedCount = Math.max(0, prunedCount);
        prunedReasons = List.copyOf(prunedReasons == null ? List.of() : prunedReasons);
        provenanceSummary = List.copyOf(provenanceSummary == null ? List.of() : provenanceSummary);
        estimatedTokens = Math.max(0, estimatedTokens);
        retrievalMicros = Math.max(0, retrievalMicros);
        retrievalDiagnostics = retrievalDiagnostics == null
                ? RetrievalDiagnostics.empty() : retrievalDiagnostics;
    }
    /** E0-E4 source-compatible constructor. */
    public EvidencePacket(int schemaVersion, UUID responseId, UUID npcStableId,
            UUID playerId, UUID worldId, String queryKind, String requestedSubject,
            String requestedPredicate, List<EvidenceRef> supporting,
            List<EvidenceRef> contradicting, List<EvidenceRef> contextual,
            List<String> unknownSlots, EvidenceSufficiency sufficiency,
            List<String> restrictions, List<String> omittedExistingEvidence,
            int prunedCount, List<String> prunedReasons,
            List<EvidenceSourceKind> provenanceSummary, int estimatedTokens,
            long retrievalMicros, boolean budgetExhausted) {
        this(schemaVersion, responseId, npcStableId, playerId, worldId, queryKind,
                requestedSubject, requestedPredicate, supporting, contradicting, contextual,
                unknownSlots, sufficiency, restrictions, omittedExistingEvidence, prunedCount,
                prunedReasons, provenanceSummary, estimatedTokens, retrievalMicros,
                budgetExhausted, RetrievalDiagnostics.empty());
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
