package com.inigmasgames.persistentnpcs.perception;

/** One bounded semantic feature derived from authoritative world assets. */
public record EnvironmentFeature(
        String category,
        String label,
        int count,
        double distanceMeters,
        String direction,
        int importance) {

    public String compact() {
        String location = distanceMeters < 1.5 ? "nearby"
                : "about %.0fm %s".formatted(distanceMeters, direction);
        return label + " (" + location + (count > 1 ? ", samples=" + count : "") + ")";
    }

    /** NPC-facing concept without raw counts or precise measurements. */
    public String semantic() {
        String proximity = distanceMeters < 1.5 ? "within reach"
                : distanceMeters <= 5 ? "close by"
                : distanceMeters <= 10 ? "nearby" : "at the edge of view";
        String bearing = direction == null || direction.isBlank()
                || "nearby".equalsIgnoreCase(direction) ? "" : " to the " + direction;
        return label + " (" + proximity + bearing + ")";
    }
}
