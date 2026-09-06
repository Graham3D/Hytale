package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Deterministic path-length/lifetime guard around a native projectile carrier. */
public final class ProjectileFlight {
    private final double maxDistance;
    private final double maxLifetimeSeconds;
    private Vec3 lastPosition;
    private double travelled;
    private double elapsed;

    public ProjectileFlight(Vec3 origin, double speed, double maxDistance) {
        if (origin == null || speed <= 0.0 || maxDistance <= 0.0)
            throw new IllegalArgumentException("Projectile flight requires positive speed and distance");
        this.lastPosition = origin;
        this.maxDistance = maxDistance;
        this.maxLifetimeSeconds = maxDistance / speed;
    }

    public Observation observe(double deltaSeconds, Vec3 position) {
        if (deltaSeconds < 0.0 || position == null) throw new IllegalArgumentException("Invalid projectile observation");
        elapsed += deltaSeconds;
        travelled += Math.sqrt(position.distanceSquared(lastPosition));
        lastPosition = position;
        boolean expired = travelled + 1.0e-6 >= maxDistance || elapsed + 1.0e-6 >= maxLifetimeSeconds;
        return new Observation(position, travelled, elapsed, maxDistance, maxLifetimeSeconds, expired);
    }

    public Vec3 lastPosition() { return lastPosition; }
    public double travelled() { return travelled; }
    public double elapsed() { return elapsed; }

    public record Observation(Vec3 position, double travelled, double elapsed,
                              double maxDistance, double maxLifetimeSeconds, boolean expired) { }
}
