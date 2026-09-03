package com.inigmasgames.persistentnpcs.home;

public record HomeBehaviorConfig(
        Boolean enabled,
        Double wanderRadius,
        Integer minimumIdleSeconds,
        Integer maximumIdleSeconds,
        Integer investigationPauseSeconds) {

    public boolean isEnabled() { return enabled == null || enabled; }
    public double effectiveRadius() {
        return wanderRadius == null ? 4.0 : Math.max(1.5, Math.min(10.0, wanderRadius));
    }
    public int effectiveMinimumIdleSeconds() {
        return minimumIdleSeconds == null ? 30 : Math.max(10, minimumIdleSeconds);
    }
    public int effectiveMaximumIdleSeconds() {
        return maximumIdleSeconds == null ? 75
                : Math.max(effectiveMinimumIdleSeconds(), maximumIdleSeconds);
    }
    public int effectiveInvestigationPauseSeconds() {
        return investigationPauseSeconds == null ? 5
                : Math.max(2, Math.min(30, investigationPauseSeconds));
    }
}
