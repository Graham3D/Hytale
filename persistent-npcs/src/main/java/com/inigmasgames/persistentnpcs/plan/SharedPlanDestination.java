package com.inigmasgames.persistentnpcs.plan;

import java.util.UUID;

/** Server-resolved destination. Coordinates may be absent for a named future meeting. */
public record SharedPlanDestination(
        UUID worldId, Double x, Double y, Double z, String label) {

    public SharedPlanDestination normalized() {
        boolean any = x != null || y != null || z != null;
        boolean all = x != null && y != null && z != null
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        if (any && !all) {
            throw new IllegalArgumentException(
                    "A shared-plan destination requires all three finite coordinates");
        }
        return new SharedPlanDestination(worldId, x, y, z,
                label == null ? "" : compact(label, 120));
    }

    public boolean hasCoordinates() {
        return x != null && y != null && z != null;
    }

    public String describe() {
        if (hasCoordinates()) {
            String name = label == null || label.isBlank() ? "destination" : label;
            return name + " (%.1f, %.1f, %.1f)".formatted(x, y, z);
        }
        return label == null || label.isBlank() ? "unspecified" : label;
    }

    private static String compact(String value, int maximum) {
        String text = value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
