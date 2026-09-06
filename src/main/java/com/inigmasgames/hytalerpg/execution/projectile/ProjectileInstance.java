package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Mutable lifecycle state for one native carrier; all authored inputs remain in its immutable plan. */
public final class ProjectileInstance {
    private final ProjectileExecutionPlan plan;
    private final ProjectileFlight flight;
    private final Set<String> hitTargets = new LinkedHashSet<>();
    private Termination termination;

    public ProjectileInstance(ProjectileExecutionPlan plan) {
        this.plan = plan;
        this.flight = new ProjectileFlight(plan.origin(), plan.velocity().length(), plan.maxDistance());
    }

    public synchronized boolean acceptTarget(String targetId) {
        if (termination != null || targetId == null || targetId.isBlank()) return false;
        return hitTargets.add(targetId);
    }

    public synchronized boolean previouslyHit(String targetId) { return hitTargets.contains(targetId); }
    public synchronized ProjectileFlight.Observation observe(double seconds, Vec3 position) {
        return flight.observe(seconds, position);
    }
    public synchronized boolean terminate(String reason, Vec3 position) {
        if (termination != null) return false;
        termination = new Termination(reason, position == null ? flight.lastPosition() : position,
                flight.travelled(), flight.elapsed());
        return true;
    }

    public ProjectileExecutionPlan plan() { return plan; }
    public ProjectileFlight flight() { return flight; }
    public synchronized Optional<Termination> termination() { return Optional.ofNullable(termination); }
    public synchronized Set<String> hitTargets() { return Set.copyOf(hitTargets); }

    public record Termination(String reason, Vec3 position, double travelledDistance, double elapsedSeconds) { }
}
