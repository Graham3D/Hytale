package com.inigmasgames.hytalerpg.content;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillId;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable canonical catalog loaded from checked-in, versioned content data. */
public final class RpgCatalog {
    public static final int EXPECTED_SKILLS = 87;
    public static final int EXPECTED_PASSIVES = 66;

    private final Map<SkillId, SkillDefinition> skills;
    private final Map<PassiveId, PassiveDefinition> passives;
    private final Map<String, List<SkillDefinition>> skillAliases;
    private final Map<String, List<PassiveDefinition>> passiveAliases;

    public RpgCatalog(Collection<SkillDefinition> skills, Collection<PassiveDefinition> passives) {
        Map<SkillId, SkillDefinition> skillMap = new LinkedHashMap<>();
        for (SkillDefinition definition : skills) {
            if (skillMap.put(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate SkillId: " + definition.id());
        }
        Map<PassiveId, PassiveDefinition> passiveMap = new LinkedHashMap<>();
        for (PassiveDefinition definition : passives) {
            if (passiveMap.put(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate PassiveId: " + definition.id());
        }
        this.skills = Map.copyOf(skillMap);
        this.passives = Map.copyOf(passiveMap);
        this.skillAliases = indexSkills(skillMap.values());
        this.passiveAliases = indexPassives(passiveMap.values());
    }

    public static RpgCatalog loadCanonical() {
        Gson gson = new Gson();
        SkillDto[] skillDtos = read(gson, "/rpg/catalog/skills.json", SkillDto[].class);
        PassiveDto[] passiveDtos = read(gson, "/rpg/catalog/passives.json", PassiveDto[].class);
        List<SkillDefinition> skills = new ArrayList<>(skillDtos.length);
        for (SkillDto dto : skillDtos) skills.add(dto.toDefinition());
        List<PassiveDefinition> passives = new ArrayList<>(passiveDtos.length);
        for (PassiveDto dto : passiveDtos) passives.add(dto.toDefinition());
        RpgCatalog catalog = new RpgCatalog(skills, passives);
        catalog.validateCanonicalCounts();
        return catalog;
    }

    private static <T> T read(Gson gson, String resource, Class<T> type) {
        var stream = RpgCatalog.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing canonical catalog: " + resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            T value = gson.fromJson(reader, type);
            if (value == null) throw new IllegalStateException("Empty canonical catalog: " + resource);
            return value;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load canonical catalog: " + resource, error);
        }
    }

    public void validateCanonicalCounts() {
        if (skills.size() != EXPECTED_SKILLS) throw new IllegalStateException("Expected 87 canonical skills, loaded " + skills.size());
        if (passives.size() != EXPECTED_PASSIVES) throw new IllegalStateException("Expected 66 canonical passives, loaded " + passives.size());
    }

    public Collection<SkillDefinition> skills() { return skills.values(); }
    public Collection<PassiveDefinition> passives() { return passives.values(); }
    public Optional<SkillDefinition> skill(SkillId id) { return Optional.ofNullable(skills.get(id)); }
    public Optional<PassiveDefinition> passive(PassiveId id) { return Optional.ofNullable(passives.get(id)); }

    public CatalogResolution<SkillDefinition> resolveSkill(String query) {
        return resolve(query, skills, skillAliases, SkillDefinition::name);
    }

    public CatalogResolution<PassiveDefinition> resolvePassive(String query) {
        return resolve(query, passives, passiveAliases, PassiveDefinition::name);
    }

    private static <I, T> CatalogResolution<T> resolve(String query, Map<I, T> byId,
                                                       Map<String, List<T>> aliases,
                                                       java.util.function.Function<T, String> displayName) {
        String normalized = compact(query);
        if (normalized.isBlank()) return CatalogResolution.notFound("Content name cannot be blank.");
        List<T> exact = aliases.get(normalized);
        if (exact != null) {
            if (exact.size() == 1) return CatalogResolution.resolved(exact.getFirst());
            return CatalogResolution.ambiguous(exact.stream().map(displayName).sorted().toList());
        }
        LinkedHashSet<T> fuzzy = new LinkedHashSet<>();
        aliases.forEach((alias, values) -> {
            if (alias.startsWith(normalized) || normalized.startsWith(alias)) fuzzy.addAll(values);
        });
        if (fuzzy.size() == 1) return CatalogResolution.resolved(fuzzy.getFirst());
        if (fuzzy.size() > 1) return CatalogResolution.ambiguous(fuzzy.stream().map(displayName).sorted().toList());
        return CatalogResolution.notFound("Unknown content: " + query);
    }

    private static Map<String, List<SkillDefinition>> indexSkills(Collection<SkillDefinition> definitions) {
        Map<String, List<SkillDefinition>> result = new LinkedHashMap<>();
        for (SkillDefinition definition : definitions) {
            add(result, definition.name(), definition);
            add(result, definition.id().value(), definition);
            add(result, definition.id().canonical(), definition);
            definition.aliases().forEach(alias -> add(result, alias, definition));
        }
        return immutableIndex(result);
    }

    private static Map<String, List<PassiveDefinition>> indexPassives(Collection<PassiveDefinition> definitions) {
        Map<String, List<PassiveDefinition>> result = new LinkedHashMap<>();
        for (PassiveDefinition definition : definitions) {
            add(result, definition.name(), definition);
            add(result, definition.id().value(), definition);
            add(result, definition.id().canonical(), definition);
            definition.aliases().forEach(alias -> add(result, alias, definition));
        }
        return immutableIndex(result);
    }

    private static <T> void add(Map<String, List<T>> index, String alias, T value) {
        List<T> values = index.computeIfAbsent(compact(alias), ignored -> new ArrayList<>());
        if (!values.contains(value)) values.add(value);
    }

    private static <T> Map<String, List<T>> immutableIndex(Map<String, List<T>> index) {
        Map<String, List<T>> result = new LinkedHashMap<>();
        index.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    static String compact(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^rpg\\.(skill|passive)\\.", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static final class SkillDto {
        int schemaVersion; String id; String name; String description; String tier; int phase;
        List<String> tags; String weaponRequirement; String scalingClass; String basePowerSource;
        Double innateBasePower; String resourceType; String castCost; String upkeep; String castTime;
        String cooldown; String targetMode; String maxRange; String geometry; String travelSpeed;
        String lifetime; String powerCoefficient; boolean canCrit; List<String> statusApplications;
        String vfxRecipeId; String soundRecipeId; List<String> linkCompatibilityTags;
        SourceDto sourceAcquisition; List<String> aliases;
        SkillDefinition toDefinition() {
            return new SkillDefinition(schemaVersion, new SkillId(id), name, description, tier, phase,
                    set(tags), weaponRequirement, scalingClass, basePowerSource, innateBasePower,
                    resourceType, castCost, upkeep, castTime, cooldown, targetMode, maxRange, geometry,
                    travelSpeed, lifetime, powerCoefficient, canCrit, list(statusApplications),
                    vfxRecipeId, soundRecipeId, set(linkCompatibilityTags), sourceAcquisition.toDefinition(), list(aliases));
        }
    }

    private static final class SourceDto {
        String signatureEnemyId; String validationState; Double learnChance; String acquisitionRarity; String difficultyVariant;
        SkillDefinition.SourceAcquisition toDefinition() {
            return new SkillDefinition.SourceAcquisition(signatureEnemyId, validationState, learnChance, acquisitionRarity, difficultyVariant);
        }
    }

    private static final class PassiveDto {
        int schemaVersion; String id; String name; String description; String tier; int phase;
        List<String> compatibleTags; List<String> requiredFamilies; List<String> requiredCapabilities;
        List<String> compatibleAnyPayloads; List<String> incompatibleTags; String compatibilityExpression;
        List<String> modifierOps; List<String> addedTags; List<String> removedTags; String familyConversion;
        String triggerHook; int priority; String stackingGroup; int maxCopies; int spawnBudgetCost;
        int childProjectileCount; String executionNotes; String safetyNotes; String fixture; List<String> aliases;
        PassiveDefinition toDefinition() {
            return new PassiveDefinition(schemaVersion, new PassiveId(id), name, description, tier, phase,
                    set(compatibleTags), set(requiredFamilies), set(requiredCapabilities), set(compatibleAnyPayloads),
                    set(incompatibleTags), compatibilityExpression, list(modifierOps), set(addedTags), set(removedTags),
                    familyConversion, triggerHook, priority, stackingGroup, maxCopies, spawnBudgetCost,
                    childProjectileCount, executionNotes, safetyNotes, fixture, list(aliases));
        }
    }

    private static Set<String> set(List<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
    private static List<String> list(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
