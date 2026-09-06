package com.inigmasgames.hytalerpg.combat.resource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Runtime-only conservative hostile-combat timestamps used by Home restoration. */
public final class HostileCombatTracker {
    private final LongSupplier nanoTime;
    private final Map<UUID, Long> lastHostile = new ConcurrentHashMap<>();
    public HostileCombatTracker(LongSupplier nanoTime) { this.nanoTime = nanoTime; }
    public void markHostile(UUID actor) { lastHostile.put(actor, nanoTime.getAsLong()); }
    public double secondsSinceHostile(UUID actor) {
        Long last = lastHostile.get(actor);
        return last == null ? Double.POSITIVE_INFINITY : Math.max(0.0, (nanoTime.getAsLong() - last) / 1_000_000_000.0);
    }
    public void clear(UUID actor) { lastHostile.remove(actor); }
}
