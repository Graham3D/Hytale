package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.HytaleGearLoot;
import java.util.function.Supplier;

/** Native-owned Storage fallback when the candidate Gear Master receipt owner is disabled. */
public final class InventoryAliasDropSystem extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {
    private final Supplier<HytaleGearLoot> gearRuntime;
    public InventoryAliasDropSystem(Supplier<HytaleGearLoot> gearRuntime) {
        super(DropItemEvent.PlayerRequest.class);
        this.gearRuntime = gearRuntime;
    }
    @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
    @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer, DropItemEvent.PlayerRequest event) {
        if (event.isCancelled() || gearRuntime.get() != null) return;
        var actor = chunk.getReferenceTo(index);
        var player = chunk.getComponent(index, PlayerRef.getComponentType());
        var entity = store.getComponent(actor, Player.getComponentType());
        if (entity == null || !(entity.getPageManager().getCustomPage() instanceof InventoryProbePage page)
                || !page.ownsSpatialAlias(event.getInventorySectionId())) return;
        event.setCancelled(true);
        var source = page.nativeStorageForDrop(event.getInventorySectionId(), event.getSlotId());
        var storage = store.getComponent(actor, InventoryComponent.Storage.getComponentType());
        if (source == null || storage == null || source.slot() >= storage.getInventory().getCapacity()
                || !source.fingerprint().equals(storage.getInventory().getItemStack(source.slot()))) {
            player.sendMessage(Message.raw("Drop source changed. Refresh Inventory and try again."));
            return;
        }
        if (GearNativeItems.managed(source.fingerprint())) {
            player.sendMessage(Message.raw("Managed gear drop requires the gear custody service."));
            return;
        }
        var removed = storage.getInventory().removeItemStackFromSlot(source.slot(), source.fingerprint().getQuantity());
        if (!ItemStack.isEmpty(removed.getOutput()))
            com.hypixel.hytale.server.core.entity.ItemUtils.throwItem(actor, removed.getOutput(), 6.0f, store);
        page.refreshAfterSpatialDrop(actor, store);
    }
}
