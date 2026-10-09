package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Canonical Electrified/Blind miss filter: strongest chance on outgoing Physical, one roll. */
public final class NativeStatusPhysicalMiss extends DamageEventSystem {
    private final StatusService statuses;
    private final DoubleSupplier roll;
    private final java.util.LinkedHashMap<UUID,Long> nativeOrdinals=new java.util.LinkedHashMap<>();
    public NativeStatusPhysicalMiss(StatusService statuses){this(statuses,null);}
    public NativeStatusPhysicalMiss(StatusService statuses,DoubleSupplier roll){
        this.statuses=Objects.requireNonNull(statuses);this.roll=roll;
    }
    @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
            new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(damage.isCancelled()||damage.getAmount()<=0||damage.getCause()!=DamageCause.PHYSICAL
                ||!(damage.getSource() instanceof Damage.EntitySource source)
                ||source.getRef()==null||!source.getRef().isValid()
                ||source.getRef().equals(chunk.getReferenceTo(index)))return;
        var id=buffer.getComponent(source.getRef(),UUIDComponent.getComponentType());
        var victim=chunk.getComponent(index,UUIDComponent.getComponentType());
        if(id!=null&&victim!=null&&shouldMiss(statuses.physicalMissChance(id.getUuid()),
                roll==null?nativeRoll(id.getUuid(),victim.getUuid(),damage):roll.getAsDouble()))
            damage.setCancelled(true);
        }
    }
    private synchronized double nativeRoll(UUID actor,UUID victim,Damage damage){
        var metadata=HytaleDamageAdapter.metadata(damage);
        String contact;
        if(metadata!=null)contact=metadata.rootCastId()+"/"+metadata.correlationId()+"/"+metadata.effectInstanceId();
        else{
            long ordinal=nativeOrdinals.getOrDefault(actor,0L)+1;
            nativeOrdinals.remove(actor);
            if(nativeOrdinals.size()>=4096)nativeOrdinals.remove(nativeOrdinals.keySet().iterator().next());
            nativeOrdinals.put(actor,ordinal);
            contact="NATIVE/"+ordinal;
        }
        return stableRoll(actor,victim,contact);
    }
    static double stableRoll(UUID actor,UUID victim,String contact){
        if(actor==null||victim==null||contact==null||contact.isBlank())
            throw new IllegalArgumentException("INVALID_NATIVE_MISS_KEY");
        long hash=0xcbf29ce484222325L;
        for(char ch:(actor.toString()+"/"+victim+"/"+contact).toCharArray()){
            hash^=ch;hash*=0x100000001b3L;
        }
        return (hash>>>11)*0x1.0p-53;
    }
    public static boolean shouldMiss(double chance,double roll){
        if(!Double.isFinite(chance)||chance<0||chance>.5||!Double.isFinite(roll)||roll<0||roll>=1)
            throw new IllegalArgumentException("INVALID_STATUS_MISS_INPUT");
        return roll<chance;
    }
}
