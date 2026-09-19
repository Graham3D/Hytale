package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Atomic, immutable view consumed by HUDs, diagnostics, and future admin surfaces. */
public record OrbisReadinessSnapshot(
        long revision,
        Instant capturedAt,
        List<OrbisReadinessRow> rows) {
    public OrbisReadinessSnapshot {
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        rows = List.copyOf(rows == null ? List.of() : rows);
    }

    public OrbisReadinessRow row(OrbisReadinessSystem system) {
        return rows.stream().filter(value -> value.system() == system).findFirst()
                .orElseGet(() -> OrbisReadinessRow.initial(system));
    }

    public Map<OrbisReadinessSystem, OrbisReadinessRow> bySystem() {
        EnumMap<OrbisReadinessSystem, OrbisReadinessRow> values =
                new EnumMap<>(OrbisReadinessSystem.class);
        rows.forEach(value -> values.put(value.system(), value));
        return Map.copyOf(values);
    }
}
