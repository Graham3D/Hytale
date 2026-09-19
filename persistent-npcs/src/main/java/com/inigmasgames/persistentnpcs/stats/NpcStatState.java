package com.inigmasgames.persistentnpcs.stats;

import java.util.Map;
import java.util.UUID;

public record NpcStatState(int schemaVersion, UUID stableNpcId, long revision, String savedAt,
        String captureReason, Map<String, NpcStatRecord> stats) {
    public NpcStatState {
        if (schemaVersion != 1 || stableNpcId == null || revision < 1 || savedAt == null
                || captureReason == null || stats == null)
            throw new IllegalArgumentException("Invalid NPC stat state header");
        java.time.Instant.parse(savedAt);
        if (!java.util.Set.of("CREATE", "MIGRATION_FROM_LIVE", "MIGRATION_FROM_BASELINE", "CHECKPOINT",
                "PRE_REMOVE", "WORLD_UNLOAD", "PLUGIN_SHUTDOWN", "OPERATOR_RESET").contains(captureReason))
            throw new IllegalArgumentException("Invalid NPC stat capture reason");
        stats = Map.copyOf(stats);
        if (stats.size() > 256 || stats.keySet().stream().anyMatch(id -> id.isBlank() || id.length() > 200))
            throw new IllegalArgumentException("Invalid stat asset IDs");
    }
}
