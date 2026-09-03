package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable cross-thread speech callback; only OrbisTurnCoordinator mutates branch state. */
public record OrbisSpeechEvent(Type type, TurnId turnId, BranchId branchId,
        ResponseId responseId, UUID npcStableId, long branchEpoch,
        SpeechChunkId speechChunkId, TtsRequestId ttsRequestId, PlaybackId playbackId,
        Instant at, Map<String, String> facts) {
    public OrbisSpeechEvent {
        if (type == null || turnId == null || branchId == null || responseId == null
                || npcStableId == null || branchEpoch < 1 || at == null) {
            throw new IllegalArgumentException("correlated speech event required");
        }
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    public enum Type {
        SPEECH_QUEUED,
        TTS_SYNTHESIZING,
        AUDIO_READY,
        PLAYBACK_QUEUED,
        SPEAKING,
        CHUNK_PLAYBACK_COMPLETE,
        SPEECH_COMPLETE,
        SPEECH_CANCELLED,
        SPEECH_INTERRUPTED,
        TTS_CANCELLED,
        TTS_RESULT_DISCARDED_STALE,
        PLAYBACK_INTERRUPTED,
        TTS_FAILED,
        PLAYBACK_FAILED,
        TIMED_OUT,
        RESOURCE_SCHEDULE_EVENT,
        CALLBACK_REJECTED_STALE
    }
}
