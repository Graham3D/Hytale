package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.UUID;
import java.util.Map;

/** Immutable generation-zero projectile state captured at the skill commit boundary. */
public record ProjectileExecutionPlan(
        String rootCastId,
        String skillInstanceId,
        String projectileInstanceId,
        UUID ownerId,
        String skillId,
        String compiledPlanHash,
        CombatSnapshot snapshot,
        int generation,
        Map<String, Integer> remainingContinuationBudgets,
        int remainingSpawnedEffects,
        int remainingTriggeredSecondaries,
        long spawnTimestampNanos,
        String configId,
        Vec3 origin,
        Vec3 velocity,
        double radius,
        double maxDistance,
        double maxLifetimeSeconds) {

    public ProjectileExecutionPlan {
        if (rootCastId == null || rootCastId.isBlank() || skillInstanceId == null || skillInstanceId.isBlank()
                || projectileInstanceId == null || projectileInstanceId.isBlank() || ownerId == null
                || skillId == null || skillId.isBlank() || compiledPlanHash == null || compiledPlanHash.isBlank()
                || snapshot == null || generation < 0 || remainingContinuationBudgets == null
                || remainingContinuationBudgets.values().stream().anyMatch(value -> value == null || value < 0)
                || remainingSpawnedEffects < 0
                || remainingTriggeredSecondaries < 0 || spawnTimestampNanos < 0
                || configId == null || configId.isBlank() || origin == null || velocity == null
                || velocity.lengthSquared() < 1.0e-12 || radius <= 0.0 || maxDistance <= 0.0
                || maxLifetimeSeconds <= 0.0)
            throw new IllegalArgumentException("Incomplete projectile execution plan");
        remainingContinuationBudgets = Map.copyOf(remainingContinuationBudgets);
    }

    public static ProjectileExecutionPlan generationZero(SkillExecutionContext context, UUID owner,
                                                          Vec3 origin, Vec3 direction, String configId,
                                                          double speed, long spawnTimestampNanos) {
        CompiledSkillPlan.SafetyBudgets budgets = context.compiledPlan().safetyBudgets();
        if (budgets.maxGeneration() < 0 || budgets.maxSpawnedEffects() < 1
                || budgets.maxTriggeredSecondaries() < 0)
            throw new IllegalStateException("Compiled projectile safety budget rejects generation zero");
        var projectile = context.profile().projectile();
        return new ProjectileExecutionPlan(context.rootCastId(), context.skillInstanceId(),
                context.skillInstanceId() + "-projectile-0", owner, context.profile().skillId(),
                context.compiledPlan().planHash(), context.snapshot(), 0,
                Map.of("SPLIT", 0, "PIERCE", 0, "FORK", 0, "CHAIN", 0, "RICOCHET", 0, "RETURN", 0),
                budgets.maxSpawnedEffects() - 1, budgets.maxTriggeredSecondaries(),
                spawnTimestampNanos, configId, origin, direction.normalized().multiply(speed),
                projectile.radius(), projectile.maxDistance(), projectile.maxDistance() / speed);
    }
}
