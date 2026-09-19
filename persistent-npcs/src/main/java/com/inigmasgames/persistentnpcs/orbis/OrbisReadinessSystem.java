package com.inigmasgames.persistentnpcs.orbis;

/** Stable systems displayed by the in-game Orbis readiness panel. */
public enum OrbisReadinessSystem {
    MOONSHINE("Moonshine"),
    NEMOTRON("Nemotron"),
    CHATTERBOX("Chatterbox"),
    ORBIS("Orbis");

    private final String displayName;

    OrbisReadinessSystem(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }
}
