package com.inigmasgames.compat.immersivenpcs.v1;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Server-authored gameplay fact. It never carries commands or executable behavior. */
public record GameplayEvent(
        UUID eventId,
        EventType type,
        Instant occurredAt,
        UUID actorId,
        UUID targetId,
        Map<String, String> facts) {

    public enum EventType {
        ENTITY_DAMAGED
    }

    public GameplayEvent {
        if (eventId == null || type == null || occurredAt == null) {
            throw new IllegalArgumentException("eventId, type, and occurredAt are required");
        }
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
}
