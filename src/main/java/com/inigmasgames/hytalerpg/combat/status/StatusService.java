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
    private final Map<UUID, Map<String, SlowState>> slows = new HashMap<>();
    private final Map<UUID, java.util.ArrayDeque<Long>> controls = new HashMap<>();
    private record ChillBonusKey(UUID owner,String root,UUID target){}
    private final Map<ChillBonusKey,Long> chillBonusEnds=new HashMap<>();
    public StatusService(CombatBalanceProfile profile, LongSupplier nanoTime) { this.profile = profile; this.nanoTime = nanoTime; }

    public record ChillApplication(java.util.List<Result> results,String bonusGate){
        public ChillApplication {results=java.util.List.copyOf(results);}
    }
    /** One payload opportunity, shared by projectile/area/Aura adapters. Children retain the same root. */
    public synchronized ChillApplication applyChill(UUID owner,String root,UUID target,ControlProfile control,int authoredStacks,boolean deepFreeze){
        return applyChill(owner,root,target,control,authoredStacks,deepFreeze,Double.NaN);
    }
    public synchronized ChillApplication applyChill(UUID owner,String root,UUID target,ControlProfile control,int authoredStacks,boolean deepFreeze,double seconds){
        if(owner==null||target==null||root==null||root.isBlank()||root.length()>256||control==null||authoredStacks<1||authoredStacks>5)
            throw new IllegalArgumentException("Invalid source-owned Chill application");
        var results=new java.util.ArrayList<Result>();
        // A payload is atomic at the threshold: never leave another Chill behind a newly created Frozen.
        if(inspect(target).active().containsKey(RpgStatusType.FROZEN))return new ChillApplication(java.util.List.of(
                new Result(Outcome.REJECTED,RpgStatusType.CHILL,0,0,"Frozen already active")),"FROZEN_ACTIVE");
        for(int i=0;i<authoredStacks;i++){
            var result=apply(target,RpgStatusType.CHILL,control,seconds);results.add(result);
            if(result.outcome()==Outcome.THRESHOLD)return new ChillApplication(results,"BASE_THRESHOLD");
            if(result.outcome()==Outcome.REJECTED)return new ChillApplication(results,"BASE_REJECTED");
        }
        if(!deepFreeze)return new ChillApplication(results,"NOT_LINKED");
        long now=nanoTime.getAsLong();chillBonusEnds.values().removeIf(end->end<=now);
        var key=new ChillBonusKey(owner,root,target);
        if(chillBonusEnds.containsKey(key))return new ChillApplication(results,"ROOT_TARGET_ONE_SECOND_ICD");
        if(chillBonusEnds.size()>=4096||chillBonusEnds.keySet().stream().filter(k->k.owner.equals(owner)).count()>=256)
            return new ChillApplication(results,"CHILL_BONUS_BUDGET");
        chillBonusEnds.put(key,now+1_000_000_000L);
        var extra=apply(target,RpgStatusType.CHILL,control,seconds);results.add(extra);
        return new ChillApplication(results,extra.outcome()==Outcome.REJECTED?"BONUS_REJECTED":"BONUS_APPLIED");
    }
    public synchronized void forgetSource(UUID owner){chillBonusEnds.keySet().removeIf(k->k.owner.equals(owner));}
    public synchronized int retainedChillBonusCount(){long now=nanoTime.getAsLong();chillBonusEnds.values().removeIf(end->end<=now);return chillBonusEnds.size();}

    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control) {
        return apply(target, type, control, Double.NaN);
    }

    /** Applies an authored runtime duration while retaining the canonical control-resistance policy. */
    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control,
                                     double authoredDurationSeconds) {
        expire(target);
        if (control.protectedEntity()) return new Result(Outcome.REJECTED, type, 0, 0, "protected target rejects hostile status");
        if (type == RpgStatusType.CHILL) return applyChill(target, control,Double.isFinite(authoredDurationSeconds)&&authoredDurationSeconds>0?authoredDurationSeconds:profile.chillDurationSeconds);
        if (isHardControl(type) && control.blocksHardControl()) {
            if (type == RpgStatusType.FROZEN || type == RpgStatusType.ROOT && control.boss())
                return applySimple(target, RpgStatusType.FROZEN_SUBSTITUTE_SLOW,
                    profile.frozenDurationSeconds, 1, true, "control-resistant target: 30% Slow substitute");
            return new Result(Outcome.REJECTED, type, 0, 0.0, "target control profile rejects hard control");
        }
        if (type == RpgStatusType.FROZEN && frozenImmunityEnds.getOrDefault(target, 0L) > nanoTime.getAsLong())
            return new Result(Outcome.REJECTED, type, 0, 0.0, "Frozen immunity active");
        double duration = switch (type) {
            case FROZEN, FROZEN_SUBSTITUTE_SLOW -> profile.frozenDurationSeconds;
            case BURN -> profile.burnDurationSeconds;
            case POISON -> profile.poisonDurationSeconds;
            case BLEED -> 4;
            case ROOT, FEAR, TAUNT, STAGGER -> profile.frozenDurationSeconds;
            case CHILL -> profile.chillDurationSeconds;
        };
        if (Double.isFinite(authoredDurationSeconds) && authoredDurationSeconds > 0.0)
            duration = authoredDurationSeconds;
        if (isHardControl(type) && type != RpgStatusType.TAUNT) {
            long now = nanoTime.getAsLong();
            var history = controls.computeIfAbsent(target, ignored -> new java.util.ArrayDeque<>());
            while (!history.isEmpty() && now - history.getFirst() >= 10_000_000_000L) history.removeFirst();
            if (history.size() >= 3) return new Result(Outcome.REJECTED, type, 0, 0, "rolling hard-control resistance");
            duration *= control.durationMultiplier() * Math.scalb(1d, -history.size());
            history.addLast(now);
        }
        return applySimple(target, type, duration, 1, true, "applied");
    }
    private Result applyChill(UUID target, ControlProfile control,double seconds) {
        EnumMap<RpgStatusType, State> actor = states.computeIfAbsent(target, ignored -> new EnumMap<>(RpgStatusType.class));
        int stacks = actor.getOrDefault(RpgStatusType.CHILL, new State(0, 0L)).stacks + 1;
        if (stacks >= profile.chillMaximumStacks) {
            Result frozen = apply(target, RpgStatusType.FROZEN, control);
            if (frozen.outcome == Outcome.REJECTED) {
                applySimple(target, RpgStatusType.CHILL, seconds, profile.chillMaximumStacks - 1, true,
                        "Chill held below threshold during control immunity");
                return new Result(Outcome.REJECTED, RpgStatusType.CHILL, profile.chillMaximumStacks - 1,
                        seconds, frozen.detail);
            }
            actor.remove(RpgStatusType.CHILL);
            return new Result(Outcome.THRESHOLD, frozen.type, frozen.stacks, frozen.remainingSeconds,
                    "consumed " + profile.chillMaximumStacks + " Chill; " + frozen.detail);
        }
        return applySimple(target, RpgStatusType.CHILL, seconds, stacks,
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
    /** Read-model projection of the source-owned periodic runtime, not a second DoT timer or damage writer. */
    public synchronized void projectPeriodic(UUID target, RpgStatusType type, int stacks, double seconds) {
        if (type != RpgStatusType.BURN && type != RpgStatusType.POISON && type != RpgStatusType.BLEED)
            throw new IllegalArgumentException("Not a periodic status");
        if (stacks <= 0 || seconds <= 0) { remove(target, type); return; }
        applySimple(target, type, seconds, stacks, true, "source-owned periodic projection");
    }
    public synchronized Snapshot inspect(UUID target) {
        expire(target);
        EnumMap<RpgStatusType, StatusView> result = new EnumMap<>(RpgStatusType.class);
        long now = nanoTime.getAsLong();
        states.getOrDefault(target, new EnumMap<>(RpgStatusType.class)).forEach((type, state) ->
                result.put(type, new StatusView(state.stacks, Math.max(0.0, (state.endsAtNanos - now) / 1_000_000_000.0))));
        return new Snapshot(result);
    }
    /** Authored Slow overrides share one strongest-only channel with Chill and the Frozen substitute. */
    public synchronized void applySlow(UUID target, String source, double magnitude, double seconds) {
        if (target == null || source == null || source.isBlank() || !Double.isFinite(magnitude) || magnitude <= 0
                || !Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("Invalid Slow");
        strongestSlow(target);
        Map<String, SlowState> entries = slows.computeIfAbsent(target, ignored -> new HashMap<>());
        if (entries.size() >= 32 && !entries.containsKey(source)) throw new IllegalStateException("SLOW_SOURCE_BUDGET");
        entries.put(source, new SlowState(Math.min(.6, magnitude), nanoTime.getAsLong() + Math.round(seconds * 1e9)));
    }
    public synchronized SlowView strongestSlow(UUID target) {
        var active = inspect(target).active();
        double strength = 0, seconds = 0; long now = nanoTime.getAsLong();
        var chill = active.get(RpgStatusType.CHILL);
        if (chill != null) { strength = chill.stacks() * profile.chillMovementPenaltyPerStack; seconds = chill.remainingSeconds(); }
        var substitute = active.get(RpgStatusType.FROZEN_SUBSTITUTE_SLOW);
        if (substitute != null && profile.protectedFrozenSlow >= strength) {
            strength = profile.protectedFrozenSlow; seconds = substitute.remainingSeconds();
        }
        Map<String, SlowState> sources = slows.get(target);
        if (sources != null) {
            sources.values().removeIf(value -> value.ends <= now);
            for (SlowState value : sources.values()) {
                double remaining = (value.ends - now) / 1e9;
                if (value.magnitude > strength || value.magnitude == strength && remaining > seconds) {
                    strength = value.magnitude; seconds = remaining;
                }
            }
            if (sources.isEmpty()) slows.remove(target);
        }
        return new SlowView(Math.min(.6, strength), seconds);
    }
    private record SlowState(double magnitude, long ends) { }
    public record SlowView(double magnitude, double remainingSeconds) { }
    private void expire(UUID target) {
        EnumMap<RpgStatusType, State> actor = states.get(target);
        if (actor == null) return;
        long now = nanoTime.getAsLong();
        long frozenEnd = actor.containsKey(RpgStatusType.FROZEN) ? actor.get(RpgStatusType.FROZEN).endsAtNanos : Long.MAX_VALUE;
        actor.entrySet().removeIf(entry -> entry.getValue().endsAtNanos <= now);
        if (frozenEnd <= now) frozenImmunityEnds.put(target, frozenEnd + Math.round(profile.frozenImmunitySeconds * 1_000_000_000.0));
        if (actor.isEmpty()) states.remove(target);
    }
    private static boolean isHardControl(RpgStatusType type) {
        return type == RpgStatusType.FROZEN || type == RpgStatusType.ROOT || type == RpgStatusType.FEAR
                || type == RpgStatusType.TAUNT || type == RpgStatusType.STAGGER;
    }
    private record State(int stacks, long endsAtNanos) { }
    /** Native entity removal is the terminal authority for victim-owned status memory. */
    public synchronized void forget(UUID target) {
        states.remove(target); slows.remove(target); controls.remove(target); frozenImmunityEnds.remove(target);
        chillBonusEnds.keySet().removeIf(k->k.owner.equals(target)||k.target.equals(target));
    }
    public enum Outcome { APPLIED, REFRESHED, THRESHOLD, REJECTED }
    public record Result(Outcome outcome, RpgStatusType type, int stacks, double remainingSeconds, String detail) { }
    public record StatusView(int stacks, double remainingSeconds) { }
    public record Snapshot(Map<RpgStatusType, StatusView> active) { public Snapshot { active = Map.copyOf(active); } }
}
