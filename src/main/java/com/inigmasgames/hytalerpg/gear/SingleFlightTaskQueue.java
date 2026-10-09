package com.inigmasgames.hytalerpg.gear;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesces periodic work so it cannot backlog a shared receipt executor. */
final class SingleFlightTaskQueue {
    private final AtomicBoolean pending = new AtomicBoolean();

    boolean submit(Executor executor, Runnable task) {
        if (!pending.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try { task.run(); }
                finally { pending.set(false); }
            });
            return true;
        } catch (RuntimeException rejected) {
            pending.set(false);
            throw rejected;
        }
    }
}
