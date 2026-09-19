package com.inigmasgames.persistentnpcs.profile;

import java.util.List;

public record OccupationDefinition(
        String id,
        List<String> knowledgeDomains,
        List<String> capabilities,
        String defaultWorkplace,
        List<NpcScheduleEntry> defaultSchedule,
        List<String> behavioralPreferences) {
}
