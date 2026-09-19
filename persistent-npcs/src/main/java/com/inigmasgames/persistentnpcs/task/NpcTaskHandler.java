package com.inigmasgames.persistentnpcs.task;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** World-thread continuation hook for persisted multi-stage tasks. */
@FunctionalInterface
public interface NpcTaskHandler {
    NpcTask resume(
            NpcTask task,
            Ref<EntityStore> npcRef,
            Store<EntityStore> store,
            World world);
}
