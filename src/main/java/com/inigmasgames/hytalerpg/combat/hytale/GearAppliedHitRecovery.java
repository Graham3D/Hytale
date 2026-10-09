package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Correlates committed attack sources with post-ApplyDamage HP receipts. */
public final class GearAppliedHitRecovery implements HytaleDamageLifecycleSystems.AppliedObserver {
    public interface Eligibility {
        record SentinelAttack(UUID actor,GearEffectSnapshot boundItem) {
            public SentinelAttack {Objects.requireNonNull(actor);Objects.requireNonNull(boundItem);}
        }
        boolean hostile(Ref<EntityStore> source,Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        double normalHealthMaximum(UUID actor,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer);
        double spendableManaMaximum(UUID actor,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer);
        default boolean sentinel(Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){return false;}
        /** Resolve only a live owned Iron Sentinel lease with an authored token/attack identity. */
        default SentinelAttack sentinelAttack(Ref<EntityStore> source,HytaleDamageMetadata metadata,
                                               CommandBuffer<EntityStore> buffer){return null;}
        default SentinelAttack sentinelAttack(Ref<EntityStore> source,HytaleDamageMetadata metadata,
                                               HytaleDamageAdapter.GearHitSource gear,
                                               CommandBuffer<EntityStore> buffer){return sentinelAttack(source,metadata,buffer);}
    }
    private record RootKey(UUID actor,String world,String root) { }
    private record Committed(GearEffectSnapshot snapshot,boolean sentinel,double expires) { }
    private final GearRecoveryRuntime recovery;
    private final Eligibility eligibility;
    private final Map<RootKey,Committed> committed=new HashMap<>();
    public GearAppliedHitRecovery(GearRecoveryRuntime recovery,Eligibility eligibility) {
        this.recovery=Objects.requireNonNull(recovery);
        this.eligibility=Objects.requireNonNull(eligibility);
    }
    /** Skill owner calls at accepted attack/release using its committed valid-equipment snapshot. */
    public synchronized void committedAttack(UUID actor,String world,String root,GearEffectSnapshot snapshot,
                                             boolean sentinel,double now) {
        if(actor==null||world==null||world.isBlank()||root==null||root.isBlank()||snapshot==null
                ||!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_COMMITTED_RECOVERY_ROOT");
        maintain(now);
        if(committed.size()>=65536)return;
        committed.putIfAbsent(new RootKey(actor,world,root),new Committed(snapshot,sentinel,now+30));
    }
    public synchronized void maintain(double now){
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_RECOVERY_CLOCK");
        Iterator<Committed> values=committed.values().iterator();
        while(values.hasNext())if(values.next().expires()<=now)values.remove();
    }
    public synchronized void cancel(UUID actor){committed.keySet().removeIf(key->key.actor().equals(actor));}
    public synchronized void cancelWorld(String world){committed.keySet().removeIf(key->key.world().equals(world));}
    @Override public synchronized void observed(HytaleDamageLifecycleSystems.AppliedReceipt receipt,Ref<EntityStore> target,
                                   Ref<EntityStore> source,CommandBuffer<EntityStore> buffer) {
        var metadata=receipt.metadata();
        if(source==null||!source.isValid()||metadata==null||metadata.actorId()==null
                ||metadata.origin()!=HytaleDamageMetadata.Origin.DIRECT
                ||metadata.noLeech())return;
        var sourceIdentity=buffer.getComponent(source,UUIDComponent.getComponentType());
        if(sourceIdentity==null||!eligibility.hostile(source,target,buffer))return;
        var gear=receipt.gearHit();
        var sentinelAttack=gear==null?null:eligibility.sentinelAttack(source,metadata,gear,buffer);
        boolean projectedSummon=eligibility.sentinel(source,buffer);
        if(projectedSummon&&sentinelAttack==null||!metadata.actorId().equals(sourceIdentity.getUuid())
                ||sentinelAttack!=null&&!sourceIdentity.getUuid().equals(sentinelAttack.actor()))return;
        String world=buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid().toString();
        double now=System.nanoTime()/1e9;
        boolean payable=!receipt.cancelled()&&!receipt.blocked()&&receipt.actualHealthLoss()>0;
        double healthMaximum=payable?eligibility.normalHealthMaximum(sourceIdentity.getUuid(),source,buffer):0;
        double manaMaximum=payable?eligibility.spendableManaMaximum(sourceIdentity.getUuid(),source,buffer):0;
        accepted(receipt,sourceIdentity.getUuid(),world,projectedSummon,sentinelAttack,healthMaximum,manaMaximum,now);
    }
    /** The native observer passes only a hostile source and its post-ApplyDamage receipt here. */
    synchronized void accepted(HytaleDamageLifecycleSystems.AppliedReceipt receipt,UUID sourceId,String world,
                               boolean projectedSummon,Eligibility.SentinelAttack sentinelAttack,
                               double healthMaximum,double manaMaximum,double now) {
        var metadata=receipt.metadata();
        if(metadata==null||sourceId==null||world==null||metadata.actorId()==null
                ||metadata.origin()!=HytaleDamageMetadata.Origin.DIRECT||metadata.noLeech()
                ||!metadata.actorId().equals(sourceId))return;
        var gear=receipt.gearHit();
        boolean sentinelSource=sentinelAttack!=null;
        if(projectedSummon&&!sentinelSource)return;
        if(sentinelSource&&!sourceId.equals(sentinelAttack.actor()))return;
        if(gear!=null){
            var key=new RootKey(sourceId,world,gear.hit().rootId());
            var prior=committed.get(key);
            if(prior!=null&&(prior.expires()<=now||prior.sentinel()!=sentinelSource
                    ||!prior.snapshot().revision().equals(gear.hit().snapshot().revision())
                    ||!prior.snapshot().items().equals(gear.hit().snapshot().items())))return;
            if(metadata.canProc()){
                if(prior==null){
                    if(committed.size()>=65536)return;
                    committed.put(key,new Committed(sentinelSource?sentinelAttack.boundItem():gear.hit().snapshot(),
                            sentinelSource,now+30));
                }
            }else if(prior==null)return;
        }else if(!metadata.canProc())return;
        if(receipt.cancelled()||receipt.blocked()||receipt.actualHealthLoss()<=0)return;
        GearEffectSnapshot snapshot;
        String root,contact;
        boolean sentinel;
        if(sentinelSource){
            snapshot=sentinelAttack.boundItem();root=metadata.effectInstanceId();
            contact=metadata.correlationId()+"/"+metadata.effectInstanceId();
            sentinel=true;
        }else if(gear!=null){
            snapshot=gear.hit().snapshot();root=gear.hit().rootId();
            contact=gear.contactId()+"/"+gear.channel();
            sentinel=false;
        }else{
            root=metadata.rootCastId();
            var sourceRoot=committed.get(new RootKey(metadata.actorId(),world,root));
            if(sourceRoot==null||sourceRoot.expires()<=now)return;
            snapshot=sourceRoot.snapshot();sentinel=sourceRoot.sentinel();
            // The skill producer must use a distinct correlation/effect pair per authored contact.
            contact=metadata.correlationId()+"/"+metadata.effectInstanceId();
        }
        UUID beneficiary=sentinelSource?sentinelAttack.actor():metadata.actorId();
        if(sentinel)manaMaximum=0;
        if(!Double.isFinite(healthMaximum)||healthMaximum<=0||!Double.isFinite(manaMaximum)||manaMaximum<0)return;
        var hit=new GearRecoveryRuntime.Receipt(beneficiary,world,root,contact,receipt.targetId(),
                receipt.before(),receipt.after(),true,true,false,false,false);
        recovery.onAttack(hit,snapshot,healthMaximum,manaMaximum,now,sentinel);
    }
}
