package com.inigmasgames.hytalerpg.gear;

/** Shared acceptance rule called by the native gate at its authored checkpoint. */
final class NativeTwinAssaultEligibility {
    private NativeTwinAssaultEligibility() {}
    static boolean accepts(GearEffectSnapshot accepted, GearInstance main, GearInstance offhand, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("INVALID_TWIN_ROLL");
        if (main == null || offhand == null || main.identity().equals(offhand.identity())
                || !main.baseId().startsWith("gm.daggers_") || !main.baseId().equals(offhand.baseId())
                || accepted.forItem(main.identity()).empty() || accepted.forItem(offhand.identity()).empty()) return false;
        double chance = accepted.forItem(main.identity()).value("WA-141");
        return Double.isFinite(chance) && chance >= 3.2 && chance <= 12
                && Math.abs(Math.round(chance * 10) / 10.0 - chance) < 1e-7
                && roll < chance / 100.0;
    }
}
