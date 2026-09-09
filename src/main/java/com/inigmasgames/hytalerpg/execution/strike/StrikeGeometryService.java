package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared deterministic selector for arcs, thrust lines, assist cones, and landing radii. */
public final class StrikeGeometryService {
    public static final int ORDINARY_QUERY_LIMIT = 64;
    public <T> QueryResult<T> query(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike,
                                    List<Candidate<T>> boundedCandidates) {
        if (boundedCandidates.size() > ORDINARY_QUERY_LIMIT)
            throw new IllegalStateException("STRIKE_QUERY_OVERFLOW");
        Vec3 direction = facing.horizontalNormalized();
        List<Decision<T>> decisions = new ArrayList<>();
        for (Candidate<T> candidate : boundedCandidates) {
            String rejection = reject(origin, direction, strike, candidate);
            decisions.add(new Decision<>(candidate, rejection == null, rejection == null ? "ACCEPTED" : rejection));
        }
        List<Candidate<T>> accepted = decisions.stream().filter(Decision::accepted).map(Decision::candidate)
                .sorted(Comparator.<Candidate<T>>comparingDouble(value -> value.position().distanceSquared(origin))
                        .thenComparing(Candidate::stableId)).toList();
        if (strike.geometry() == Stage04SkillProfile.Geometry.ASSIST_CONE && accepted.size() > 1)
            accepted = accepted.subList(0, 1);
        else if (accepted.size() > strike.targetCap())
            throw new IllegalStateException("STRIKE_TARGET_CAP_OVERFLOW");
        var selected = accepted.stream().map(Candidate::stableId).collect(java.util.stream.Collectors.toSet());
        decisions.replaceAll(value -> value.accepted() && !selected.contains(value.candidate().stableId())
                ? new Decision<>(value.candidate(), false, "SINGLE_TARGET_NOT_SELECTED") : value);
        return new QueryResult<>(accepted, List.copyOf(decisions));
    }

    /** Full-height collision volume rooted at the actor's feet; line width is explicitly doubled from half-width. */
    public static boolean intersects(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike, AreaGeometry.Bounds bounds) {
        return footprints(origin, facing, strike).stream().anyMatch(shape -> shape.intersects(bounds));
    }
    public static List<AreaGeometry> footprints(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike) {
        var direction = facing.horizontalNormalized();
        double height = strike.details().height();
        if (strike.geometry() == Stage04SkillProfile.Geometry.LINE)
            return List.of(new AreaGeometry(AreaGeometry.Kind.RECTANGLE, origin.add(direction.multiply(strike.range() / 2)),
                    direction, 0, 0, strike.range(), strike.lineHalfWidth() * 2, height));
        if (strike.geometry() == Stage04SkillProfile.Geometry.RADIUS || strike.angleDegrees() == 360)
            return List.of(new AreaGeometry(AreaGeometry.Kind.DISC, origin, direction, strike.range(), 360, 0, 0, height));
        if (strike.angleDegrees() <= 180)
            return List.of(sector(origin, direction, strike.range(), Math.max(1e-9, strike.angleDegrees()), height));
        double offset = Math.toRadians(strike.angleDegrees() / 4);
        var parts = new ArrayList<AreaGeometry>(2);
        for (double sign : new double[] {-1, 1}) {
            var rotated = new Vec3(direction.x() * Math.cos(offset) + direction.z() * Math.sin(offset) * sign, 0,
                    direction.z() * Math.cos(offset) - direction.x() * Math.sin(offset) * sign);
            parts.add(sector(origin, rotated, strike.range(), strike.angleDegrees() / 2, height));
        }
        return List.copyOf(parts);
    }
    private static AreaGeometry sector(Vec3 origin, Vec3 facing, double range, double angle, double height) {
        return new AreaGeometry(AreaGeometry.Kind.SECTOR, origin, facing, range, angle, 0, 0, height);
    }

    private static <T> String reject(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike,
                                     Candidate<T> candidate) {
        if (!candidate.damageable()) return "NOT_DAMAGEABLE";
        if (candidate.protectedTarget()) return "PROTECTED_TARGET";
        if (intersects(origin, facing, strike, candidate.bounds())) return null;
        Vec3 relative = candidate.position().subtract(origin);
        double horizontal = relative.horizontalLength();
        if (candidate.bounds().max().y() < origin.y() || candidate.bounds().min().y() > origin.y() + strike.details().height())
            return "OUT_OF_VERTICAL_BOUNDS";
        if (strike.geometry() != Stage04SkillProfile.Geometry.LINE
                && horizontal > strike.range() + 1.0e-9) return "OUT_OF_RANGE";
        if (strike.geometry() == Stage04SkillProfile.Geometry.RADIUS) return "OUT_OF_RANGE";
        double dot = horizontal < 1.0e-9 ? 1.0
                : (relative.x() * facing.x() + relative.z() * facing.z()) / horizontal;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        if (strike.geometry() == Stage04SkillProfile.Geometry.LINE) {
            double forward = relative.x() * facing.x() + relative.z() * facing.z();
            if (forward < 0.0 || forward > strike.range()) return "OUTSIDE_LINE_LENGTH";
            double lateral = Math.abs(relative.x() * facing.z() - relative.z() * facing.x());
            return "OUTSIDE_LINE_WIDTH";
        }
        double angle = Math.toDegrees(Math.acos(dot));
        return "OUTSIDE_ARC";
    }

    public record Candidate<T>(String stableId, T handle, Vec3 position, boolean damageable,
                               boolean protectedTarget, boolean boss, AreaGeometry.Bounds bounds) {
        public Candidate(String stableId, T handle, Vec3 position, boolean damageable, boolean protectedTarget, boolean boss) {
            this(stableId, handle, position, damageable, protectedTarget, boss, new AreaGeometry.Bounds(position, position));
        }
    }
    public record Decision<T>(Candidate<T> candidate, boolean accepted, String reason) { }
    public record QueryResult<T>(List<Candidate<T>> accepted, List<Decision<T>> decisions) { }
}
