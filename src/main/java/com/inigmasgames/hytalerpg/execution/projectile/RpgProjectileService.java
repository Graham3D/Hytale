package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import java.util.UUID;

/** Engine-neutral projectile planning, contact, termination, and owner-cleanup authority. */
public final class RpgProjectileService {
    private final ProjectileLifecycleRegistry registry;

    public RpgProjectileService(ProjectileLifecycleRegistry registry) { this.registry = registry; }

    public ProjectileExecutionPlan buildPlan(SkillExecutionContext context, UUID owner, Vec3 origin,
                                               Vec3 direction, String configId, double speed, long nowNanos) {
        return ProjectileExecutionPlan.generationZero(context, owner, origin, direction, configId, speed, nowNanos);
    }

    public ProjectileInstance onProjectileSpawn(ProjectileExecutionPlan plan) {
        ProjectileInstance instance = new ProjectileInstance(plan);
        registry.register(instance);
        return instance;
    }

    public boolean onEnemyContact(ProjectileInstance instance, String targetId) {
        return registry.get(instance.plan().projectileInstanceId()).filter(value -> value == instance).isPresent()
                && instance.acceptTarget(targetId);
    }

    public boolean onTerrainContact(ProjectileInstance instance, Vec3 position) {
        return terminate(instance, "TERRAIN_HIT", position);
    }

    /** Stage 07 owns continuation ordering; Stage 05 always forwards to terminal cleanup. */
    public boolean onForwardTermination(ProjectileInstance instance, String reason, Vec3 position) {
        return terminate(instance, reason, position);
    }

    public List<ProjectileInstance> cancelOwner(UUID owner, String reason) {
        List<ProjectileInstance> removed = registry.removeOwnedBy(owner);
        removed.forEach(instance -> instance.terminate(reason, instance.flight().lastPosition()));
        return removed;
    }

    private boolean terminate(ProjectileInstance instance, String reason, Vec3 position) {
        boolean removed = registry.remove(instance);
        return removed && instance.terminate(reason, position);
    }

    public ProjectileLifecycleRegistry registry() { return registry; }
}
