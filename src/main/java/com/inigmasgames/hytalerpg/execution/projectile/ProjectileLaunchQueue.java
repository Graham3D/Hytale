package com.inigmasgames.hytalerpg.execution.projectile;

import java.util.*;

/** Bounded native allocation schedule; every queued instance is already charged to the lifecycle registry. */
public final class ProjectileLaunchQueue<T> {
    private record Entry<T>(ProjectileInstance instance,T owner){}
    private final Map<String,Entry<T>> pending=new LinkedHashMap<>();
    public synchronized void add(ProjectileInstance instance,T owner) {
        var id=instance.plan().projectileInstanceId();
        if(pending.containsKey(id)||pending.size()>=ProjectileLifecycleRegistry.GLOBAL_CAP)
            throw new IllegalStateException("PROJECTILE_LAUNCH_QUEUE_BUDGET_OR_DUPLICATE");
        pending.put(id,new Entry<>(instance,Objects.requireNonNull(owner)));
    }
    public record Due<T>(ProjectileInstance instance,T owner){}
    public synchronized List<Due<T>> due(UUID actor,long now) {
        var due=pending.values().stream().filter(e->e.instance.plan().ownerId().equals(actor)&&e.instance.plan().spawnTimestampNanos()<=now)
                .sorted(Comparator.comparingLong((Entry<T> e)->e.instance.plan().spawnTimestampNanos()).thenComparing(e->e.instance.plan().projectileInstanceId())).toList();
        due.forEach(e->pending.remove(e.instance.plan().projectileInstanceId()));
        return due.stream().map(e->new Due<>(e.instance,e.owner)).toList();
    }
    public synchronized void cancel(UUID actor){pending.values().removeIf(e->e.instance.plan().ownerId().equals(actor));}
    public synchronized void cancel(ProjectileInstance instance){pending.remove(instance.plan().projectileInstanceId());}
    public synchronized int size(){return pending.size();}
}
