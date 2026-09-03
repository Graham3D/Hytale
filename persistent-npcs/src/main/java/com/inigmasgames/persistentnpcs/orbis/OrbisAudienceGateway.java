package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import com.inigmasgames.persistentnpcs.voice.SttSemanticCorrector;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.UUID;

/** Existing Hytale hearing/arbitration is exposed to Orbis only through this adapter. */
public interface OrbisAudienceGateway {
    CompletableFuture<PlayerUtteranceAudienceService.Resolution> resolve(
            TranscribedPlayerUtterance utterance);

    /** Deterministic correction only; implementations must never call an AI provider. */
    default SttSemanticCorrector.Correction correctTranscript(UUID playerId, String raw) {
        return new SttSemanticCorrector.Correction(raw, raw, false, 1.0,
                "CORRECTION_ADAPTER_UNAVAILABLE");
    }

    /** Speculative static context only; never commits transcript, memory, action, or truth. */
    default CompletableFuture<Map<String, String>> prefetch(UUID playerId, UUID worldId) {
        return CompletableFuture.completedFuture(Map.of("status", "UNAVAILABLE"));
    }
}
