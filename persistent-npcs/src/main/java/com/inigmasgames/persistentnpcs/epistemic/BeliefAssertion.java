package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Materialized assertion rebuilt from append-only belief events. */
public record BeliefAssertion(UUID assertionId, UUID ownerNpcId, UUID subjectId,
        String subject, String predicate, String value, String statement, Polarity polarity,
        EpistemicStatus status, double confidence, BeliefProvenance provenance,
        TemporalScope temporalScope, AssertionScope assertionScope,
        List<UUID> supportIds, List<UUID> conflictIds, int revision,
        Instant learnedAt, Instant lastConfirmedAt) {
    public BeliefAssertion {
        if (assertionId == null || ownerNpcId == null || provenance == null
                || revision < 1) throw new IllegalArgumentException(
                        "stable owner/assertion/provenance/revision required");
        subject = clean(subject, 100); predicate = BeliefPredicateRegistry.canonical(predicate);
        value = clean(value, 240); statement = clean(statement, 600);
        polarity = polarity == null ? Polarity.POSITIVE : polarity;
        status = status == null ? EpistemicStatus.BELIEVED : status;
        confidence = Math.max(0, Math.min(1, confidence));
        temporalScope = temporalScope == null ? TemporalScope.stable(Instant.now())
                : temporalScope;
        assertionScope = assertionScope == null ? AssertionScope.ENTITY : assertionScope;
        supportIds = List.copyOf(supportIds == null ? List.of() : supportIds);
        conflictIds = List.copyOf(conflictIds == null ? List.of() : conflictIds);
        learnedAt = learnedAt == null ? Instant.now() : learnedAt;
        lastConfirmedAt = lastConfirmedAt == null ? learnedAt : lastConfirmedAt;
    }
    public boolean activeAt(Instant at) {
        return status != EpistemicStatus.SUPERSEDED && status != EpistemicStatus.RETRACTED
                && status != EpistemicStatus.EXPIRED && temporalScope.validAt(at);
    }
    public String key() {
        return ownerNpcId + "|" + (subjectId == null ? subject.toLowerCase(
                java.util.Locale.ROOT) : subjectId) + "|" + predicate;
    }
    private static String clean(String value, int max) {
        String result = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return result.length() <= max ? result : result.substring(0, max);
    }
    public enum Polarity { POSITIVE, NEGATIVE }
    public enum AssertionScope { SELF, ENTITY, RELATIONSHIP, WORLD, EVENT }
    public record TemporalScope(BeliefPredicateRegistry.Stability stability,
            Instant validFrom, Instant validUntil, String authoredReference) {
        public TemporalScope {
            stability = stability == null ? BeliefPredicateRegistry.Stability.STABLE : stability;
            authoredReference = authoredReference == null ? "" : authoredReference.strip();
        }
        public static TemporalScope stable(Instant at) {
            return new TemporalScope(BeliefPredicateRegistry.Stability.STABLE, at, null, "");
        }
        public boolean validAt(Instant at) {
            Instant value = at == null ? Instant.now() : at;
            return (validFrom == null || !value.isBefore(validFrom))
                    && (validUntil == null || value.isBefore(validUntil));
        }
    }
}
