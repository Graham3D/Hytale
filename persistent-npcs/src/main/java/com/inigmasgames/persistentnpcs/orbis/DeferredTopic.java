package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Session-only semantic context for a response interrupted before delivery completed. */
public record DeferredTopic(
        UUID npcStableId,
        UUID playerStableId,
        TurnId sourceTurnId,
        ResponseId sourceResponseId,
        String topic,
        String selectedIntent,
        List<String> deliveredChunks,
        String partialChunk,
        List<String> undeliveredChunks,
        Instant interruptedAt,
        String interruptionReason,
        Instant expiresAt,
        int remainingTurns) {

    public DeferredTopic {
        if (npcStableId == null || playerStableId == null || sourceTurnId == null
                || sourceResponseId == null) {
            throw new IllegalArgumentException("Deferred topic requires stable correlation");
        }
        topic = compact(topic, 500);
        selectedIntent = compact(selectedIntent, 100);
        deliveredChunks = compactList(deliveredChunks);
        partialChunk = compact(partialChunk, 500);
        undeliveredChunks = compactList(undeliveredChunks);
        interruptedAt = interruptedAt == null ? Instant.now() : interruptedAt;
        interruptionReason = compact(interruptionReason, 80);
        expiresAt = expiresAt == null ? interruptedAt.plusSeconds(120) : expiresAt;
        remainingTurns = Math.max(0, remainingTurns);
    }

    public DeferredTopic nextTurn() {
        return new DeferredTopic(npcStableId, playerStableId, sourceTurnId, sourceResponseId,
                topic, selectedIntent, deliveredChunks, partialChunk, undeliveredChunks,
                interruptedAt, interruptionReason, expiresAt, remainingTurns - 1);
    }

    public String cognitionSummary(String npcName) {
        String who = npcName == null || npcName.isBlank() ? "The NPC" : npcName;
        String heard = deliveredChunks.isEmpty() ? "No complete speech chunk was delivered."
                : "The player heard: " + String.join(" ", deliveredChunks);
        String partial = partialChunk.isBlank() ? ""
                : " One following chunk was interrupted during playback and was only partially heard.";
        return who + " was interrupted while discussing: " + topic + ". " + heard
                + partial + " Undelivered wording is not player-known; answer the new request first "
                + "and only return to this subject if it remains relevant.";
    }

    private static List<String> compactList(List<String> values) {
        return (values == null ? List.<String>of() : values).stream()
                .map(value -> compact(value, 500)).filter(value -> !value.isBlank()).toList();
    }

    private static String compact(String value, int max) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
