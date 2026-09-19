package com.inigmasgames.taverns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Resolves the Tavern-owned explicit Comfort registry against loaded item assets.
 * Candidate heuristics are deliberately kept out of the production registry.
 */
final class ComfortRegistry {
    private static final String CONFIG_NAME = "comfort_registry.json";
    private static final int CURRENT_SCHEMA_VERSION = 4;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Config config;
    private final Map<ComfortCategory, ComfortThreshold> thresholds;
    private volatile Map<String, ComfortDefinition> definitions = Map.of();

    private ComfortRegistry(Config config) {
        this.config = config;
        this.thresholds = resolveThresholds(config);
    }

    static ComfortRegistry load(
            Path dataDirectory,
            Consumer<String> info,
            Consumer<Throwable> error) {
        Config config;
        Path external = dataDirectory.resolve(CONFIG_NAME);
        try {
            Config bundled = loadBundledDefaults();
            Files.createDirectories(dataDirectory);
            if (!Files.exists(external)) {
                try (InputStream source = ComfortRegistry.class.getResourceAsStream("/" + CONFIG_NAME)) {
                    if (source == null) {
                        throw new IOException("Bundled " + CONFIG_NAME + " is missing");
                    }
                    Files.copy(source, external, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Config overrides;
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                overrides = GSON.fromJson(reader, Config.class);
            }
            if (overrides != null
                    && overrides.schemaVersion != CURRENT_SCHEMA_VERSION) {
                Path backup = dataDirectory.resolve(
                        "comfort_registry.v" + overrides.schemaVersion + ".backup.json");
                Files.copy(external, backup, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(
                        external,
                        GSON.toJson(bundled) + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                info.accept("Migrated Comfort registry schema "
                        + overrides.schemaVersion + " to " + CURRENT_SCHEMA_VERSION
                        + "; previous registry backed up to " + backup + ".");
                config = bundled;
            } else {
                validate(overrides);
                config = merge(bundled, overrides);
            }
            validate(config);
        } catch (Exception exception) {
            error.accept(new IOException(
                    "Could not load " + external + "; bundled Comfort defaults will be used",
                    exception));
            config = loadBundledDefaults();
        }

        return new ComfortRegistry(config);
    }

    synchronized int resolveLoadedItems(Consumer<String> info) {
        Map<String, ComfortDefinition> resolved = resolve(config);
        definitions = Map.copyOf(resolved);
        Map<ComfortCategory, Integer> categoryCounts = new LinkedHashMap<>();
        for (ComfortDefinition definition : resolved.values()) {
            categoryCounts.merge(definition.category(), 1, Integer::sum);
        }
        info.accept("Registered " + resolved.size() + " Comfort asset(s): " + categoryCounts + ".");
        return resolved.size();
    }

    Optional<ComfortDefinition> find(String assetId) {
        return Optional.ofNullable(definitions.get(assetId));
    }

    int size() {
        return definitions.size();
    }

    List<ComfortDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    Map<ComfortCategory, ComfortThreshold> thresholds() {
        return thresholds;
    }

    private static Config loadBundledDefaults() {
        try (InputStream source = ComfortRegistry.class.getResourceAsStream("/" + CONFIG_NAME)) {
            if (source == null) {
                throw new IllegalStateException("Bundled " + CONFIG_NAME + " is missing");
            }
            Config config = GSON.fromJson(
                    new InputStreamReader(source, StandardCharsets.UTF_8), Config.class);
            validate(config);
            return config;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled " + CONFIG_NAME, exception);
        }
    }

    private static void validate(Config config) {
        if (config == null || config.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Comfort registry schemaVersion must be " + CURRENT_SCHEMA_VERSION);
        }
        if (config.regionalValues == null || config.entries == null) {
            throw new IllegalArgumentException("Comfort registry maps must not be null");
        }
        for (String region : List.of(
                "EMERALD_WILDS", "HOWLING_SANDS",
                "WHISPERFROST_FRONTIERS", "DEVASTATED_LANDS")) {
            RegionValues values = config.regionalValues.get(region);
            if (values == null
                    || values.commonValue() < 0 || values.commonValue() > 8
                    || values.discoveryValue() < 0 || values.discoveryValue() > 8) {
                throw new IllegalArgumentException("Missing or invalid regionalValues." + region);
            }
        }
        if (config.thresholds == null) {
            throw new IllegalArgumentException("Comfort threshold map must not be null");
        }
        for (Map.Entry<String, ThresholdConfig> entry : config.thresholds.entrySet()) {
            ComfortCategory.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            ThresholdConfig value = entry.getValue();
            if (value == null || value.minimum < 1
                    || value.density != null && value.density < 1) {
                throw new IllegalArgumentException(
                        "Invalid Comfort threshold " + entry.getKey());
            }
            ComfortCountMode.valueOf(value.countMode.toUpperCase(Locale.ROOT));
        }
        for (Map.Entry<String, EntryOverride> entry : config.entries.entrySet()) {
            EntryOverride value = entry.getValue();
            if (value == null || Boolean.FALSE.equals(value.enabled)) {
                continue;
            }
            if (value.category == null || value.category.isBlank()) {
                throw new IllegalArgumentException(
                        "Comfort entry " + entry.getKey() + " requires a category");
            }
            ComfortCategory.valueOf(value.category.toUpperCase(Locale.ROOT));
            if (value.comfort != null && (value.comfort < 0 || value.comfort > 8)) {
                throw new IllegalArgumentException(
                        "Comfort entry " + entry.getKey() + " must be between 0 and 8");
            }
        }
    }

    private static Config merge(Config bundled, Config overrides) {
        Config merged = new Config();
        merged.schemaVersion = bundled.schemaVersion;
        merged.defaultRegion = overrides.defaultRegion == null
                ? bundled.defaultRegion
                : overrides.defaultRegion;
        merged.regionalValues.putAll(bundled.regionalValues);
        merged.regionalValues.putAll(overrides.regionalValues);
        merged.thresholds.putAll(bundled.thresholds);
        merged.thresholds.putAll(overrides.thresholds);
        merged.entries.putAll(bundled.entries);
        merged.entries.putAll(overrides.entries);
        return merged;
    }

    private static Map<String, ComfortDefinition> resolve(Config config) {
        Map<String, ComfortDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, EntryOverride> entry : config.entries.entrySet()) {
            String assetId = entry.getKey();
            EntryOverride override = entry.getValue();
            if (override == null || Boolean.FALSE.equals(override.enabled)) {
                continue;
            }
            Item item = Item.getAssetMap().getAsset(assetId);
            if (item == null || !item.hasBlockType()
                    || CoreDefinitions.byItemId(assetId).isPresent()) {
                continue;
            }
            ComfortCategory category = ComfortCategory.valueOf(
                    override.category.toUpperCase(Locale.ROOT));
            String region = override.region != null
                    ? override.region.toUpperCase(Locale.ROOT)
                    : config.defaultRegion.toUpperCase(Locale.ROOT);
            String source = progression(override);
            int comfort = override.comfort != null
                    ? override.comfort
                    : regionalValue(config, region, source);
            if (comfort <= 0) {
                continue;
            }
            resolved.put(assetId, new ComfortDefinition(
                    assetId, category, comfort, region, source));
        }
        return resolved;
    }

    private static int regionalValue(Config config, String region, String source) {
        RegionValues values = config.regionalValues.get(region);
        if (values == null) {
            throw new IllegalArgumentException("Unknown Comfort region " + region);
        }
        return "DISCOVERY".equals(source)
                ? values.discoveryValue()
                : values.commonValue();
    }

    private static String progression(EntryOverride override) {
        String value = override.progression != null
                ? override.progression
                : override.source;
        if (value == null) {
            return "COMMON";
        }
        value = value.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "DISCOVERY", "DISCOVERABLE" -> "DISCOVERY";
            case "COMMON", "CRAFTABLE" -> "COMMON";
            default -> throw new IllegalArgumentException(
                    "Unknown Comfort progression class " + value);
        };
    }

    private static Map<ComfortCategory, ComfortThreshold> resolveThresholds(Config config) {
        EnumMap<ComfortCategory, ComfortThreshold> resolved =
                new EnumMap<>(ComfortCategory.class);
        resolved.putAll(ComfortThreshold.designDefaults());
        for (Map.Entry<String, ThresholdConfig> entry : config.thresholds.entrySet()) {
            ComfortCategory category = ComfortCategory.valueOf(
                    entry.getKey().toUpperCase(Locale.ROOT));
            ThresholdConfig value = entry.getValue();
            resolved.put(category, new ComfortThreshold(
                    value.enabled,
                    value.minimum,
                    value.density,
                    ComfortCountMode.valueOf(
                            value.countMode.toUpperCase(Locale.ROOT))));
        }
        return Map.copyOf(resolved);
    }

    private static final class Config {
        int schemaVersion;
        String defaultRegion;
        Map<String, RegionValues> regionalValues = new LinkedHashMap<>();
        Map<String, ThresholdConfig> thresholds = new LinkedHashMap<>();
        Map<String, EntryOverride> entries = new LinkedHashMap<>();
    }

    private static final class RegionValues {
        Integer common;
        Integer discovery;
        Integer craftable;
        Integer discoverable;

        int commonValue() {
            return common != null ? common : craftable == null ? 0 : craftable;
        }

        int discoveryValue() {
            return discovery != null
                    ? discovery
                    : discoverable == null ? 0 : discoverable;
        }
    }

    private static final class ThresholdConfig {
        boolean enabled = true;
        int minimum;
        Integer density;
        String countMode;
    }

    private static final class EntryOverride {
        Boolean enabled;
        String category;
        String region;
        String progression;
        String source;
        Integer comfort;
    }
}
