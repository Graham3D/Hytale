package com.inigmasgames.persistentnpcs.background;

import com.inigmasgames.persistentnpcs.autonomy.SimulationTier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistent background-life state, separate from authored profile and loaded ECS state. */
public record BackgroundLifeState(
        UUID npcId,
        UUID worldId,
        SimulationTier tier,
        BackgroundActivityType activity,
        String logicalLocation,
        String destination,
        String goal,
        Instant activityStartedAt,
        Instant activityEndsAt,
        UUID inProgressOperation,
        Instant lastAdvancedAt,
        Instant nextDecisionAt,
        List<BackgroundLifeEvent> history) {

    public static BackgroundLifeState initial(UUID npcId, UUID worldId, String home) {
        return new BackgroundLifeState(npcId, worldId, SimulationTier.DORMANT,
                BackgroundActivityType.IDLE, clean(home, "home"), "",
                "follow authored routine", Instant.EPOCH, Instant.EPOCH,
                null, Instant.EPOCH, Instant.EPOCH, List.of());
    }

    public BackgroundLifeState normalized() {
        return new BackgroundLifeState(npcId, worldId,
                tier == null ? SimulationTier.DORMANT : tier,
                activity == null ? BackgroundActivityType.IDLE : activity,
                clean(logicalLocation, "unknown"), clean(destination, ""),
                clean(goal, "follow authored routine"),
                activityStartedAt == null ? Instant.EPOCH : activityStartedAt,
                activityEndsAt == null ? Instant.EPOCH : activityEndsAt,
                inProgressOperation,
                lastAdvancedAt == null ? Instant.EPOCH : lastAdvancedAt,
                nextDecisionAt == null ? Instant.EPOCH : nextDecisionAt,
                history == null ? List.of() : List.copyOf(history));
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
