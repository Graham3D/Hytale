package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;

/** One immutable row in the operator-facing readiness snapshot. */
public record OrbisReadinessRow(
        OrbisReadinessSystem system,
        String displayName,
        int readinessPercent,
        int filledPips,
        OrbisReadinessStatus status,
        String detail,
        Instant lastTransitionAt) {
    public OrbisReadinessRow {
        java.util.Objects.requireNonNull(system, "system");
        displayName = displayName == null || displayName.isBlank()
                ? system.displayName() : displayName.strip();
        readinessPercent = Math.max(0, Math.min(100, readinessPercent));
        filledPips = Math.max(0, Math.min(10, readinessPercent / 10));
        status = status == null ? OrbisReadinessStatus.NOT_STARTED : status;
        detail = detail == null ? "" : detail.strip();
        lastTransitionAt = lastTransitionAt == null ? Instant.now() : lastTransitionAt;
    }

    public static OrbisReadinessRow initial(OrbisReadinessSystem system) {
        return new OrbisReadinessRow(system, system.displayName(), 0, 0,
                OrbisReadinessStatus.NOT_STARTED, "Awaiting startup", Instant.now());
    }
}
