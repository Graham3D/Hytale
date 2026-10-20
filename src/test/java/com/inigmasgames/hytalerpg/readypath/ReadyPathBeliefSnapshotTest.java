package com.inigmasgames.hytalerpg.readypath;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.epistemic.*;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Real journal/snapshot I/O, never a simulated Hytale join or client benchmark. */
class ReadyPathBeliefSnapshotTest {
    @TempDir Path root;
    private final UUID npc = UUID.randomUUID();

    private void append(SourcedBeliefStore store, int value) {
        store.assertBelief(new BeliefProposal(null, npc, new UUID(0, value + 1),
                "person " + value, "NAME", "person " + value, "Name is person " + value,
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.BELIEVED, .8,
                new BeliefProvenance(EvidenceSourceKind.PLAYER_TESTIMONY, npc,
                        List.of("TEST:" + value), false, false), null,
                BeliefAssertion.AssertionScope.ENTITY, List.of(), Instant.now()));
    }
    private static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static Object snapshotDiskLock(SourcedBeliefStore store) throws Exception {
        var f = JsonFiles.class.getDeclaredField("WRITE_LOCKS"); f.setAccessible(true);
        Object[] locks = (Object[]) f.get(null);
        return locks[Math.floorMod(store.snapshotPath().toAbsolutePath().normalize().hashCode(), locks.length)];
    }
    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for deterministic I/O boundary");
    }

    @Test void blockedSnapshotIoDoesNotBlockReadersOrAcceptedMutations() throws Exception {
        try (var store = new SourcedBeliefStore(root); var pool = Executors.newFixedThreadPool(2)) {
            store.load();
            Thread writer = (Thread) field(store, "writer");
            synchronized (snapshotDiskLock(store)) {
                for (int i = 0; i < 32; i++) append(store, i);
                until(() -> writer.getState() == Thread.State.BLOCKED);
                // The actual disk writer is now waiting inside JsonFiles.writeAtomic.
                assertEquals(32, pool.submit(() -> store.current(npc, null, "NAME").size()).get(2, TimeUnit.SECONDS));
                pool.submit(() -> append(store, 32)).get(2, TimeUnit.SECONDS);
                assertEquals(33, store.history(npc).size());
            }
        }
        try (var restored = new SourcedBeliefStore(root)) {
            restored.load(); assertEquals(33, restored.history(npc).size());
            assertEquals(33, restored.current(npc, null, "NAME").size());
        }
    }

    @Test void capturedSnapshotPlusLaterJournalTailReplaysExactlyOnce() throws Exception {
        Path older = root.resolve("intermediate.snapshot");
        Path snapshot;
        List<BeliefEvent> expected;
        try (var store = new SourcedBeliefStore(root)) {
            store.load(); snapshot = store.snapshotPath();
            for (int i = 0; i < 32; i++) append(store, i);
            until(() -> Files.isRegularFile(snapshot));
            Files.copy(snapshot, older);
            for (int i = 32; i < 48; i++) append(store, i);
            expected = store.history(npc);
        }
        // Rehearse a crash before the final snapshot replacement: preserve the full journal.
        Files.copy(older, snapshot, StandardCopyOption.REPLACE_EXISTING);
        try (var restored = new SourcedBeliefStore(root)) {
            restored.load(); assertEquals(expected, restored.history(npc));
            assertEquals(48, restored.current(npc, null, "NAME").size());
        }
    }

    @Test void legacyFallbackRetainsReplayCompatibleSnapshotFormat() throws Exception {
        String previous = System.getProperty("hywind.readypath.legacy");
        List<BeliefEvent> expected;
        try {
            System.setProperty("hywind.readypath.legacy", "true");
            try (var store = new SourcedBeliefStore(root)) {
                store.load();
                for (int i = 0; i < 48; i++) append(store, i);
                expected = store.history(npc);
            }
            System.setProperty("hywind.readypath.legacy", "false");
            try (var restored = new SourcedBeliefStore(root)) {
                restored.load(); assertEquals(expected, restored.history(npc));
                append(restored, 48); expected = restored.history(npc);
            }
            System.setProperty("hywind.readypath.legacy", "true");
            try (var restored = new SourcedBeliefStore(root)) {
                restored.load(); assertEquals(expected, restored.history(npc));
            }
        } finally {
            if (previous == null) System.clearProperty("hywind.readypath.legacy");
            else System.setProperty("hywind.readypath.legacy", previous);
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 4, 8, 16})
    void concurrentReadersSeeCoherentSnapshotsWhileIoIsBlocked(int readers) throws Exception {
        try (var store = new SourcedBeliefStore(root); var pool = Executors.newFixedThreadPool(readers)) {
            store.load();
            Thread writer = (Thread) field(store, "writer");
            synchronized (snapshotDiskLock(store)) {
                for (int i = 0; i < 32; i++) append(store, i);
                until(() -> writer.getState() == Thread.State.BLOCKED);
                var start = new CountDownLatch(1);
                List<Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < readers; i++) futures.add(pool.submit(() -> {
                    start.await(); return store.current(npc, null, "NAME").size();
                }));
                start.countDown();
                for (var result : futures) assertEquals(32, result.get(2, TimeUnit.SECONDS));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "corrupt", "checksum"})
    void rebuildsFromAuthoritativeJournalWhenSnapshotIsUnavailable(String fault) throws Exception {
        Path snapshot, journal;
        List<BeliefEvent> expected;
        try (var store = new SourcedBeliefStore(root)) {
            store.load(); snapshot = store.snapshotPath(); journal = store.eventPath();
            for (int i = 0; i < 40; i++) append(store, i);
            expected = store.history(npc);
        }
        byte[] originalJournal = Files.readAllBytes(journal);
        if (fault.equals("missing")) Files.delete(snapshot);
        else if (fault.equals("corrupt")) Files.writeString(snapshot, "{broken");
        else Files.writeString(snapshot, Files.readString(snapshot).replace("person 0", "altered name"));
        try (var restored = new SourcedBeliefStore(root)) {
            restored.load(); assertEquals(expected, restored.history(npc));
            assertEquals(40, restored.current(npc, null, "NAME").size());
        }
        assertArrayEquals(originalJournal, Files.readAllBytes(journal), "Cache recovery must not rewrite authority");
    }

    @Test void shutdownDrainsAcceptedWorkAndRejectsLateMutation() throws Exception {
        var store = new SourcedBeliefStore(root); store.load();
        List<BeliefEvent> expected;
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> closing;
            synchronized (snapshotDiskLock(store)) {
                for (int i = 0; i < 48; i++) append(store, i);
                expected = store.history(npc);
                Thread writer = (Thread) field(store, "writer");
                until(() -> writer.getState() == Thread.State.BLOCKED);
                var closed = (java.util.concurrent.atomic.AtomicBoolean) field(store, "closed");
                closing = pool.submit(store::close);
                until(closed::get);
                assertFalse(closing.isDone());
                assertThrows(IllegalStateException.class, () -> append(store, 99));
            }
            closing.get(10, TimeUnit.SECONDS);
        } finally { store.close(); }
        try (var restored = new SourcedBeliefStore(root)) {
            restored.load(); assertEquals(expected, restored.history(npc));
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 4})
    void largerDurableHistoriesPreserveEveryAcceptedEvent(int multiplier) throws Exception {
        int count = 128 * multiplier;
        List<BeliefEvent> expected;
        try (var store = new SourcedBeliefStore(root)) {
            store.load();
            for (int i = 0; i < count; i++) {
                append(store, i);
                if (i % 64 == 63) store.awaitIdle();
            }
            expected = store.history(npc);
        }
        long begin = System.nanoTime();
        try (var restored = new SourcedBeliefStore(root)) {
            restored.load();
            long loadNanos = System.nanoTime() - begin;
            assertEquals(expected, restored.history(npc));
            assertEquals(count, restored.current(npc, null, "NAME").size());
            System.out.printf(Locale.ROOT, "READYPATH_NPC_HISTORY scale=%d events=%d restoreMs=%.3f clientJoinMs=UNKNOWN%n", multiplier, count, loadNanos / 1e6);
        }
    }
}
