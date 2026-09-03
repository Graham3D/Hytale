package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Exact generated NPC speech. NPC listeners consume text directly; STT is never involved. */
public record NpcSpeechEvent(
        UUID speakerNpcId,
        UUID targetNpcId,
        Set<UUID> audience,
        String text,
        UUID conversationId,
        NpcSpeechLocation location,
        Instant timestamp,
        String gameTime,
        String intent,
        String topic,
        String emotion,
        double audibilityMeters) {

    public NpcSpeechEvent normalized() {
        if (speakerNpcId == null || conversationId == null || location == null) {
            throw new IllegalArgumentException("NPC speech requires speaker, conversation, location");
        }
        String exact = text == null ? "" : text.strip();
        if (exact.isBlank()) throw new IllegalArgumentException("NPC speech text is empty");
        return new NpcSpeechEvent(speakerNpcId, targetNpcId,
                audience == null ? Set.of() : Set.copyOf(audience), exact, conversationId,
                location, timestamp == null ? Instant.now() : timestamp,
                gameTime == null ? "unknown" : gameTime.strip(), clean(intent, 80),
                clean(topic, 120), clean(emotion, 40),
                Math.max(1.0, Math.min(32.0, audibilityMeters)));
    }

    public boolean addresses(UUID npcId) {
        return npcId != null && (npcId.equals(targetNpcId)
                || (targetNpcId == null && audience != null && audience.contains(npcId)));
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
