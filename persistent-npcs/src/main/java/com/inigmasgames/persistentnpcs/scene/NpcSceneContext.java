package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.UUID;

/** Authoritative, already-filtered context for one bounded NPC-to-NPC scene. */
public record NpcSceneContext(
        String event,
        String perception,
        String activeTask,
        double distance,
        Instant now,
        UUID worldId,
        double x,
        double y,
        double z,
        String gameTime,
        NpcActivityState firstState,
        NpcActivityState secondState,
        boolean lineOfSight,
        String firstPrivateContext,
        String secondPrivateContext) {

    public NpcSceneContext(
            String event, String perception, String activeTask, double distance, Instant now) {
        this(event, perception, activeTask, distance, now, null, 0, 0, 0, "unknown",
                NpcActivityState.IDLE, NpcActivityState.IDLE, true, "None.", "None.");
    }

    public NpcSceneContext normalized() {
        return new NpcSceneContext(text(event), text(perception), text(activeTask),
                Math.max(0, distance), now == null ? Instant.now() : now,
                worldId, finite(x), finite(y), finite(z), text(gameTime),
                firstState == null ? NpcActivityState.IDLE : firstState,
                secondState == null ? NpcActivityState.IDLE : secondState,
                lineOfSight, text(firstPrivateContext), text(secondPrivateContext));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "None." : value.strip();
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0;
    }
}
