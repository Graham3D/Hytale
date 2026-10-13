package com.inigmasgames.hytalerpg.diagnostics;

import java.util.concurrent.atomic.AtomicLong;

/** Process-wide queued-data reservation, shared by HRO and independently enabled legacy writers. */
public final class SharedTraceBudget {
    public static final long LIMIT = 16L * 1024 * 1024;
    private static final AtomicLong USED = new AtomicLong();
    private SharedTraceBudget() { }
    public static boolean acquire(long bytes) {
        if (bytes <= 0 || bytes > LIMIT) return false;
        // Bounded atomic reservation, no CAS retry loop. An over-limit reservation retains no data.
        long before = USED.getAndAdd(bytes);
        if(before > LIMIT-bytes){USED.addAndGet(-bytes);return false;}
        return true;
    }
    public static void release(long bytes) { USED.addAndGet(-bytes); }
    public static long used() { return USED.get(); }
}
