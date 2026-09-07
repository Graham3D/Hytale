package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Mutable lifecycle state for one native carrier; all authored inputs remain in its immutable plan. */
public final class ProjectileInstance {
    private final ProjectileExecutionPlan plan;
    private ProjectileFlight flight;
    private final Set<String> hitTargets = new LinkedHashSet<>();
    private final Set<String> returnTargets = new LinkedHashSet<>();
    private final java.util.Map<String,Integer> budgets;
    private Vec3 direction;
    private boolean returning;
    private double completedDistance,completedSeconds,returnDistance,originalLifetime;
    private int motionRevision;
    private long nativeClockNanos;
    private double lastBounceSeconds=Double.NEGATIVE_INFINITY;
    private final ProjectileHoming homing=new ProjectileHoming();
    private Termination termination;

    public ProjectileInstance(ProjectileExecutionPlan plan) {
        this.plan = plan;
        this.flight = new ProjectileFlight(plan.origin(), plan.velocity().length(), plan.maxDistance(),plan.maxLifetimeSeconds());
        this.budgets=new java.util.HashMap<>(plan.remainingContinuationBudgets());
        this.direction=plan.velocity().normalized();this.returnDistance=plan.maxDistance();
        this.originalLifetime=plan.maxLifetimeSeconds();
        this.nativeClockNanos=plan.spawnTimestampNanos();
    }

    public synchronized boolean acceptTarget(String targetId) {
        if (termination != null || targetId == null || targetId.isBlank()) return false;
        var ledger=returning?returnTargets:hitTargets;
        return ledger.size()<256 && ledger.add(targetId);
    }

    public synchronized boolean previouslyHit(String targetId) { return (returning?returnTargets:hitTargets).contains(targetId); }
    public synchronized boolean spend(String kind) {
        int count=budgets.getOrDefault(kind,0);if(count<=0)return false;budgets.put(kind,count-1);return true;
    }
    public synchronized int remaining(String kind) { return budgets.getOrDefault(kind,0); }
    public synchronized java.util.Map<String,Integer> budgets() { return java.util.Map.copyOf(budgets); }
    public synchronized boolean returning() { return returning; }
    public synchronized Vec3 direction() { return direction; }
    public synchronized void redirect(Vec3 direction) { this.direction=direction.normalized();motionRevision++; }
    public synchronized int motionRevision() { return motionRevision; }
    public ProjectileHoming homing(){return homing;}
    public synchronized boolean bounce(Vec3 normal) {
        if(returning||normal.lengthSquared()<1e-12||totalSeconds()-lastBounceSeconds<.05-1e-9||remaining("RICOCHET")<=0)return false;
        normal=normal.normalized();double dot=direction.x()*normal.x()+direction.y()*normal.y()+direction.z()*normal.z();
        if(dot>=-1e-9)return false;
        spend("RICOCHET");lastBounceSeconds=totalSeconds();redirect(direction.subtract(normal.multiply(2*dot)));return true;
    }
    public synchronized boolean bounceIntervalReady(){return totalSeconds()-lastBounceSeconds>=.05-1e-9;}
    public synchronized double remainingDistance() { return flight.remainingDistance(); }
    public synchronized double originalMaxDistance() { return returnDistance; }
    public synchronized double originalMaxLifetimeSeconds() { return originalLifetime; }
    public synchronized double remainingSeconds() { return flight.remainingSeconds(); }
    public synchronized double totalDistance() { return completedDistance+flight.travelled(); }
    public synchronized double totalSeconds() { return completedSeconds+flight.elapsed(); }
    public synchronized void inheritVisited(ProjectileInstance parent) {
        hitTargets.addAll(parent.hitTargets());returnDistance=parent.returnDistance;
        originalLifetime=parent.originalLifetime;
        completedDistance=parent.totalDistance();completedSeconds=parent.totalSeconds();
        lastBounceSeconds=parent.lastBounceSeconds;
    }
    public synchronized boolean beginReturn(Vec3 position,Vec3 caster) {
        if(returning||!spend("RETURN")||position.distanceSquared(caster)<=.25)return false;
        completedDistance+=flight.travelled();completedSeconds+=flight.elapsed();returning=true;
        flight=new ProjectileFlight(position,plan.velocity().length(),returnDistance,returnDistance/plan.velocity().length()+.1);
        direction=caster.subtract(position).normalized();motionRevision++;return true;
    }
    public synchronized ProjectileFlight.Observation observe(double seconds, Vec3 position) {
        return flight.observe(seconds, position);
    }
    /** Native callbacks and player ticks share one clock; redirects cannot skip or double-charge elapsed time. */
    public synchronized ProjectileFlight.Observation sampleNativeClock(long now) {
        long elapsed=now-nativeClockNanos;
        if(elapsed<0)return flight.observe(0,flight.lastPosition());
        nativeClockNanos=now;
        return flight.observe(elapsed/1e9,flight.lastPosition());
    }
    public synchronized boolean terminate(String reason, Vec3 position) {
        if (termination != null) return false;
        termination = new Termination(reason, position == null ? flight.lastPosition() : position,
                totalDistance(), totalSeconds());
        return true;
    }

    public ProjectileExecutionPlan plan() { return plan; }
    public ProjectileFlight flight() { return flight; }
    public synchronized Optional<Termination> termination() { return Optional.ofNullable(termination); }
    public synchronized Set<String> hitTargets() { return Set.copyOf(hitTargets); }

    public record Termination(String reason, Vec3 position, double travelledDistance, double elapsedSeconds) { }
}
