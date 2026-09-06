package com.inigmasgames.hytalerpg.execution.strike;

import java.util.OptionalInt;

/** Monotonic authored repeat schedule; hit zero is executed at initial dispatch. */
public final class StrikeRepeatSchedule {
    private final int repeats;
    private final long intervalNanos;
    private int nextHitIndex = 1;
    private long nextDueNanos;

    public StrikeRepeatSchedule(int repeats, double intervalSeconds, long initialDispatchNanos) {
        if (repeats < 1 || intervalSeconds < 0.0 || !Double.isFinite(intervalSeconds))
            throw new IllegalArgumentException("Invalid strike repeat schedule");
        this.repeats = repeats;
        this.intervalNanos = Math.round(intervalSeconds * 1_000_000_000.0);
        this.nextDueNanos = initialDispatchNanos + intervalNanos;
    }

    public OptionalInt claimDue(long nowNanos) {
        if (nextHitIndex >= repeats || nowNanos < nextDueNanos) return OptionalInt.empty();
        int claimed = nextHitIndex++;
        nextDueNanos += intervalNanos;
        return OptionalInt.of(claimed);
    }
    public boolean complete() { return nextHitIndex >= repeats; }
    public long nextDueNanos() { return nextDueNanos; }
}
