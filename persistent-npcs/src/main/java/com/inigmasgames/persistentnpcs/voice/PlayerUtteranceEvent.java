package com.inigmasgames.persistentnpcs.voice;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One immutable player-speech world event fanned to every eligible Immersive NPC. */
public record PlayerUtteranceEvent(
        UUID utteranceId,
        UUID playerId,
        String transcript,
        UUID worldId,
        double playerX,
        double playerY,
        double playerZ,
        Instant timestamp,
        Set<UUID> directAddressTargets,
        PlayerSpeechIntent speechIntent,
        List<EligibleNpcListener> eligibleNpcListeners,
        long endpointMillis,
        long sttMillis,
        long audienceResolutionMillis) {

    public PlayerUtteranceEvent {
        if (utteranceId == null || playerId == null) {
            throw new IllegalArgumentException("Player utterance requires stable IDs");
        }
        transcript = transcript == null ? "" : transcript.replaceAll("\\s+", " ").strip();
        timestamp = timestamp == null ? Instant.now() : timestamp;
        directAddressTargets = Set.copyOf(
                directAddressTargets == null ? Set.of() : directAddressTargets);
        speechIntent = speechIntent == null ? PlayerSpeechIntent.CONVERSATION : speechIntent;
        eligibleNpcListeners = List.copyOf(
                eligibleNpcListeners == null ? List.of() : eligibleNpcListeners);
    }

    public boolean isRemoteHailFor(UUID npcId) {
        return directAddressTargets.contains(npcId) && eligibleNpcListeners.stream()
                .anyMatch(listener -> listener.npcId().equals(npcId)
                        && listener.rangeClass() == UtteranceRangeClass.REMOTE_HAIL);
    }
}
