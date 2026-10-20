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
        this(origin,speed,maxDistance,maxDistance/speed);
    }
    public ProjectileFlight(Vec3 origin,double speed,double maxDistance,double lifetimeSeconds) {
        if (origin == null || !Double.isFinite(speed)||!Double.isFinite(maxDistance)||!Double.isFinite(lifetimeSeconds)
                || speed <= 0.0 || maxDistance <= 0.0 || lifetimeSeconds<=0)
            throw new IllegalArgumentException("Projectile flight requires positive speed and distance");
        this.lastPosition = origin;
        this.maxDistance = maxDistance;
        this.maxLifetimeSeconds = lifetimeSeconds;
    }

    public Observation observe(double deltaSeconds, Vec3 position) {
        if (!Double.isFinite(deltaSeconds)||deltaSeconds < 0.0 || position == null) throw new IllegalArgumentException("Invalid projectile observation");
        elapsed += deltaSeconds;
        travelled += Math.sqrt(position.distanceSquared(lastPosition));
        lastPosition = position;
        boolean expired = travelled + 1.0e-6 >= maxDistance || elapsed + 1.0e-6 >= maxLifetimeSeconds;
        return new Observation(position, travelled, elapsed, maxDistance, maxLifetimeSeconds, expired);
    }

    public Vec3 lastPosition() { return lastPosition; }
    public double travelled() { return travelled; }
    public double elapsed() { return elapsed; }
    public double remainingDistance() { return Math.max(0,maxDistance-travelled); }
    public double remainingSeconds() { return Math.max(0,maxLifetimeSeconds-elapsed); }

    public record Observation(Vec3 position, double travelled, double elapsed,
                              double maxDistance, double maxLifetimeSeconds, boolean expired) { }
}
