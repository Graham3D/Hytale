package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** Reusable status authority. Timers use monotonic time and reapplication refreshes rather than stacking durations. */
public final class StatusService {
    private final CombatBalanceProfile profile;
    private final LongSupplier nanoTime;
    private final com.inigmasgames.hytalerpg.combat.damage.CriticalRoller chance;
    private java.util.function.ToDoubleFunction<UUID> fortuneEscape = ignored -> 0;
    private java.util.function.ToDoubleFunction<UUID> resistance = ignored -> 0;
    private java.util.function.BiPredicate<UUID, String> immunity = (target, status) -> false;
    private java.util.function.BiPredicate<UUID, UUID> externalMutation = (source, target) -> true;
    private java.util.function.Predicate<UUID> slowImmunity = target -> false;
    private java.util.function.BiPredicate<UUID, String> encounterImmunity = (target, status) -> false;
    private java.util.function.BiPredicate<UUID, UUID> encounterMutation = (source, target) -> true;
    private java.util.function.Predicate<UUID> encounterSlowImmunity = target -> false;
    private final Map<UUID, EnumMap<RpgStatusType, State>> states = new HashMap<>();
    private final Map<UUID, Long> frozenImmunityEnds = new HashMap<>();
    private final Map<UUID, Map<String, SlowState>> slows = new HashMap<>();
    private final Map<UUID, java.util.ArrayDeque<Long>> stackedSlowEnds = new HashMap<>();
    private record SlowKey(UUID owner,String skill,UUID target) { }
    private final Map<SlowKey,Long> slowSourceLocks = new HashMap<>();
    private final Map<UUID,Long> slowTargetLocks = new HashMap<>();
    private final Map<UUID, Map<String, Long>> regenerationSuppression = new HashMap<>();
    private final Map<UUID, UUID> fearSources = new HashMap<>();
    private final Map<UUID, String> fearRoots = new HashMap<>();
    private final Map<UUID, java.util.ArrayDeque<Long>> controls = new HashMap<>();
    private record ChillBonusKey(UUID owner,String root,UUID target){}
    private final Map<ChillBonusKey,Long> chillBonusEnds=new HashMap<>();
    public StatusService(CombatBalanceProfile profile, LongSupplier nanoTime) {
        this(profile, nanoTime, new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(Math::random));
    }
    public StatusService(CombatBalanceProfile profile, LongSupplier nanoTime,
                         com.inigmasgames.hytalerpg.combat.damage.CriticalRoller chance) {
        this.profile = profile; this.nanoTime = nanoTime; this.chance = java.util.Objects.requireNonNull(chance);
    }
    public synchronized void configureFortuneEscape(java.util.function.ToDoubleFunction<UUID> provider) {
        fortuneEscape = java.util.Objects.requireNonNull(provider);
    }
    public synchronized void configureSlowImmunity(java.util.function.Predicate<UUID> provider){slowImmunity=java.util.Objects.requireNonNull(provider);}
    /** Pure lifecycle gate for an already accepted package; never repeats status or Fortune rolls. */
    public synchronized boolean allowsExternalMutation(UUID source,UUID target){return externalMutation.test(source,target)&&encounterMutation.test(source,target);}
    /** Encounter providers compose with ordinary resistance and native/gear immunity; they never replace them. */
    public synchronized void configureEncounterAdmission(java.util.function.BiPredicate<UUID,String> immunity,
            java.util.function.BiPredicate<UUID,UUID> mutation,java.util.function.Predicate<UUID> slowImmunity){
        encounterImmunity=java.util.Objects.requireNonNull(immunity);
        encounterMutation=java.util.Objects.requireNonNull(mutation);
        encounterSlowImmunity=java.util.Objects.requireNonNull(slowImmunity);
    }
    public synchronized void configureAdmission(java.util.function.ToDoubleFunction<UUID> resistance,
            java.util.function.BiPredicate<UUID, String> immunity,
            java.util.function.BiPredicate<UUID, UUID> externalMutation) {
        this.resistance = java.util.Objects.requireNonNull(resistance);
        this.immunity = java.util.Objects.requireNonNull(immunity);
        this.externalMutation = java.util.Objects.requireNonNull(externalMutation);
    }
    public enum Admission { ACCEPTED, PROTECTED, IMMUNE, NO_PROC, NO_STATUS_MUTATION, PROC_LOCK, STATUS_CHANCE_FAILED, FORTUNE_STATUS_AVOIDED }

    /** Preflight is read-only. Call immediately before publication, on the owning world thread.
     * Periodic projection and threshold conversion use the already-admitted package and never re-enter. */
    public synchronized Admission admit(StatusApplication application, ControlProfile control, boolean wouldMutate) {
        return admit(application,control,wouldMutate,chance);
    }
    /** ME supplies its existing purpose-separated encounter RNG; ordinary callers retain the kernel stream. */
    public synchronized Admission admit(StatusApplication application, ControlProfile control, boolean wouldMutate,
                                       com.inigmasgames.hytalerpg.combat.damage.CriticalRoller opportunityRandom) {
        return admit(application,control,wouldMutate,opportunityRandom,()->true);
    }
    /** Attempt-time locks belong after eligibility and before the single chance/Fortune gateway. */
    public synchronized Admission admit(StatusApplication application, ControlProfile control, boolean wouldMutate,
            com.inigmasgames.hytalerpg.combat.damage.CriticalRoller opportunityRandom,java.util.function.BooleanSupplier claimOpportunity) {
        java.util.Objects.requireNonNull(opportunityRandom);java.util.Objects.requireNonNull(claimOpportunity);
        UUID target = application.target(); String status = application.status();
        if (control.protectedEntity() || !allowsExternalMutation(application.source(), target)) return Admission.PROTECTED;
        if (immunity.test(target, status) || encounterImmunity.test(target,status) || status.equals("FROZEN") && frozenImmuneWithoutMutation(target)) return Admission.IMMUNE;
        if (control.blocksHardControl() && (status.equals("STAGGER") || status.equals("FEAR")
                || status.equals("ROOT") && !control.boss() || status.equals("TAUNT"))) return Admission.IMMUNE;
        if (application.noProc()) return Admission.NO_PROC;
        if (!wouldMutate || status.equals("CHILL") && activeWithoutExpiryMutation(target, RpgStatusType.FROZEN))
            return Admission.NO_STATUS_MUTATION;
        double rawResistance = resistance.applyAsDouble(target);
        if (!Double.isFinite(rawResistance)) throw new IllegalStateException("Invalid status resistance provider");
        double effective = Math.clamp(rawResistance - application.sourcePenetration(), 0, .75);
        if(application.combinedSourceChance()>0&&!claimOpportunity.getAsBoolean())return Admission.PROC_LOCK;
        if (!opportunityRandom.chance(application.combinedSourceChance() * (1 - effective))) return Admission.STATUS_CHANCE_FAILED;
        if (application.fortuneEligible() && opportunityRandom.chance(fortuneEscape.applyAsDouble(target)))
            return Admission.FORTUNE_STATUS_AVOIDED;
        return Admission.ACCEPTED;
    }
    private boolean activeWithoutExpiryMutation(UUID target, RpgStatusType status) {
        var actor = states.get(target); var state = actor == null ? null : actor.get(status);
        return state != null && state.endsAtNanos > nanoTime.getAsLong();
    }
    private boolean frozenImmuneWithoutMutation(UUID target) {
        long now = nanoTime.getAsLong();
        if (frozenImmunityEnds.getOrDefault(target, 0L) > now) return true;
        var actor = states.get(target); var frozen = actor == null ? null : actor.get(RpgStatusType.FROZEN);
        return frozen != null && frozen.endsAtNanos <= now
                && frozen.endsAtNanos + Math.round(profile.frozenImmunitySeconds * 1e9) > now;
    }
    public synchronized Result applyElectrifiedHostile(UUID source, UUID target, ControlProfile control, int stacks, double seconds) {
        if (stacks < 1 || stacks > 5) throw new IllegalArgumentException("Invalid Electrified payload");
        var admitted = admit(StatusApplication.hostile(source, target, RpgStatusType.ELECTRIFIED), control, true);
        if (admitted != Admission.ACCEPTED) return new Result(Outcome.REJECTED, RpgStatusType.ELECTRIFIED, 0, 0, admitted.name());
        Result result = null;
        for (int i = 0; i < stacks; i++) result = applyElectrified(target, seconds);
        return result;
    }
    public synchronized Result applyHostile(UUID source, UUID target, RpgStatusType type, ControlProfile control, double seconds) {
        return apply(StatusApplication.hostile(source, target, type), type, control, seconds);
    }
    public synchronized Result applyHostile(UUID source,UUID target,RpgStatusType type,ControlProfile control,double seconds,
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear){
        var application=StatusApplication.hostile(source,target,type);
        var admitted=admit(application,control,true);
        return admitted==Admission.ACCEPTED?apply(target,type,control,seconds,targetGear)
                :new Result(Outcome.REJECTED,type,0,0,admitted.name());
    }
    public synchronized Result apply(StatusApplication application, RpgStatusType type, ControlProfile control, double seconds) {
        if (!application.status().equals(type.name())) throw new IllegalArgumentException("Mismatched status application");
        var admitted = admit(application, control, true);
        return admitted == Admission.ACCEPTED ? apply(application.target(), type, control, seconds)
                : new Result(Outcome.REJECTED, type, 0, 0, admitted.name());
    }
    public synchronized ChillApplication applyChillHostile(UUID source, String root, UUID target, ControlProfile control,
                                                          int stacks, boolean deepFreeze, double seconds) {
        var admitted = admit(StatusApplication.hostile(source, target, RpgStatusType.CHILL), control, true);
        if (admitted != Admission.ACCEPTED) return new ChillApplication(java.util.List.of(
                new Result(Outcome.REJECTED, RpgStatusType.CHILL, 0, 0, admitted.name())), admitted.name());
        return applyChill(source, root, target, control, stacks, deepFreeze, seconds);
    }

    public record ChillApplication(java.util.List<Result> results,String bonusGate){
        public ChillApplication {results=java.util.List.copyOf(results);}
    }
    /** One payload opportunity, shared by projectile/area/Aura adapters. Children retain the same root. */
    public synchronized ChillApplication applyChill(UUID owner,String root,UUID target,ControlProfile control,int authoredStacks,boolean deepFreeze){
        return applyChill(owner,root,target,control,authoredStacks,deepFreeze,Double.NaN);
    }
    public synchronized ChillApplication applyChill(UUID owner,String root,UUID target,ControlProfile control,int authoredStacks,boolean deepFreeze,double seconds){
        return applyChill(owner,root,target,control,authoredStacks,deepFreeze,seconds,
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY);
    }
    public synchronized ChillApplication applyChill(UUID owner,String root,UUID target,ControlProfile control,
            int authoredStacks,boolean deepFreeze,double seconds,
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear){
        if(owner==null||target==null||root==null||root.isBlank()||root.length()>256||control==null||authoredStacks<1||authoredStacks>5)
            throw new IllegalArgumentException("Invalid source-owned Chill application");
        if(targetGear==null)throw new IllegalArgumentException("TARGET_GEAR_REQUIRED");
        var results=new java.util.ArrayList<Result>();
        // A payload is atomic at the threshold: never leave another Chill behind a newly created Frozen.
        if(inspect(target).active().containsKey(RpgStatusType.FROZEN))return new ChillApplication(java.util.List.of(
                new Result(Outcome.REJECTED,RpgStatusType.CHILL,0,0,"Frozen already active")),"FROZEN_ACTIVE");
        for(int i=0;i<authoredStacks;i++){
            var result=apply(target,RpgStatusType.CHILL,control,seconds,targetGear);results.add(result);
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
        var extra=apply(target,RpgStatusType.CHILL,control,seconds,targetGear);results.add(extra);
        return new ChillApplication(results,extra.outcome()==Outcome.REJECTED?"BONUS_REJECTED":"BONUS_APPLIED");
    }
    public synchronized void forgetSource(UUID owner){
        chillBonusEnds.keySet().removeIf(k->k.owner.equals(owner));
        slowSourceLocks.keySet().removeIf(key->key.owner.equals(owner));
        for(var entry:regenerationSuppression.entrySet())
            entry.getValue().keySet().removeIf(key->key.startsWith(owner+"/"));
        regenerationSuppression.values().removeIf(Map::isEmpty);
        for(var victim:new java.util.ArrayList<>(fearSources.entrySet()))
            if(victim.getValue().equals(owner))remove(victim.getKey(),RpgStatusType.FEAR);
    }
    public synchronized int retainedChillBonusCount(){long now=nanoTime.getAsLong();chillBonusEnds.values().removeIf(end->end<=now);return chillBonusEnds.size();}

    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control) {
        return apply(target, type, control, Double.NaN);
    }

    /** Applies an authored runtime duration while retaining the canonical control-resistance policy. */
    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control,
                                     double authoredDurationSeconds) {
        return apply(target,type,control,authoredDurationSeconds,
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY);
    }

    /** Target's admitted equipment changes hard-control duration, never admission or immunity time. */
    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control,
                                     double authoredDurationSeconds,
                                     com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear) {
        return apply(target,type,control,authoredDurationSeconds,targetGear,false);
    }
    /** Channel state is sampled at admission; a later channel interruption cannot undo this duration. */
    public synchronized Result apply(UUID target, RpgStatusType type, ControlProfile control,
                                     double authoredDurationSeconds,
                                     com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear,
                                     boolean channelActive) {
        if(targetGear==null)throw new IllegalArgumentException("TARGET_GEAR_REQUIRED");
        expire(target);
        if (control.protectedEntity()) return new Result(Outcome.REJECTED, type, 0, 0, "protected target rejects hostile status");
        if (type == RpgStatusType.CHILL) return applyChill(target, control,Double.isFinite(authoredDurationSeconds)&&authoredDurationSeconds>0?authoredDurationSeconds:profile.chillDurationSeconds,targetGear);
        if (type == RpgStatusType.ELECTRIFIED)
            return applyElectrified(target, Double.isFinite(authoredDurationSeconds) && authoredDurationSeconds > 0 ? authoredDurationSeconds : 6.0);
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
            case ROOT, FEAR, TAUNT, STAGGER, STUN, SILENCE, BLIND -> profile.frozenDurationSeconds;
            case SLOW -> 5.0;
            case ELECTRIFIED -> 6.0;
            case LIGHTNING_VULNERABILITY -> 5.0;
            case ALACRITY -> 6.0;
            case CHILL -> profile.chillDurationSeconds;
        };
        if (Double.isFinite(authoredDurationSeconds) && authoredDurationSeconds > 0.0)
            duration = authoredDurationSeconds;
        if(isHardControl(type)) {
            duration *= Math.max(0,1-targetGear.percent("WA-080")
                    -(channelActive?targetGear.percent("WA-109"):0));
            if(duration<=0)return new Result(Outcome.REJECTED,type,0,0,"zero-duration control has no admission");
        }
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
    /** Shared Lightning debuff: one refreshed six-second timer and a hard five-stack cap. */
    public synchronized Result applyElectrified(UUID target, double seconds) {
        if (target == null || !Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("Invalid Electrified");
        expire(target);
        EnumMap<RpgStatusType, State> actor = states.computeIfAbsent(target, ignored -> new EnumMap<>(RpgStatusType.class));
        int before = actor.getOrDefault(RpgStatusType.ELECTRIFIED, new State(0, 0L)).stacks;
        int stacks = Math.min(5, before + 1);
        actor.put(RpgStatusType.ELECTRIFIED, new State(stacks, nanoTime.getAsLong() + Math.round(seconds * 1e9)));
        return new Result(before > 0 ? Outcome.REFRESHED : Outcome.APPLIED, RpgStatusType.ELECTRIFIED, stacks, seconds,
                "Physical miss chance=" + stacks * .05);
    }
    public synchronized double physicalMissChance(UUID target) {
        var status = inspect(target).active().get(RpgStatusType.ELECTRIFIED);
        double electrified=status == null ? 0 : Math.min(.25, status.stacks() * .05);
        return inspect(target).active().containsKey(RpgStatusType.BLIND) ? Math.max(.5,electrified) : electrified;
    }
    public synchronized double lightningDamageFactor(UUID target) {
        return inspect(target).active().containsKey(RpgStatusType.LIGHTNING_VULNERABILITY) ? 1.5 : 1.0;
    }
    public synchronized double cooldownRecoveryRate(UUID target) {
        return inspect(target).active().containsKey(RpgStatusType.ALACRITY) ? .30 : 0.0;
    }
    private Result applyChill(UUID target, ControlProfile control,double seconds,
                              com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear) {
        EnumMap<RpgStatusType, State> actor = states.computeIfAbsent(target, ignored -> new EnumMap<>(RpgStatusType.class));
        int stacks = actor.getOrDefault(RpgStatusType.CHILL, new State(0, 0L)).stacks + 1;
        if (stacks >= profile.chillMaximumStacks) {
            Result frozen = apply(target, RpgStatusType.FROZEN, control,Double.NaN,targetGear);
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
        expire(target);
        EnumMap<RpgStatusType, State> actor = states.get(target);
        if (actor == null || actor.remove(type) == null) return false;
        if (type == RpgStatusType.FROZEN)
            frozenImmunityEnds.put(target, nanoTime.getAsLong() + Math.round(profile.frozenImmunitySeconds * 1_000_000_000.0));
        if(type==RpgStatusType.FEAR){fearSources.remove(target);fearRoots.remove(target);}
        if (actor.isEmpty()) states.remove(target);
        return true;
    }
    public synchronized boolean removeExternal(UUID source,UUID target,RpgStatusType type){
        return allowsExternalMutation(source,target)&&remove(target,type);
    }
    /** Source policy supplies the exact statuses; this owner only records actual removals. */
    public record CleanseSource(UUID world,String root,String skillInstance,String correlation,
                                UUID caster,UUID recipient,
                                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot admittedGear) {
        public CleanseSource {
            Objects.requireNonNull(world);Objects.requireNonNull(caster);Objects.requireNonNull(recipient);
            Objects.requireNonNull(admittedGear);
            if(root==null||root.isBlank()||skillInstance==null||skillInstance.isBlank()
                    ||correlation==null||correlation.isBlank())throw new IllegalArgumentException("INVALID_CLEANSE_SOURCE");
        }
    }
    public static final class CleanseReceipt {
        private final CleanseSource source;
        private final List<RpgStatusType> removed;
        private final double at;
        private boolean consumed;
        private CleanseReceipt(CleanseSource source,List<RpgStatusType> removed,double at){
            this.source=source;this.removed=List.copyOf(removed);this.at=at;
        }
        public CleanseSource source(){return source;}
        public List<RpgStatusType> removed(){return removed;}
        public double at(){return at;}
        public synchronized boolean claim(){if(consumed)return false;consumed=true;return true;}
    }
    /** Native projection completes before the successful-cleanse event is delivered. */
    public synchronized void cleanse(CleanseSource source,UUID target,Collection<RpgStatusType> selected,
                                      Runnable projectNative,Consumer<CleanseReceipt> onSuccess){
        Objects.requireNonNull(source);Objects.requireNonNull(target);Objects.requireNonNull(selected);
        Objects.requireNonNull(projectNative);Objects.requireNonNull(onSuccess);
        if(!source.recipient().equals(target))throw new IllegalArgumentException("CLEANSE_TARGET_MISMATCH");
        var chosen=List.copyOf(selected);
        if(chosen.size()>RpgStatusType.values().length)throw new IllegalArgumentException("CLEANSE_SELECTION_BUDGET");
        // Periodic packages and authored/stacked Slow have other active state; a read-model removal is not a cleanse.
        if(chosen.stream().anyMatch(type->type==RpgStatusType.BURN||type==RpgStatusType.POISON
                ||type==RpgStatusType.BLEED||type==RpgStatusType.SLOW))
            throw new IllegalArgumentException("SEPARATE_STATUS_OWNER_REQUIRED");
        var removed=new ArrayList<RpgStatusType>();
        for(var type:chosen){
            if(remove(target,type))removed.add(type);
        }
        if(removed.isEmpty())return;
        projectNative.run();
        onSuccess.accept(new CleanseReceipt(source,removed,nanoTime.getAsLong()/1e9));
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
    /** Five independently expiring stacks, with canonical target and source admission locks. */
    public synchronized Result applyStackingSlow(UUID owner,String canonicalSkill,UUID target,ControlProfile control) {
        if(owner==null||canonicalSkill==null||canonicalSkill.isBlank()||target==null||control==null)
            throw new IllegalArgumentException("INVALID_STACKING_SLOW");
        expire(target);
        if(control.protectedEntity())return new Result(Outcome.REJECTED,RpgStatusType.SLOW,0,0,"protected target");
        long now=nanoTime.getAsLong();
        slowSourceLocks.values().removeIf(end->end<=now);
        slowTargetLocks.values().removeIf(end->end<=now);
        var key=new SlowKey(owner,canonicalSkill,target);
        if(slowSourceLocks.getOrDefault(key,0L)>now||slowTargetLocks.getOrDefault(target,0L)>now)
            return new Result(Outcome.REJECTED,RpgStatusType.SLOW,0,0,"Slow admission lock");
        var ends=stackedSlowEnds.computeIfAbsent(target,ignored->new java.util.ArrayDeque<>());
        if(ends.size()>=5)return new Result(Outcome.REJECTED,RpgStatusType.SLOW,5,0,"Slow stack cap");
        if(slowSourceLocks.size()>=8192||slowTargetLocks.size()>=4096)
            return new Result(Outcome.REJECTED,RpgStatusType.SLOW,ends.size(),0,"Slow admission budget");
        ends.addLast(now+5_000_000_000L);
        slowSourceLocks.put(key,now+5_000_000_000L);
        slowTargetLocks.put(target,now+750_000_000L);
        states.computeIfAbsent(target,ignored->new EnumMap<>(RpgStatusType.class))
                .put(RpgStatusType.SLOW,new State(ends.size(),ends.getLast()));
        return new Result(Outcome.APPLIED,RpgStatusType.SLOW,ends.size(),5,"stacking 10% Slow");
    }
    /** Rending affects positive passive Health regeneration only; sources select strongest once. */
    public synchronized void suppressHealthRegeneration(UUID target,String source,double seconds) {
        if(target==null||source==null||source.isBlank()||!Double.isFinite(seconds)||seconds<=0)
            throw new IllegalArgumentException("INVALID_REGEN_SUPPRESSION");
        long now=nanoTime.getAsLong();
        var sources=regenerationSuppression.computeIfAbsent(target,ignored->new HashMap<>());
        sources.values().removeIf(end->end<=now);
        if(sources.size()>=32&&!sources.containsKey(source))throw new IllegalStateException("REGEN_SUPPRESSION_SOURCE_BUDGET");
        sources.put(source,now+Math.round(seconds*1e9));
    }
    public synchronized double healthRegenerationFactor(UUID target) {
        var sources=regenerationSuppression.get(target);
        if(sources==null)return 1;
        long now=nanoTime.getAsLong();sources.values().removeIf(end->end<=now);
        if(sources.isEmpty()){regenerationSuppression.remove(target);return 1;}
        return .5;
    }
    public synchronized void assignFearSource(UUID target,UUID source,String root) {
        if(target==null||source==null||root==null||root.isBlank()
                ||!inspect(target).active().containsKey(RpgStatusType.FEAR))
            throw new IllegalArgumentException("FEAR_SOURCE_REQUIRES_ACTIVE_FEAR");
        fearSources.put(target,source);fearRoots.put(target,root);
    }
    public synchronized UUID fearSource(UUID target) {
        return inspect(target).active().containsKey(RpgStatusType.FEAR)?fearSources.get(target):null;
    }
    public synchronized boolean breakFearOnDamage(UUID target,String root) {
        if(target==null||root==null||root.isBlank())throw new IllegalArgumentException("INVALID_FEAR_BREAK");
        if(!inspect(target).active().containsKey(RpgStatusType.FEAR)
                ||root.equals(fearRoots.get(target)))return false;
        return remove(target,RpgStatusType.FEAR);
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
        var stacked=active.get(RpgStatusType.SLOW);
        if(stacked!=null&&stacked.stacks()*.1>=strength){strength=stacked.stacks()*.1;seconds=stacked.remainingSeconds();}
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
        // Expiry still runs while immune; immunity affects only the resulting movement component.
        return slowImmunity.test(target)||encounterSlowImmunity.test(target)?new SlowView(0,0):new SlowView(Math.min(.6, strength), seconds);
    }
    /** WA-081 acts on the selected slow; native movement publishes this resolved view once. */
    public synchronized SlowView strongestSlow(UUID target,
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot targetGear) {
        if(targetGear==null)throw new IllegalArgumentException("TARGET_GEAR_REQUIRED");
        SlowView selected=strongestSlow(target);
        return new SlowView(selected.magnitude()*Math.max(0,1-targetGear.percent("WA-081")),
                selected.remainingSeconds());
    }
    private record SlowState(double magnitude, long ends) { }
    public record SlowView(double magnitude, double remainingSeconds) { }
    private void expire(UUID target) {
        EnumMap<RpgStatusType, State> actor = states.get(target);
        if (actor == null) return;
        long now = nanoTime.getAsLong();
        var stacked=stackedSlowEnds.get(target);
        if(stacked!=null){
            while(!stacked.isEmpty()&&stacked.getFirst()<=now)stacked.removeFirst();
            if(stacked.isEmpty()){stackedSlowEnds.remove(target);actor.remove(RpgStatusType.SLOW);}
            else actor.put(RpgStatusType.SLOW,new State(stacked.size(),stacked.getLast()));
        }
        long frozenEnd = actor.containsKey(RpgStatusType.FROZEN) ? actor.get(RpgStatusType.FROZEN).endsAtNanos : Long.MAX_VALUE;
        actor.entrySet().removeIf(entry -> entry.getValue().endsAtNanos <= now);
        if (frozenEnd <= now) frozenImmunityEnds.put(target, frozenEnd + Math.round(profile.frozenImmunitySeconds * 1_000_000_000.0));
        if (actor.isEmpty()) states.remove(target);
        if(!actor.containsKey(RpgStatusType.FEAR)){fearSources.remove(target);fearRoots.remove(target);}
    }
    private static boolean isHardControl(RpgStatusType type) {
        return type == RpgStatusType.FROZEN || type == RpgStatusType.ROOT || type == RpgStatusType.FEAR
                || type == RpgStatusType.TAUNT || type == RpgStatusType.STAGGER || type == RpgStatusType.STUN
                || type == RpgStatusType.SILENCE || type == RpgStatusType.BLIND;
    }
    private record State(int stacks, long endsAtNanos) { }
    /** Native entity removal is the terminal authority for victim-owned status memory. */
    public synchronized void forget(UUID target) {
        forgetSource(target);
        states.remove(target); slows.remove(target); stackedSlowEnds.remove(target);
        slowTargetLocks.remove(target);slowSourceLocks.keySet().removeIf(key->key.target.equals(target));
        regenerationSuppression.remove(target);
        fearSources.remove(target);fearRoots.remove(target);
        controls.remove(target); frozenImmunityEnds.remove(target);
        chillBonusEnds.keySet().removeIf(k->k.owner.equals(target)||k.target.equals(target));
    }
    public enum Outcome { APPLIED, REFRESHED, THRESHOLD, REJECTED }
    public record Result(Outcome outcome, RpgStatusType type, int stacks, double remainingSeconds, String detail) { }
    public record StatusView(int stacks, double remainingSeconds) { }
    public record Snapshot(Map<RpgStatusType, StatusView> active) { public Snapshot { active = Map.copyOf(active); } }
}
