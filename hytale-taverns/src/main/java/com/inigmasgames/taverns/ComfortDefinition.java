package com.inigmasgames.taverns;

import java.util.Objects;

/** Resolved Comfort metadata for one registered block item. */
record ComfortDefinition(
        String assetId,
        ComfortCategory category,
        int comfort,
        String region,
        String source) {

    ComfortDefinition {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(source, "source");
        if (comfort < 0 || comfort > 8) {
            throw new IllegalArgumentException("comfort must be between 0 and 8");
        }
    }
}
