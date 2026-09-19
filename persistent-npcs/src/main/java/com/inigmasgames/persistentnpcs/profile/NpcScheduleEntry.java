package com.inigmasgames.persistentnpcs.profile;

import java.util.List;

/** Authored, game-time daily schedule rule; runtime progress is stored separately. */
public record NpcScheduleEntry(
        int startHour,
        int endHour,
        String taskType,
        String location,
        List<String> days) {

    public NpcScheduleEntry normalized() {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("Schedule hours must be in the range 0-23");
        }
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("Schedule taskType is required");
        }
        return new NpcScheduleEntry(startHour, endHour,
                taskType.strip().toUpperCase(java.util.Locale.ROOT),
                location == null ? "" : location.strip(),
                days == null ? List.of() : List.copyOf(days));
    }
}
