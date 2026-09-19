package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coordinator-confined, bounded and deliberately non-persistent deferred state. */
final class DeferredTopicStore {
    static final int MAX_PER_CONVERSATION = 2;
    static final int MAX_TURNS = 3;
    static final long TTL_SECONDS = 120;
    private final Map<Key, ArrayDeque<DeferredTopic>> topics = new LinkedHashMap<>();

    void add(DeferredTopic topic) {
        Key key = new Key(topic.playerStableId(), topic.npcStableId());
        ArrayDeque<DeferredTopic> queue = topics.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        queue.removeIf(value -> value.sourceResponseId().equals(topic.sourceResponseId()));
        queue.addFirst(topic);
        while (queue.size() > MAX_PER_CONVERSATION) queue.removeLast();
    }

    Result context(UUID playerId, UUID npcId, Instant now) {
        Key key = new Key(playerId, npcId);
        ArrayDeque<DeferredTopic> queue = topics.get(key);
        if (queue == null) return new Result("", List.of(), List.of());
        java.util.ArrayList<DeferredTopic> expired = new java.util.ArrayList<>();
        queue.removeIf(value -> {
            boolean remove = value.remainingTurns() <= 0 || !now.isBefore(value.expiresAt());
            if (remove) expired.add(value);
            return remove;
        });
        DeferredTopic selected = queue.peekFirst();
        if (selected == null) {
            topics.remove(key);
            return new Result("", List.of(), List.copyOf(expired));
        }
        queue.removeFirst();
        DeferredTopic advanced = selected.nextTurn();
        if (advanced.remainingTurns() > 0) queue.addFirst(advanced);
        if (queue.isEmpty()) topics.remove(key);
        return new Result(selected.cognitionSummary(""), List.of(selected), List.copyOf(expired));
    }

    int count(UUID playerId, UUID npcId, Instant now) {
        Key key = new Key(playerId, npcId);
        ArrayDeque<DeferredTopic> queue = topics.get(key);
        if (queue != null) {
            queue.removeIf(value -> value.remainingTurns() <= 0
                    || !now.isBefore(value.expiresAt()));
            if (queue.isEmpty()) topics.remove(key);
        }
        return queue == null ? 0 : queue.size();
    }

    void removePlayer(UUID playerId) {
        topics.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    record Result(String summary, List<DeferredTopic> consumed,
            List<DeferredTopic> expired) { }
    private record Key(UUID playerId, UUID npcId) { }
}
