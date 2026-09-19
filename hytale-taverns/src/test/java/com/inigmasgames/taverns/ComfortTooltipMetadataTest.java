package com.inigmasgames.taverns;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Regression assertions for all eleven active Comfort categories and tooltip metadata. */
public final class ComfortTooltipMetadataTest {
    private ComfortTooltipMetadataTest() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> displayCategories = new HashSet<>();
        for (ComfortCategory category : ComfortCategory.values()) {
            displayCategories.add(category.tooltipTranslationKey());
        }
        assert ComfortCategory.values().length == 11;
        assert displayCategories.size() == 11 : displayCategories;
        assert ComfortCategory.DOORS.tooltipToken().equals("doors");
        assert ComfortCategory.WINDOWS.tooltipToken().equals("windows");
        assert ComfortCategory.DECO.tooltipToken().equals("deco");

        ComfortThreshold scaled = new ComfortThreshold(
                true, 2, 100, ComfortCountMode.PLACED_INSTANCES);
        assert scaled.requiredCount(0) == 2;
        assert scaled.requiredCount(100) == 2;
        assert scaled.requiredCount(201) == 3;

        UUID coreId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Map<ComfortCategory, ComfortThreshold> thresholds =
                new EnumMap<>(ComfortCategory.class);
        thresholds.putAll(ComfortThreshold.designDefaults());
        thresholds.put(ComfortCategory.SEATING, new ComfortThreshold(
                true, 3, 100, ComfortCountMode.PLACED_INSTANCES));
        thresholds.put(ComfortCategory.DECO, new ComfortThreshold(
                true, 2, null, ComfortCountMode.DISTINCT_ASSET_TYPES));

        List<RegisteredComfortObject> objects = List.of(
                object(coreId, worldId, "Chair_High", ComfortCategory.SEATING, 8, true),
                object(coreId, worldId, "Chair_Mid", ComfortCategory.SEATING, 5, true),
                object(coreId, worldId, "Chair_Low", ComfortCategory.SEATING, 2, true),
                object(coreId, worldId, "Table", ComfortCategory.TABLES, 3, true),
                object(coreId, worldId, "Deco_A", ComfortCategory.DECO, 8, true),
                objectAt(coreId, worldId, "Deco_A", ComfortCategory.DECO, 8, true, 1),
                object(coreId, worldId, "Deco_B", ComfortCategory.DECO, 4, true),
                object(coreId, worldId, "Invalid_Bed", ComfortCategory.BEDS, 8, false));
        ComfortScore score = ComfortManager.calculateScore(objects, 201, thresholds);
        assert score.categoryValues().get(ComfortCategory.SEATING) == 2 : score;
        assert score.categoryValues().get(ComfortCategory.TABLES) == 3 : score;
        assert score.categoryValues().get(ComfortCategory.DECO) == 4 : score;
        assert score.currentCounts().get(ComfortCategory.DECO) == 2 : score;
        assert score.requiredCounts().get(ComfortCategory.SEATING) == 3 : score;
        assert score.totalComfort() == 9 : score;

        ComfortScore belowThreshold = ComfortManager.calculateScore(
                List.of(
                        object(coreId, worldId, "Chair_A", ComfortCategory.SEATING, 8, true),
                        object(coreId, worldId, "Chair_B", ComfortCategory.SEATING, 7, true)),
                201,
                thresholds);
        assert belowThreshold.categoryValues().get(ComfortCategory.SEATING) == 0
                : belowThreshold;

        List<TavernsHud.ComfortSource> sources = ComfortManager.contributingSources(
                objects, score, thresholds);
        assert sources.equals(List.of(
                new TavernsHud.ComfortSource("Table", 3),
                new TavernsHud.ComfortSource("Chair_High", 8),
                new TavernsHud.ComfortSource("Chair_Mid", 5),
                new TavernsHud.ComfortSource("Chair_Low", 2),
                new TavernsHud.ComfortSource("Deco_A", 8),
                new TavernsHud.ComfortSource("Deco_B", 4))) : sources;

        List<RegisteredComfortObject> maximum = new java.util.ArrayList<>();
        for (ComfortCategory category : ComfortCategory.values()) {
            maximum.add(object(coreId, worldId, category.name(), category, 8, true));
        }
        ComfortScore maximumScore = ComfortManager.calculateScore(maximum);
        assert maximumScore.totalComfort() == 88 : maximumScore;
        assert maximumScore.relaxedMinutes() == 23 : maximumScore;

        String language;
        try (InputStream stream = ComfortTooltipMetadataTest.class.getResourceAsStream(
                "/Server/Languages/en-US/server.lang")) {
            assert stream != null;
            language = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assert language.contains("taverns.item.comfort.seating.4 = Comfort: 4 — Seating");
        assert language.contains("taverns.item.comfort.doors.1 = Comfort: 1 — Doors");
        assert language.contains("taverns.item.comfort.windows.1 = Comfort: 1 — Windows");
        assert language.contains("taverns.item.comfort.deco.8 = Comfort: 8 — Deco");
        assert language.contains(
                "taverns.item.comfort_with_description.beds.1 = "
                        + "Comfort: 1 — Beds\\n{vanillaDescription}");
        assert language.contains("taverns.comfort.category.signs = Signs / Banners");
        assert !language.contains("{comfort}");
        assert !language.contains("{category}");

        ComfortTooltipInstaller installer = new ComfortTooltipInstaller();
        ComfortDefinition definition = new ComfortDefinition(
                "Furniture_Tavern_Door", ComfortCategory.DOORS, 1,
                "EMERALD_WILDS", "COMMON");
        var comfortOnly = installer.buildTranslationProperties(
                "server.items.Furniture_Tavern_Door.name", null, definition);
        assert comfortOnly.getName().equals(
                "server.items.Furniture_Tavern_Door.name");
        assert comfortOnly.getDescription().equals(
                "server.taverns.item.comfort.doors.1");
        assert comfortOnly.getDescriptionArguments() == null
                || comfortOnly.getDescriptionArguments().isEmpty();

        var nativeDescription = new com.hypixel.hytale.server.core.asset.type.item.config
                .ItemTranslationProperties(
                        "server.items.Furniture_Crude_Bed.name",
                        "server.items.Furniture_Crude_Bed.description");
        ComfortDefinition bed = new ComfortDefinition(
                "Furniture_Crude_Bed", ComfortCategory.BEDS, 1,
                "EMERALD_WILDS", "COMMON");
        var combined = installer.buildTranslationProperties(
                nativeDescription.getName(), nativeDescription, bed);
        assert combined.getDescription().equals(
                "server.taverns.item.comfort_with_description.beds.1");
        assert combined.getDescriptionArguments().containsKey("vanillaDescription");
        assert combined.toPacket().descriptionArguments.containsKey("vanillaDescription");

        ComfortDefinition approvedDeco = new ComfortDefinition(
                "Deco_Kweebec_Plush", ComfortCategory.DECO, 2,
                "EMERALD_WILDS", "DISCOVERY");
        var decoTooltip = installer.buildTranslationProperties(
                "server.items.Deco_Kweebec_Plush.name", null, approvedDeco);
        assert decoTooltip.getDescription().equals(
                "server.taverns.item.comfort.deco.2");

        ComfortDefinition simpleWindow = new ComfortDefinition(
                "Furniture_Village_Window", ComfortCategory.WINDOWS, 1,
                "EMERALD_WILDS", "COMMON");
        var windowTooltip = installer.buildTranslationProperties(
                "server.items.Furniture_Village_Window.name", null, simpleWindow);
        assert windowTooltip.getDescription().equals(
                "server.taverns.item.comfort.windows.1");

        String registry;
        try (InputStream stream = ComfortTooltipMetadataTest.class.getResourceAsStream(
                "/comfort_registry.json")) {
            assert stream != null;
            registry = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assert registry.contains("\"Furniture_Tavern_Door\"");
        assert registry.contains("\"schemaVersion\": 4");
        assert registry.contains("\"Furniture_Tavern_Window\"");
        assert registry.contains("\"Furniture_Kweebec_Bed\"");
        assert registry.contains("\"progression\": \"DISCOVERY\"");
        assert registry.contains("\"DISTINCT_ASSET_TYPES\"");
        assert registry.contains("\"DECO\"");
        assert registry.contains("\"enabled\": true");
        assert registry.contains("\"Deco_Kweebec_Plush\"");
        assert registry.contains("\"Furniture_Kweebec_Statue\"");
        assert registry.contains("\"category\": \"DECO\"");
        assertRegistryEntry(registry, "Furniture_Village_Window", "WINDOWS",
                "EMERALD_WILDS", "COMMON");
        assertRegistryEntry(registry, "Furniture_Village_Sign", "SIGNS",
                "EMERALD_WILDS", "COMMON");
        assertRegistryEntry(registry, "Furniture_Tavern_Door", "DOORS",
                "EMERALD_WILDS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Desert_Window", "WINDOWS",
                "HOWLING_SANDS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Feran_Sign", "SIGNS",
                "HOWLING_SANDS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Lumberjack_Sign", "SIGNS",
                "WHISPERFROST_FRONTIERS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Human_Ruins_Window", "WINDOWS",
                "WHISPERFROST_FRONTIERS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Jungle_Chair", "SEATING",
                "DEVASTATED_LANDS", "DISCOVERY");
        assertRegistryEntry(registry, "Furniture_Ancient_Door", "DOORS",
                "DEVASTATED_LANDS", "DISCOVERY");
        assertRegistryEntry(registry, "Wood_Drywood_Fence_Gate", "DOORS",
                "HOWLING_SANDS", "COMMON");
        assert !registry.contains("\"Arcade_Machine\"");
        assert !registry.contains("Furniture_Tavern_Ladder");
        assert !registry.contains("Objective_Treasure_Map");
        assert !registry.contains("Furniture_Cybercity_Windows");
        assert !registry.contains("\"Bench_");
        assert !registry.contains("Rock_Sandstone_Cobble");
        assert !registry.contains("BUILDING_STRUCTURES");

        System.out.println("ComfortTooltipMetadataTest passed");
    }

    private static void assertRegistryEntry(
            String registry,
            String assetId,
            String category,
            String region,
            String progression) {
        int start = registry.indexOf("\"" + assetId + "\"");
        assert start >= 0 : assetId;
        int end = registry.indexOf("\n    }", start);
        assert end > start : assetId;
        String entry = registry.substring(start, end);
        assert entry.contains("\"category\": \"" + category + "\"") : entry;
        assert entry.contains("\"region\": \"" + region + "\"") : entry;
        assert entry.contains("\"progression\": \"" + progression + "\"") : entry;
    }

    private static RegisteredComfortObject object(
            UUID coreId,
            UUID worldId,
            String assetId,
            ComfortCategory category,
            int comfort,
            boolean valid) {
        return objectAt(coreId, worldId, assetId, category, comfort, valid, 0);
    }

    private static RegisteredComfortObject objectAt(
            UUID coreId,
            UUID worldId,
            String assetId,
            ComfortCategory category,
            int comfort,
            boolean valid,
            int x) {
        return new RegisteredComfortObject(
                coreId, worldId, x, 0, 0, assetId, category, comfort, valid);
    }
}
