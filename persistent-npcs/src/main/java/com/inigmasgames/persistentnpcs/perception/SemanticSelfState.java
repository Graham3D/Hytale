package com.inigmasgames.persistentnpcs.perception;

import java.util.List;

/** Authoritative self-state expressed without engine identifiers or debug syntax. */
public record SemanticSelfState(
        String stableIdentity,
        boolean physicallyLoaded,
        String positionAwareness,
        String semanticLocation,
        String hytaleState,
        String currentActivity,
        String activeOperation,
        String interactionTarget,
        List<String> nearbyEntities) {

    public SemanticSelfState {
        stableIdentity = safe(stableIdentity, "unknown NPC");
        positionAwareness = safe(positionAwareness, "current position unavailable");
        semanticLocation = safe(semanticLocation, "location name unknown");
        hytaleState = safe(hytaleState, physicallyLoaded ? "present in the world" : "not loaded");
        currentActivity = safe(currentActivity, "idle");
        activeOperation = safe(activeOperation, "none");
        interactionTarget = safe(interactionTarget, "none");
        nearbyEntities = List.copyOf(nearbyEntities == null ? List.of() : nearbyEntities);
    }

    public SemanticSelfState withRuntime(
            String activity, String operation, String hytaleStateValue) {
        return new SemanticSelfState(stableIdentity, physicallyLoaded, positionAwareness,
                semanticLocation, hytaleStateValue, activity, operation, interactionTarget,
                nearbyEntities);
    }

    public String promptBlock() {
        return "You are " + stableIdentity + ". You are not the player and not any other NPC. "
                + "Your authoritative physical state is: " + hytaleState + ". "
                + "Your current location understanding is: " + semanticLocation + ". "
                + "Your current activity is " + currentActivity + ". "
                + "Your active operation is " + activeOperation + ". "
                + "Your interaction target is " + interactionTarget + "."
                + (nearbyEntities.isEmpty() ? "" : " Nearby: " + String.join(", ", nearbyEntities)
                        + ".");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
