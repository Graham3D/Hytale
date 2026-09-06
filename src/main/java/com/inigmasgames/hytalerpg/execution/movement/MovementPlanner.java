package com.inigmasgames.hytalerpg.execution.movement;

import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Pure movement trajectory math. Native collision supplies the permitted fraction for each segment. */
public final class MovementPlanner {
    public Plan plan(Vec3 origin, Vec3 requestedDirection, double requestedDistance,
                     Stage04SkillProfile.Movement profile, CollisionProbe collision) {
        Vec3 direction = requestedDirection.horizontalNormalized();
        double distance = Math.max(0.0, Math.min(profile.maxDistance(), requestedDistance));
        Vec3 requested = direction.multiply(distance);
        double fraction = Math.max(0.0, Math.min(1.0, collision.permittedFraction(origin, requested)));
        Vec3 applied = requested.multiply(fraction);
        double duration = profile.kind() == Stage04SkillProfile.MovementKind.DASH
                ? profile.maximumDurationSeconds()
                : clamp(distance / 16.0, profile.minimumDurationSeconds(), profile.maximumDurationSeconds());
        return new Plan(origin, origin.add(applied), requested, applied, duration, fraction < 1.0 - 1.0e-9,
                profile.kind(), profile.apexHeight());
    }

    public Vec3 sample(Plan plan, double progress) {
        double t = clamp(progress, 0.0, 1.0);
        Vec3 base = plan.origin().add(plan.appliedDisplacement().multiply(t));
        if (plan.kind() != Stage04SkillProfile.MovementKind.LEAP) return base;
        return new Vec3(base.x(), base.y() + 4.0 * plan.apexHeight() * t * (1.0 - t), base.z());
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    @FunctionalInterface public interface CollisionProbe { double permittedFraction(Vec3 origin, Vec3 displacement); }
    public record Plan(Vec3 origin, Vec3 destination, Vec3 requestedDisplacement, Vec3 appliedDisplacement,
                       double durationSeconds, boolean clamped, Stage04SkillProfile.MovementKind kind,
                       double apexHeight) {
        public double appliedDistance() { return appliedDisplacement.horizontalLength(); }
    }
}
