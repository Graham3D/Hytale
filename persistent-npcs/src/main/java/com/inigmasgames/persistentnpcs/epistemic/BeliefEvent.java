package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Versioned append-only durable mutation. */
public record BeliefEvent(int schemaVersion, long sequence, UUID eventId, EventType type,
        BeliefAssertion assertion, List<UUID> relatedAssertionIds, Instant occurredAt,
        String checksum) {
    public static final int SCHEMA_VERSION = 1;
    public BeliefEvent {
        if (schemaVersion < 1 || eventId == null || type == null || assertion == null
                || sequence < 1) throw new IllegalArgumentException("complete belief event required");
        relatedAssertionIds = List.copyOf(relatedAssertionIds == null
                ? List.of() : relatedAssertionIds);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        checksum = checksum == null ? "" : checksum;
    }
    public enum EventType {
        BELIEF_ASSERTED, BELIEF_REINFORCED, BELIEF_CONTRADICTED,
        BELIEF_SUPERSEDED, BELIEF_RETRACTED, BELIEF_EXPIRED, BELIEF_DERIVED,
        BELIEF_SHARED
    }
}
