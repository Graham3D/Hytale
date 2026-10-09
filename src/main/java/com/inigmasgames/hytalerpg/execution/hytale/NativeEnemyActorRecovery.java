package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity;
import java.util.UUID;

/** Restores native staging before a saved published actor is exposed on LOAD. */
public final class NativeEnemyActorRecovery extends HolderSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery(){return EnemyActorIdentity.getComponentType();}
    @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
        if(reason!=AddReason.LOAD)return;
        restore(holder,store.getExternalData().getWorld().getWorldConfig().getUuid());
    }
    /** Exact saved holder check before any ordinary per-NPC LOAD observer can attach. */
    public static EnemyStaging.State restore(Holder<EntityStore> holder,UUID world){
        var identity=holder.getComponent(EnemyActorIdentity.getComponentType()).state();
        var id=holder.getComponent(UUIDComponent.getComponentType());
        var npc=holder.getComponent(NPCEntity.getComponentType());
        if(id==null||npc==null||!id.getUuid().equals(identity.nativeEntity())
                ||!npc.getRoleName().equals(identity.nativeRole())
                ||!world.equals(identity.world()))
            throw new IllegalStateException("ENEMY_ACTOR_LOAD_IDENTITY_MISMATCH");
        return EnemyStaging.prepare(holder,identity.world(),identity.encounter(),identity.generation());
    }
    @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){}
}
