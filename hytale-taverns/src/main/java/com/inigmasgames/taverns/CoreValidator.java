package com.inigmasgames.taverns;

import java.util.Collection;
import java.util.Optional;

/** Centralized spatial rules for all Core types. */
final class CoreValidator {
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 319;

    private final TavernRepository repository;

    CoreValidator(TavernRepository repository) {
        this.repository = repository;
    }

    Optional<String> validate(CoreRecord proposed) {
        CoreDefinition definition;
        try {
            definition = CoreDefinitions.require(proposed.type());
        } catch (IllegalStateException exception) {
            return Optional.of(exception.getMessage());
        }
        Cuboid bounds = proposed.bounds();
        if (bounds.minY() < MIN_WORLD_Y || bounds.maxY() > MAX_WORLD_Y) {
            return Optional.of("The Core volume must stay inside world height 0-319.");
        }
        if (!bounds.contains(proposed.coreX(), proposed.coreY(), proposed.coreZ())) {
            return Optional.of("The physical Core block must remain inside its volume.");
        }
        if (bounds.volume() <= 0 || bounds.volume() > definition.maximumVolume()) {
            return Optional.of("The current safety limit is " + definition.maximumVolume() + " blocks for this Core.");
        }

        Collection<CoreRecord> intersections = repository.findIntersectingCores(
                proposed.worldId(), bounds, proposed.coreId());
        if (proposed.type().isPrimary()) {
            boolean anotherTavern = intersections.stream()
                    .anyMatch(other -> other.type().isPrimary() && !other.tavernId().equals(proposed.tavernId()));
            if (anotherTavern) {
                return Optional.of("Tavern volumes cannot overlap another Tavern.");
            }
            boolean excludesSpecializedCore = repository.findCoresByTavern(proposed.tavernId()).stream()
                    .anyMatch(other -> !other.type().isPrimary() && !contains(bounds, other.bounds()));
            if (excludesSpecializedCore) {
                return Optional.of("The Tavern Core must continue to contain all of its specialized Cores.");
            }
            return Optional.empty();
        }

        Optional<CoreRecord> tavernCore = repository.findPrimaryCore(proposed.tavernId());
        if (tavernCore.isEmpty() || !contains(tavernCore.get().bounds(), bounds)) {
            return Optional.of("A specialized Core must remain fully inside its Tavern Core.");
        }
        boolean specializedOverlap = intersections.stream()
                .anyMatch(other -> !other.type().isPrimary());
        return specializedOverlap
                ? Optional.of("Specialized Core volumes cannot overlap one another.")
                : Optional.empty();
    }

    private static boolean contains(Cuboid outer, Cuboid inner) {
        return outer.contains(inner.minX(), inner.minY(), inner.minZ())
                && outer.contains(inner.maxX(), inner.maxY(), inner.maxZ());
    }
}
