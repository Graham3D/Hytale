package com.inigmasgames.hytalerpg.execution.strike;

import java.util.OptionalInt;

/** Monotonic authored repeat schedule; hit zero is executed at initial dispatch. */
public final class StrikeRepeatSchedule {
    private final int repeats;
    private final long intervalNanos;
    private int nextHitIndex = 1;
    private long nextDueNanos;
    private final long initialDispatchNanos;
    private final long actionWindowNanos;

    public StrikeRepeatSchedule(int repeats, double intervalSeconds, long initialDispatchNanos) {
        this(repeats, intervalSeconds, initialDispatchNanos, 0);
    }
    public StrikeRepeatSchedule(int repeats, double intervalSeconds, long initialDispatchNanos, double actionWindowSeconds) {
        if (repeats < 1 || intervalSeconds < 0.0 || !Double.isFinite(intervalSeconds))
            throw new IllegalArgumentException("Invalid strike repeat schedule");
        if (!Double.isFinite(actionWindowSeconds) || actionWindowSeconds < 0 || actionWindowSeconds > 10
                || actionWindowSeconds > 0 && actionWindowSeconds < (repeats - 1) * intervalSeconds)
            throw new IllegalArgumentException("Invalid strike action window");
        this.actionWindowNanos = Math.round(actionWindowSeconds * 1e9);
        this.repeats = repeats;
        this.initialDispatchNanos=initialDispatchNanos;
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
    public boolean complete(long nowNanos) { return complete() && nowNanos - initialDispatchNanos >= actionWindowNanos; }
    public long nextDueNanos() { return nextDueNanos; }
    public boolean exceededMaximumAge(long nowNanos,double seconds){return (nowNanos-initialDispatchNanos)/1e9>seconds;}
}
