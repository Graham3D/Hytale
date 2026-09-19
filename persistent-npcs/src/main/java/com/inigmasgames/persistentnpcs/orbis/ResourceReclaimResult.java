package com.inigmasgames.persistentnpcs.orbis;

/** Result of an explicit provider-owned lifecycle action. */
public record ResourceReclaimResult(String action, String outcome, boolean resourcesChanged) {
    public ResourceReclaimResult {
        action = action == null || action.isBlank() ? "NONE" : action;
        outcome = outcome == null || outcome.isBlank() ? "UNKNOWN" : outcome;
    }
}
