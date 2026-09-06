package com.inigmasgames.hytalerpg.combat.status;

/** Snapshot of authoritative target control rules at status request time. */
public record ControlProfile(boolean protectedEntity, boolean boss, boolean hardControlResistant) {
    public static final ControlProfile NORMAL = new ControlProfile(false, false, false);
    public boolean blocksHardControl() { return protectedEntity || boss || hardControlResistant; }
}
