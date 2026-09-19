package com.inigmasgames.persistentnpcs.conversation;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationSessionManager {
    private final Duration idleTimeout;
    private final com.inigmasgames.persistentnpcs.memory.MemoryStore memories;
    private final ConcurrentHashMap<SessionKey, ConversationSession> sessions =
            new ConcurrentHashMap<>();

    public ConversationSessionManager(Duration idleTimeout) {
        this(idleTimeout, null);
    }

    public ConversationSessionManager(Duration idleTimeout,
            com.inigmasgames.persistentnpcs.memory.MemoryStore memories) {
        this.idleTimeout = idleTimeout;
        this.memories = memories;
    }

    public ConversationSession focus(UUID npcId, UUID playerId, Instant now) {
        SessionKey key = new SessionKey(playerId, npcId);
        return sessions.compute(key, (ignored, current) -> {
            if (current != null && !expired(current, now)) {
                current.touch(now);
                return current;
            }
            ConversationSession created = new ConversationSession(UUID.randomUUID(), npcId,
                    playerId, now);
            if (memories != null) created.epistemicWorkspace().restoreCommitments(
                    memories.openCommitmentsReadOnly(npcId, playerId, 4).stream()
                            .map(com.inigmasgames.persistentnpcs.memory.MemoryRecord::summary)
                            .toList(), now);
            if (memories != null) created.epistemicWorkspace().restoreOpenTopics(
                    memories.openTopicsReadOnly(npcId, playerId, 4).stream()
                            .map(com.inigmasgames.persistentnpcs.memory.MemoryRecord::summary)
                            .toList(), now);
            return created;
        });
    }

    public Optional<ConversationSession> active(UUID playerId, Instant now) {
        removeExpired(now);
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().playerId().equals(playerId))
                .map(java.util.Map.Entry::getValue)
                .max(Comparator.comparing(ConversationSession::lastActivity));
    }

    public Optional<ConversationSession> active(UUID playerId, UUID npcId, Instant now) {
        SessionKey key = new SessionKey(playerId, npcId);
        ConversationSession session = sessions.get(key);
        if (session == null) {
            return Optional.empty();
        }
        if (expired(session, now)) {
            sessions.remove(key, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void end(UUID playerId) {
        sessions.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void end(UUID playerId, UUID npcId) {
        sessions.remove(new SessionKey(playerId, npcId));
    }

    public void endNpc(UUID npcId) {
        sessions.keySet().removeIf(key -> key.npcId().equals(npcId));
    }

    public void clear() {
        sessions.clear();
    }

    private void removeExpired(Instant now) {
        sessions.entrySet().removeIf(entry -> expired(entry.getValue(), now));
    }

    private boolean expired(ConversationSession session, Instant now) {
        return Duration.between(session.lastActivity(), now).compareTo(idleTimeout) > 0;
    }

    private record SessionKey(UUID playerId, UUID npcId) {
    }
}
