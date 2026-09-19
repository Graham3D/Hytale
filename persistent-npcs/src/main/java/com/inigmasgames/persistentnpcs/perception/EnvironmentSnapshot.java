package com.inigmasgames.persistentnpcs.perception;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Compact semantic environment facts; raw block samples never enter an LLM request. */
public record EnvironmentSnapshot(
        UUID worldId,
        Instant capturedAt,
        double npcX,
        double npcY,
        double npcZ,
        Double playerX,
        Double playerY,
        Double playerZ,
        int scanRadius,
        int sampledBlocks,
        long scanMillis,
        String biomeOrZone,
        String terrain,
        List<EnvironmentFeature> importantObjects,
        List<EnvironmentFeature> structuralFeatures,
        List<EnvironmentFeature> naturalFeatures,
        Map<String, Integer> dominantMaterials) {

    public EnvironmentSnapshot {
        capturedAt = capturedAt == null ? Instant.EPOCH : capturedAt;
        biomeOrZone = blankAsUnknown(biomeOrZone);
        terrain = blankAsUnknown(terrain);
        importantObjects = List.copyOf(importantObjects == null ? List.of() : importantObjects);
        structuralFeatures = List.copyOf(
                structuralFeatures == null ? List.of() : structuralFeatures);
        naturalFeatures = List.copyOf(naturalFeatures == null ? List.of() : naturalFeatures);
        dominantMaterials = Map.copyOf(
                dominantMaterials == null ? Map.of() : dominantMaterials);
    }

    public static EnvironmentSnapshot unavailable(UUID worldId, double x, double y, double z) {
        return new EnvironmentSnapshot(worldId, Instant.EPOCH, x, y, z,
                null, null, null, 0, 0, 0, "not exposed", "unknown",
                List.of(), List.of(), List.of(), Map.of());
    }

    public long ageMillis(Instant now) {
        if (capturedAt.equals(Instant.EPOCH)) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(capturedAt, now).toMillis());
    }

    public boolean isUsable() {
        return scanRadius > 0 && sampledBlocks > 0;
    }

    public boolean supports(String category) {
        String sought = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (sought.equals("grass") && terrain.toLowerCase(Locale.ROOT).contains("grass")) {
            return true;
        }
        if (sought.equals("stone") && terrain.toLowerCase(Locale.ROOT).contains("stone")) {
            return true;
        }
        return java.util.stream.Stream.of(importantObjects, structuralFeatures, naturalFeatures)
                .flatMap(List::stream)
                .anyMatch(feature -> feature.category().equalsIgnoreCase(sought)
                        || feature.label().toLowerCase(Locale.ROOT).contains(sought));
    }

    public String semanticBlock() {
        if (!isUsable()) {
            return "CURRENT ENVIRONMENT (authoritative): insufficient loaded-world data.";
        }
        return """
                CURRENT ENVIRONMENT (authoritative, captured on demand):
                - Terrain: %s
                - Important objects: %s
                - Structural features: %s
                - Vegetation/natural features: %s
                - Dominant materials: %s
                - Biome/zone: %s
                """.formatted(terrain, semanticFeatures(importantObjects),
                semanticFeatures(structuralFeatures),
                semanticFeatures(naturalFeatures), dominantMaterials.isEmpty() ? "unknown"
                        : String.join(", ", dominantMaterials.keySet()), biomeOrZone).strip();
    }

    public String groundedDescription() {
        if (!isUsable()) {
            return "I can't make out enough of the nearby environment to describe it reliably.";
        }
        StringBuilder reply = new StringBuilder("We're near ").append(naturalTerrain(terrain));
        List<EnvironmentFeature> notable = java.util.stream.Stream
                .concat(importantObjects.stream(), structuralFeatures.stream())
                .limit(3).toList();
        if (!notable.isEmpty()) {
            reply.append(". I can see ").append(notable.stream()
                    .map(EnvironmentSnapshot::naturalFeature)
                    .collect(Collectors.joining(", ")));
        }
        if (!naturalFeatures.isEmpty()) {
            reply.append(". There's also ").append(naturalFeature(naturalFeatures.getFirst()));
        }
        return reply.append('.').toString();
    }

    private static String naturalTerrain(String value) {
        String text = value == null ? "the surrounding area" : value.toLowerCase(Locale.ROOT);
        if (text.contains("stone") || text.contains("masonry")) {
            return text.contains("vegetation") ? "a stone building with plants nearby"
                    : "a stone building";
        }
        if (text.contains("wood")) return "a wooden structure";
        return text.replace('/', ' ').replaceAll("\\s+", " ").strip();
    }

    private static String naturalFeature(EnvironmentFeature feature) {
        String label = feature.label().toLowerCase(Locale.ROOT)
                .replace('_', ' ').replaceAll("\\s+", " ").strip();
        if (label.equals("focused player")) label = "you";
        else if (label.startsWith("bench ")) label = "a workbench";
        else if (label.startsWith("furniture ")) label = "some furniture";
        else if (!label.matches("^(?:a|an|the|you)\\b.*")) label = "a " + label;
        String direction = feature.direction() == null ? "" : feature.direction().strip();
        if (!direction.isBlank() && !direction.equalsIgnoreCase("nearby")) {
            label += " to the " + direction.toLowerCase(Locale.ROOT);
        } else if (!label.equals("you")) {
            label += " nearby";
        }
        return label;
    }

    public String debugBlock() {
        return """
                ENVIRONMENT PERCEPTION DEBUG
                snapshotAgeMs=%s scanRadius=%dm sampledBlocks=%d scanDurationMs=%d
                npcPosition=%.1f, %.1f, %.1f playerPosition=%s
                importantObjects=%s
                structuralFeatures=%s
                naturalFeatures=%s
                finalSemanticBlock=
                %s
                """.formatted(capturedAt.equals(Instant.EPOCH) ? "unavailable"
                        : ageMillis(Instant.now()), scanRadius, sampledBlocks, scanMillis,
                npcX, npcY, npcZ, playerX == null ? "unavailable"
                        : "%.1f, %.1f, %.1f".formatted(playerX, playerY, playerZ),
                features(importantObjects), features(structuralFeatures),
                features(naturalFeatures), semanticBlock()).strip();
    }

    private static String features(List<EnvironmentFeature> values) {
        return values.isEmpty() ? "none" : values.stream()
                .map(EnvironmentFeature::compact).collect(Collectors.joining("; "));
    }

    private static String semanticFeatures(List<EnvironmentFeature> values) {
        return values.isEmpty() ? "none" : values.stream()
                .map(EnvironmentFeature::semantic).collect(Collectors.joining("; "));
    }

    private static String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
