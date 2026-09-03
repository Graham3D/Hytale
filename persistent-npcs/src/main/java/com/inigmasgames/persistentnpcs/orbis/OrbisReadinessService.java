package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Orbis-owned readiness authority. Provider threads publish real lifecycle transitions;
 * consumers only read cached immutable snapshots and never trigger provider work.
 */
public final class OrbisReadinessService implements AutoCloseable {
    private final AtomicLong revision = new AtomicLong();
    private final AtomicReference<OrbisReadinessSnapshot> snapshot =
            new AtomicReference<>(initialSnapshot());
    private final CopyOnWriteArrayList<Consumer<OrbisReadinessSnapshot>> listeners =
            new CopyOnWriteArrayList<>();

    public OrbisReadinessSnapshot snapshot() { return snapshot.get(); }

    public void transition(OrbisReadinessSystem system, int percent,
            OrbisReadinessStatus status, String detail) {
        transition(system, system.displayName(), percent, status, detail);
    }

    public void transition(OrbisReadinessSystem system, String displayName, int percent,
            OrbisReadinessStatus status, String detail) {
        java.util.Objects.requireNonNull(system, "system");
        while (true) {
            OrbisReadinessSnapshot prior = snapshot.get();
            EnumMap<OrbisReadinessSystem, OrbisReadinessRow> values =
                    new EnumMap<>(OrbisReadinessSystem.class);
            Arrays.stream(OrbisReadinessSystem.values())
                    .forEach(value -> values.put(value, prior.row(value)));
            values.put(system, new OrbisReadinessRow(system, displayName, percent, 0,
                    status, detail, Instant.now()));
            OrbisReadinessSnapshot next = new OrbisReadinessSnapshot(
                    revision.incrementAndGet(), Instant.now(),
                    Arrays.stream(OrbisReadinessSystem.values()).map(values::get).toList());
            if (snapshot.compareAndSet(prior, next)) {
                listeners.forEach(listener -> notify(listener, next));
                return;
            }
        }
    }

    /** Retains truthful progress when a provider fails instead of falsely resetting to zero. */
    public void fail(OrbisReadinessSystem system, OrbisReadinessStatus status, String detail) {
        OrbisReadinessRow row = snapshot().row(system);
        transition(system, row.displayName(), row.readinessPercent(), status, detail);
    }

    public AutoCloseable subscribe(Consumer<OrbisReadinessSnapshot> listener) {
        if (listener == null) return () -> { };
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private static OrbisReadinessSnapshot initialSnapshot() {
        return new OrbisReadinessSnapshot(0, Instant.now(),
                Arrays.stream(OrbisReadinessSystem.values())
                        .map(OrbisReadinessRow::initial).toList());
    }

    private static void notify(Consumer<OrbisReadinessSnapshot> listener,
            OrbisReadinessSnapshot value) {
        try { listener.accept(value); } catch (RuntimeException ignored) { }
    }

    @Override public void close() { listeners.clear(); }
}
