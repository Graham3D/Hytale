package com.inigmasgames.persistentnpcs.cognition;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Persisted derived affect. It is descriptive, never authoritative world state. */
public record NpcEmotionalState(
        UUID npcId,
        NpcEmotion emotion,
        double intensity,
        Instant updatedAt,
        String source) {

    private static final double DECAY_HALF_LIFE_MINUTES = 12.0;

    public NpcEmotionalState normalized() {
        return new NpcEmotionalState(npcId, emotion == null ? NpcEmotion.CALM : emotion,
                Math.max(0.0, Math.min(1.0, intensity)),
                updatedAt == null ? Instant.EPOCH : updatedAt,
                source == null ? "" : source.strip());
    }

    public NpcEmotionalState decayed(Instant now) {
        NpcEmotionalState value = normalized();
        if (value.emotion == NpcEmotion.CALM || value.updatedAt.equals(Instant.EPOCH)) {
            return value;
        }
        double minutes = Math.max(0.0,
                Duration.between(value.updatedAt, now).toMillis() / 60_000.0);
        double decayed = value.intensity * Math.pow(0.5,
                minutes / DECAY_HALF_LIFE_MINUTES);
        return decayed < 0.08
                ? new NpcEmotionalState(npcId, NpcEmotion.CALM, 0.0, now, "baseline")
                : new NpcEmotionalState(npcId, emotion, decayed, updatedAt, source);
    }
}
