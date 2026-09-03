package com.inigmasgames.persistentnpcs.scene;

import java.util.UUID;

public record NpcSpeechLocation(UUID worldId, double x, double y, double z) {
    public double distanceTo(NpcSpeechLocation other) {
        // Two null IDs are the same detached/test scene. One null and one real world ID are not.
        if (other == null || !java.util.Objects.equals(worldId, other.worldId())) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x();
        double dy = y - other.y();
        double dz = z - other.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
