package com.inigmasgames.hytalerpg.execution.strike;

import java.util.HashSet;
import java.util.Set;

/** Per-instance, per-repeat hit dedup. A later authored repeat may hit the same target once again. */
public final class SkillHitLedger {
    private final Set<Key> hits = new HashSet<>();
    public synchronized boolean accept(String skillInstanceId, int hitIndex, String targetId) {
        return hits.add(new Key(skillInstanceId, hitIndex, targetId));
    }
    public synchronized void clear(String skillInstanceId) {
        hits.removeIf(key -> key.skillInstanceId.equals(skillInstanceId));
    }
    private record Key(String skillInstanceId, int hitIndex, String targetId) { }
}
