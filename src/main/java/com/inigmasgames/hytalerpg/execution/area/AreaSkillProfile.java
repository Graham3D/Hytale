package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Numeric authoring contract. Travel/collision radius is intentionally not an area field. */
public record AreaSkillProfile(AreaGeometry.Kind geometry, double radius, double angleDegrees,
        double length, double width, double height, double placementRange, double lifetimeSeconds,
        double armingSeconds, double warningSeconds, double intervalSeconds, int impactCount,
        double firstImpactSeconds, double impactRadius, int perTargetHitCap, double targetIntervalSeconds,
        double coefficient, double innerRadius, double innerCoefficient, double edgeFalloff,
        String status, double statusSeconds, int chillStacks, int innerChillStacks,
        double statusInnerRadius, double statusInnerSeconds, double displacement, double pullCoreRadius,
        boolean firstTargetOnly, boolean stratified, double finalCoefficient, double finalPull,
        String element, int candidateBudget, boolean periodic, double statusIntervalSeconds,
        double overheadHeight, double descentSeconds, boolean alternatingIceStone,
        double pullSpeed, double visualCoreRadius) {
    public AreaSkillProfile {
        if (geometry == null || element == null || status == null || impactCount < 0 || impactCount > 48
                || perTargetHitCap < 1 || (candidateBudget != 64 && candidateBudget != 256))
            throw new IllegalArgumentException("Invalid area profile");
        for (double value : new double[]{radius, angleDegrees, length, width, height, placementRange,
                lifetimeSeconds, armingSeconds, warningSeconds, intervalSeconds, firstImpactSeconds,
                impactRadius, targetIntervalSeconds, coefficient, innerRadius, innerCoefficient,
                edgeFalloff, statusSeconds, statusInnerRadius, statusInnerSeconds, displacement,
                pullCoreRadius, finalCoefficient, finalPull, statusIntervalSeconds,
                overheadHeight, descentSeconds, pullSpeed, visualCoreRadius})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid area numeric value");
        if (height <= 0 || edgeFalloff > 1 || (impactCount > 1 && intervalSeconds <= 0))
            throw new IllegalArgumentException("Invalid area timing/height/falloff");
        if (periodic && (lifetimeSeconds <= 0 || intervalSeconds <= 0 || impactCount != 0))
            throw new IllegalArgumentException("Periodic areas require finite duration and time integration, not discrete impacts");
        if (stratified && (impactRadius <= 0 || impactRadius > radius || impactCount < 1))
            throw new IllegalArgumentException("Invalid stratified impact/warning profile");
        if (overheadHeight > 0 && (descentSeconds <= 0 || descentSeconds > warningSeconds))
            throw new IllegalArgumentException("Overhead descent must fit its warning");
        if (finalCoefficient > 0 && (impactCount <= 1 || lifetimeSeconds <= firstImpactSeconds + (impactCount - 1) * intervalSeconds))
            throw new IllegalArgumentException("Final blast must follow the sub-impacts");
    }
    public AreaGeometry footprint(Vec3 origin, Vec3 direction, double radiusFactor) {
        return new AreaGeometry(geometry, origin, direction, radius * radiusFactor, angleDegrees, length, width, height);
    }
    public boolean trap() { return armingSeconds > 0; }
    public boolean persistent() { return lifetimeSeconds > 0 || warningSeconds > 0; }
}
