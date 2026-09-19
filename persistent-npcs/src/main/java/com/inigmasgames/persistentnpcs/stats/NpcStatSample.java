package com.inigmasgames.persistentnpcs.stats;

/** Immutable world-thread observation, safe to serialize on the repository writer. */
public record NpcStatSample(double current, double minimum, double maximum) {
    public NpcStatSample {
        if (!Double.isFinite(current) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum > maximum || Math.abs(current) > Float.MAX_VALUE
                || Math.abs(minimum) > Float.MAX_VALUE || Math.abs(maximum) > Float.MAX_VALUE)
            throw new IllegalArgumentException("Invalid live NPC stat sample");
    }
}
