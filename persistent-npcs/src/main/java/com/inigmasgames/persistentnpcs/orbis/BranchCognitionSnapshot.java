package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable input captured before branch cognition leaves the coordinator. */
public record BranchCognitionSnapshot(
        TurnId turnId,
        BranchId branchId,
        ResponseId responseId,
        ProviderRequestId providerRequestId,
        UUID utteranceId,
        UUID npcStableId,
        String npcName,
        UUID playerStableId,
        UUID worldId,
        String canonicalTranscript,
        boolean directAddress,
        boolean responseOwner,
        String distanceBand,
        String directionFromPlayer,
        UtteranceRangeClass rangeClass,
        SpeechProjection projection,
        PlayerSpeechIntent speechIntent,
        Map<String, String> audienceState,
        String authoritativeWorldSnapshotRef,
        long endpointMillis,
        long sttMillis,
        long audienceResolutionMillis,
        String provider,
        String model,
        String endpoint,
        String deferredConversationContext,
        long branchEpoch,
        CancellationScope cancellation,
        Instant capturedAt) {

    public BranchCognitionSnapshot {
        java.util.Objects.requireNonNull(turnId, "turnId");
        java.util.Objects.requireNonNull(branchId, "branchId");
        java.util.Objects.requireNonNull(responseId, "responseId");
        java.util.Objects.requireNonNull(providerRequestId, "providerRequestId");
        java.util.Objects.requireNonNull(utteranceId, "utteranceId");
        java.util.Objects.requireNonNull(npcStableId, "npcStableId");
        java.util.Objects.requireNonNull(playerStableId, "playerStableId");
        java.util.Objects.requireNonNull(worldId, "worldId");
        canonicalTranscript = canonicalTranscript == null ? "" : canonicalTranscript.strip();
        npcName = npcName == null ? "" : npcName;
        distanceBand = distanceBand == null ? "unknown" : distanceBand;
        directionFromPlayer = directionFromPlayer == null ? "unknown" : directionFromPlayer;
        audienceState = Map.copyOf(audienceState == null ? Map.of() : audienceState);
        authoritativeWorldSnapshotRef = authoritativeWorldSnapshotRef == null
                ? "" : authoritativeWorldSnapshotRef;
        provider = provider == null ? "unknown" : provider;
        model = model == null ? "unknown" : model;
        endpoint = endpoint == null ? "" : endpoint;
        deferredConversationContext = deferredConversationContext == null
                ? "" : deferredConversationContext.strip();
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
