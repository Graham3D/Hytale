package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Important-event/cooldown/budget gate. It schedules no continuous thought loop. */
public final class AutonomyGate {
    private final int perMinuteBudget;
    private final int npcCooldownSeconds;
    private final ArrayDeque<Instant> global = new ArrayDeque<>();
    private final Map<UUID, Instant> npcLastRequest = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.LongAdder claims =
            new java.util.concurrent.atomic.LongAdder();

    public AutonomyGate(int perMinuteBudget, int npcCooldownSeconds) {
        this.perMinuteBudget = Math.max(0, perMinuteBudget);
        this.npcCooldownSeconds = Math.max(1, npcCooldownSeconds);
    }

    public synchronized boolean claim(NpcFrameworkEvent event, boolean important) {
        if (!important || event.npcId() == null || perMinuteBudget == 0) {
            return false;
        }
        Instant now = event.occurredAt();
        Instant prior = npcLastRequest.get(event.npcId());
        if (prior != null && now.isBefore(prior.plusSeconds(npcCooldownSeconds))) {
            return false;
        }
        while (!global.isEmpty() && global.peekFirst().isBefore(now.minusSeconds(60))) {
            global.removeFirst();
        }
        if (global.size() >= perMinuteBudget) {
            return false;
        }
        global.addLast(now);
        npcLastRequest.put(event.npcId(), now);
        claims.increment();
        return true;
    }

    public long claimCount() {
        return claims.sum();
    }
}
