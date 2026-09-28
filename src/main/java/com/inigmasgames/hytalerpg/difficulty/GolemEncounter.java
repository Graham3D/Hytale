package com.inigmasgames.hytalerpg.difficulty;

import java.util.*;

/** Frozen source ownership travels with the existing encounter journal across restart. */
public record GolemEncounter(DifficultyId difficulty,String milestone,String profileId,String sourceId,Source source) {
    public enum Source { NATIVE_SPAWN_MARKER, OPERATOR_CAMPAIGN_PLACEMENT }
    public GolemEncounter {
        Objects.requireNonNull(difficulty);Objects.requireNonNull(source);
        if(!GolemMilestones.REQUIRED_V1.contains(milestone)||!"rpg.golem-milestones.v1".equals(profileId)
                ||sourceId==null||sourceId.isBlank()||sourceId.length()>256)throw new IllegalArgumentException("INVALID_GOLEM_ENCOUNTER");
    }
}
