package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Reusable status authority. Timers use monotonic time and reapplication refreshes rather than stacking durations. */
public final class StatusService {
    private final CombatBalanceProfile profile;
    private final LongSupplier nanoTime;
    private final Map<UUID, EnumMap<RpgStatusType, State>> states = new HashMap<>();
    private final Map<UUID, Long> frozenImmunityEnds = new HashMap<>();
    public StatusService(CombatBalanceProfile profile, LongSupplier nanoTime) { this.profile = profile; this.nanoTime = nanoTime; }

    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control) {
        return apply(target, type, control, Double.NaN);
    }

    /** Applies an authored runtime duration while retaining the canonical control-resistance policy. */
    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control,
                                     double authoredDurationSeconds) {
        expire(target);
        if (type == RpgStatusType.CHILL) return applyChill(target, control);
        if (isHardControl(type) && control.blocksHardControl()) {
            if (type == RpgStatusType.FROZEN) return applySimple(target, RpgStatusType.FROZEN_SUBSTITUTE_SLOW,
                    profile.frozenDurationSeconds, 1, true, "protected target: 30% Slow substitute");
            return new Result(Outcome.REJECTED, type, 0, 0.0, "target control profile rejects hard control");
        }
        if (type == RpgStatusType.FROZEN && frozenImmunityEnds.getOrDefault(target, 0L) > nanoTime.getAsLong())
            return new Result(Outcome.REJECTED, type, 0, 0.0, "Frozen immunity active");
        double duration = switch (type) {
            case FROZEN, FROZEN_SUBSTITUTE_SLOW -> profile.frozenDurationSeconds;
            case BURN -> profile.burnDurationSeconds;
            case POISON -> profile.poisonDurationSeconds;
            case ROOT, FEAR, TAUNT, STAGGER -> profile.frozenDurationSeconds;
            case CHILL -> profile.chillDurationSeconds;
        };
        if (Double.isFinite(authoredDurationSeconds) && authoredDurationSeconds > 0.0)
            duration = authoredDurationSeconds;
        return applySimple(target, type, duration, 1, true, "applied");
    }
    private Result applyChill(UUID target, ControlProfile control) {
        EnumMap<RpgStatusType, State> actor = states.computeIfAbsent(target, ignored -> new EnumMap<>(RpgStatusType.class));
        int stacks = actor.getOrDefault(RpgStatusType.CHILL, new State(0, 0L)).stacks + 1;
        if (stacks >= profile.chillMaximumStacks) {
            actor.remove(RpgStatusType.CHILL);
            Result frozen = apply(target, RpgStatusType.FROZEN, control);
            return new Result(Outcome.THRESHOLD, frozen.type, frozen.stacks, frozen.remainingSeconds,
                    "consumed " + profile.chillMaximumStacks + " Chill; " + frozen.detail);
        }
        return applySimple(target, RpgStatusType.CHILL, profile.chillDurationSeconds, stacks,
                actor.containsKey(RpgStatusType.CHILL), "Chill movement penalty=" + (stacks * profile.chillMovementPenaltyPerStack));
    }
    private Result applySimple(UUID target, RpgStatusType type, double seconds, int stacks, boolean refreshable, String detail) {
        EnumMap<RpgStatusType, State> actor = states.computeIfAbsent(target, ignored -> new EnumMap<>(RpgStatusType.class));
        boolean existed = actor.containsKey(type);
        actor.put(type, new State(stacks, nanoTime.getAsLong() + Math.round(seconds * 1_000_000_000.0)));
        return new Result(existed && refreshable ? Outcome.REFRESHED : Outcome.APPLIED, type, stacks, seconds, detail);
    }
    public synchronized boolean remove(UUID target, RpgStatusType type) {
        EnumMap<RpgStatusType, State> actor = states.get(target);
        if (actor == null || actor.remove(type) == null) return false;
        if (type == RpgStatusType.FROZEN)
            frozenImmunityEnds.put(target, nanoTime.getAsLong() + Math.round(profile.frozenImmunitySeconds * 1_000_000_000.0));
        if (actor.isEmpty()) states.remove(target);
        return true;
    }
    public synchronized Snapshot inspect(UUID target) {
        expire(target);
        EnumMap<RpgStatusType, StatusView> result = new EnumMap<>(RpgStatusType.class);
        long now = nanoTime.getAsLong();
        states.getOrDefault(target, new EnumMap<>(RpgStatusType.class)).forEach((type, state) ->
                result.put(type, new StatusView(state.stacks, Math.max(0.0, (state.endsAtNanos - now) / 1_000_000_000.0))));
        return new Snapshot(result);
    }
    private void expire(UUID target) {
        EnumMap<RpgStatusType, State> actor = states.get(target);
        if (actor == null) return;
        long now = nanoTime.getAsLong();
        boolean frozenExpired = actor.containsKey(RpgStatusType.FROZEN) && actor.get(RpgStatusType.FROZEN).endsAtNanos <= now;
        actor.entrySet().removeIf(entry -> entry.getValue().endsAtNanos <= now);
        if (frozenExpired) frozenImmunityEnds.put(target, now + Math.round(profile.frozenImmunitySeconds * 1_000_000_000.0));
        if (actor.isEmpty()) states.remove(target);
    }
    private static boolean isHardControl(RpgStatusType type) {
        return type == RpgStatusType.FROZEN || type == RpgStatusType.ROOT || type == RpgStatusType.FEAR
                || type == RpgStatusType.TAUNT || type == RpgStatusType.STAGGER;
    }
    private record State(int stacks, long endsAtNanos) { }
    public enum Outcome { APPLIED, REFRESHED, THRESHOLD, REJECTED }
    public record Result(Outcome outcome, RpgStatusType type, int stacks, double remainingSeconds, String detail) { }
    public record StatusView(int stacks, double remainingSeconds) { }
    public record Snapshot(Map<RpgStatusType, StatusView> active) { public Snapshot { active = Map.copyOf(active); } }
}
