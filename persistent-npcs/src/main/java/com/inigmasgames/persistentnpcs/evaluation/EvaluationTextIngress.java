package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.orbis.OrbisTurnCoordinator;
import com.inigmasgames.persistentnpcs.orbis.TurnIngressSource;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative evaluation text entering the production accepted-transcript boundary. */
public final class EvaluationTextIngress {
    private final OrbisTurnCoordinator coordinator;
    private final ConcurrentHashMap<UUID, List<UUID>> audiences = new ConcurrentHashMap<>();

    public EvaluationTextIngress(OrbisTurnCoordinator coordinator) {
        this.coordinator = java.util.Objects.requireNonNull(coordinator, "coordinator");
    }

    public UUID submit(UUID playerId, UUID worldId, double x, double y, double z,
            String text, List<UUID> audience) {
        UUID utteranceId = UUID.randomUUID();
        submit(utteranceId, playerId, worldId, x, y, z, text, audience);
        return utteranceId;
    }

    public void submit(UUID utteranceId, UUID playerId, UUID worldId,
            double x, double y, double z, String text, List<UUID> audience) {
        if (playerId == null || worldId == null || text == null || text.isBlank()) {
            throw new IllegalArgumentException("complete evaluation utterance required");
        }
        if (utteranceId == null) throw new IllegalArgumentException("utteranceId required");
        audiences.put(utteranceId, List.copyOf(audience == null ? List.of() : audience));
        long now = System.nanoTime();
        coordinator.accept(new TranscribedPlayerUtterance(utteranceId, playerId, text,
                worldId, x, y, z, Instant.now(), now, now, now, now,
                TurnIngressSource.AUTHORITATIVE_EVALUATION_TEXT, null));
    }

    public List<UUID> audience(UUID utteranceId) {
        return audiences.getOrDefault(utteranceId, List.of());
    }

    public void completed(UUID utteranceId) { audiences.remove(utteranceId); }
    public Map<UUID, List<UUID>> pending() { return Map.copyOf(audiences); }
}
