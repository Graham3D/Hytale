package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import java.util.*;

/** Shared admission for pending AND live native actors. UUID ownership survives native Ref churn.
 * World-thread callbacks may run on different worlds: every mutation is synchronized. */
public final class SummonRegistry {
    public static final int OWNER_LIMIT=32, GLOBAL_LIMIT=256;
    private final Map<UUID,Lease> leases=new LinkedHashMap<>();
    public static final class Lease {
        private final UUID token=UUID.randomUUID();
        private final SkillExecutionContext context;
        private final double expires;
        private final GearInstance boundItem;
        private final GearEffectSnapshot ownerEffects;
        private final GearEffectSnapshot boundEffects;
        private final IronSentinelStatProjection.Stats sentinelStats;
        private final UUID boundOwner,boundWorld;
        private final String rootCastId,skillInstanceId,correlationId;
        private UUID entity;
        private double nextAttack;
        private final double maximumHealth,coefficient,interval,snapshottedMagicPower,baseArrowCoefficient,passiveMagnitudeFactor;
        private final String roleId;
        private Map<String,Double> nativeProtection=Map.of();
        private int attacks;
        private Lease(SkillExecutionContext context,double now,CorpseLedger.Source source,GearInstance ironSource,GearEffectSnapshot effects) {
            this.context=context;var modifiers=context.compiledPlan().summonModifiers();
            ownerEffects=Objects.requireNonNull(effects);
            double damage=ownerPercent(effects,"WA-113"),health=ownerPercent(effects,"WA-114");
            double rate=ownerPercent(effects,"WA-117"),duration=ownerPercent(effects,"WA-119");
            var spec=context.profile().summon();
            if(spec.decoy()){damage=0;health=0;rate=0;}
            if(spec.ironSentinel()!=(ironSource!=null))throw new IllegalArgumentException("Iron Sentinel source mismatch");
            boundItem=ironSource;
            boundEffects=ironSource==null?GearEffectSnapshot.EMPTY:new GearEffectSnapshot(List.of(ironSource));
            boundOwner=context.request().actorId();boundWorld=context.target().worldId();
            rootCastId=context.rootCastId();skillInstanceId=context.skillInstanceId();correlationId=context.request().correlationId();
            sentinelStats=ironSource==null?null:IronSentinelStatProjection.project(context.effectiveSkillLevel(),boundEffects,spec.attackInterval());
            expires=ironSource==null?now+Math.max(1,spec.lifetime()*modifiers.lifetimeFactor()*(1+duration)):Double.POSITIVE_INFINITY;
            double damageFactor=increasedRatio(ironSource==null?context.snapshot().modifiers():
                    IronSentinelAffixes.physicalModifiers(ironSource),damage);
            if(ironSource!=null){
                maximumHealth=sentinelStats.finalMaxHealth()*modifiers.healthAndPowerFactor()*(1+health);
                coefficient=modifiers.healthAndPowerFactor()*damageFactor;interval=sentinelStats.attackInterval()/(1+rate);roleId=spec.roleId();
                snapshottedMagicPower=0;baseArrowCoefficient=0;passiveMagnitudeFactor=modifiers.healthAndPowerFactor();
            }
            else if(source==null){
                maximumHealth=context.snapshot().derivedStats().maxHealth()*spec.healthFactor()*modifiers.healthAndPowerFactor()*(1+health);
                if(spec.nativeRanged()){
                    if(Math.abs(spec.coefficient()-SummonArrowDamage.BASE_COEFFICIENT)>1e-12)
                        throw new IllegalArgumentException("SUMMON_ARROW_BASE_COEFFICIENT_MISMATCH");
                    snapshottedMagicPower=SummonArrowDamage.snapshotMagicPower(context.snapshot());
                    baseArrowCoefficient=SummonArrowDamage.BASE_COEFFICIENT;
                    passiveMagnitudeFactor=modifiers.healthAndPowerFactor()*damageFactor;
                    coefficient=baseArrowCoefficient*passiveMagnitudeFactor;
                }else{
                    snapshottedMagicPower=0;baseArrowCoefficient=spec.coefficient();passiveMagnitudeFactor=modifiers.healthAndPowerFactor()*damageFactor;
                    coefficient=spec.coefficient()*passiveMagnitudeFactor;
                }
                interval=SummonNativeActions.attackPeriod(spec.roleId(),spec.attackInterval(),rate*100);
                roleId=SummonNativeActions.roleId(spec.roleId(),rate*100);
            }
            else {
                double magic=context.snapshot().basePower()*context.snapshot().derivedStats().magicDamageMultiplier();
                var stats=CorpseLedger.revive(source,context.snapshot().derivedStats().maxHealth(),magic);
                maximumHealth=stats.maximumHealth()*modifiers.healthAndPowerFactor()*(1+health);coefficient=magic==0?0:stats.hitPower()/magic*modifiers.healthAndPowerFactor()*damageFactor;interval=stats.attackInterval()/(1+rate);roleId=source.projectionRole();
                snapshottedMagicPower=magic;baseArrowCoefficient=magic==0?0:stats.hitPower()/magic;passiveMagnitudeFactor=modifiers.healthAndPowerFactor();
            }
            nextAttack=now+interval;
        }
        private Lease(IronSentinelBinding binding,double now){
            context=null;boundItem=binding.boundItem();ownerEffects=binding.ownerSnapshot();
            boundEffects=new GearEffectSnapshot(List.of(boundItem));boundOwner=binding.ownerId();boundWorld=binding.worldId();
            rootCastId="iron-sentinel-"+binding.instanceId();skillInstanceId=rootCastId;
            correlationId=rootCastId;
            sentinelStats=IronSentinelStatProjection.project(binding.restoredLevel(),boundEffects,binding.restoredInterval());
            double factor=binding.restoredPowerFactor();
            maximumHealth=sentinelStats.finalMaxHealth()*factor*(1+ownerPercent(ownerEffects,"WA-114"));
            coefficient=factor*increasedRatio(IronSentinelAffixes.physicalModifiers(boundItem),
                    ownerPercent(ownerEffects,"WA-113"));
            interval=sentinelStats.attackInterval()/(1+ownerPercent(ownerEffects,"WA-117"));roleId="RPG_Iron_Sentinel";
            expires=Double.POSITIVE_INFINITY;snapshottedMagicPower=0;baseArrowCoefficient=0;passiveMagnitudeFactor=factor;
            nextAttack=now+interval;
        }
        public UUID token(){return token;}
        public SkillExecutionContext context(){return context;}
        public UUID owner(){return boundOwner;}
        public UUID world(){return boundWorld;}
        public String rootCastId(){return rootCastId;}
        public String skillInstanceId(){return skillInstanceId;}
        public String correlationId(){return correlationId;}
        public UUID entity(){return entity;}
        public double expires(){return expires;}
        public double maximumHealth(){return maximumHealth;}
        public double coefficient(){return coefficient;}
        public double snapshottedMagicPower(){return snapshottedMagicPower;}
        public double baseArrowCoefficient(){return baseArrowCoefficient;}
        public double passiveMagnitudeFactor(){return passiveMagnitudeFactor;}
        public double baseArrowPhysicalDamage(){return nativeRanged()?SummonArrowDamage.basePhysicalDamage(snapshottedMagicPower):0;}
        public double interval(){return interval;}
        public String roleId(){return roleId;}
        /** Captured from the spawned NPC's public native armor/effect projection before the
         * lease becomes active. The engine applies this baseline in its own armor system. */
        public Map<String,Double> nativeProtection(){return nativeProtection;}
        public void captureNativeProtection(Map<String,Double> values){
            if(entity!=null||!nativeProtection.isEmpty())throw new IllegalStateException("SUMMON_NATIVE_DEFENSE_ALREADY_CAPTURED");
            values.forEach((cause,value)->{if(cause==null||value==null||!Double.isFinite(value)||value<0||value>=1)
                throw new IllegalArgumentException("Invalid native summon defense");});
            nativeProtection=Map.copyOf(values);
        }
        public boolean ironSentinel(){return boundItem!=null;}
        public GearInstance boundItem(){return boundItem;}
        public GearEffectSnapshot ownerEffects(){return ownerEffects;}
        public GearEffectSnapshot boundEffects(){return boundEffects;}
        public IronSentinelStatProjection.Stats sentinelStats(){return sentinelStats;}
        public boolean nativeRanged(){return !ironSentinel()&&context.profile().summon().nativeRanged();}
    }
    public synchronized String admission(UUID owner,int count) {
        if(owner==null||count<1||count>OWNER_LIMIT)return "SUMMON_INVALID_COUNT";
        if(leases.size()+count>GLOBAL_LIMIT)return "SUMMON_GLOBAL_CAP";
        if(leases.values().stream().filter(v->v.owner().equals(owner)).count()+count>OWNER_LIMIT)return "SUMMON_OWNER_CAP";
        return "PASS";
    }
    public synchronized String admission(UUID owner, int count, boolean decoy){
        if(decoy&&leases.values().stream().anyMatch(l->l.owner().equals(owner)&&!l.ironSentinel()&&l.context.profile().summon().decoy()))return "DECOY_ALREADY_ACTIVE";
        return admission(owner,count);
    }
    /** Replacement admission counts the outgoing same-skill batch as already released. The
     * live executor performs that release before reserving the new batch, so the caps remain
     * strict and a recast can never transiently exceed them. */
    public synchronized String admissionReplacing(UUID owner,UUID world,String skillId,int count,boolean decoy){
        if(owner==null||world==null||skillId==null||skillId.isBlank()||count<1||count>OWNER_LIMIT)return "SUMMON_INVALID_COUNT";
        long replaceable=leases.values().stream().filter(v->!v.ironSentinel()&&v.owner().equals(owner)&&v.world().equals(world)
                &&v.context.profile().skillId().equals(skillId)).count();
        if(decoy&&leases.values().stream().anyMatch(l->!l.ironSentinel()&&l.owner().equals(owner)&&l.context.profile().summon().decoy()
                &&!(l.world().equals(world)&&l.context.profile().skillId().equals(skillId))))return "DECOY_ALREADY_ACTIVE";
        if(leases.size()-replaceable+count>GLOBAL_LIMIT)return "SUMMON_GLOBAL_CAP";
        long owned=leases.values().stream().filter(v->v.owner().equals(owner)).count();
        if(owned-replaceable+count>OWNER_LIMIT)return "SUMMON_OWNER_CAP";
        return "PASS";
    }
    public synchronized List<Lease> reserve(SkillExecutionContext context,double now) {
        return reserve(context,now,null,GearEffectSnapshot.EMPTY);
    }
    public synchronized List<Lease> reserve(SkillExecutionContext context,double now,CorpseLedger.Source corpse) {
        return reserve(context,now,corpse,GearEffectSnapshot.EMPTY);
    }
    public synchronized List<Lease> reserve(SkillExecutionContext context,double now,CorpseLedger.Source corpse,GearEffectSnapshot effects) {
        clock(now);
        if(context.derivedRelease()||context.target()==null)throw new IllegalArgumentException("SUMMON_REPLAY_OR_TARGET_INVALID");
        if(!context.rootCastId().equals(context.snapshot().rootCastId())||!context.skillInstanceId().equals(context.snapshot().skillInstanceId())
                ||!context.request().actorId().equals(context.snapshot().actorId()))throw new IllegalArgumentException("SUMMON_SNAPSHOT_IDENTITY_MISMATCH");
        if(leases.values().stream().anyMatch(v->v.owner().equals(context.request().actorId())&&v.context.rootCastId().equals(context.rootCastId())))
            throw new IllegalStateException("SUMMON_ROOT_ALREADY_ACTIVE");
        int count=context.compiledPlan().summonModifiers().count(context.profile().summon().baseCount(context.effectiveSkillLevel()));String admission=admission(context.request().actorId(),count,context.profile().summon().decoy());
        if(context.profile().summon().corpseRequired()!=(corpse!=null)||corpse!=null&&(!corpse.eligible()||count!=1
                ||!corpse.entity().equals(context.target().entityId())||!corpse.world().equals(context.target().worldId())))
            throw new IllegalArgumentException("CORPSE_SOURCE_IDENTITY_MISMATCH");
        if(!admission.equals("PASS"))throw new IllegalStateException(admission);
        List<Lease> result=new ArrayList<>();
        for(int i=0;i<count;i++){var lease=new Lease(context,now,corpse,null,effects);leases.put(lease.token,lease);result.add(lease);}
        return List.copyOf(result);
    }
    /** The permanent companion is a singleton regardless of summon-count passives. */
    public synchronized Lease reserveIronSentinel(SkillExecutionContext context,double now,GearInstance source){
        return reserveIronSentinel(context,now,source,GearEffectSnapshot.EMPTY);
    }
    public synchronized Lease reserveIronSentinel(SkillExecutionContext context,double now,GearInstance source,GearEffectSnapshot effects){
        clock(now);Objects.requireNonNull(source);
        if(!context.profile().summon().ironSentinel()||context.derivedRelease()||context.target()==null)
            throw new IllegalArgumentException("Iron Sentinel cast identity invalid");
        if(leases.values().stream().anyMatch(lease->lease.owner().equals(context.request().actorId())&&lease.ironSentinel()))
            throw new IllegalStateException("IRON_SENTINEL_ALREADY_ACTIVE");
        String gate=admission(context.request().actorId(),1);
        if(!gate.equals("PASS"))throw new IllegalStateException(gate);
        var lease=new Lease(context,now,null,source,effects);leases.put(lease.token,lease);return lease;
    }
    /** Keep the outgoing actor live during the durable handoff. The incoming lease remains
     * suspended by its native projection until the replacement receipt is acknowledged. */
    public synchronized Replacement reserveReplacingIronSentinel(SkillExecutionContext context,double now,GearInstance source){
        return reserveReplacingIronSentinel(context,now,source,GearEffectSnapshot.EMPTY);
    }
    public synchronized Replacement reserveReplacingIronSentinel(SkillExecutionContext context,double now,GearInstance source,GearEffectSnapshot effects){
        clock(now);Objects.requireNonNull(source);
        if(!context.profile().summon().ironSentinel()||context.derivedRelease()||context.target()==null)
            throw new IllegalArgumentException("Iron Sentinel replacement identity invalid");
        var outgoing=leases.values().stream().filter(v->v.ironSentinel()&&v.owner().equals(context.request().actorId())).toList();
        if(outgoing.size()!=1||!outgoing.getFirst().world().equals(context.target().worldId()))
            throw new IllegalStateException("SENTINEL_REPLACEMENT_ACTOR_MISSING");
        if(leases.size()>GLOBAL_LIMIT||leases.values().stream().filter(v->v.owner().equals(context.request().actorId())).count()>OWNER_LIMIT)
            throw new IllegalStateException("SUMMON_REPLACEMENT_CAP");
        var incoming=new Lease(context,now,null,source,effects);leases.put(incoming.token,incoming);
        return new Replacement(outgoing,List.of(incoming));
    }
    public synchronized Lease restoreIronSentinel(IronSentinelBinding binding,double now){
        clock(now);Objects.requireNonNull(binding);
        if(binding.state()!=IronSentinelBinding.State.RESTORING)throw new IllegalArgumentException("SENTINEL_RESTORE_NOT_CLAIMED");
        if(leases.values().stream().anyMatch(l->l.ironSentinel()&&l.owner().equals(binding.ownerId())))
            throw new IllegalStateException("IRON_SENTINEL_ALREADY_ACTIVE");
        String gate=admission(binding.ownerId(),1);if(!gate.equals("PASS"))throw new IllegalStateException(gate);
        var lease=new Lease(binding,now);leases.put(lease.token,lease);return lease;
    }
    public record Replacement(List<Lease> replaced,List<Lease> reserved) {
        public Replacement {replaced=List.copyOf(replaced);reserved=List.copyOf(reserved);}
    }
    /** Registry-side replacement is one synchronized mutation: outgoing ownership is removed
     * before incoming capacity is reserved, and restored if an unexpected reservation failure
     * occurs. Native entity cleanup is performed from the returned immutable replaced list. */
    public synchronized Replacement replaceAndReserve(SkillExecutionContext context,double now,CorpseLedger.Source corpse) {
        return replaceAndReserve(context,now,corpse,GearEffectSnapshot.EMPTY);
    }
    public synchronized Replacement replaceAndReserve(SkillExecutionContext context,double now,CorpseLedger.Source corpse,GearEffectSnapshot effects) {
        clock(now);UUID owner=context.request().actorId(),world=context.target().worldId();String skill=context.profile().skillId();
        int count=context.compiledPlan().summonModifiers().count(context.profile().summon().baseCount(context.effectiveSkillLevel()));
        String admission=admissionReplacing(owner,world,skill,count,context.profile().summon().decoy());
        if(!admission.equals("PASS"))throw new IllegalStateException(admission);
        var removed=cancelSkill(owner,world,skill);
        try{return new Replacement(removed,reserve(context,now,corpse,effects));}
        catch(RuntimeException failure){removed.forEach(v->leases.put(v.token,v));throw failure;}
    }
    public synchronized boolean activate(Lease lease,UUID entity,double now) {
        clock(now);Objects.requireNonNull(entity);
        if(leases.get(lease.token)!=lease||lease.entity!=null||now>=lease.expires)return false;
        if(leases.values().stream().anyMatch(v->entity.equals(v.entity)))return false;
        lease.entity=entity;return true;
    }
    public synchronized Optional<Lease> find(UUID token){return Optional.ofNullable(leases.get(token));}
    public synchronized Optional<Lease> findByEntity(UUID entity){return leases.values().stream()
            .filter(lease->entity.equals(lease.entity())).findFirst();}
    public synchronized Optional<Lease> iron(UUID owner){return leases.values().stream()
            .filter(l->l.ironSentinel()&&l.owner().equals(owner)).findFirst();}
    public synchronized Optional<Lease> owned(UUID owner,UUID world,UUID entity){return leases.values().stream()
            .filter(l->l.owner().equals(owner)&&l.world().equals(world)&&entity!=null&&entity.equals(l.entity())).findFirst();}
    public synchronized List<Lease> owned(UUID owner,UUID world){return leases.values().stream().filter(l->l.owner().equals(owner)&&l.world().equals(world)&&l.entity()!=null).toList();}
    public enum EndReason { NATURAL_EXPIRY, ENEMY_KILL, OTHER_DEATH, OWNER_GONE, LEASH, VOLUNTARY, NATIVE_REMOVAL }
    public record End(Lease lease,EndReason reason,boolean deathPact){}
    public synchronized Optional<End> end(UUID token,EndReason reason){
        Objects.requireNonNull(reason);var lease=leases.remove(token);
        return lease==null?Optional.empty():Optional.of(new End(lease,reason,!lease.ironSentinel()&&lease.entity!=null&&lease.context.compiledPlan().summonModifiers().deathPact()
                &&(reason==EndReason.NATURAL_EXPIRY||reason==EndReason.ENEMY_KILL)));
    }
    /** Benefit publication either succeeds before removal, or leaves the owned lease untouched.
     * Caller must not do native entity removal or a second resource charge inside this callback. */
    public synchronized Optional<Lease> consume(UUID owner,UUID world,UUID entity,double now,Runnable publishBenefit){
        clock(now);var lease=owned(owner,world,entity).orElse(null);
        if(lease==null||lease.ironSentinel()||now>=lease.expires||!lease.context.compiledPlan().finalTags().contains("TEMPORARY_COMBAT_SUMMON"))return Optional.empty();
        publishBenefit.run();leases.remove(lease.token);return Optional.of(lease);
    }
    /** Claims before damage dispatch. Reentrant calls and a stalled tick never catch up multiple attacks. */
    public synchronized int claimAttack(UUID token,double now) {
        clock(now);var lease=leases.get(token);
        if(lease==null||!lease.ironSentinel()&&lease.context.profile().summon().decoy()||lease.entity==null||now>=lease.expires||now+1e-9<lease.nextAttack)return 0;
        lease.nextAttack=now+lease.interval;return ++lease.attacks;
    }
    public synchronized Optional<Lease> remove(UUID token){return Optional.ofNullable(leases.remove(token));}
    public synchronized List<Lease> cancel(UUID owner) {
        var removed=leases.values().stream().filter(v->v.owner().equals(owner)&&!v.ironSentinel()).toList();
        removed.forEach(v->leases.remove(v.token));return removed;
    }
    /** Removes only one owner's same-world, same-skill batch. Replacement is never an
     * eligible Death Pact terminal cause and never publishes corpse/reward ownership. */
    public synchronized List<Lease> cancelSkill(UUID owner,UUID world,String skillId) {
        Objects.requireNonNull(owner);Objects.requireNonNull(world);Objects.requireNonNull(skillId);
        var removed=leases.values().stream().filter(v->!v.ironSentinel()&&v.owner().equals(owner)&&v.world().equals(world)
                &&v.context.profile().skillId().equals(skillId)).toList();
        removed.forEach(v->leases.remove(v.token));return removed;
    }
    public synchronized int size(){return leases.size();}
    public synchronized boolean owns(UUID entity){return leases.values().stream().anyMatch(v->entity.equals(v.entity));}
    private static void clock(double now){if(!Double.isFinite(now))throw new IllegalArgumentException("Nonfinite clock");}
    private static double ownerPercent(GearEffectSnapshot effects,String id){
        // Capability admission belongs to the accepted equipment snapshot, not the consumer.
        return effects.percent(id);
    }
    private static double increasedRatio(com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets buckets,double bonus){
        if(bonus==0)return 1;
        double baseline=buckets.factor();
        if(baseline<=0)throw new IllegalStateException("SUMMON_INCREASED_DAMAGE_BASELINE_ZERO");
        return buckets.withIncreased(bonus).factor()/baseline;
    }
}
