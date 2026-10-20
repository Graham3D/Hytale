package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;

/** Copied-save stock source fence, applied before the native pickup ticker can inspect an item. */
public final class SpatialStockPickupGuard extends HolderSystem<EntityStore> {
    private final Path marker;
    public SpatialStockPickupGuard(Path marker){this.marker=marker;}
    @Override public Query<EntityStore> getQuery(){return ItemComponent.getComponentType();}
    @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
        if(!Files.isRegularFile(marker)||holder.getComponent(PreventPickup.getComponentType())!=null)return;
        holder.putComponent(PreventPickup.getComponentType(),PreventPickup.INSTANCE);
        // A protected source may outlive its owner's save; recovery must still find it.
        holder.tryRemoveComponent(DespawnComponent.getComponentType());
    }
    @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){}
}
