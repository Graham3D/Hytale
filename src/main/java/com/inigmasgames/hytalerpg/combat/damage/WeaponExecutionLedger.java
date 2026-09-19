package com.inigmasgames.hytalerpg.combat.damage;

import java.util.*;

/** Root-owned bounded identity ledger. No time-window inference, eviction/replay, or global ECS cache. */
public final class WeaponExecutionLedger {
    public static final int MAX_EXECUTIONS = 256;
    private final UUID actor;
    private final String root;
    private UUID world;
    private final Map<WeaponDamageExecution.Identity, WeaponFireDecision> executions = new HashMap<>();
    private boolean closed;
    public WeaponExecutionLedger(UUID actor, String root) {
        this.actor = Objects.requireNonNull(actor); this.root = Objects.requireNonNull(root);
    }
    public synchronized WeaponFireDecision acquire(WeaponDamageExecution execution) {
        if (closed) throw new IllegalStateException("WEAPON_ROOT_CLOSED");
        var id = execution.identity();
        if (!actor.equals(id.actorId()) || !root.equals(id.rootId()) || world != null && !world.equals(id.worldId()))
            throw new IllegalArgumentException("FOREIGN_WEAPON_EXECUTION_ROOT");
        var existing = executions.get(id);
        if (existing != null) {
            if (!existing.execution().equals(execution)) throw new IllegalArgumentException("WEAPON_SOURCE_CHANGED_WITHIN_EXECUTION");
            return existing;
        }
        if (executions.size() >= MAX_EXECUTIONS) throw new IllegalStateException("WEAPON_EXECUTION_BUDGET");
        world = id.worldId();
        var decision = new WeaponFireDecision(execution); executions.put(id, decision); return decision;
    }
    /** The source evaluator is owned here, before victim delivery, and is never called on reuse. */
    public synchronized WeaponFireDecision produce(WeaponDamageExecution.Identity id,
            java.util.function.Supplier<WeaponDamageExecution> producer) {
        if (closed) throw new IllegalStateException("WEAPON_ROOT_CLOSED");
        if (!actor.equals(id.actorId()) || !root.equals(id.rootId()) || world != null && !world.equals(id.worldId()))
            throw new IllegalArgumentException("FOREIGN_WEAPON_EXECUTION_ROOT");
        var existing = executions.get(id);
        if (existing != null) return existing;
        if (executions.size() >= MAX_EXECUTIONS) throw new IllegalStateException("WEAPON_EXECUTION_BUDGET");
        var result = Objects.requireNonNull(producer.get());
        if (!id.equals(result.identity())) throw new IllegalArgumentException("PRODUCER_IDENTITY_CHANGED");
        return acquire(result);
    }
    public synchronized void close() {
        closed = true; executions.values().forEach(WeaponFireDecision::close); executions.clear();
    }
}
