package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Throttled native-world HUD projection loop (4 Hz maximum per player). */
public final class RpgHudTickSystem extends EntityTickingSystem<EntityStore> {
    private final RpgHudCoordinator coordinator;
    public RpgHudTickSystem(RpgHudCoordinator coordinator) { this.coordinator = coordinator; }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(PlayerRef.getComponentType(), EntityStatMap.getComponentType());
    }
    @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.HUD)){
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        var hand = InventoryComponent.getItemInHand(store,chunk.getReferenceTo(index));
        if (player != null && stats != null) coordinator.tick(player, stats,
                ItemStack.isEmpty(hand), !ItemStack.isEmpty(hand) && hand.getItem().getWeapon() != null);
        if(player!=null&&stats!=null)coordinator.tickEnemyTarget(player,store,chunk.getReferenceTo(index));

        }
    }
}
