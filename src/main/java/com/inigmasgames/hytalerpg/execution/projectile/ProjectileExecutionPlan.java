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
        double maxLifetimeSeconds,
        ProjectileMotion motion) {

    /** Compatibility constructor for existing linear-projectile fixtures and secondary producers. */
    public ProjectileExecutionPlan(String rootCastId,String skillInstanceId,String projectileInstanceId,UUID ownerId,
            String skillId,String compiledPlanHash,CombatSnapshot snapshot,int generation,Map<String,Integer> remainingContinuationBudgets,
            int remainingSpawnedEffects,int remainingTriggeredSecondaries,long spawnTimestampNanos,String configId,Vec3 origin,Vec3 velocity,
            double radius,double maxDistance,double maxLifetimeSeconds) {
        this(rootCastId,skillInstanceId,projectileInstanceId,ownerId,skillId,compiledPlanHash,snapshot,generation,remainingContinuationBudgets,
                remainingSpawnedEffects,remainingTriggeredSecondaries,spawnTimestampNanos,configId,origin,velocity,radius,maxDistance,maxLifetimeSeconds,
                ProjectileMotion.LINEAR);
    }

    public ProjectileExecutionPlan {
        if (rootCastId == null || rootCastId.isBlank() || skillInstanceId == null || skillInstanceId.isBlank()
                || projectileInstanceId == null || projectileInstanceId.isBlank() || ownerId == null
                || skillId == null || skillId.isBlank() || compiledPlanHash == null || compiledPlanHash.isBlank()
                || snapshot == null || generation < 0 || remainingContinuationBudgets == null
                || remainingContinuationBudgets.values().stream().anyMatch(value -> value == null || value < 0)
                || remainingSpawnedEffects < 0
                || remainingTriggeredSecondaries < 0
                || configId == null || configId.isBlank() || origin == null || velocity == null
                || velocity.lengthSquared() < 1.0e-12 || !Double.isFinite(velocity.lengthSquared())
                || !Double.isFinite(radius) || !Double.isFinite(maxDistance) || !Double.isFinite(maxLifetimeSeconds)
                || radius <= 0.0 || maxDistance <= 0.0
                || maxLifetimeSeconds <= 0.0 || motion==null)
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
        var modifiers=context.compiledPlan().projectileModifiers();
        if(modifiers.ballistics()&&projectile.gravity()!=0)throw new IllegalStateException("BALLISTICS_GRAVITY_UNSUPPORTED");
        speed*=modifiers.speedFactor();double distance=projectile.maxDistance()*modifiers.distanceFactor();
        ProjectileMotion motion=projectile.details().motion();
        Vec3 velocity=direction.normalized().multiply(speed);double lifetime=projectile.capLifetime(distance/speed);
        if(motion.timedBallistic()) {
            double factor=modifiers.speedFactor();
            Vec3 target=context.target()==null?origin.add(direction.normalized().multiply(distance)):context.target().point();
            if(target.subtract(origin).horizontalLength()<1e-6)target=origin.add(direction.normalized().multiply(distance));
            var solution=ProjectileBallistics.timed(origin,target,motion.horizontalSpeed()*factor,motion.gravity(),distance,
                    motion.minimumTravelSeconds()/factor,motion.maximumTravelSeconds()/factor).orElseThrow(
                            ()->new IllegalStateException("TIMED_BALLISTIC_TARGET_UNREACHABLE"));
            velocity=solution.velocity();distance=solution.pathLength();
            lifetime=solution.flightSeconds()+motion.safetyLifetimeSeconds();
            if(projectile.independentLifetimeSeconds()>0)lifetime=Math.min(lifetime,projectile.independentLifetimeSeconds());
        } else if(projectile.details().pattern().ballisticAim()) {
            double seconds=projectile.independentLifetimeSeconds();
            distance=speed*seconds+.5*projectile.gravity()*seconds*seconds;
        }
        return new ProjectileExecutionPlan(context.rootCastId(), context.skillInstanceId(),
                context.skillInstanceId() + (context.barrageBatch()>0?"-batch-"+context.barrageBatch():"")+"-projectile-0", owner, context.profile().skillId(),
                context.compiledPlan().planHash(), context.snapshot(), context.derivedRelease()?1:0,
                Map.of("SHRAPNEL",modifiers.shrapnel()?1:0,"SPLINTERBURST",modifiers.splinterburst()?1:0,
                        "PIERCE", modifiers.pierce(), "FORK", modifiers.fork(), "CHAIN", modifiers.chain(),
                        "RICOCHET", modifiers.ricochet(), "RETURN", modifiers.returning(),
                        "ROOT_LAUNCHES",projectile.details().pattern().selectedRootLaunches(context.compiledPlan(),context.rootCastId()),"IS_LAUNCH",1),
                // Echo is the second authorized release of this root, not another first release.
                // The retained registry validates the declared ordinal against existing root carriers.
                budgets.maxSpawnedEffects() - (context.echo() ? 2 : 1), budgets.maxTriggeredSecondaries(),
                spawnTimestampNanos, configId, origin, velocity,
                projectile.radius(),distance,lifetime,motion);
    }
    /** One original batch expands into the complete symmetric Volley before native allocation. */
    public static java.util.List<ProjectileExecutionPlan> launchBatch(SkillExecutionContext context,UUID owner,Vec3 origin,
            Vec3 direction,String configId,double speed,long now) {
        var center=generationZero(context,owner,origin,direction,configId,speed,now);
        var pattern=context.profile().projectile().details().pattern();
        int authoredCount=pattern.selectedCount(context.rootCastId());
        int volley=context.compiledPlan().projectileModifiers().batchSize();
        if(volley==1&&authoredCount==1)return java.util.List.of(center);
        var batch=new java.util.ArrayList<ProjectileExecutionPlan>();
        for(int authored=0;authored<authoredCount;authored++)for(int index=0;index<volley;index++)batch.add(new ProjectileExecutionPlan(center.rootCastId(),center.skillInstanceId(),
                context.skillInstanceId()+"-batch-"+context.barrageBatch()+"-projectile-"+(authored*volley+index),owner,center.skillId(),center.compiledPlanHash(),
                center.snapshot(),center.generation(),center.remainingContinuationBudgets(),center.remainingSpawnedEffects(),center.remainingTriggeredSecondaries(),
                now+Math.round(authored*pattern.intervalSeconds()*1e9),configId,origin,
                ProjectileContinuation.yaw(context.profile().skillId().equals("charged_bolt")
                        ?pattern.randomizedDirection(direction.horizontalNormalized(),authored,authoredCount,context.rootCastId()).horizontalNormalized()
                        :pattern.direction(direction,authored,authoredCount),volley==3?(index-1)*12:0).multiply(center.velocity().length()),
                center.radius(),center.maxDistance(),center.maxLifetimeSeconds(),center.motion()));
        return java.util.List.copyOf(batch);
    }
}
