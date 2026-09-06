package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared deterministic selector for arcs, thrust lines, assist cones, and landing radii. */
public final class StrikeGeometryService {
    public <T> QueryResult<T> query(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike,
                                    List<Candidate<T>> boundedCandidates) {
        Vec3 direction = facing.horizontalNormalized();
        List<Decision<T>> decisions = new ArrayList<>();
        for (Candidate<T> candidate : boundedCandidates) {
            String rejection = reject(origin, direction, strike, candidate);
            decisions.add(new Decision<>(candidate, rejection == null, rejection == null ? "ACCEPTED" : rejection));
        }
        List<Candidate<T>> accepted = decisions.stream().filter(Decision::accepted).map(Decision::candidate)
                .sorted(Comparator.comparingDouble(value -> value.position().distanceSquared(origin)))
                .limit(strike.targetCap()).toList();
        if (strike.geometry() == Stage04SkillProfile.Geometry.ASSIST_CONE && accepted.size() > 1)
            accepted = accepted.subList(0, 1);
        return new QueryResult<>(accepted, List.copyOf(decisions));
    }

    private static <T> String reject(Vec3 origin, Vec3 facing, Stage04SkillProfile.Strike strike,
                                     Candidate<T> candidate) {
        if (!candidate.damageable()) return "NOT_DAMAGEABLE";
        Vec3 relative = candidate.position().subtract(origin);
        double horizontal = relative.horizontalLength();
        if (Math.abs(relative.y()) > 2.5) return "OUT_OF_VERTICAL_BOUNDS";
        if (strike.geometry() != Stage04SkillProfile.Geometry.LINE
                && horizontal > strike.range() + 1.0e-9) return "OUT_OF_RANGE";
        if (strike.geometry() == Stage04SkillProfile.Geometry.RADIUS) return null;
        double dot = horizontal < 1.0e-9 ? 1.0
                : (relative.x() * facing.x() + relative.z() * facing.z()) / horizontal;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        if (strike.geometry() == Stage04SkillProfile.Geometry.LINE) {
            double forward = relative.x() * facing.x() + relative.z() * facing.z();
            if (forward < 0.0 || forward > strike.range()) return "OUTSIDE_LINE_LENGTH";
            double lateral = Math.abs(relative.x() * facing.z() - relative.z() * facing.x());
            return lateral <= strike.lineHalfWidth() + 1.0e-9 ? null : "OUTSIDE_LINE_WIDTH";
        }
        double angle = Math.toDegrees(Math.acos(dot));
        return angle <= strike.angleDegrees() * 0.5 + 1.0e-9 ? null : "OUTSIDE_ARC";
    }

    public record Candidate<T>(String stableId, T handle, Vec3 position, boolean damageable,
                               boolean protectedTarget, boolean boss) { }
    public record Decision<T>(Candidate<T> candidate, boolean accepted, String reason) { }
    public record QueryResult<T>(List<Candidate<T>> accepted, List<Decision<T>> decisions) { }
}
