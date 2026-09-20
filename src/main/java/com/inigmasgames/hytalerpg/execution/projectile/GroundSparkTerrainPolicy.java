package com.inigmasgames.hytalerpg.execution.projectile;

/** Exact block-step boundary for Spark's manually ground-resolved travel. */
public final class GroundSparkTerrainPolicy {
    public static final double MAX_STEP_BLOCKS=1.05;
    private GroundSparkTerrainPolicy() { }
    public static boolean traversableHeight(double rise) {
        return Double.isFinite(rise)&&Math.abs(rise)<=MAX_STEP_BLOCKS;
    }
    public static boolean barrier(double rise) {
        return Double.isFinite(rise)&&rise>MAX_STEP_BLOCKS;
    }
    public static boolean unsupportedDrop(double rise) {
        return !Double.isFinite(rise)||rise< -MAX_STEP_BLOCKS;
    }
}
