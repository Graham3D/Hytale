package com.inigmasgames.taverns;

import java.util.Objects;
import java.util.UUID;

/** Stable world registration for a Comfort-contributing object inside one Core. */
record RegisteredComfortObject(
        UUID coreId,
        UUID worldId,
        int x,
        int y,
        int z,
        String assetId,
        ComfortCategory category,
        int comfort,
        boolean valid) {

    RegisteredComfortObject {
        Objects.requireNonNull(coreId, "coreId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(category, "category");
        if (comfort < 0 || comfort > 8) {
            throw new IllegalArgumentException("comfort must be between 0 and 8");
        }
    }
}
