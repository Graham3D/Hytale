package com.inigmasgames.persistentnpcs.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable self/world/attention/intention summary; raw ECS objects never enter persistence. */
public record NpcCognitionRuntimeState(
        UUID npcId,
        SimulationTier simulationTier,
        PersistentNpcIntent activeIntent,
        List<GroundedStimulus> attendedWorldFacts,
        Map<String, Instant> cooldowns,
        String currentNeed,
        String currentGoal,
        String attentionReason,
        List<String> rejectedCandidates,
        String lastActionResult,
        Instant lastEvaluatedAt,
        Instant lastReflectionAt) {

    public static NpcCognitionRuntimeState initial(UUID npcId) {
        return new NpcCognitionRuntimeState(npcId, SimulationTier.ACTIVE, null,
                List.of(), Map.of(), "none", "remain safe and meaningfully occupied",
                "none", List.of(), "none", Instant.EPOCH, Instant.EPOCH);
    }
}
