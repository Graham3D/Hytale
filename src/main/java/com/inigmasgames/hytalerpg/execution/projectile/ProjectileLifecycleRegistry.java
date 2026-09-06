package com.inigmasgames.hytalerpg.execution.projectile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded registry preventing duplicate projectile IDs and orphaned owner state. */
public final class ProjectileLifecycleRegistry {
    private final Map<String, ProjectileInstance> active = new LinkedHashMap<>();

    public synchronized void register(ProjectileInstance instance) {
        var plan = instance.plan();
        if (plan.generation() > 3 || plan.remainingSpawnedEffects() >= 48
                || plan.remainingTriggeredSecondaries() > 16)
            throw new IllegalStateException("Projectile recursion budget exceeds canonical bounds");
        long rootCount = active.values().stream()
                .filter(value -> value.plan().rootCastId().equals(plan.rootCastId())).count();
        if (rootCount >= 48 - plan.remainingSpawnedEffects())
            throw new IllegalStateException("Projectile root spawn budget exhausted");
        if (active.putIfAbsent(plan.projectileInstanceId(), instance) != null)
            throw new IllegalStateException("Duplicate projectile instance ID");
    }

    public synchronized Optional<ProjectileInstance> get(String projectileId) {
        return Optional.ofNullable(active.get(projectileId));
    }
    public synchronized boolean remove(ProjectileInstance instance) {
        return active.remove(instance.plan().projectileInstanceId(), instance);
    }
    public synchronized List<ProjectileInstance> ownedBy(UUID owner) {
        return active.values().stream().filter(value -> value.plan().ownerId().equals(owner)).toList();
    }
    public synchronized List<ProjectileInstance> removeOwnedBy(UUID owner) {
        List<ProjectileInstance> removed = new ArrayList<>();
        for (ProjectileInstance instance : new ArrayList<>(active.values())) {
            if (instance.plan().ownerId().equals(owner) && active.remove(instance.plan().projectileInstanceId(), instance))
                removed.add(instance);
        }
        return List.copyOf(removed);
    }
    public synchronized int size() { return active.size(); }
}
