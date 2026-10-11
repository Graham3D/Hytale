package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.*;

/** Bounded, source-owned finite effects. No native references, damage writer, second resource pool or Aura timer. */
public final class FiniteSupportEffects {
    public static final int MAX_EFFECTS=4096,MAX_OWNER_EFFECTS=256,MAX_TARGET_EFFECTS=32;
    public enum Stat { DEFENSE_BREAK, DIRECT_WEAKEN, AURA_PHYSICAL_INCREASE, AURA_RESISTANCE_ADD, AURA_MOVEMENT_INCREASE, AURA_RECOVERY_INCREASE }
    public enum SourceKind { MONSTER_AFFIX }
    /** Typed stat provenance; never an invisible Skill or equipment grant. */
    public record StatSource(UUID world,UUID actor,long generation,SourceKind kind,String definitionId) {
        public StatSource {
            Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(kind);
            if(generation<0||definitionId==null||!Set.of("ME-014","ME-025","ME-023").contains(definitionId))
                throw new IllegalArgumentException("INVALID_TIMED_STAT_SOURCE");
        }
    }
    public record StatKey(StatSource source,UUID target,long targetGeneration,Stat stat) {
        public StatKey {Objects.requireNonNull(source);Objects.requireNonNull(target);Objects.requireNonNull(stat);
            if(targetGeneration<0||!(stat==Stat.DEFENSE_BREAK?"ME-025":stat==Stat.DIRECT_WEAKEN?"ME-014":"ME-023").equals(source.definitionId()))
                throw new IllegalArgumentException("INVALID_TIMED_STAT_KEY");}
    }
    public record StatEffect(StatKey key,double fraction,double starts,double ends) {
        public StatEffect {Objects.requireNonNull(key);
            if(!Double.isFinite(fraction)||fraction<0||fraction>1||!Double.isFinite(starts)||!Double.isFinite(ends)||ends<=starts||ends-starts>120)
                throw new IllegalArgumentException("INVALID_TIMED_STAT_EFFECT");}
    }
    private final Map<StatKey,StatEffect> statEffects=new LinkedHashMap<>();
    public record IntrinsicShieldKey(UUID world,UUID target,long generation,String definitionId) {
        public IntrinsicShieldKey {
            Objects.requireNonNull(world);Objects.requireNonNull(target);
            if(generation<0||!"ME-027".equals(definitionId))throw new IllegalArgumentException("INVALID_INTRINSIC_SHIELD_SOURCE");
        }
    }
    public record IntrinsicShield(IntrinsicShieldKey key,double capacity,double consumed) {
        public IntrinsicShield {
            Objects.requireNonNull(key);
            if(!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE||!Double.isFinite(consumed)||consumed<0||consumed>capacity)
                throw new IllegalArgumentException("INVALID_INTRINSIC_SHIELD");
        }
        public double remaining(){return Math.max(0,capacity-consumed);}
    }
    public record IntrinsicAbsorption(IntrinsicShield after,double amount) {}
    private final Map<IntrinsicShieldKey,IntrinsicShield> intrinsicShields=new LinkedHashMap<>();
    /** Intrinsic initialization/restore. External healing or refresh must never call this entry point. */
    public synchronized IntrinsicShield restoreIntrinsicShield(IntrinsicShield saved) {
        var key=saved.key();var prior=intrinsicShields.get(key);
        if(prior!=null){
            if(prior.capacity()!=saved.capacity())throw new IllegalStateException("INTRINSIC_SHIELD_BASELINE_CHANGED");
            saved=new IntrinsicShield(key,prior.capacity(),Math.max(prior.consumed(),saved.consumed()));
        }else{
            if(intrinsicShields.keySet().stream().anyMatch(k->k.world.equals(key.world)&&k.target.equals(key.target)))
                throw new IllegalStateException("INTRINSIC_SHIELD_GENERATION_STILL_BOUND");
            if(size()>=MAX_EFFECTS||ownedCount(key.target)>=MAX_OWNER_EFFECTS||targetCount(key.target)>=MAX_TARGET_EFFECTS)
                throw new IllegalStateException("FINITE_SUPPORT_INTRINSIC_SHIELD_BUDGET");
        }
        intrinsicShields.put(key,saved);return saved;
    }
    public synchronized Optional<IntrinsicShield> intrinsicShield(IntrinsicShieldKey key){return Optional.ofNullable(intrinsicShields.get(key));}
    public synchronized Optional<IntrinsicShield> intrinsicShield(UUID world,UUID target){return intrinsicShields.values().stream()
            .filter(s->s.key.world.equals(world)&&s.key.target.equals(target)).findFirst();}
    public synchronized void unbindIntrinsicShield(IntrinsicShieldKey key){intrinsicShields.remove(key);}
    private long ownedCount(UUID actor){return effects.keySet().stream().filter(k->k.owner.equals(actor)).count()
            +statEffects.keySet().stream().filter(k->k.source.actor().equals(actor)).count()
            +intrinsicShields.keySet().stream().filter(k->k.target.equals(actor)).count();}
    private long targetCount(UUID target){return effects.keySet().stream().filter(k->k.target.equals(target)).count()
            +statEffects.keySet().stream().filter(k->k.target.equals(target)).count()
            +intrinsicShields.keySet().stream().filter(k->k.target.equals(target)).count();}
    /** Caller already resolved chance/status immunity; recheck the encounter gate at publication. */
    public synchronized boolean applyStat(StatEffect effect,double now,java.util.function.BooleanSupplier mayMutate) {
        Objects.requireNonNull(effect);Objects.requireNonNull(mayMutate);
        var next=statCandidate(effect,now);
        if(!mayMutate.getAsBoolean())return false;
        statEffects.clear();statEffects.putAll(next);return true;
    }
    /** Capacity preflight before consuming a status opportunity. No provider is added/refreshed. */
    public synchronized void requireStatAdmission(StatEffect effect,double now){statCandidate(effect,now);}
    private Map<StatKey,StatEffect> statCandidate(StatEffect effect,double now){
        if(!Double.isFinite(now)||now<effect.starts()||now>=effect.ends())throw new IllegalArgumentException("INVALID_TIMED_STAT_TIME");
        expire(now);
        var key=effect.key();var next=new LinkedHashMap<>(statEffects);next.put(key,effect);
        long ownerCount=effects.values().stream().filter(e->e.key.owner.equals(key.source.actor())).count()
                +next.keySet().stream().filter(k->k.source.actor().equals(key.source.actor())).count();
        long targetCount=effects.values().stream().filter(e->e.key.target.equals(key.target)).count()
                +next.keySet().stream().filter(k->k.target.equals(key.target)).count();
        ownerCount+=intrinsicShields.keySet().stream().filter(k->k.target.equals(key.source.actor())).count();
        targetCount+=intrinsicShields.keySet().stream().filter(k->k.target.equals(key.target)).count();
        if(next.size()+effects.size()+intrinsicShields.size()>MAX_EFFECTS||ownerCount>MAX_OWNER_EFFECTS||targetCount>MAX_TARGET_EFFECTS)
            throw new IllegalStateException("FINITE_SUPPORT_STAT_BUDGET");
        return next;
    }
    public synchronized double winningStat(UUID world,UUID target,long generation,Stat stat,double now) {
        expire(now);return statEffects.values().stream().filter(e->e.key.source.world().equals(world)&&e.key.target.equals(target)
                &&e.key.targetGeneration==generation&&e.key.stat==stat).mapToDouble(StatEffect::fraction).max().orElse(0);
    }
    /** Operator inspection reads the same live lease winner without expiring/removing any owner state. */
    public synchronized double peekWinningStat(UUID world,UUID target,long generation,Stat stat,double now){
        if(!Double.isFinite(now))throw new IllegalArgumentException("INVALID_TIMED_STAT_TIME");
        return statEffects.values().stream().filter(e->e.key.source.world().equals(world)&&e.key.target.equals(target)
                &&e.key.targetGeneration==generation&&e.key.stat==stat&&e.starts()<=now&&now<e.ends())
                .mapToDouble(StatEffect::fraction).max().orElse(0);
    }
    /** Remaining lifetime is persisted by the encounter owner; monotonic clock values are not saved. */
    public synchronized List<StatEffect> statEffects(UUID world,UUID target,long generation,double now) {
        expire(now);return statEffects.values().stream().filter(e->e.key.source.world().equals(world)&&e.key.target.equals(target)
                &&e.key.targetGeneration==generation).toList();
    }
    public synchronized boolean cleanseStats(UUID world,UUID target,long generation,java.util.function.BooleanSupplier mayMutate) {
        if(!mayMutate.getAsBoolean())return false;
        return statEffects.keySet().removeIf(k->k.source.world().equals(world)&&k.target.equals(target)&&k.targetGeneration==generation
                &&(k.stat==Stat.DEFENSE_BREAK||k.stat==Stat.DIRECT_WEAKEN));
    }
    /** Atomic replacement of one source's short aura leases, using the same finite-effect budgets/expiry. */
    public synchronized boolean replaceAuraStats(StatSource source,List<StatEffect> leases,double now,java.util.function.BooleanSupplier mayProvide){
        if(!source.definitionId().equals("ME-023")||leases.size()>16||!Double.isFinite(now))throw new IllegalArgumentException("INVALID_AURA_PROVIDER");
        var before=new LinkedHashMap<>(statEffects);
        try{
            statEffects.keySet().removeIf(key->key.source.equals(source));
            if(!mayProvide.getAsBoolean())return false;
            var keys=new HashSet<StatKey>();
            for(var lease:leases){
                if(!lease.key.source.equals(source)||!keys.add(lease.key)||lease.ends-now>.500000001)
                    throw new IllegalArgumentException("INVALID_AURA_PROVIDER_LEASE");
                if(!applyStat(lease,now,mayProvide)){
                    statEffects.keySet().removeIf(key->key.source.equals(source));
                    return false;
                }
            }
            if(!mayProvide.getAsBoolean()){
                statEffects.keySet().removeIf(key->key.source.equals(source));
                return false;
            }
            return true;
        }catch(RuntimeException error){statEffects.clear();statEffects.putAll(before);throw error;}
    }
    public record Key(UUID world,UUID owner,String skill,UUID target){}
    public record Effect(Key key,SupportProfile.Kind kind,double magnitude,double movement,double starts,double ends,
                         String rootCastId,String skillInstanceId,String correlationId,SkillExecutionContext context,double shieldRemaining,
                         double baseShieldCapacity,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot admittedGear){
        public Effect(Key key,SupportProfile.Kind kind,double magnitude,double movement,double starts,double ends,
                      String rootCastId,String skillInstanceId,String correlationId,SkillExecutionContext context,
                      double shieldRemaining,double baseShieldCapacity){
            this(key,kind,magnitude,movement,starts,ends,rootCastId,skillInstanceId,correlationId,context,
                    shieldRemaining,baseShieldCapacity,context==null?com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY:context.gearSnapshot());
        }
        public Effect(Key key,SupportProfile.Kind kind,double magnitude,double movement,double starts,double ends,
                      String rootCastId,String skillInstanceId,String correlationId,SkillExecutionContext context,double shieldRemaining){
            this(key,kind,magnitude,movement,starts,ends,rootCastId,skillInstanceId,correlationId,context,shieldRemaining,0);
        }
        public Optional<com.inigmasgames.hytalerpg.combat.damage.MaxHealthDamageCap> damageCap(){
            return kind==SupportProfile.Kind.MAX_HEALTH_DAMAGE_CAP?Optional.of(new com.inigmasgames.hytalerpg.combat.damage.MaxHealthDamageCap(magnitude)):Optional.empty();
        }
        Effect remaining(double value){return new Effect(key,kind,magnitude,movement,starts,ends,rootCastId,skillInstanceId,correlationId,context,value,baseShieldCapacity,admittedGear);}
    }
    private final Map<Key,Effect> effects=new LinkedHashMap<>();
    private record CleansePair(UUID world,UUID caster,UUID recipient){}
    private final Map<CleansePair,Double> cleanseLocks=new HashMap<>();
    /** Consumes only an owner-issued post-removal receipt, never an attempted cleanse. */
    public synchronized Optional<Effect> cleanseResolved(StatusService.CleanseReceipt receipt,double normalMaximumHealth){
        Objects.requireNonNull(receipt);
        if(!Double.isFinite(normalMaximumHealth)||normalMaximumHealth<=0)throw new IllegalArgumentException("Invalid recipient Health maximum");
        if(!receipt.claim())return Optional.empty();
        var source=receipt.source();double at=receipt.at();
        expire(at);
        double percent=source.admittedGear().percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.CLEANSE_RESPITE);
        if(receipt.removed().isEmpty()||percent<=0)return Optional.empty();
        var pair=new CleansePair(source.world(),source.caster(),source.recipient());
        if(cleanseLocks.getOrDefault(pair,Double.NEGATIVE_INFINITY)>at)return Optional.empty();
        double capacity=normalMaximumHealth*percent;
        var key=new Key(source.world(),source.caster(),"cleanse:respite",source.recipient());
        var effect=new Effect(key,SupportProfile.Kind.CLEANSE_BARRIER,capacity,0,at,at+3,
                source.root(),source.skillInstance(),source.correlation(),null,capacity,capacity,source.admittedGear());
        var next=new LinkedHashMap<>(effects);next.put(key,effect);
        requireBudget(next,source.caster(),List.of(source.recipient()));
        if(cleanseLocks.size()>=MAX_EFFECTS&&!cleanseLocks.containsKey(pair))throw new IllegalStateException("CLEANSE_LOCK_CAPACITY");
        effects.clear();effects.putAll(next);cleanseLocks.put(pair,at+10);
        return Optional.of(effect);
    }
    private record Root(UUID world,UUID owner,String id){}
    private static final class RootState {int used;double ends;boolean reported,shared;}
    private final Map<Root,RootState> secondaryRoots=new HashMap<>();
    private boolean fullReported;
    public synchronized int claimSecondary(Effect effect,double now){
        expire(now);var key=new Root(effect.key.world,effect.key.owner,effect.rootCastId);
        var state=secondaryRoots.get(key);
        if(state==null){
            if(secondaryRoots.size()>=MAX_EFFECTS){if(fullReported)return 0;fullReported=true;return -1;}
            fullReported=false;state=new RootState();secondaryRoots.put(key,state);
        }
        state.ends=Math.max(state.ends,effect.ends);
        if(state.used<16){state.used++;return 1;}
        if(state.reported)return 0;state.reported=true;return -1;
    }
    /** Observe the completed native heal, never equate a rejected write with overheal.
     * Overflow is one capped recipient pool, including casts from different owners/skills. */
    public synchronized Optional<Effect> healingResolved(SkillExecutionContext context,UUID target,double requested,
                                                          double before,double after,double maximum,double now){
        if(!context.compiledPlan().supportModifiers().overflow())return Optional.empty();
        for(double value:new double[]{requested,before,after,maximum,now})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("INVALID_HEAL_OBSERVATION");
        if(maximum<=0||before>maximum||after>maximum||Math.abs(after-Math.min(maximum,before+requested))>1e-4)
            throw new IllegalStateException("HEAL_WRITE_NOT_CONFIRMED");
        double overheal=Math.max(0,requested-(maximum-before));
        if(overheal<=1e-9)return Optional.empty();
        expire(now);UUID world=context.target().worldId();
        double capacity=Math.min(maximum*.2,overheal);
        var previous=forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.OVERFLOW)
                .max(Comparator.comparingDouble(Effect::shieldRemaining).thenComparing(e->e.key.owner.toString()));
        var ownerContext=previous.isPresent()&&previous.get().shieldRemaining>capacity?previous.get().context:context;
        capacity=Math.min(maximum*.2,Math.max(capacity,previous.map(Effect::shieldRemaining).orElse(0d)));
        var key=new Key(world,ownerContext.request().actorId(),ownerContext.profile().skillId(),Objects.requireNonNull(target));
        var effect=new Effect(key,SupportProfile.Kind.OVERFLOW,capacity,0,now,now+6,ownerContext.rootCastId(),ownerContext.skillInstanceId(),
                ownerContext.request().correlationId(),ownerContext,capacity);
        var next=new LinkedHashMap<>(effects);
        next.values().removeIf(e->e.key.world.equals(world)&&e.key.target.equals(target)&&e.kind==SupportProfile.Kind.OVERFLOW);
        next.put(key,effect);requireBudget(next,key.owner,List.of(target));
        effects.clear();effects.putAll(next);return Optional.of(effect);
    }
    public static boolean isShield(Effect e){return e.kind==SupportProfile.Kind.SHIELD||e.kind==SupportProfile.Kind.OVERFLOW||e.kind==SupportProfile.Kind.SHARED_SHIELD||e.kind==SupportProfile.Kind.CONSUME_MINION||e.kind==SupportProfile.Kind.CLEANSE_BARRIER;}
    /** One self-only, source-owned effect carries both the skill-damage bonus and shield. */
    public synchronized void consumeMinion(SkillExecutionContext context,double now){
        var p=context.profile().summonAction();
        if(p==null||p.kind()!=com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CONSUME_MINION||!Double.isFinite(now))
            throw new IllegalArgumentException("INVALID_CONSUME_BENEFIT");
        expire(now);UUID actor=context.request().actorId();var key=new Key(context.target().worldId(),actor,context.profile().skillId(),actor);
        var effect=new Effect(key,SupportProfile.Kind.CONSUME_MINION,p.damageIncreased(),0,now,now+p.duration(),context.rootCastId(),context.skillInstanceId(),
                context.request().correlationId(),context,context.snapshot().derivedStats().maxHealth()*p.shieldFraction());
        var next=new LinkedHashMap<>(effects);next.put(key,effect);requireBudget(next,actor,List.of(actor));effects.clear();effects.putAll(next);
    }
    /** One derived ally shield, with the created parent's already-modified capacity; no redirect or recursive share. */
    public synchronized Optional<Effect> shareCreatedShield(SkillExecutionContext context,UUID ally,double now){
        if(!context.compiledPlan().supportModifiers().sharedAegis()||ally==null||ally.equals(context.request().actorId())||
                !context.request().actorId().equals(context.target().entityId()))return Optional.empty();
        expire(now);var parent=effects.get(new Key(context.target().worldId(),context.request().actorId(),context.profile().skillId(),context.request().actorId()));
        if(parent==null||parent.kind!=SupportProfile.Kind.SHIELD||!parent.skillInstanceId.equals(context.skillInstanceId()))return Optional.empty();
        var key=new Key(parent.key.world,parent.key.owner,parent.key.skill,ally);
        var child=new Effect(key,SupportProfile.Kind.SHARED_SHIELD,parent.magnitude*.5,0,now,parent.ends,parent.rootCastId,
                parent.skillInstanceId,parent.correlationId,context,parent.magnitude*.5,parent.baseShieldCapacity*.5);
        var root=new Root(key.world,key.owner,parent.rootCastId);
        if(secondaryRoots.containsKey(root)&&secondaryRoots.get(root).shared)return Optional.empty();
        var next=new LinkedHashMap<>(effects);
        next.values().removeIf(e->e.key.world.equals(key.world)&&e.key.owner.equals(key.owner)&&e.key.skill.equals(key.skill)&&e.kind==SupportProfile.Kind.SHARED_SHIELD);
        next.put(key,child);requireBudget(next,key.owner,List.of(ally));
        if(claimSecondary(child,now)!=1)return Optional.empty();secondaryRoots.get(root).shared=true;
        effects.clear();effects.putAll(next);return Optional.of(child);
    }
    public synchronized void apply(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        var next=prepare(context,targets,seconds,now);effects.clear();effects.putAll(next);
    }
    /** Select only the lifetime of a cast finite support receipt. The profile retains its authored base. */
    public static double durationSeconds(SkillExecutionContext context){
        var profile=Objects.requireNonNull(context).profile().support();
        if(profile==null)throw new IllegalArgumentException("Support profile missing");
        double base=profile.durationSeconds();
        if(context.profile().skillId().equals("spirit_shield"))return 10;
        if(!profile.finiteEffect()||profile.hostileTarget())return base;
        return base*(1+context.gearSnapshot().percent(
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.FINITE_SUPPORT_DURATION));
    }
    public synchronized void applyShield(SkillExecutionContext context,List<UUID> targets,double seconds,double capacity,double now){
        if(context.profile().support().kind()!=SupportProfile.Kind.SHIELD||!Double.isFinite(capacity)||capacity<=0)
            throw new IllegalArgumentException("Invalid shield capacity");
        var next=prepare(context,targets,seconds,now);
        for(var target:targets){
            var key=new Key(context.target().worldId(),context.request().actorId(),context.profile().skillId(),target);
            var e=next.get(key);
            // max(oldRemaining, newCreated), capped at newCreated, equals capacity: replace, never add copies.
            double factor=1+context.gearSnapshot().percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.BARRIER_STRENGTH);
            next.put(key,new Effect(key,e.kind,capacity,e.movement,e.starts,e.ends,e.rootCastId,e.skillInstanceId,e.correlationId,e.context,capacity,capacity/factor));
        }
        effects.clear();effects.putAll(next);
    }
    /** Equipment changes capacity in place; they never refill absorbed points or restart expiry. */
    public synchronized void reprojectShieldCapacity(UUID owner,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot live,double now){
        Objects.requireNonNull(owner);Objects.requireNonNull(live);expire(now);
        double currentFactor=1+live.percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.BARRIER_STRENGTH);
        for(var entry:List.copyOf(effects.entrySet())){
            var effect=entry.getValue();
            if(!effect.key.owner.equals(owner)||effect.key.skill.equals("spirit_shield")
                    ||!Set.of(SupportProfile.Kind.SHIELD,SupportProfile.Kind.SHARED_SHIELD).contains(effect.kind))continue;
            double capacity=effect.baseShieldCapacity*currentFactor;
            double remaining=Math.min(effect.shieldRemaining,capacity);
            effects.put(entry.getKey(),new Effect(effect.key,effect.kind,capacity,effect.movement,effect.starts,effect.ends,
                    effect.rootCastId,effect.skillInstanceId,effect.correlationId,effect.context,remaining,effect.baseShieldCapacity));
        }
    }
    /** Validate before native/status side effects; apply revalidates again when publishing the batch. */
    public synchronized void requireAdmission(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        prepare(context,targets,seconds,now);
    }
    private Map<Key,Effect> prepare(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        var p=context.profile().support();
        if(!p.finiteEffect()||!Double.isFinite(now)||!Double.isFinite(seconds)||seconds<=0||seconds>120)
            throw new IllegalArgumentException("Invalid finite support effect");
        if(targets.isEmpty()||targets.size()>64||new HashSet<>(targets).size()!=targets.size())throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        expire(now);
        var next=new LinkedHashMap<>(effects);
        UUID actor=context.request().actorId(),world=context.target().worldId();
        // One mark per caster, not one mark per victim or one additional mark on each reapplication.
        if(p.kind()==SupportProfile.Kind.MARK)next.entrySet().removeIf(e->e.getKey().owner().equals(actor)&&e.getValue().kind()==p.kind());
        for(UUID target:targets){
            var key=new Key(world,actor,context.profile().skillId(),Objects.requireNonNull(target));
            double magnitude=p.kind()==SupportProfile.Kind.REFLECT?p.coefficient()*context.snapshot().modifiers().factor():p.coefficient();
            double movement=p.movementIncreased();
            if(context.compiledPlan().retaliation()){
                if(p.kind()==SupportProfile.Kind.WEAKEN)magnitude=1-(1-magnitude)*.70;
                else if(Set.of(SupportProfile.Kind.RALLY,SupportProfile.Kind.HOWL,SupportProfile.Kind.MARK,SupportProfile.Kind.IMBUE).contains(p.kind()))magnitude*=.70;
                movement*=.70;
            }
            next.put(key,new Effect(key,p.kind(),magnitude,movement,now,now+seconds,
                    context.rootCastId(),context.skillInstanceId(),context.request().correlationId(),context,0));
        }
        requireBudget(next,actor,targets);
        return next;
    }
    private void requireBudget(Map<Key,Effect> next,UUID actor,List<UUID> targets){
        if(next.size()+statEffects.size()+intrinsicShields.size()>MAX_EFFECTS||next.values().stream().filter(e->e.key.owner.equals(actor)).count()
                +statEffects.keySet().stream().filter(k->k.source.actor().equals(actor)).count()
                +intrinsicShields.keySet().stream().filter(k->k.target.equals(actor)).count()>MAX_OWNER_EFFECTS)
            throw new IllegalStateException("FINITE_SUPPORT_OWNER_OR_GLOBAL_BUDGET");
        for(UUID target:targets)if(next.values().stream().filter(e->e.key.target.equals(target)).count()
                +statEffects.keySet().stream().filter(k->k.target.equals(target)).count()
                +intrinsicShields.keySet().stream().filter(k->k.target.equals(target)).count()>MAX_TARGET_EFFECTS)
            throw new IllegalStateException("FINITE_SUPPORT_TARGET_BUDGET");
    }
    public synchronized List<Effect> forTarget(UUID world,UUID target,double now){
        expire(now);return effects.values().stream().filter(e->e.key.world.equals(world)&&e.key.target.equals(target)).toList();
    }
    /** Identical named bonuses/debuffs refresh/choose strongest; different Increased contributions add once. */
    public synchronized ModifierBuckets damageModifiers(UUID world,UUID source,UUID target,ModifierBuckets base,double now){
        return victimModifiers(world,source,target,outgoingModifiers(world,source,base,now),now);
    }
    /** ME Cursed is direct-only. Call after ordinary outgoing composition, never while creating a DoT package. */
    public synchronized ModifierBuckets directWeakening(UUID world,UUID source,long generation,ModifierBuckets base,double now){
        double fraction=winningStat(world,source,generation,Stat.DIRECT_WEAKEN,now);
        if(fraction==0)return base;
        var less=new ArrayList<>(base.less());less.add(fraction);
        return new ModifierBuckets(base.increased(),base.reduced(),base.more(),less);
    }
    public synchronized double nativeDirectOutgoingFactor(UUID world,UUID source,long generation,double now){
        return nativeOutgoingFactor(world,source,now)*(1-winningStat(world,source,generation,Stat.DIRECT_WEAKEN,now));
    }
    /** Capture outgoing buff/debuff contributions with source-owned DoT snapshots, never at every later DoT tick. */
    public synchronized ModifierBuckets outgoingModifiers(UUID world,UUID source,ModifierBuckets base,double now){
        expire(now);double rally=0,howl=0,weak=0,consume=0;
        for(var e:effects.values()){
            if(!e.key.world.equals(world))continue;
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.HOWL)howl=Math.max(howl,e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.CONSUME_MINION)consume=Math.max(consume,e.magnitude);
        }
        var increased=new ArrayList<>(base.increased());if(rally>0)increased.add(rally);if(howl>0)increased.add(howl);
        if(consume>0)increased.add(consume);
        var less=new ArrayList<>(base.less());if(weak>0)less.add(weak);
        return new ModifierBuckets(increased,base.reduced(),base.more(),less);
    }
    /** Victim-side mark is evaluated at the native hit boundary, not baked into the offensive DoT snapshot. */
    public synchronized ModifierBuckets victimModifiers(UUID world,UUID source,UUID target,ModifierBuckets base,double now){
        double mark=forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.MARK&&e.key.owner.equals(source))
                .mapToDouble(Effect::magnitude).max().orElse(0);
        if(mark==0)return base;var increased=new ArrayList<>(base.increased());increased.add(mark);
        return new ModifierBuckets(increased,base.reduced(),base.more(),base.less());
    }
    /** Native non-RPG hits have no RPG mark and no RPG snapshot bucket. Self-costs/reflections are excluded by caller. */
    public synchronized double nativeOutgoingFactor(UUID world,UUID source,double now){
        expire(now);double rally=0,howl=0,weak=0;
        for(var e:effects.values())if(e.key.world.equals(world)&&e.key.target.equals(source)){
            if(e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.kind==SupportProfile.Kind.HOWL)howl=Math.max(howl,e.magnitude);
            if(e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
        }
        return (1+rally+howl)*(1-weak);
    }
    public synchronized Optional<Effect> control(UUID world,UUID target,SupportProfile.Kind kind,double now){
        return forTarget(world,target,now).stream().filter(e->e.kind==kind)
                .max(Comparator.comparingDouble(Effect::starts).thenComparing(e->e.key.owner.toString()));
    }
    public synchronized boolean directDamage(UUID world,UUID target,double now,boolean direct,boolean positiveApplied){
        if(!direct||!positiveApplied)return false;
        return effects.values().removeIf(e->e.key.world.equals(world)&&e.key.target.equals(target)&&e.kind==SupportProfile.Kind.FEAR&&now-e.starts>=.25);
    }
    public synchronized double movementIncreased(UUID world,UUID target,double now){
        var named=new EnumMap<SupportProfile.Kind,Double>(SupportProfile.Kind.class);
        for(var e:forTarget(world,target,now))named.merge(e.kind,e.movement,Math::max);
        return named.values().stream().mapToDouble(Double::doubleValue).sum();
    }
    public record Absorption(Effect effect,double amount,double remaining){}
    public record ShieldHit(double remainder,double absorbed,double redirected,List<Absorption> allocations,
                            List<IntrinsicAbsorption> intrinsicAllocations){}
    @FunctionalInterface public interface Transfer { boolean transfer(Effect shield,double amount); }
    /** Already-mitigated incoming damage: one redirect, then ordered bounded absorption; no direct Health writes. */
    public synchronized ShieldHit shieldHit(UUID world,UUID target,double incoming,boolean mayRedirect,double now,Transfer transfer){
        if(!Double.isFinite(incoming)||incoming<0)throw new IllegalArgumentException("Invalid incoming damage");
        var shields=forTarget(world,target,now).stream().filter(e->isShield(e)&&e.shieldRemaining>0)
                .sorted(Comparator.comparingDouble(Effect::starts).thenComparing(e->e.key.owner.toString())).toList();
        double remaining=incoming,redirected=0,absorbed=0;var allocations=new ArrayList<Absorption>();
        if(mayRedirect){
            var chosen=shields.stream().filter(e->e.kind==SupportProfile.Kind.SHIELD&&!e.key.owner.equals(target)
                    &&!e.key.skill.equals("spirit_shield")).findFirst();
            if(chosen.isPresent()&&transfer.transfer(chosen.get(),incoming*.2)){redirected=incoming*.2;remaining-=redirected;}
        }
        for(var shield:shields){
            var current=effects.get(shield.key);if(current==null)continue;
            double used=Math.min(remaining,current.shieldRemaining);remaining-=used;absorbed+=used;
            if(used>0)allocations.add(new Absorption(current,used,current.shieldRemaining-used));
            if(current.shieldRemaining-used<=1e-9&&current.kind!=SupportProfile.Kind.CONSUME_MINION)effects.remove(shield.key);
            else effects.put(shield.key,current.remaining(current.shieldRemaining-used));
            if(remaining<=1e-9)break;
        }
        var intrinsicAllocations=new ArrayList<IntrinsicAbsorption>();
        for(var entry:intrinsicShields.entrySet()){
            var shield=entry.getValue();
            if(!shield.key.world.equals(world)||!shield.key.target.equals(target))continue;
            double used=Math.min(remaining,shield.remaining());
            if(used<=0)continue;
            var after=new IntrinsicShield(shield.key,shield.capacity,Math.min(shield.capacity,shield.consumed+used));
            entry.setValue(after);remaining-=used;absorbed+=used;intrinsicAllocations.add(new IntrinsicAbsorption(after,used));
        }
        return new ShieldHit(remaining,absorbed,redirected,List.copyOf(allocations),List.copyOf(intrinsicAllocations));
    }
    public synchronized Optional<Effect> reflection(UUID world,UUID target,double now){
        return forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.REFLECT)
                .max(Comparator.comparingDouble(Effect::magnitude).thenComparing(e->e.key.owner.toString()));
    }
    public synchronized void remove(Key key){effects.remove(key);}
    /** Removes only ME-023 membership when an actor dies or leaves its native world. */
    public synchronized void withdrawAuraActor(UUID world,UUID actor,long generation){
        statEffects.keySet().removeIf(k->k.source.world().equals(world)
                &&(k.target.equals(actor)&&k.targetGeneration==generation
                ||k.source.actor().equals(actor)&&k.source.generation()==generation)
                &&k.source.definitionId().equals("ME-023"));
    }
    public synchronized void forget(UUID actor){
        effects.values().removeIf(e->e.key.owner.equals(actor)||e.key.target.equals(actor));
        secondaryRoots.keySet().removeIf(k->k.owner.equals(actor));
        cleanseLocks.keySet().removeIf(k->k.caster.equals(actor)||k.recipient.equals(actor));
        statEffects.keySet().removeIf(k->k.target.equals(actor)||k.source.actor().equals(actor)
                &&k.stat!=Stat.DEFENSE_BREAK&&k.stat!=Stat.DIRECT_WEAKEN);
        intrinsicShields.keySet().removeIf(k->k.target.equals(actor));
    }
    public synchronized void clearWorld(UUID world){
        effects.values().removeIf(e->e.key.world.equals(world));
        secondaryRoots.keySet().removeIf(k->k.world.equals(world));
        cleanseLocks.keySet().removeIf(k->k.world.equals(world));
        statEffects.keySet().removeIf(k->k.source.world().equals(world));
        intrinsicShields.keySet().removeIf(k->k.world.equals(world));
    }
    public synchronized void expire(double now){
        effects.values().removeIf(e->e.ends<=now);
        secondaryRoots.values().removeIf(s->s.ends<=now);
        cleanseLocks.values().removeIf(end->end<=now);
        statEffects.values().removeIf(e->e.ends<=now);
    }
    public synchronized int secondaryRootCount(){return secondaryRoots.size();}
    public synchronized int size(){return effects.size()+statEffects.size()+intrinsicShields.size();}
}
