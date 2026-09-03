package com.inigmasgames.taverns;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable world reference and zoning data for one physical Core. */
public record CoreRecord(
        UUID coreId,
        UUID tavernId,
        CoreType type,
        UUID worldId,
        int coreX,
        int coreY,
        int coreZ,
        Cuboid bounds,
        int expansionUnits,
        int paidExpansionUnits,
        Set<Long> intersectedChunks) {

    public CoreRecord {
        Objects.requireNonNull(coreId, "coreId");
        Objects.requireNonNull(tavernId, "tavernId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bounds, "bounds");
        intersectedChunks = Set.copyOf(intersectedChunks);
        if (expansionUnits < 0) {
            throw new IllegalArgumentException("expansionUnits cannot be negative");
        }
        if (paidExpansionUnits < 0 || paidExpansionUnits > expansionUnits) {
            throw new IllegalArgumentException("paidExpansionUnits must be between zero and expansionUnits");
        }
    }

    public static CoreRecord create(
            UUID coreId, UUID tavernId, CoreDefinition definition, UUID worldId,
            int coreX, int coreY, int coreZ) {
        Cuboid bounds = definition.startingBounds(coreX, coreY, coreZ);
        return new CoreRecord(
                coreId, tavernId, definition.type(), worldId,
                coreX, coreY, coreZ, bounds, 0, 0, bounds.intersectedChunks());
    }

    public CoreRecord withBounds(Cuboid newBounds, int newExpansionUnits, int newPaidExpansionUnits) {
        return new CoreRecord(
                coreId, tavernId, type, worldId, coreX, coreY, coreZ,
                newBounds, newExpansionUnits, newPaidExpansionUnits, newBounds.intersectedChunks());
    }
}
