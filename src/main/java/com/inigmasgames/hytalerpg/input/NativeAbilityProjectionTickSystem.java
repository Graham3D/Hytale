package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Repairs the native rune projection when either the RPG loadout or native AbilitySlots changes. */
public final class NativeAbilityProjectionTickSystem extends EntityTickingSystem<EntityStore> {
    private final NativeAbilityProjectionService projection;
    public NativeAbilityProjectionTickSystem(NativeAbilityProjectionService projection) { this.projection = projection; }

    @Override public Query<EntityStore> getQuery() {
        return Query.and(PlayerRef.getComponentType(), InventoryComponent.AbilitySlots.getComponentType());
    }

    @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        InventoryComponent.AbilitySlots slots = chunk.getComponent(index,
                InventoryComponent.AbilitySlots.getComponentType());
        if (player != null && slots != null) projection.tick(player.getUuid(), slots);
    }
}
