package com.inigmasgames.persistentnpcs.memory;

/** Deterministic persistence/decay tier; LANDMARK records are never auto-trimmed. */
public enum MemoryDurability {
    TRANSIENT(0.20, 3.0),
    NORMAL(0.45, 30.0),
    IMPORTANT(0.75, 365.0),
    LANDMARK(1.00, Double.POSITIVE_INFINITY);

    private final double retrievalWeight;
    private final double decayHalfLifeDays;

    MemoryDurability(double retrievalWeight, double decayHalfLifeDays) {
        this.retrievalWeight = retrievalWeight;
        this.decayHalfLifeDays = decayHalfLifeDays;
    }

    public double retrievalWeight() { return retrievalWeight; }

    public double decayHalfLifeDays() { return decayHalfLifeDays; }
}
