package com.inigmasgames.persistentnpcs.cognition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A claim learned from an entity. It is evidence, never authoritative world state. */
public record SourcedBelief(
        UUID beliefId,
        UUID npcId,
        UUID sourceEntityId,
        UUID subjectEntityId,
        String subject,
        String predicate,
        String object,
        String semanticLocation,
        String temporalReference,
        String proposition,
        Instant timestamp,
        double confidence,
        double urgency,
        UUID conversationId,
        UUID responseId,
        UUID utteranceId,
        List<String> evidenceRefs) {

    /** Source-compatible constructor for beliefs written before R033. */
    public SourcedBelief(UUID beliefId, UUID npcId, UUID sourceEntityId,
            UUID subjectEntityId, String subject, String predicate, String proposition,
            Instant timestamp, double confidence, double urgency, UUID conversationId,
            UUID responseId, List<String> evidenceRefs) {
        this(beliefId, npcId, sourceEntityId, subjectEntityId, subject, predicate,
                "", "", "", proposition, timestamp, confidence, urgency,
                conversationId, responseId, null, evidenceRefs);
    }

    public SourcedBelief normalized() {
        if (npcId == null || sourceEntityId == null) {
            throw new IllegalArgumentException("Sourced belief requires NPC and source IDs");
        }
        String claim = compact(proposition, 600);
        if (claim.isBlank()) throw new IllegalArgumentException("Belief proposition is required");
        return new SourcedBelief(beliefId == null ? UUID.randomUUID() : beliefId,
                npcId, sourceEntityId, subjectEntityId, compact(subject, 80),
                compact(predicate, 40).toUpperCase(java.util.Locale.ROOT),
                compact(object, 180), compact(semanticLocation, 180),
                compact(temporalReference, 80), claim,
                timestamp == null ? Instant.now() : timestamp, clamp(confidence),
                clamp(urgency), conversationId, responseId, utteranceId,
                evidenceRefs == null ? List.of() : evidenceRefs.stream()
                        .filter(java.util.Objects::nonNull).map(value -> compact(value, 160))
                        .filter(value -> !value.isBlank()).distinct().toList());
    }

    public String fingerprint() {
        return (subjectEntityId == null ? subject.toLowerCase(java.util.Locale.ROOT)
                : subjectEntityId.toString()) + ":" + predicate + ":"
                + proposition.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").strip();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
