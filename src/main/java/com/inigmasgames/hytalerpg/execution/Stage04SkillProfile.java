package com.inigmasgames.hytalerpg.execution;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-only runtime configuration consumed by shared family executors. */
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
        Reaction reaction,
        Projectile projectile) {

    public Stage04SkillProfile {
        secondaryFamilies = Set.copyOf(secondaryFamilies == null ? Set.of() : secondaryFamilies);
        allowedMainHandKinds = Set.copyOf(allowedMainHandKinds == null ? Set.of() : allowedMainHandKinds);
        requiredOffHandKinds = Set.copyOf(requiredOffHandKinds == null ? Set.of() : requiredOffHandKinds);
        if (skillId == null || skillId.isBlank() || family == null || resourceCost < 0.0
                || cooldownSeconds < 0.0 || windupSeconds < 0.0)
            throw new IllegalArgumentException("Invalid runtime skill profile");
    }

    public boolean hasFamily(Family candidate) {
        return family == candidate || secondaryFamilies.contains(candidate.name());
    }

    public double damageCoefficient() {
        if (strike != null) return strike.coefficient();
        if (projectile != null) return projectile.coefficient();
        return 0.0;
    }

    public Map<String, Double> authoredStatuses() {
        if (strike != null && !strike.statusId().isBlank())
            return Map.of(strike.statusId(), strike.statusSeconds());
        if (projectile != null && !projectile.statusId().isBlank())
            return Map.of(projectile.statusId(), projectile.statusSeconds());
        return Map.of();
    }

    public enum Family { STRIKE, MOVEMENT, REACTION, PROJECTILE }
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

    public record Projectile(String configId, double speed, double maxDistance, double radius,
                             double gravity, int targetCap, double coefficient,
                             String statusId, double statusSeconds, double periodicCoefficient,
                             int periodicTicks, double periodicIntervalSeconds,
                             String ammoItemId, int ammoQuantity, boolean fullyCharged,
                             double knockbackDistance,
                             Map<String, String> configIdsByWeaponKind,
                             Map<String, Double> speedsByWeaponKind) {
        public Projectile {
            configId = configId == null ? "" : configId;
            statusId = statusId == null ? "" : statusId;
            ammoItemId = ammoItemId == null ? "" : ammoItemId;
            configIdsByWeaponKind = Map.copyOf(configIdsByWeaponKind == null ? Map.of() : configIdsByWeaponKind);
            speedsByWeaponKind = Map.copyOf(speedsByWeaponKind == null ? Map.of() : speedsByWeaponKind);
            if (configId.isBlank() || speed <= 0.0 || maxDistance <= 0.0 || radius <= 0.0
                    || !Double.isFinite(gravity) || targetCap < 1 || coefficient < 0.0
                    || statusSeconds < 0.0 || periodicCoefficient < 0.0 || periodicTicks < 0
                    || periodicIntervalSeconds < 0.0 || ammoQuantity < 0 || knockbackDistance < 0.0
                    || speedsByWeaponKind.values().stream().anyMatch(value -> value == null || value <= 0.0))
                throw new IllegalArgumentException("Invalid projectile profile");
            if ((ammoItemId.isBlank()) != (ammoQuantity == 0))
                throw new IllegalArgumentException("Projectile ammunition ID and quantity must be declared together");
            if ((periodicTicks == 0) != (periodicCoefficient == 0.0 || periodicIntervalSeconds == 0.0))
                throw new IllegalArgumentException("Projectile periodic payload must be fully declared or absent");
        }
        public double maximumLifetimeSeconds() { return maxDistance / speed; }
        public String configIdFor(String weaponKind) {
            return configIdsByWeaponKind.getOrDefault(weaponKind, configId);
        }
        public double speedFor(String weaponKind) {
            return speedsByWeaponKind.getOrDefault(weaponKind, speed);
        }
        public double maximumLifetimeSeconds(String weaponKind) { return maxDistance / speedFor(weaponKind); }
        public boolean requiresAmmo() { return ammoQuantity > 0; }
        public boolean hasPeriodicStatus() { return periodicTicks > 0; }
    }
}
