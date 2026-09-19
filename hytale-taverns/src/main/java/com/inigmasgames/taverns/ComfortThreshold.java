package com.inigmasgames.taverns;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Data-driven threshold policy for one Comfort category. */
record ComfortThreshold(
        boolean enabled,
        int minimum,
        Integer density,
        ComfortCountMode countMode) {

    ComfortThreshold {
        if (minimum < 1) {
            throw new IllegalArgumentException("minimum must be at least 1");
        }
        if (density != null && density < 1) {
            throw new IllegalArgumentException("density must be positive when configured");
        }
        Objects.requireNonNull(countMode, "countMode");
    }

    int requiredCount(int eligibleFloorArea) {
        if (density == null || eligibleFloorArea <= 0) {
            return minimum;
        }
        int areaCount = (eligibleFloorArea + density - 1) / density;
        return Math.max(minimum, areaCount);
    }

    static Map<ComfortCategory, ComfortThreshold> designDefaults() {
        EnumMap<ComfortCategory, ComfortThreshold> defaults =
                new EnumMap<>(ComfortCategory.class);
        for (ComfortCategory category : ComfortCategory.values()) {
            defaults.put(category, new ComfortThreshold(
                    true,
                    1,
                    null,
                    category == ComfortCategory.DECO
                            ? ComfortCountMode.DISTINCT_ASSET_TYPES
                            : ComfortCountMode.PLACED_INSTANCES));
        }
        return Map.copyOf(defaults);
    }
}
