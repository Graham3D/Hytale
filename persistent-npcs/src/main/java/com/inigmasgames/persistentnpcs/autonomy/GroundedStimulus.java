package com.inigmasgames.persistentnpcs.autonomy;

import java.time.Instant;
import java.util.UUID;

/** A current-world fact. It is invalid unless its source can be revalidated. */
public record GroundedStimulus(
        String targetId,
        String semanticType,
        String assetId,
        UUID worldId,
        double x,
        double y,
        double z,
        double distanceMeters,
        String source,
        Instant observedAt) {
}
