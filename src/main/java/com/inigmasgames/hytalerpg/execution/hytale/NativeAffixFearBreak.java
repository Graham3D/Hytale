package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.Objects;

/** Breaks affix Fear on a later accepted direct hit, including native non-RPG attacks. */
public final class NativeAffixFearBreak extends DamageEventSystem {
    private final StatusService statuses;
    public NativeAffixFearBreak(StatusService statuses){this.statuses=Objects.requireNonNull(statuses);}
    @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(damage.isCancelled()||damage.getAmount()<=0
                ||Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)))return;
        HytaleDamageMetadata metadata=HytaleDamageAdapter.metadata(damage);
        boolean direct=metadata!=null?metadata.origin()==HytaleDamageMetadata.Origin.DIRECT:
                damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=null
                        ||damage.getSource() instanceof Damage.ProjectileSource;
        if(!direct)return;
        String root=metadata==null?"NATIVE/"+System.identityHashCode(damage):metadata.rootCastId();
        statuses.breakFearOnDamage(chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid(),root);
        }
    }
}
