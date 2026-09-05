package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Ref removal callback still has native components; capture before Hytale invalidates the ref. */
public final class NpcStatRemovalCaptureSystem extends RefSystem<EntityStore> {
    private final NpcStatRuntimeBridge bridge;
    public NpcStatRemovalCaptureSystem(NpcStatRuntimeBridge bridge) { this.bridge = bridge; }
    @Override public Query<EntityStore> getQuery() { return NpcStatRuntimeBridge.query(); }
    @Override public void onEntityAdded(Ref<EntityStore> ref, AddReason reason,
            Store<EntityStore> store, CommandBuffer<EntityStore> commands) { bridge.added(ref, store, reason); }
    @Override public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason,
            Store<EntityStore> store, CommandBuffer<EntityStore> commands) { bridge.removed(ref, store, reason); }
}
