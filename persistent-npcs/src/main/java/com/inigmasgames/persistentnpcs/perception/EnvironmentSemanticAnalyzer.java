package com.inigmasgames.persistentnpcs.perception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Converts real asset metadata into a small, priority-ranked scene description. */
public final class EnvironmentSemanticAnalyzer {
    private static final int MAX_POI = 6;
    private static final int MAX_STRUCTURE = 5;
    private static final int MAX_NATURAL = 4;

    public EnvironmentSnapshot summarize(
            UUID worldId,
            Instant capturedAt,
            double npcX,
            double npcY,
            double npcZ,
            Double playerX,
            Double playerY,
            Double playerZ,
            int radius,
            List<EnvironmentSample> samples,
            long scanMillis) {
        Map<String, Aggregate> pois = new HashMap<>();
        Map<String, Aggregate> structures = new HashMap<>();
        Map<String, Aggregate> nature = new HashMap<>();
        Map<String, Integer> materials = new HashMap<>();
        Map<String, Double> surfaceHeights = new HashMap<>();
        int solidCount = 0;
        int grassLike = 0;
        int stoneLike = 0;

        for (EnvironmentSample sample : samples) {
            String identity = joined(sample).toLowerCase(Locale.ROOT);
            double distance = distance(npcX, npcY, npcZ, sample);
            String direction = direction(sample.x() - npcX, sample.z() - npcZ);
            if (!sample.fluid()) {
                solidCount++;
                String column = Math.floor(sample.x()) + ":" + Math.floor(sample.z());
                surfaceHeights.merge(column, sample.y(), Math::max);
            }

            if (has(identity, "portal", "gateway", "teleporter")) {
                add(pois, "portal", sample.light() ? "glowing portal" : "portal",
                        100, distance, direction);
            } else if (sample.door()) {
                add(pois, "door", "door", 80, distance, direction);
            } else if (sample.craftingStation()) {
                add(pois, "crafting_station", label(sample.assetId(), "crafting station"),
                        75, distance, direction);
            } else if (sample.container()) {
                add(pois, "container", label(sample.assetId(), "container"),
                        70, distance, direction);
            } else if (sample.furniture()) {
                add(pois, "furniture", label(sample.assetId(), "furniture"),
                        50, distance, direction);
            } else if (sample.light()) {
                add(pois, "light", label(sample.assetId(), "light source"),
                        45, distance, direction);
            } else if (sample.interactable()) {
                add(pois, "interactable", label(sample.assetId(), "interactable"),
                        40, distance, direction);
            }

            if (sample.fluid() || has(identity, "water", "river", "ocean")) {
                add(nature, "water", "water", 65, distance, direction);
            }
            if (has(identity, "lava", "magma")) {
                add(nature, "lava", "lava", 70, distance, direction);
            }
            if (has(identity, "leaf", "leaves", "vine", "moss", "lichen", "ivy",
                    "plant", "flower", "bush", "shrub", "fern", "cactus")) {
                add(nature, "vegetation", has(identity, "moss", "lichen", "ivy", "vine")
                        ? "vegetation/moss" : "vegetation", 35, distance, direction);
            }
            if (has(identity, "tree", "log", "trunk")) {
                add(nature, "tree", "trees/wooded vegetation", 36, distance, direction);
            }

            boolean grass = has(identity, "grass", "turf");
            boolean stone = has(identity, "stone", "rock", "brick", "cobble", "masonry",
                    "slate", "granite", "marble", "basalt", "sandstone");
            if (sample.fluid()) {
                materials.merge(has(identity, "lava", "magma") ? "lava" : "water/fluid",
                        1, Integer::sum);
            } else if (stone) {
                materials.merge("stone/masonry", 1, Integer::sum);
            } else if (grass) {
                materials.merge("grass/turf", 1, Integer::sum);
            } else if (has(identity, "dirt", "soil", "mud", "sand", "gravel")) {
                materials.merge("soil/ground", 1, Integer::sum);
            } else if (has(identity, "wood", "timber", "plank", "log", "trunk")) {
                materials.merge("wood", 1, Integer::sum);
            } else if (has(identity, "leaf", "leaves", "vine", "moss", "plant", "flower")) {
                materials.merge("vegetation", 1, Integer::sum);
            }
            grassLike += grass ? 1 : 0;
            stoneLike += stone ? 1 : 0;
            if (has(identity, "ruin", "ruins")) {
                add(structures, "ruins", "stone ruins", 65, distance, direction);
            } else if (has(identity, "column", "pillar")) {
                add(structures, "column", stone ? "stone columns/pillars" : "columns/pillars",
                        58, distance, direction);
            } else if (has(identity, "wall", "rampart")) {
                add(structures, "wall", stone ? "stone walls" : "constructed walls",
                        52, distance, direction);
            } else if (stone) {
                add(structures, "stone", "stone/masonry structures", 30, distance, direction);
            } else if (has(identity, "wood", "timber", "plank")) {
                add(structures, "wood", "wooden construction", 28, distance, direction);
            }
        }

        if (playerX != null && playerY != null && playerZ != null) {
            double dx = playerX - npcX;
            double dy = playerY - npcY;
            double dz = playerZ - npcZ;
            add(pois, "player", "focused player", 90,
                    Math.sqrt(dx * dx + dy * dy + dz * dz), direction(dx, dz));
        }

        String terrain;
        boolean flatSurface = !surfaceHeights.isEmpty()
                && surfaceHeights.values().stream().mapToDouble(Double::doubleValue).max()
                        .orElse(0)
                - surfaceHeights.values().stream().mapToDouble(Double::doubleValue).min()
                        .orElse(0) <= 1.25;
        if (solidCount == 0) {
            terrain = "unknown terrain";
        } else if (stoneLike >= Math.max(12, grassLike / 2)) {
            terrain = nature.containsKey("vegetation")
                    ? "a constructed stone/masonry area with vegetation"
                    : "a constructed stone/masonry area";
        } else if (grassLike >= Math.max(8, stoneLike * 2)) {
            terrain = flatSurface ? "open flat grassy terrain" : "open grassy terrain";
        } else if (nature.containsKey("water")) {
            terrain = "mixed terrain near water";
        } else {
            terrain = "mixed nearby terrain";
        }

        LinkedHashMap<String, Integer> dominant = new LinkedHashMap<>();
        materials.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5).forEach(entry -> dominant.put(entry.getKey(), entry.getValue()));
        return new EnvironmentSnapshot(worldId, capturedAt, npcX, npcY, npcZ,
                playerX, playerY, playerZ, radius, samples.size(), scanMillis,
                "not exposed by the current loaded-world accessor", terrain,
                features(pois, MAX_POI), features(structures, MAX_STRUCTURE),
                features(nature, MAX_NATURAL), dominant);
    }

    private static List<EnvironmentFeature> features(Map<String, Aggregate> values, int maximum) {
        return values.values().stream()
                .sorted(Comparator.comparingInt(Aggregate::importance).reversed()
                        .thenComparingDouble(Aggregate::distance))
                .limit(maximum)
                .map(value -> new EnvironmentFeature(value.category, value.label, value.count,
                        value.distance, value.direction, value.importance))
                .toList();
    }

    private static void add(Map<String, Aggregate> target, String category, String label,
            int importance, double distance, String direction) {
        target.compute(category, (ignored, existing) -> existing == null
                ? new Aggregate(category, label, 1, distance, direction, importance)
                : existing.add(distance, direction, importance));
    }

    private static String joined(EnvironmentSample sample) {
        return String.join(" ", safe(sample.assetId()), safe(sample.group()),
                safe(sample.material()), safe(sample.model()));
    }

    private static boolean has(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static double distance(double x, double y, double z, EnvironmentSample sample) {
        double dx = sample.x() - x;
        double dy = sample.y() - y;
        double dz = sample.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String direction(double dx, double dz) {
        if (Math.hypot(dx, dz) < 1.5) {
            return "nearby";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0) {
            degrees += 360;
        }
        String[] names = {"north", "northeast", "east", "southeast",
                "south", "southwest", "west", "northwest"};
        return names[(int) Math.round(degrees / 45.0) % names.length];
    }

    private static String label(String assetId, String fallback) {
        String value = cleanLabel(assetId);
        return value.isBlank() ? fallback : value;
    }

    private static String cleanLabel(String value) {
        return safe(value).replaceAll("[_/]+", " ").replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Aggregate(String category, String label, int count, double distance,
            String direction, int importance) {
        private Aggregate add(double candidateDistance, String candidateDirection,
                int candidateImportance) {
            return candidateDistance < distance
                    ? new Aggregate(category, label, count + 1, candidateDistance,
                            candidateDirection, Math.max(importance, candidateImportance))
                    : new Aggregate(category, label, count + 1, distance, direction,
                            Math.max(importance, candidateImportance));
        }
    }
}
