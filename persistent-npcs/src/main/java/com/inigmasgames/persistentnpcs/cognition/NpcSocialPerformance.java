package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import java.util.UUID;

@FunctionalInterface
public interface NpcSocialPerformance {
    void perform(UUID npcId, UUID playerId, NpcResponsePlan plan, EnvironmentSnapshot environment);

    static NpcSocialPerformance unavailable() {
        return (npcId, playerId, plan, environment) -> { };
    }
}
