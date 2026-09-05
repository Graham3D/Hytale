package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Holder Setup/Balancing have completed before ticks; effective bounds recalculate before hydration. */
public final class NpcStatHydrationSystem extends EntityTickingSystem<EntityStore>
        implements EntityStatsSystems.StatModifyingSystem {
    private final NpcStatRuntimeBridge bridge;
    public NpcStatHydrationSystem(NpcStatRuntimeBridge bridge) { this.bridge = bridge; }
    @Override public Query<EntityStore> getQuery() { return NpcStatRuntimeBridge.query(); }
    @Override public boolean isParallel(int size, int taskCount) { return false; }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, EntityStatsSystems.Recalculate.class));
        // Changes already orders AFTER all registered StatModifyingSystem implementations.
    }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
        bridge.hydrate(chunk.getReferenceTo(index), store);
    }
}
