package com.inigmasgames.persistentnpcs.llm;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Abuse protection only; OrbisResourceScheduler exclusively owns inference admission. */
public final class ConversationRateLimiter implements AutoCloseable {
    private final int perPlayerPerMinute;
    private final Map<UUID, ArrayDeque<Instant>> playerRequests = new ConcurrentHashMap<>();

    public ConversationRateLimiter(int perPlayerPerMinute) {
        if (perPlayerPerMinute < 1) throw new IllegalArgumentException(
                "per-player rate limit must be positive");
        this.perPlayerPerMinute = perPlayerPerMinute;
    }

    public CompletableFuture<Permit> acquire(UUID playerId) {
        if (!claim(playerId, Instant.now())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Player dialogue rate limit reached; wait before asking again."));
        }
        return CompletableFuture.completedFuture(Permit.INSTANCE);
    }

    private boolean claim(UUID playerId, Instant now) {
        ArrayDeque<Instant> requests = playerRequests.computeIfAbsent(
                playerId, ignored -> new ArrayDeque<>());
        synchronized (requests) {
            Instant cutoff = now.minusSeconds(60);
            while (!requests.isEmpty() && requests.peekFirst().isBefore(cutoff)) {
                requests.removeFirst();
            }
            if (requests.size() >= perPlayerPerMinute) return false;
            requests.addLast(now);
            return true;
        }
    }

    @Override public void close() { playerRequests.clear(); }

    public enum Permit implements AutoCloseable {
        INSTANCE;
        public long queueLatencyMillis() { return 0; }
        @Override public void close() { }
    }
}
