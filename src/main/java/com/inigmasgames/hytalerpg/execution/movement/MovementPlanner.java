package com.inigmasgames.hytalerpg.execution.movement;

import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Pure movement trajectory math. Native collision supplies the permitted fraction for each segment. */
public final class MovementPlanner {
    public Plan plan(Vec3 origin, Vec3 requestedDirection, double requestedDistance,
                     Stage04SkillProfile.Movement profile, CollisionProbe collision) {
        if(!Double.isFinite(requestedDistance)||requestedDistance<0)throw new IllegalArgumentException("INVALID_MOVEMENT_DISTANCE");
        Vec3 direction = requestedDirection.horizontalNormalized();
        double distance = Math.max(0.0, Math.min(profile.maxDistance(), requestedDistance));
        Vec3 requested = direction.multiply(distance);
        double fraction = fraction(collision.permittedFraction(origin, requested));
        Vec3 applied = requested.multiply(fraction);
        double duration = profile.details().travelSpeed()>0?clamp(applied.horizontalLength()/profile.details().travelSpeed(),profile.minimumDurationSeconds(),profile.maximumDurationSeconds())
                :profile.kind() == Stage04SkillProfile.MovementKind.DASH
                ? profile.maximumDurationSeconds()
                : clamp(distance / 16.0, profile.minimumDurationSeconds(), profile.maximumDurationSeconds());
        return new Plan(origin, origin.add(applied), requested, applied, duration, fraction < 1.0 - 1.0e-9,
                profile.kind(), profile.apexHeight());
    }
    /** Ground-targeted leaps retain endpoint elevation and sweep the arc, not a straight chord through a hill. */
    public Plan groundPlan(Vec3 origin,Vec3 destination,Stage04SkillProfile.Movement profile,CollisionProbe collision,
                           java.util.function.Predicate<Vec3> supported) {
        Vec3 delta=destination.subtract(origin);double distance=delta.horizontalLength();
        if(!profile.details().groundTarget()||distance<=1e-6||distance>profile.maxDistance()+1e-6||!supported.test(destination))
            throw new IllegalArgumentException("NO_VALID_LOADED_MOVEMENT_GROUND");
        double speed=profile.details().travelSpeed()>0?profile.details().travelSpeed():16;
        var result=new Plan(origin,destination,delta,delta,clamp(distance/speed,profile.minimumDurationSeconds(),profile.maximumDurationSeconds()),false,profile.kind(),profile.apexHeight());
        Vec3 prior=origin;
        for(Vec3 point:segments(result,0,1)){
            if(fraction(collision.permittedFraction(prior,point.subtract(prior)))<1-1e-6)throw new IllegalArgumentException("MOVEMENT_ARC_BLOCKED");
            prior=point;
        }
        return result;
    }
    /** <=0.25m chord steps using a conservative arc-length bound; no frame-dependent chord shortcut. */
    public java.util.List<Vec3> segments(Plan plan,double fromProgress,double toProgress) {
        if(!Double.isFinite(fromProgress)||!Double.isFinite(toProgress)||fromProgress<0||toProgress>1||toProgress<fromProgress)
            throw new IllegalArgumentException("INVALID_MOVEMENT_PROGRESS");
        double upper=plan.appliedDisplacement().length()+4*plan.apexHeight();
        int count=Math.max(1,(int)Math.ceil(upper*(toProgress-fromProgress)/.25));
        if(count>256)throw new IllegalArgumentException("MOVEMENT_SEGMENT_BUDGET");
        var result=new java.util.ArrayList<Vec3>(count);
        for(int i=1;i<=count;i++)result.add(sample(plan,fromProgress+(toProgress-fromProgress)*i/count));
        return java.util.List.copyOf(result);
    }
    private static double fraction(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("MOVEMENT_COLLISION_UNAVAILABLE");return value;}

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
