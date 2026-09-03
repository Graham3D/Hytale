package com.inigmasgames.persistentnpcs.social;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcSocialAttentionState {
    private final UUID npcId;
    private final ConcurrentHashMap<UUID, Instant> perceivedPlayers = new ConcurrentHashMap<>();
    private volatile UUID focusedPlayerUuid;
    private volatile UUID conversationSessionId;
    private volatile NpcListenState listenState = NpcListenState.IDLE;
    private volatile Instant lastSeenAt;

    public NpcSocialAttentionState(UUID npcId) {
        this.npcId = npcId;
    }

    public UUID npcId() { return npcId; }
    public Map<UUID, Instant> perceivedPlayers() { return Map.copyOf(perceivedPlayers); }
    public UUID focusedPlayerUuid() { return focusedPlayerUuid; }
    public UUID conversationSessionId() { return conversationSessionId; }
    public NpcListenState listenState() { return listenState; }
    public Instant lastSeenAt() { return lastSeenAt; }

    public void perceive(UUID playerId, Instant now) {
        perceivedPlayers.put(playerId, now);
        lastSeenAt = now;
    }

    public void forget(UUID playerId) {
        perceivedPlayers.remove(playerId);
    }

    public void focus(UUID playerId, UUID sessionId, Instant now) {
        focusedPlayerUuid = playerId;
        conversationSessionId = sessionId;
        listenState = NpcListenState.LISTENING;
        lastSeenAt = now;
    }

    public void release() {
        focusedPlayerUuid = null;
        conversationSessionId = null;
        listenState = NpcListenState.IDLE;
    }
}
