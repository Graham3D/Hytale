package com.inigmasgames.hytalerpg.combat.status;

/** Snapshot of authoritative target control rules at status request time. */
public record ControlProfile(boolean protectedEntity, boolean boss, boolean hardControlResistant, boolean elite) {
    public ControlProfile(boolean protectedEntity, boolean boss, boolean hardControlResistant) {
        this(protectedEntity, boss, hardControlResistant, false);
    }
    public static final ControlProfile NORMAL = new ControlProfile(false, false, false);
    public boolean blocksHardControl() { return protectedEntity || boss || hardControlResistant; }
    public double durationMultiplier() { return elite ? .5 : 1; }
    public double displacementMultiplier() { return blocksHardControl() ? 0 : elite ? .5 : 1; }
}
