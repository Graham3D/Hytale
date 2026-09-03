package com.inigmasgames.persistentnpcs.orbis;

/** Truthful lifecycle states exposed by the cached Orbis readiness model. */
public enum OrbisReadinessStatus {
    NOT_STARTED,
    STARTING,
    LOADING,
    WARMING,
    READY,
    DEGRADED,
    ERROR,
    DISABLED
}
