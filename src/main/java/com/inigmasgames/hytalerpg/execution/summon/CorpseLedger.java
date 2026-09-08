package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Exclusive claims over native death anchors. No corpse is invented by killing a projection.
 * Runtime entries follow native lifetime; consumption receipts survive removal/restart. */
public final class CorpseLedger {
    public static final int LIMIT=1024;
    public enum Rank { COMMON, SPECIALIST, ELITE, MINIBOSS, BOSS }
    public record Source(UUID entity,UUID world,Vec3 anchor,String role,String projectionRole,
                         Rank rank,double maximumHealth,double basePower,double attackInterval,
                         boolean player,boolean owned,boolean protectedActor,boolean story) {
        public Source {
            Objects.requireNonNull(entity);Objects.requireNonNull(world);Objects.requireNonNull(anchor);Objects.requireNonNull(rank);
            if(role==null||role.isBlank()||projectionRole==null||!projectionRole.startsWith("RPG_Summon_")
                    ||!positive(maximumHealth,basePower,attackInterval))throw new IllegalArgumentException("Invalid corpse source");
        }
        public boolean eligible(){return !player&&!owned&&!protectedActor&&!story&&Set.of(Rank.COMMON,Rank.SPECIALIST,Rank.ELITE).contains(rank);}
    }
    public record Claim(UUID nonce,UUID entity,UUID owner,String root,Source source){}
    private static final class Entry {
        final Source source;Claim claim;boolean consumed;
        Entry(Source source){this.source=source;}
    }
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final CorpseConsumptionStore consumption;
    public CorpseLedger(CorpseConsumptionStore consumption){this.consumption=Objects.requireNonNull(consumption);}
    public synchronized boolean observe(Source source){
        if(!source.eligible()||entries.containsKey(source.entity())||entries.size()>=LIMIT||consumption.consumed(source.world(),source.entity()))return false;
        entries.put(source.entity(),new Entry(source));return true;
    }
    public synchronized Optional<Source> available(UUID entity,UUID world){
        var value=entries.get(entity);return value!=null&&!value.consumed&&value.claim==null&&value.source.world().equals(world)?Optional.of(value.source):Optional.empty();
    }
    public synchronized List<Source> available(UUID world){return entries.values().stream().filter(v->!v.consumed&&v.claim==null&&v.source.world().equals(world)).map(v->v.source).toList();}
    public synchronized Claim reserve(UUID entity,UUID world,UUID owner,String root){
        Objects.requireNonNull(owner);if(root==null||root.isBlank())throw new IllegalArgumentException("Missing root");
        var value=entries.get(entity);
        if(value==null||value.consumed||!value.source.world().equals(world))throw new IllegalStateException("CORPSE_UNAVAILABLE");
        if(value.claim!=null){
            if(value.claim.owner().equals(owner)&&value.claim.root().equals(root))return value.claim;
            throw new IllegalStateException("CORPSE_ALREADY_CLAIMED");
        }
        value.claim=new Claim(UUID.randomUUID(),entity,owner,root,value.source);return value.claim;
    }
    public synchronized boolean consume(Claim claim){
        var value=entries.get(claim.entity());
        if(value==null||value.claim!=claim||value.consumed)return false;
        // Mark runtime state first: an IO failure must never release an ambiguous durable claim.
        value.consumed=true;return consumption.consume(claim);
    }
    public synchronized boolean release(Claim claim){
        var value=entries.get(claim.entity());
        if(value==null||value.claim!=claim||value.consumed)return false;
        value.claim=null;return true;
    }
    public synchronized void remove(UUID entity){entries.remove(entity);}
    public synchronized void cancelUncommitted(UUID owner){entries.values().stream().filter(v->!v.consumed&&v.claim!=null&&v.claim.owner().equals(owner)).forEach(v->v.claim=null);}
    public synchronized int size(){return entries.size();}
    public record ReviveStats(double maximumHealth,double hitPower,double attackInterval){}
    public static ReviveStats revive(Source source,double casterMaxHealth,double resolvedMagicPower){
        if(!source.eligible()||!positive(casterMaxHealth)||!Double.isFinite(resolvedMagicPower)||resolvedMagicPower<0)
            throw new IllegalArgumentException("Invalid revive inputs");
        return new ReviveStats(Math.min(.6*source.maximumHealth(),2*casterMaxHealth),
                Math.min(.6*source.basePower(),.8*resolvedMagicPower),Math.max(source.attackInterval(),1));
    }
    private static boolean positive(double... values){for(double value:values)if(!Double.isFinite(value)||value<=0)return false;return true;}
}
