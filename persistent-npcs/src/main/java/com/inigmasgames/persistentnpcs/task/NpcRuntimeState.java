package com.inigmasgames.persistentnpcs.task;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Persisted simulation state, separate from authored profile data. */
public record NpcRuntimeState(
        UUID npcId,
        UUID worldId,
        String logicalLocation,
        String taskType,
        String destination,
        Instant expectedArrival,
        String scheduleState,
        Map<String, Integer> inventorySummary,
        long economicBalance,
        Instant lastAdvanced) {

    public NpcRuntimeState normalized() {
        return new NpcRuntimeState(npcId, worldId,
                logicalLocation == null ? "unknown" : logicalLocation,
                taskType == null ? "IDLE" : taskType,
                destination == null ? "" : destination,
                expectedArrival, scheduleState == null ? "IDLE" : scheduleState,
                inventorySummary == null ? Map.of() : Map.copyOf(inventorySummary),
                economicBalance, lastAdvanced == null ? Instant.EPOCH : lastAdvanced);
    }
}
