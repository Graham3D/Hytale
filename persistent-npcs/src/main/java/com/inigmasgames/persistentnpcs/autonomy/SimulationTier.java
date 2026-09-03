package com.inigmasgames.persistentnpcs.autonomy;

/** Controls update frequency without changing authoritative outcomes. */
public enum SimulationTier {
    ACTIVE(2_000),
    BACKGROUND(30_000),
    DORMANT(300_000);

    private final long intervalMillis;

    SimulationTier(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public long intervalMillis() {
        return intervalMillis;
    }
}
