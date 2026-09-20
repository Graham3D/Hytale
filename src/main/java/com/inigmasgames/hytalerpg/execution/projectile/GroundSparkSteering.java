package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Objects;
import java.util.SplittableRandom;

/** Replay-stable, independently seeded, piecewise-random ground steering for Spark. */
public final class GroundSparkSteering {
    private final SplittableRandom random;
    private Vec3 forward;
    private Vec3 direction;
    private double segmentRemaining;
    private int observedMotionRevision;

    public GroundSparkSteering(String projectileInstanceId, Vec3 launchDirection, int motionRevision) {
        if (projectileInstanceId == null || projectileInstanceId.isBlank())
            throw new IllegalArgumentException("SPARK_PROJECTILE_ID_REQUIRED");
        this.random = new SplittableRandom(Objects.hash(projectileInstanceId, "HYWIND_SPARK_GROUND_STEERING"));
        this.forward = horizontal(launchDirection);
        this.direction = forward;
        this.observedMotionRevision = motionRevision;
    }

    public Vec3 direction() {
        if (segmentRemaining <= 1.0e-9) chooseSegment();
        return direction;
    }

    public void consume(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("INVALID_SPARK_DISTANCE");
        segmentRemaining = Math.max(0, segmentRemaining - distance);
    }

    /** Continuation redirects (notably Ricochet) establish a new forward frame. */
    public void synchronize(Vec3 authoritativeDirection, int motionRevision) {
        if (motionRevision == observedMotionRevision) return;
        forward = horizontal(authoritativeDirection);
        direction = forward;
        segmentRemaining = 0;
        observedMotionRevision = motionRevision;
    }

    /** Marks a steering-authored direction write without treating it as an external redirect. */
    public void markOwnedRevision(int motionRevision) { observedMotionRevision = motionRevision; }

    public double segmentRemaining() { return segmentRemaining; }

    private void chooseSegment() {
        double magnitude;
        double roll = random.nextDouble();
        if (roll < .16) magnitude = 58 + random.nextDouble() * 20;       // occasional large kick
        else if (roll < .30) magnitude = 4 + random.nextDouble() * 15;   // brief near-forward dart
        else magnitude = 20 + random.nextDouble() * 36;                  // constant irregular crawl
        double yaw = random.nextBoolean() ? magnitude : -magnitude;
        direction = ProjectileContinuation.yaw(forward, yaw).horizontalNormalized();
        segmentRemaining = .12 + random.nextDouble() * .34;
    }

    private static Vec3 horizontal(Vec3 value) {
        if (value == null || value.horizontalLengthSquared() < 1.0e-12)
            throw new IllegalArgumentException("SPARK_HORIZONTAL_DIRECTION_REQUIRED");
        return value.horizontalNormalized();
    }
}
