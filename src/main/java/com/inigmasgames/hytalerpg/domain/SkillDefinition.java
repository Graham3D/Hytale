package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, data-driven skill contract consumed by shared family executors in later stages. */
public record SkillDefinition(
        int schemaVersion,
        SkillId id,
        String name,
        String description,
        String tier,
        int phase,
        Set<String> tags,
        String weaponRequirement,
        String scalingClass,
        String basePowerSource,
        Double innateBasePower,
        String resourceType,
        String castCost,
        String upkeep,
        String castTime,
        String cooldown,
        String targetMode,
        String maxRange,
        String geometry,
        String travelSpeed,
        String lifetime,
        String powerCoefficient,
        boolean canCrit,
        List<String> statusApplications,
        String vfxRecipeId,
        String soundRecipeId,
        Set<String> linkCompatibilityTags,
        SourceAcquisition sourceAcquisition,
        List<String> aliases) {

    public SkillDefinition {
        if (schemaVersion < 1) throw new IllegalArgumentException("Skill schemaVersion must be positive");
        Objects.requireNonNull(id, "id");
        name = require(name, "name");
        description = require(description, "description");
        tier = require(tier, "tier");
        tags = Set.copyOf(tags);
        linkCompatibilityTags = Set.copyOf(linkCompatibilityTags);
        statusApplications = List.copyOf(statusApplications);
        aliases = List.copyOf(aliases);
        Objects.requireNonNull(sourceAcquisition, "sourceAcquisition");
    }

    public String family() {
        return tags.stream().filter(tag -> switch (tag) {
            case "STRIKE", "PROJECTILE", "GROUND_ZONE", "AURA", "SUMMON", "MOVEMENT", "BARRIER",
                    "TRAP", "BEAM", "LINE", "DIRECT_TARGET", "TRANSFORMATION", "CHANNEL", "REACTION" -> true;
            default -> false;
        }).findFirst().orElseGet(() -> tags.stream().sorted().findFirst().orElse("UTILITY"));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Skill " + field + " is required");
        return value;
    }

    public record SourceAcquisition(String signatureEnemyId, String validationState, Double learnChance,
                                    String acquisitionRarity, String difficultyVariant) {}
}
