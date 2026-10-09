package com.inigmasgames.hytalerpg.execution.summon;

import java.util.Locale;

/** Asset identities for the authored Skeleton Archer bow timeline. Each accepted roll has
 * one role, one root/action and one Shoot animation profile in the packaged asset set. */
public final class SummonNativeActions {
    private SummonNativeActions() {}
    public static final String ARCHER = "RPG_Summon_Skeleton_Archer";
    public static final double ARCHER_NATIVE_PERIOD = 1.2;

    public static String roleId(String baseRole, double points) {
        if (points == 0) return baseRole;
        if (!ARCHER.equals(baseRole)) return baseRole;
        return ARCHER + "_Command_" + String.format(Locale.ROOT, "%03d", grid(points));
    }

    public static double attackPeriod(String baseRole, double authoredPeriod, double points) {
        if (!Double.isFinite(authoredPeriod) || authoredPeriod <= 0) throw new IllegalArgumentException("Invalid summon period");
        if (points == 0) return authoredPeriod;
        grid(points);
        double nativePeriod = ARCHER.equals(baseRole) ? Math.max(authoredPeriod, ARCHER_NATIVE_PERIOD) : authoredPeriod;
        return nativePeriod / (1 + points / 100);
    }

    private static int grid(double points) {
        if (!Double.isFinite(points)) throw new IllegalArgumentException("Invalid Command roll");
        int tenths = (int)Math.round(points * 10);
        if (tenths < 32 || tenths > 240 || Math.abs(points * 10 - tenths) > 1e-6)
            throw new IllegalArgumentException("Command roll outside native asset grid: " + points);
        return tenths;
    }
}
