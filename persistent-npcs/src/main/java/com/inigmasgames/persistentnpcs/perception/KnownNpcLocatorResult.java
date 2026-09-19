package com.inigmasgames.persistentnpcs.perception;

import java.util.UUID;

/** Semantic locator result. Stable IDs remain internal and are never rendered for dialogue. */
public record KnownNpcLocatorResult(
        KnownNpcLocationStatus status,
        UUID targetStableId,
        String targetName,
        String approximateDistance,
        String direction,
        String semanticLocation,
        boolean navigationPossible,
        boolean directGuideRequest) {

    public KnownNpcLocatorResult {
        status = status == null ? KnownNpcLocationStatus.NOT_FOUND : status;
        targetName = clean(targetName, "that person");
        approximateDistance = clean(approximateDistance, "unknown distance");
        direction = clean(direction, "unknown direction");
        semanticLocation = clean(semanticLocation, "location unknown");
    }

    public boolean found() {
        return status == KnownNpcLocationStatus.FOUND;
    }

    public String semanticBlock() {
        return switch (status) {
            case FOUND -> targetName + " is " + approximateDistance + " to the " + direction
                    + ". Current location understanding: " + semanticLocation + ". "
                    + (navigationPossible ? "Guidance is physically possible."
                            : "Guidance is not currently possible.");
            case NOT_FOUND -> targetName
                    + " is not within the bounded nearby area I can check.";
            case NOT_LOADED -> targetName
                    + " is known to me, but is not presently available in loaded world state.";
            case UNKNOWN_RELATIONSHIP -> "I do not have an established relationship with "
                    + targetName + ", so I cannot locate them through social knowledge.";
        };
    }

    private static String clean(String value, String fallback) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.isBlank() ? fallback : text.length() <= 120 ? text : text.substring(0, 120);
    }
}
