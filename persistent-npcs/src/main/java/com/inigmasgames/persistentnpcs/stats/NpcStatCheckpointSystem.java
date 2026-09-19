package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Read only: native changes run first; one-second immutable snapshots, never disk IO on tick. */
public final class NpcStatCheckpointSystem extends EntityTickingSystem<EntityStore> {
    private final NpcStatRuntimeBridge bridge;
    public NpcStatCheckpointSystem(NpcStatRuntimeBridge bridge) { this.bridge = bridge; }
    @Override public Query<EntityStore> getQuery() { return NpcStatRuntimeBridge.query(); }
    @Override public boolean isParallel(int size, int taskCount) { return false; }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, NpcStatHydrationSystem.class),
                new SystemDependency<>(Order.AFTER, EntityStatsSystems.Changes.class));
    }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
        bridge.checkpoint(chunk.getReferenceTo(index), store);
    }
}
