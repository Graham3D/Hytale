package com.inigmasgames.persistentnpcs.ui;

import java.util.concurrent.Executor;
import java.util.function.LongConsumer;

/** One queued world-thread dispatch, one current intent, zero background model creation. */
public final class AppearancePreviewGate implements AutoCloseable {
    private long generation;
    private LongConsumer latest;
    private boolean queued;
    private boolean closed;
    private int active;
    private long cancelled;

    public synchronized boolean request(Executor world, LongConsumer work) {
        if (closed) throw new IllegalStateException("Appearance preview gate closed");
        generation++;
        boolean coalesced = latest != null;
        if (coalesced) cancelled++;
        latest = work;
        if (!queued) {
            queued = true;
            try { world.execute(this::drain); }
            catch (RuntimeException failure) { queued = false; latest = null; throw failure; }
        }
        return coalesced;
    }
    private synchronized void drain() {
        queued = false;
        if (closed || latest == null) return;
        LongConsumer work = latest;
        latest = null;
        active = 1;
        try { work.accept(generation); } finally { active = 0; }
    }
    public synchronized void cancel() { generation++; if (latest != null) cancelled++; latest = null; }
    public synchronized boolean current(long expected) { return !closed && expected == generation; }
    public synchronized int active() { return active; }
    public synchronized int pending() { return latest == null ? 0 : 1; }
    public synchronized long cancelled() { return cancelled; }
    @Override public synchronized void close() { cancel(); closed = true; }
}
