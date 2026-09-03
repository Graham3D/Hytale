package com.inigmasgames.persistentnpcs.perception;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable engine-facing capture. This type is diagnostic/action input only and must never be
 * serialized into an LLM prompt or ordinary NPC speech.
 */
public record RawPerceptionSnapshot(
        UUID responseId,
        Instant capturedAt,
        String captureThread,
        long captureMillis,
        NpcPerceptionSnapshot engineSnapshot,
        int totalBlockSamples,
        List<EnvironmentSample> boundedBlockObservations) {

    private static final int MAX_DEBUG_BLOCKS = 160;

    public RawPerceptionSnapshot {
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        captureThread = captureThread == null ? "unknown" : captureThread;
        engineSnapshot = engineSnapshot == null
                ? NpcPerceptionSnapshot.unavailable(null) : engineSnapshot;
        boundedBlockObservations = List.copyOf(boundedBlockObservations == null
                ? List.of() : boundedBlockObservations.stream().limit(MAX_DEBUG_BLOCKS).toList());
        totalBlockSamples = Math.max(totalBlockSamples, boundedBlockObservations.size());
        captureMillis = Math.max(0, captureMillis);
    }

    public static RawPerceptionSnapshot unavailable(UUID responseId, UUID npcId) {
        return new RawPerceptionSnapshot(responseId, Instant.now(), Thread.currentThread().getName(),
                0, NpcPerceptionSnapshot.unavailable(npcId), 0, List.of());
    }

    public static RawPerceptionSnapshot fromLegacy(
            UUID responseId, NpcPerceptionSnapshot snapshot) {
        return new RawPerceptionSnapshot(responseId, Instant.now(), "detached-legacy", 0,
                snapshot, snapshot == null || snapshot.environment() == null
                        ? 0 : snapshot.environment().sampledBlocks(), List.of());
    }

    public String debugBlock() {
        NpcPerceptionSnapshot value = engineSnapshot;
        StringBuilder text = new StringBuilder()
                .append("captureThread=").append(captureThread)
                .append(" captureMs=").append(captureMillis)
                .append(" capturedAt=").append(capturedAt)
                .append('\n').append("npcId=").append(value.npcId())
                .append(" entityId=").append(value.npcEntityId())
                .append(" worldId=").append(value.worldId())
                .append('\n').append("npcPosition=")
                .append("%.2f, %.2f, %.2f".formatted(value.x(), value.y(), value.z()))
                .append(" gameTime=").append(value.gameTime())
                .append('\n').append("players=").append(value.nearbyPlayers())
                .append('\n').append("npcs=").append(value.nearbyNpcs())
                .append('\n').append("hostiles=").append(value.nearbyHostiles())
                .append('\n').append("items=").append(value.nearbyItems())
                .append('\n').append("interactables=").append(value.nearbyInteractables())
                .append('\n').append("stations=").append(value.nearbyCraftingStations())
                .append('\n').append("sampleCount=").append(totalBlockSamples)
                .append(" retainedRawBlocks=").append(boundedBlockObservations.size());
        boundedBlockObservations.stream().limit(40).forEach(sample -> text.append('\n')
                .append(sample.assetId()).append(" @ ")
                .append("%.1f, %.1f, %.1f".formatted(sample.x(), sample.y(), sample.z()))
                .append(" group=").append(sample.group())
                .append(" model=").append(sample.model()));
        return text.toString();
    }
}
