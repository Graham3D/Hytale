package com.inigmasgames.hytalerpg.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Idempotent ownership of bounded active execution state for an actor. */
public final class SkillInstanceLifecycle {
    private final Map<UUID, Active> active = new HashMap<>();
    public synchronized boolean begin(UUID actor, String instanceId, Phase phase) {
        return active.putIfAbsent(actor, new Active(instanceId, phase)) == null;
    }
    public synchronized boolean transition(UUID actor, String instanceId, Phase expected, Phase next) {
        Active current = active.get(actor);
        if (current == null || !current.instanceId.equals(instanceId) || current.phase != expected) return false;
        active.put(actor, new Active(instanceId, next)); return true;
    }
    public synchronized boolean terminate(UUID actor, String instanceId) {
        Active current = active.get(actor);
        if (current == null || !current.instanceId.equals(instanceId)) return false;
        active.remove(actor); return true;
    }
    public synchronized Optional<Active> cancel(UUID actor) { return Optional.ofNullable(active.remove(actor)); }
    public synchronized Optional<Active> active(UUID actor) { return Optional.ofNullable(active.get(actor)); }
    public enum Phase { WINDUP, COMMITTED, STRIKE_REPEAT, MOVEMENT, REACTION, PROJECTILE }
    public record Active(String instanceId, Phase phase) { }
}
