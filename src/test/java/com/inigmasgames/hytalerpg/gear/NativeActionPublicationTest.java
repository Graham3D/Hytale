package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeActionPublicationTest {
    @Test void worldTickDoesNotWaitForAssetStorePublicationAndQueuesOnlyOnce() throws Exception {
        String id = "action-" + UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        java.util.function.Supplier<String> publisher = () -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return id;
        };
        try {
            assertNull(NativeActionPublication.ready(id, publisher));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(NativeActionPublication.ready(id, publisher));
        } finally {
            release.countDown();
        }
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            while (NativeActionPublication.ready(id, publisher) == null) Thread.sleep(1);
        });
        assertEquals(id, NativeActionPublication.ready(id, publisher));
        assertEquals(1, calls.get());
    }

    @Test void publicationFailureLeavesWorldTickFreeAndDoesNotSpinRetry() throws Exception {
        String id = "action-" + UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var calls = new AtomicInteger();
        java.util.function.Supplier<String> publisher = () -> {
            calls.incrementAndGet();
            entered.countDown();
            throw new IllegalStateException("fixture failure");
        };
        assertNull(NativeActionPublication.ready(id, publisher));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 100; i++) assertNull(NativeActionPublication.ready(id, publisher));
        });
        assertEquals(1, calls.get());
    }
}
