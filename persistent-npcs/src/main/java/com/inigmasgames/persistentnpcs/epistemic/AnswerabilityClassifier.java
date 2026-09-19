package com.inigmasgames.persistentnpcs.epistemic;

/** E2 deterministic classification. Absence is never converted into a negative fact. */
public final class AnswerabilityClassifier {
    private AnswerabilityClassifier() { }

    public static Answerability classify(DialogueFrame frame, EpistemicQueryPlan query,
            EvidencePacket packet) {
        if (frame.inputQualityConcern()) return Answerability.UNRESOLVED;
        if (frame.ambiguous() || query.ambiguous()) return Answerability.AMBIGUOUS;
        EpistemicQueryKind kind = kind(query.queryKind());
        if (kind == EpistemicQueryKind.SUBJECTIVE_PREFERENCE
                || kind == EpistemicQueryKind.GENERAL_SOCIAL) return Answerability.SUBJECTIVE;
        if (!packet.contradicting().isEmpty()
                || packet.sufficiency() == EvidenceSufficiency.CONFLICTED) {
            return Answerability.CONFLICTED;
        }
        if (packet.sufficiency() == EvidenceSufficiency.PARTIAL) {
            return Answerability.PARTIALLY_KNOWN;
        }
        if (packet.sufficiency() == EvidenceSufficiency.STALE
                || packet.sufficiency() == EvidenceSufficiency.IRRELEVANT) {
            return kind == EpistemicQueryKind.CURRENT_PERCEPTION
                    ? Answerability.NEEDS_CURRENT_PERCEPTION : Answerability.UNKNOWN;
        }
        if (kind == EpistemicQueryKind.CLARIFICATION && packet.supporting().isEmpty()) {
            return Answerability.NEEDS_CLARIFICATION;
        }
        if (kind == EpistemicQueryKind.ACTION_REQUEST) {
            return packet.supporting().isEmpty() ? Answerability.UNKNOWN
                    : Answerability.NEEDS_ACTION;
        }
        if (packet.supporting().isEmpty()) {
            if (kind == EpistemicQueryKind.CURRENT_PERCEPTION
                    && packet.contextual().isEmpty()) {
                return Answerability.NEEDS_CURRENT_PERCEPTION;
            }
            return Answerability.UNKNOWN;
        }
        boolean onlyBelieved = packet.supporting().stream().allMatch(value ->
                !value.authoritative() && value.status() != EpistemicStatus.KNOWN);
        return onlyBelieved ? Answerability.INFERRED : Answerability.KNOWN;
    }

    private static EpistemicQueryKind kind(String value) {
        try { return EpistemicQueryKind.valueOf(value); }
        catch (RuntimeException ignored) { return EpistemicQueryKind.UNRESOLVED; }
    }
}
