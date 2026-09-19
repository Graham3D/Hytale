package com.inigmasgames.taverns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Threshold-aware Tavern Comfort plus the design-document Relaxed duration. */
record ComfortScore(
        int totalComfort,
        int relaxedMinutes,
        Map<ComfortCategory, Integer> categoryValues,
        Map<ComfortCategory, Integer> currentCounts,
        Map<ComfortCategory, Integer> requiredCounts) {

    static ComfortScore calculate(
            List<RegisteredComfortObject> objects,
            int eligibleFloorArea,
            Map<ComfortCategory, ComfortThreshold> thresholds) {
        EnumMap<ComfortCategory, Integer> values = new EnumMap<>(ComfortCategory.class);
        EnumMap<ComfortCategory, Integer> current = new EnumMap<>(ComfortCategory.class);
        EnumMap<ComfortCategory, Integer> required = new EnumMap<>(ComfortCategory.class);

        for (ComfortCategory category : ComfortCategory.values()) {
            ComfortThreshold threshold = thresholds.getOrDefault(
                    category,
                    ComfortThreshold.designDefaults().get(category));
            if (!threshold.enabled()) {
                values.put(category, 0);
                current.put(category, 0);
                required.put(category, threshold.requiredCount(eligibleFloorArea));
                continue;
            }
            List<RegisteredComfortObject> eligible = eligibleForCategory(
                    objects, category, threshold.countMode());
            int requiredCount = threshold.requiredCount(eligibleFloorArea);
            int categoryComfort = eligible.size() < requiredCount
                    ? 0
                    : eligible.get(requiredCount - 1).comfort();
            values.put(category, categoryComfort);
            current.put(category, eligible.size());
            required.put(category, requiredCount);
        }

        int totalComfort = values.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int relaxed = totalComfort == 0
                ? 0
                : (int) Math.round(2.46 * Math.sqrt(totalComfort));
        return new ComfortScore(
                totalComfort,
                relaxed,
                Map.copyOf(values),
                Map.copyOf(current),
                Map.copyOf(required));
    }

    static List<RegisteredComfortObject> contributors(
            List<RegisteredComfortObject> objects,
            ComfortScore score,
            Map<ComfortCategory, ComfortThreshold> thresholds) {
        List<RegisteredComfortObject> contributors = new ArrayList<>();
        for (ComfortCategory category : ComfortCategory.values()) {
            if (score.categoryValues().getOrDefault(category, 0) <= 0) {
                continue;
            }
            ComfortThreshold threshold = thresholds.getOrDefault(
                    category,
                    ComfortThreshold.designDefaults().get(category));
            List<RegisteredComfortObject> eligible = eligibleForCategory(
                    objects, category, threshold.countMode());
            int count = score.requiredCounts().getOrDefault(category, 1);
            contributors.addAll(eligible.subList(0, Math.min(count, eligible.size())));
        }
        return List.copyOf(contributors);
    }

    private static List<RegisteredComfortObject> eligibleForCategory(
            List<RegisteredComfortObject> objects,
            ComfortCategory category,
            ComfortCountMode countMode) {
        Comparator<RegisteredComfortObject> order = Comparator
                .comparingInt(RegisteredComfortObject::comfort).reversed()
                .thenComparing(RegisteredComfortObject::assetId)
                .thenComparingInt(RegisteredComfortObject::x)
                .thenComparingInt(RegisteredComfortObject::y)
                .thenComparingInt(RegisteredComfortObject::z);
        List<RegisteredComfortObject> sorted = objects.stream()
                .filter(object -> object.valid()
                        && object.comfort() > 0
                        && object.category() == category)
                .sorted(order)
                .toList();
        if (countMode == ComfortCountMode.PLACED_INSTANCES) {
            return sorted;
        }

        Map<String, RegisteredComfortObject> distinct = new LinkedHashMap<>();
        for (RegisteredComfortObject object : sorted) {
            distinct.putIfAbsent(object.assetId(), object);
        }
        return List.copyOf(distinct.values());
    }
}
