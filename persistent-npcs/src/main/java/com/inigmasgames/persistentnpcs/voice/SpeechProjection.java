package com.inigmasgames.persistentnpcs.voice;

/** Performance metadata for spatial NPC speech; it never changes lexical dialogue. */
public enum SpeechProjection {
    NORMAL(0.0),
    CALL(1.5),
    SHOUT(2.5);

    private final double gainBoostDb;

    SpeechProjection(double gainBoostDb) {
        this.gainBoostDb = gainBoostDb;
    }

    /** A small pre-limiter boost; the worker's existing limiter remains authoritative. */
    public double gainBoostDb() {
        return gainBoostDb;
    }
}
