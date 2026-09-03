package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.orbis.TurnIngressSource;
import java.time.Instant;
import java.util.UUID;

/** One authoritative transcript plus immutable ingress metadata, produced once per utterance. */
public record TranscribedPlayerUtterance(
        UUID utteranceId,
        UUID playerId,
        String transcript,
        UUID worldId,
        double playerX,
        double playerY,
        double playerZ,
        Instant timestamp,
        long firstFrameNanos,
        long endpointNanos,
        long sttStartedNanos,
        long sttCompletedNanos,
        TurnIngressSource ingressSource,
        UUID originalPhysicalUtteranceId) {

    public TranscribedPlayerUtterance {
        if (utteranceId == null || playerId == null) {
            throw new IllegalArgumentException("Transcribed utterance requires stable IDs");
        }
        transcript = transcript == null ? "" : transcript.replaceAll("\\s+", " ").strip();
        timestamp = timestamp == null ? Instant.now() : timestamp;
        ingressSource = ingressSource == null ? TurnIngressSource.UNKNOWN_TEXT : ingressSource;
        if (ingressSource.physicalVoice() && originalPhysicalUtteranceId == null) {
            throw new IllegalArgumentException(
                    "Voice ingress requires the original physical utterance ID");
        }
    }

    /** Source-compatible constructor for existing manual/text harnesses. */
    public TranscribedPlayerUtterance(UUID utteranceId, UUID playerId, String transcript,
            UUID worldId, double playerX, double playerY, double playerZ, Instant timestamp,
            long firstFrameNanos, long endpointNanos, long sttStartedNanos,
            long sttCompletedNanos) {
        this(utteranceId, playerId, transcript, worldId, playerX, playerY, playerZ,
                timestamp, firstFrameNanos, endpointNanos, sttStartedNanos,
                sttCompletedNanos, TurnIngressSource.MANUAL_SUBMISSION, null);
    }

    public String ingressProvenance() { return ingressSource.chain(); }

    public long endpointMillis() {
        return between(firstFrameNanos, endpointNanos);
    }

    public long sttMillis() {
        return between(sttStartedNanos, sttCompletedNanos);
    }

    private static long between(long start, long end) {
        return start <= 0 || end <= 0 ? -1
                : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0, end - start));
    }
}
