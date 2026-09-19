package com.inigmasgames.hytalerpg.execution.strike;

import java.util.OptionalInt;

/** Monotonic authored repeat schedule; the contact-delay overload schedules hit zero too. */
public final class StrikeRepeatSchedule {
    private final int repeats;
    private final long intervalNanos;
    private int nextHitIndex = 1;
    private long nextDueNanos;
    private final long initialDispatchNanos;
    private final long actionWindowNanos;
    private int nextAnimation=1;
    private boolean cancelled;

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
    /** Contact delay is separate from the animation/cycle start. Hit zero is not dispatched early. */
    public StrikeRepeatSchedule(int repeats,double intervalSeconds,long initialDispatchNanos,double actionWindowSeconds,double firstContactSeconds){
        this(repeats,intervalSeconds,initialDispatchNanos,actionWindowSeconds);
        if(!Double.isFinite(firstContactSeconds)||firstContactSeconds<0||firstContactSeconds>=intervalSeconds)
            throw new IllegalArgumentException("INVALID_FIRST_CONTACT_DELAY");
        nextHitIndex=0;nextDueNanos=initialDispatchNanos+Math.round(firstContactSeconds*1e9);
    }
    public OptionalInt claimAnimationDue(long now){
        if(cancelled||nextAnimation>=repeats||now<initialDispatchNanos+nextAnimation*intervalNanos)return OptionalInt.empty();
        return OptionalInt.of(nextAnimation++);
    }
    public double actionSeconds(){return actionWindowNanos/1e9;}
    public void cancel(){cancelled=true;}

    public OptionalInt claimDue(long nowNanos) {
        if (cancelled || nextHitIndex >= repeats || nowNanos < nextDueNanos) return OptionalInt.empty();
        int claimed = nextHitIndex++;
        nextDueNanos += intervalNanos;
        return OptionalInt.of(claimed);
    }
    public boolean complete() { return nextHitIndex >= repeats; }
    public boolean complete(long nowNanos) { return complete() && nowNanos - initialDispatchNanos >= actionWindowNanos; }
    public long nextDueNanos() { return nextDueNanos; }
    public boolean exceededMaximumAge(long nowNanos,double seconds){return (nowNanos-initialDispatchNanos)/1e9>seconds;}
}
