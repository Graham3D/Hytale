package com.inigmasgames.hytalerpg.gear;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SingleFlightTaskQueueTest {
    @Test void slowRefreshCannotFillReceiptQueue() {
        var queued = new ArrayDeque<Runnable>();
        Executor executor = queued::add;
        var refresh = new SingleFlightTaskQueue();
        var ran = new AtomicInteger();

        assertTrue(refresh.submit(executor, ran::incrementAndGet));
        for (int i = 0; i < 100; i++) assertFalse(refresh.submit(executor, ran::incrementAndGet));
        assertEquals(1, queued.size());

        queued.remove().run();
        assertEquals(1, ran.get());
        assertTrue(refresh.submit(executor, ran::incrementAndGet));
        queued.remove().run();
        assertEquals(2, ran.get());
    }

    @Test void rejectedOrFailedRefreshCanBeRetried() {
        var refresh = new SingleFlightTaskQueue();
        Executor rejected = task -> { throw new RejectedExecutionException(); };
        assertThrows(RejectedExecutionException.class, () -> refresh.submit(rejected, () -> {}));

        var queued = new ArrayDeque<Runnable>();
        Executor executor = queued::add;
        assertTrue(refresh.submit(executor, () -> { throw new IllegalStateException(); }));
        assertThrows(IllegalStateException.class, () -> queued.remove().run());
        assertTrue(refresh.submit(executor, () -> {}));
    }
}
