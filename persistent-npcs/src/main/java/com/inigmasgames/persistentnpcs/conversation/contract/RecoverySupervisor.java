package com.inigmasgames.persistentnpcs.conversation.contract;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** One shared recovery allowance for all failure classes in a response branch. */
public final class RecoverySupervisor {
    private static final ConcurrentHashMap<UUID, Entry> ATTEMPTS = new ConcurrentHashMap<>();
    private static final long STALE_MILLIS = 10 * 60 * 1_000L;

    private RecoverySupervisor() { }

    public static boolean tryAcquire(TurnExecutionPlan plan, String reason) {
        if (plan == null || plan.recoveryPolicy().maximumAttempts() == 0) return false;
        cleanupStale();
        Entry entry = ATTEMPTS.computeIfAbsent(plan.responseId(), ignored -> new Entry());
        if (entry.count.incrementAndGet() > plan.recoveryPolicy().maximumAttempts()) return false;
        entry.reason = reason == null ? "UNKNOWN" : reason;
        entry.updatedAt = System.currentTimeMillis();
        return true;
    }

    public static int attempts(UUID responseId) {
        Entry entry = responseId == null ? null : ATTEMPTS.get(responseId);
        return entry == null ? 0 : Math.min(1, entry.count.get());
    }

    public static void complete(UUID responseId) {
        if (responseId != null) ATTEMPTS.remove(responseId);
    }

    private static void cleanupStale() {
        long cutoff = Instant.now().toEpochMilli() - STALE_MILLIS;
        ATTEMPTS.entrySet().removeIf(value -> value.getValue().updatedAt < cutoff);
    }

    private static final class Entry {
        private final AtomicInteger count = new AtomicInteger();
        private volatile long updatedAt = System.currentTimeMillis();
        private volatile String reason = "";
    }
}
