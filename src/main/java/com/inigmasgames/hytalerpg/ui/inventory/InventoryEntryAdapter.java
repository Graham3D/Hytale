package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Entry policy is independent of inventory presentation and storage authority. */
public interface InventoryEntryAdapter {
    boolean open(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store);
}
