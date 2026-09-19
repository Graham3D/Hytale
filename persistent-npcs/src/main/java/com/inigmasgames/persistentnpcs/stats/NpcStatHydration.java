package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import java.util.*;
import java.util.function.Consumer;

/** Native indexed reads/writes shared by production lifecycle and SDK-backed regression tests. */
public final class NpcStatHydration {
    private NpcStatHydration() { }
    public record Marker(UUID stableId, UUID entityId, long repositoryRevision) { }
    public static Map<String, NpcStatSample> sample(EntityStatMap map) {
        if (map == null) return Map.of();
        var values = new TreeMap<String, NpcStatSample>();
        for (String id : VanillaNpcStats.IDS) {
            int index = VanillaNpcStats.index(id);
            if (index < 0) continue;
            try {
                var stat = map.get(index);
                if (stat != null && id.equals(stat.getId()))
                    values.put(id, new NpcStatSample(stat.get(), stat.getMin(), stat.getMax()));
            } catch (RuntimeException unavailable) { /* Invalid native values are unavailable, never fabricated. */ }
        }
        return Map.copyOf(values);
    }
    public static boolean differs(NpcStatState state, Map<String, NpcStatSample> live) {
        return state.stats().entrySet().stream().anyMatch(e -> live.containsKey(e.getKey())
                && Double.compare(e.getValue().current(), live.get(e.getKey()).current()) != 0);
    }
    public static Marker applyOnce(NpcStatState state, UUID entityId, EntityStatMap map,
            Marker existing, Consumer<String> log) {
        // Revision changes from checkpoints must NEVER cause a second hydration of this entity.
        if (existing != null) {
            if (!existing.stableId().equals(state.stableNpcId()) || !existing.entityId().equals(entityId))
                throw new IllegalStateException("Stale hydration identity");
            return existing;
        }
        if (map == null) throw new IllegalStateException("Native EntityStatMap not ready");
        var live = sample(map);
        for (String id : VanillaNpcStats.IDS) {
            var saved = state.stats().get(id);
            var actual = live.get(id);
            if (saved == null || actual == null) continue;
            double clamped = Math.clamp(saved.current(), actual.minimum(), actual.maximum());
            map.setStatValue(EntityStatMap.Predictable.NONE, VanillaNpcStats.index(id), (float) clamped);
            log.accept("NPC_STATS_HYDRATED stableId=" + state.stableNpcId() + " entity=" + entityId
                    + " stat=" + id + " saved=" + saved.current() + " applied=" + clamped
                    + " effectiveMin=" + actual.minimum() + " effectiveMax=" + actual.maximum()
                    + " revision=" + state.revision());
        }
        return new Marker(state.stableNpcId(), entityId, state.revision());
    }
}
