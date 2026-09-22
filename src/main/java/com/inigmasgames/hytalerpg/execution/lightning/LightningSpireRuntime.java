package com.inigmasgames.hytalerpg.execution.lightning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Native-object-free authority for Lightning Spire's emergence, READY lifetime,
 * hit-count charge and repeated discharge sequence. The Hytale adapter owns the
 * damageable entity and presentation; this ledger owns the gameplay transitions.
 */
public final class LightningSpireRuntime {
    public static final int HITS_PER_WAVE = 4;
    public static final double EMERGENCE_SECONDS = 2;
    public static final double BASE_READY_SECONDS = 10;
    public static final double BASE_RADIUS = 6;
    public static final double BASE_COEFFICIENT = 1.80;
    private static final int MAX_SPIRES = 1024;
    private static final int MAX_PENDING_WAVES = 64;

    public enum Phase { EMERGING, READY }
    public enum EndReason { READY_EXPIRED, DESTROYED, OWNER_CLEANUP, REPLACED }

    public record Spec(double readySeconds,double radius,double coefficient,double maximumHealth) {
        public Spec {
            if(!Double.isFinite(readySeconds)||readySeconds<=0||readySeconds>30
                    ||!Double.isFinite(radius)||radius<=0||radius>24
                    ||!Double.isFinite(coefficient)||coefficient<=0||coefficient>10
                    ||!Double.isFinite(maximumHealth)||maximumHealth<=0||maximumHealth>100_000)
                throw new IllegalArgumentException("INVALID_LIGHTNING_SPIRE_SPEC");
        }
        public static Spec base(int skillLevel,double durationFactor,double radiusFactor,double damageFactor){
            if(skillLevel<1||skillLevel>1001||!positive(durationFactor,radiusFactor,damageFactor))
                throw new IllegalArgumentException("INVALID_LIGHTNING_SPIRE_LEVEL_OR_MODIFIER");
            double levelDamage=1+.0125*(skillLevel-1);
            return new Spec(BASE_READY_SECONDS*durationFactor,BASE_RADIUS*radiusFactor,
                    BASE_COEFFICIENT*levelDamage*damageFactor,50+2.5*(skillLevel-1));
        }
        private static boolean positive(double... values){for(double v:values)if(!Double.isFinite(v)||v<=0)return false;return true;}
    }

    public record View(String instance,UUID owner,Phase phase,double emergenceProgress,double readyRemaining,
                       int chargeHits,int chargePercent,long completedWaves,double radius,double coefficient,
                       double maximumHealth) {}
    public record ChargeResult(String code,int chargeHits,int chargePercent,long waveSequence) {
        public boolean discharge(){return waveSequence>0;}
    }
    public record Wave(String instance,UUID owner,long sequence,double radius,double coefficient) {}
    public record Ended(String instance,UUID owner,EndReason reason,long completedWaves) {}

    private static final class State {
        final String instance; final UUID owner; final Spec spec; final double deployedAt,readyAt,expiresAt;
        int hits; long waves; boolean ended;
        final ArrayDeque<Wave> pending=new ArrayDeque<>();
        State(String instance,UUID owner,Spec spec,double now){this.instance=instance;this.owner=owner;this.spec=spec;
            deployedAt=now;readyAt=now+EMERGENCE_SECONDS;expiresAt=readyAt+spec.readySeconds();}
        View view(double now){Phase phase=now<readyAt?Phase.EMERGING:Phase.READY;
            double emergence=Math.clamp((now-deployedAt)/EMERGENCE_SECONDS,0,1);
            return new View(instance,owner,phase,emergence,Math.max(0,expiresAt-Math.max(now,readyAt)),hits,percent(hits),waves,
                    spec.radius(),spec.coefficient(),spec.maximumHealth());}
    }

    private final Map<String,State> byInstance=new HashMap<>();
    private final Map<UUID,LinkedHashSet<String>> byOwner=new HashMap<>();

    public synchronized View deploy(String instance,UUID owner,Spec spec,double now){
        requireTime(now);requireId(instance);Objects.requireNonNull(owner);Objects.requireNonNull(spec);
        if(byInstance.size()>=MAX_SPIRES)throw new IllegalStateException("LIGHTNING_SPIRE_BUDGET");
        var state=new State(instance,owner,spec,now);
        if(byInstance.putIfAbsent(instance,state)!=null)throw new IllegalStateException("LIGHTNING_SPIRE_INSTANCE_EXISTS");
        byOwner.computeIfAbsent(owner,ignored->new LinkedHashSet<>()).add(instance);return state.view(now);
    }

    public synchronized boolean active(UUID owner){var ids=byOwner.get(Objects.requireNonNull(owner));return ids!=null&&!ids.isEmpty();}
    public synchronized Optional<View> inspectOwner(UUID owner,double now){
        var all=inspectOwnerAll(owner,now);return all.isEmpty()?Optional.empty():Optional.of(all.getLast());
    }
    public synchronized List<View> inspectOwnerAll(UUID owner,double now){
        requireTime(now);var ids=byOwner.get(Objects.requireNonNull(owner));if(ids==null)return List.of();
        return ids.stream().map(byInstance::get).filter(Objects::nonNull).map(state->state.view(now)).toList();
    }
    public synchronized Optional<View> inspect(String instance,double now){requireTime(now);var s=byInstance.get(requireId(instance));return s==null?Optional.empty():Optional.of(s.view(now));}

    /** One accepted authored direct-melee event contributes exactly one hit. */
    public synchronized ChargeResult friendlyMelee(String instance,String hitIdentity,double now){
        requireTime(now);requireId(hitIdentity);var state=byInstance.get(requireId(instance));
        if(state==null||state.ended)return new ChargeResult("MISSING",0,0,0);
        if(now<state.readyAt)return new ChargeResult("EMERGING",state.hits,percent(state.hits),0);
        if(now>=state.expiresAt)return new ChargeResult("EXPIRED",state.hits,percent(state.hits),0);
        // Root/operation/target identity is supplied by the native authored-hit witness.
        if(!HitDedup.claim(state.instance,hitIdentity))return new ChargeResult("DUPLICATE",state.hits,percent(state.hits),0);
        state.hits++;
        if(state.hits<HITS_PER_WAVE)return new ChargeResult("CHARGED",state.hits,percent(state.hits),0);
        state.hits=0;long sequence=++state.waves;
        if(state.pending.size()>=MAX_PENDING_WAVES)throw new IllegalStateException("LIGHTNING_SPIRE_PENDING_WAVE_BUDGET");
        state.pending.addLast(new Wave(state.instance,state.owner,sequence,state.spec.radius(),state.spec.coefficient()));
        return new ChargeResult("DISCHARGE",0,0,sequence);
    }

    public synchronized List<Wave> drainWaves(UUID owner){
        var result=new ArrayList<Wave>();for(var view:inspectOwnerAll(owner,0))result.addAll(drainWaves(view.instance()));return List.copyOf(result);
    }
    public synchronized List<Wave> drainWaves(String instance){var state=byInstance.get(requireId(instance));if(state==null)return List.of();
        var result=new ArrayList<Wave>(state.pending);state.pending.clear();return List.copyOf(result);}

    public synchronized Optional<Ended> expire(UUID owner,double now){
        requireTime(now);for(var view:inspectOwnerAll(owner,now)){var ended=expire(view.instance(),now);if(ended.isPresent())return ended;}return Optional.empty();
    }
    public synchronized Optional<Ended> expire(String instance,double now){requireTime(now);var state=byInstance.get(requireId(instance));
        return state!=null&&now>=state.expiresAt?Optional.of(remove(state,EndReason.READY_EXPIRED)):Optional.empty();}
    public synchronized Optional<Ended> destroy(String instance){var state=byInstance.get(requireId(instance));return state==null?Optional.empty():Optional.of(remove(state,EndReason.DESTROYED));}
    public synchronized List<Ended> cancel(UUID owner,EndReason reason){var result=new ArrayList<Ended>();
        for(var view:inspectOwnerAll(Objects.requireNonNull(owner),0)){var state=byInstance.get(view.instance());if(state!=null)result.add(remove(state,Objects.requireNonNull(reason)));}
        return List.copyOf(result);}
    public synchronized Optional<Ended> cancel(String instance,EndReason reason){var state=byInstance.get(requireId(instance));return state==null?Optional.empty():Optional.of(remove(state,Objects.requireNonNull(reason)));}
    private Ended remove(State state,EndReason reason){state.ended=true;byInstance.remove(state.instance,state);var ids=byOwner.get(state.owner);
        if(ids!=null){ids.remove(state.instance);if(ids.isEmpty())byOwner.remove(state.owner);}HitDedup.forget(state.instance);return new Ended(state.instance,state.owner,reason,state.waves);}

    private static String requireId(String value){if(value==null||value.isBlank()||value.length()>512)throw new IllegalArgumentException("INVALID_LIGHTNING_SPIRE_ID");return value;}
    private static void requireTime(double now){if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_TIME");}
    private static int percent(int hits){return Math.clamp(hits*100/HITS_PER_WAVE,0,100);}

    /** Bounded per-Spire strike identity set; reset only with that deployment. */
    private static final class HitDedup {
        private static final Map<String,java.util.LinkedHashSet<String>> SEEN=new HashMap<>();
        static synchronized boolean claim(String instance,String hit){var set=SEEN.computeIfAbsent(instance,ignored->new java.util.LinkedHashSet<>());
            if(set.size()>=4096)throw new IllegalStateException("LIGHTNING_SPIRE_HIT_DEDUP_BUDGET");return set.add(hit);}
        static synchronized void forget(String instance){SEEN.remove(instance);}
    }
}
