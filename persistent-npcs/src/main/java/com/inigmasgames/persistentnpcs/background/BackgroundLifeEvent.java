package com.inigmasgames.persistentnpcs.background;

import java.time.Instant;
import java.util.UUID;

/** Logical off-screen history. It never claims that a visible physical action occurred. */
public record BackgroundLifeEvent(
        UUID eventId,
        BackgroundActivityType activity,
        String location,
        String summary,
        Instant startedAt,
        Instant completedAt,
        String source,
        boolean physicallySimulated) {
}
