package com.inigmasgames.hytalerpg.execution;

import java.util.List;
import java.util.Set;

/** Data-only configuration consumed by shared family executors. */
public record Stage04SkillProfile(
        String skillId,
        Family family,
        Set<String> secondaryFamilies,
        Set<String> allowedMainHandKinds,
        Set<String> requiredOffHandKinds,
        String resourceType,
        double resourceCost,
        double cooldownSeconds,
        double windupSeconds,
        String basePowerSource,
        double innateBasePower,
        String scaling,
        Strike strike,
        Movement movement,
        Reaction reaction) {

    public Stage04SkillProfile {
        secondaryFamilies = Set.copyOf(secondaryFamilies == null ? Set.of() : secondaryFamilies);
        allowedMainHandKinds = Set.copyOf(allowedMainHandKinds == null ? Set.of() : allowedMainHandKinds);
        requiredOffHandKinds = Set.copyOf(requiredOffHandKinds == null ? Set.of() : requiredOffHandKinds);
        if (skillId == null || skillId.isBlank() || family == null || resourceCost < 0.0
                || cooldownSeconds < 0.0 || windupSeconds < 0.0)
            throw new IllegalArgumentException("Invalid Stage 04 skill profile");
    }

    public boolean hasFamily(Family candidate) {
        return family == candidate || secondaryFamilies.contains(candidate.name());
    }

    public enum Family { STRIKE, MOVEMENT, REACTION }
    public enum Geometry { ARC, LINE, ASSIST_CONE, RADIUS }
    public enum MovementKind { DASH, LEAP }

    public record Strike(Geometry geometry, double range, double angleDegrees, double lineHalfWidth,
                         int repeats, double repeatIntervalSeconds, int targetCap,
                         double coefficient, String statusId, double statusSeconds) {
        public Strike {
            if (geometry == null || range < 0.0 || angleDegrees < 0.0 || lineHalfWidth < 0.0
                    || repeats < 1 || repeatIntervalSeconds < 0.0 || targetCap < 1 || coefficient < 0.0)
                throw new IllegalArgumentException("Invalid strike profile");
            statusId = statusId == null ? "" : statusId;
        }
    }

    public record Movement(MovementKind kind, double maxDistance, double minimumDurationSeconds,
                           double maximumDurationSeconds, double apexHeight, double landingRadius) {
        public Movement {
            if (kind == null || maxDistance < 0.0 || minimumDurationSeconds < 0.0
                    || maximumDurationSeconds < minimumDurationSeconds || apexHeight < 0.0 || landingRadius < 0.0)
                throw new IllegalArgumentException("Invalid movement profile");
        }
    }

    public record Reaction(double windowSeconds, List<String> qualifyingSignals) {
        public Reaction {
            if (windowSeconds <= 0.0) throw new IllegalArgumentException("Reaction window must be positive");
            qualifyingSignals = List.copyOf(qualifyingSignals == null ? List.of() : qualifyingSignals);
        }
    }
}
