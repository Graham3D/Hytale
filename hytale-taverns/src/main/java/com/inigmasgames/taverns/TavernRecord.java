package com.inigmasgames.taverns;

import java.util.Objects;
import java.util.UUID;

/** Durable business identity. Physical Core data is stored separately in {@link CoreRecord}. */
public record TavernRecord(
        UUID tavernId,
        UUID worldId,
        UUID ownerId,
        TavernStatus status) {

    public TavernRecord {
        Objects.requireNonNull(tavernId, "tavernId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(status, "status");
    }

    public TavernRecord withOwner(UUID newOwnerId) {
        return new TavernRecord(tavernId, worldId, newOwnerId, status);
    }

    public TavernRecord withStatus(TavernStatus newStatus) {
        return new TavernRecord(tavernId, worldId, ownerId, newStatus);
    }
}
