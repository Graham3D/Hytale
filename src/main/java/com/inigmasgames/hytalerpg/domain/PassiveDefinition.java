package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable passive contract. Compatibility and modification are interpreted by shared services. */
public record PassiveDefinition(
        int schemaVersion,
        PassiveId id,
        String name,
        String description,
        String tier,
        int phase,
        Set<String> compatibleTags,
        Set<String> requiredFamilies,
        Set<String> requiredCapabilities,
        Set<String> compatibleAnyPayloads,
        Set<String> incompatibleTags,
        String compatibilityExpression,
        List<String> modifierOps,
        Set<String> addedTags,
        Set<String> removedTags,
        String familyConversion,
        String triggerHook,
        int priority,
        String stackingGroup,
        int maxCopies,
        int spawnBudgetCost,
        int childProjectileCount,
        String executionNotes,
        String safetyNotes,
        String fixture,
        List<String> aliases) {

    public PassiveDefinition {
        if (schemaVersion < 1) throw new IllegalArgumentException("Passive schemaVersion must be positive");
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Passive name is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Passive description is required");
        compatibleTags = Set.copyOf(compatibleTags);
        requiredFamilies = Set.copyOf(requiredFamilies);
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        compatibleAnyPayloads = Set.copyOf(compatibleAnyPayloads);
        incompatibleTags = Set.copyOf(incompatibleTags);
        modifierOps = List.copyOf(modifierOps);
        addedTags = Set.copyOf(addedTags);
        removedTags = Set.copyOf(removedTags);
        aliases = List.copyOf(aliases);
        if (maxCopies < 1) throw new IllegalArgumentException("Passive maxCopies must be positive");
        if (spawnBudgetCost < 0 || childProjectileCount < 0) throw new IllegalArgumentException("Passive budgets cannot be negative");
    }
}
