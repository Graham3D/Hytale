package com.inigmasgames.persistentnpcs.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Resolved server event. UUIDs originate from Hytale/core systems, never the LLM. */
public record NpcFrameworkEvent(
        UUID eventId,
        NpcEventType type,
        UUID npcId,
        UUID actorEntityId,
        UUID targetEntityId,
        Instant occurredAt,
        Map<String, String> facts) {

    public NpcFrameworkEvent normalized() {
        return new NpcFrameworkEvent(eventId == null ? UUID.randomUUID() : eventId,
                type, npcId, actorEntityId, targetEntityId,
                occurredAt == null ? Instant.now() : occurredAt,
                facts == null ? Map.of() : Map.copyOf(facts));
    }
}
