package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.*;

/** Shared admission for pending AND live native actors. UUID ownership survives native Ref churn.
 * World-thread callbacks may run on different worlds: every mutation is synchronized. */
public final class SummonRegistry {
    public static final int OWNER_LIMIT=8, GLOBAL_LIMIT=256;
    private final Map<UUID,Lease> leases=new LinkedHashMap<>();
    public static final class Lease {
        private final UUID token=UUID.randomUUID();
        private final SkillExecutionContext context;
        private final double expires;
        private UUID entity;
        private double nextAttack;
        private final double maximumHealth,coefficient,interval;
        private final String roleId;
        private int attacks;
        private Lease(SkillExecutionContext context,double now,CorpseLedger.Source source) {
            this.context=context;expires=now+context.profile().summon().lifetime();
            var spec=context.profile().summon();
            if(source==null){maximumHealth=context.snapshot().derivedStats().maxHealth()*spec.healthFactor();coefficient=spec.coefficient();interval=spec.attackInterval();roleId=spec.roleId();}
            else {
                double magic=context.snapshot().basePower()*context.snapshot().derivedStats().magicDamageMultiplier();
                var stats=CorpseLedger.revive(source,context.snapshot().derivedStats().maxHealth(),magic);
                maximumHealth=stats.maximumHealth();coefficient=magic==0?0:stats.hitPower()/magic;interval=stats.attackInterval();roleId=source.projectionRole();
            }
            nextAttack=now+interval;
        }
        public UUID token(){return token;}
        public SkillExecutionContext context(){return context;}
        public UUID owner(){return context.request().actorId();}
        public UUID world(){return context.target().worldId();}
        public UUID entity(){return entity;}
        public double expires(){return expires;}
        public double maximumHealth(){return maximumHealth;}
        public double coefficient(){return coefficient;}
        public double interval(){return interval;}
        public String roleId(){return roleId;}
    }
    public synchronized String admission(UUID owner,int count) {
        if(owner==null||count<1||count>OWNER_LIMIT)return "SUMMON_INVALID_COUNT";
        if(leases.size()+count>GLOBAL_LIMIT)return "SUMMON_GLOBAL_CAP";
        if(leases.values().stream().filter(v->v.owner().equals(owner)).count()+count>OWNER_LIMIT)return "SUMMON_OWNER_CAP";
        return "PASS";
    }
    public synchronized List<Lease> reserve(SkillExecutionContext context,double now) {
        return reserve(context,now,null);
    }
    public synchronized List<Lease> reserve(SkillExecutionContext context,double now,CorpseLedger.Source corpse) {
        clock(now);
        if(context.derivedRelease()||context.target()==null)throw new IllegalArgumentException("SUMMON_REPLAY_OR_TARGET_INVALID");
        if(!context.rootCastId().equals(context.snapshot().rootCastId())||!context.skillInstanceId().equals(context.snapshot().skillInstanceId())
                ||!context.request().actorId().equals(context.snapshot().actorId()))throw new IllegalArgumentException("SUMMON_SNAPSHOT_IDENTITY_MISMATCH");
        if(leases.values().stream().anyMatch(v->v.owner().equals(context.request().actorId())&&v.context.rootCastId().equals(context.rootCastId())))
            throw new IllegalStateException("SUMMON_ROOT_ALREADY_ACTIVE");
        int count=context.profile().summon().count();String admission=admission(context.request().actorId(),count);
        if(context.profile().summon().corpseRequired()!=(corpse!=null)||corpse!=null&&(!corpse.eligible()||count!=1
                ||!corpse.entity().equals(context.target().entityId())||!corpse.world().equals(context.target().worldId())))
            throw new IllegalArgumentException("CORPSE_SOURCE_IDENTITY_MISMATCH");
        if(!admission.equals("PASS"))throw new IllegalStateException(admission);
        List<Lease> result=new ArrayList<>();
        for(int i=0;i<count;i++){var lease=new Lease(context,now,corpse);leases.put(lease.token,lease);result.add(lease);}
        return List.copyOf(result);
    }
    public synchronized boolean activate(Lease lease,UUID entity,double now) {
        clock(now);Objects.requireNonNull(entity);
        if(leases.get(lease.token)!=lease||lease.entity!=null||now>=lease.expires)return false;
        if(leases.values().stream().anyMatch(v->entity.equals(v.entity)))return false;
        lease.entity=entity;return true;
    }
    public synchronized Optional<Lease> find(UUID token){return Optional.ofNullable(leases.get(token));}
    /** Claims before damage dispatch. Reentrant calls and a stalled tick never catch up multiple attacks. */
    public synchronized int claimAttack(UUID token,double now) {
        clock(now);var lease=leases.get(token);
        if(lease==null||lease.entity==null||now>=lease.expires||now+1e-9<lease.nextAttack)return 0;
        lease.nextAttack=now+lease.interval;return ++lease.attacks;
    }
    public synchronized Optional<Lease> remove(UUID token){return Optional.ofNullable(leases.remove(token));}
    public synchronized List<Lease> cancel(UUID owner) {
        var removed=leases.values().stream().filter(v->v.owner().equals(owner)).toList();
        removed.forEach(v->leases.remove(v.token));return removed;
    }
    public synchronized int size(){return leases.size();}
    public synchronized boolean owns(UUID entity){return leases.values().stream().anyMatch(v->entity.equals(v.entity));}
    private static void clock(double now){if(!Double.isFinite(now))throw new IllegalArgumentException("Nonfinite clock");}
}
