package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.status.StatusService;

/** Native NPC removal releases status, immunity, Slow-source and control-history memory. */
public final class StatusRemovalSystem extends RefSystem<EntityStore> {
    private final StatusService statuses;
    public StatusRemovalSystem(StatusService statuses) { this.statuses = statuses; }
    @Override public Query<EntityStore> getQuery() { return Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType()); }
    @Override public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) { }
    @Override public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var id = store.getComponent(ref, UUIDComponent.getComponentType());
        if (id != null) statuses.forget(id.getUuid());
    }
}
