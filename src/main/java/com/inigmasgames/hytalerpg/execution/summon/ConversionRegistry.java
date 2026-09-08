package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.*;

/** Finite, exclusive relationship ownership. Original native role/allegiance are never overwritten. */
public final class ConversionRegistry {
    public enum Rank { COMMON,SPECIALIST,ELITE,BOSS,UNKNOWN }
    public record Eligibility(boolean dominatable,Rank rank,boolean eliteOptIn,boolean player,boolean allied,
                              boolean protectedOrOwned,boolean persistentThreat){
        public String boundary(){
            if(player)return "CONVERSION_PLAYER_FORBIDDEN";
            if(allied)return "CONVERSION_ALLY_FORBIDDEN";
            if(protectedOrOwned)return "CONVERSION_PROTECTED_OR_OWNED";
            if(persistentThreat)return "CONVERSION_PERSISTENT_THREAT_FORBIDDEN";
            if(!dominatable||rank==null||rank==Rank.UNKNOWN)return "CONVERSION_NOT_EXPLICITLY_DOMINATABLE";
            if(rank==Rank.BOSS||rank==Rank.ELITE&&!eliteOptIn)return "CONVERSION_RANK_FORBIDDEN";
            return "PASS";
        }
    }
    public record Lease(UUID token,SkillExecutionContext context,UUID entity,UUID originalThreat,double expires){
        public UUID owner(){return context.request().actorId();}
        public UUID world(){return context.target().worldId();}
    }
    private final Map<UUID,Lease> leases=new LinkedHashMap<>();
    public synchronized String admission(UUID owner,UUID world,UUID target){
        if(owner==null||world==null||target==null||owner.equals(target))return "CONVERSION_INVALID_IDENTITY";
        if(leases.size()>=256)return "CONVERSION_GLOBAL_CAP";
        if(leases.values().stream().anyMatch(l->l.owner().equals(owner)))return "CONVERSION_OWNER_CAP";
        if(leases.values().stream().anyMatch(l->l.world().equals(world)&&l.entity().equals(target)))return "CONVERSION_TARGET_ALREADY_OWNED";
        return "PASS";
    }
    public synchronized Lease begin(SkillExecutionContext c,Eligibility eligibility,UUID originalThreat,double now){
        if(!Double.isFinite(now)||c.derivedRelease()||c.target()==null||c.profile().conversion()==null
                ||!c.rootCastId().equals(c.snapshot().rootCastId())||!c.skillInstanceId().equals(c.snapshot().skillInstanceId())
                ||!c.request().actorId().equals(c.snapshot().actorId()))throw new IllegalArgumentException("CONVERSION_CONTEXT_INVALID");
        String allowed=eligibility.boundary();if(!allowed.equals("PASS"))throw new IllegalStateException(allowed);
        expire(now);
        allowed=admission(c.request().actorId(),c.target().worldId(),c.target().entityId());
        if(!allowed.equals("PASS"))throw new IllegalStateException(allowed);
        var lease=new Lease(UUID.randomUUID(),c,c.target().entityId(),originalThreat,now+c.profile().conversion().duration());
        leases.put(lease.token(),lease);return lease;
    }
    public synchronized Optional<Lease> find(UUID token){return Optional.ofNullable(leases.get(token));}
    public synchronized Optional<Lease> target(UUID world,UUID entity){return leases.values().stream().filter(l->l.world().equals(world)&&l.entity().equals(entity)).findFirst();}
    public synchronized Optional<Lease> end(UUID token){return Optional.ofNullable(leases.remove(token));}
    public synchronized void expire(double now){
        if(!Double.isFinite(now))throw new IllegalArgumentException("Nonfinite conversion clock");
        leases.values().removeIf(l->now>=l.expires());
    }
    public synchronized List<Lease> owned(UUID owner){return leases.values().stream().filter(l->l.owner().equals(owner)).toList();}
    public synchronized int size(){return leases.size();}
}
