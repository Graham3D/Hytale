package com.inigmasgames.persistentnpcs.orbis;

public enum ResourcePriority {
    REALTIME_CRITICAL(0),
    HIGH(1),
    NORMAL(2),
    LOW(3);

    private final int rank;
    ResourcePriority(int rank) { this.rank = rank; }
    public int rank() { return rank; }
}
