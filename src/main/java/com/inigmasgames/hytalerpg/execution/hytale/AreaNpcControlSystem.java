package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.systems.AvoidanceSystem;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.Set;

/** Entity-effect Movement/Ability flags alone are not NPC controls in the pinned build.
 * Operates on native transient steering/queued actions, never role identity, animation speed or saved AI state. */
public final class AreaNpcControlSystem extends EntityTickingSystem<EntityStore> {
    private final StatusService statuses;
    public AreaNpcControlSystem(StatusService statuses) { this.statuses = statuses; }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(AreaStatusProjection.getComponentType(), NPCEntity.getComponentType(), UUIDComponent.getComponentType());
    }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, AvoidanceSystem.class),
                new SystemDependency<>(Order.BEFORE, SteeringSystem.class),
                new SystemDependency<>(Order.BEFORE, InteractionSystems.TickInteractionManagerSystem.class));
    }
    @Override public void tick(float delta, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){
        var ref = chunk.getReferenceTo(index);
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) return;
        var role = chunk.getComponent(index, NPCEntity.getComponentType()).getRole();
        if (role == null) return;
        var id = chunk.getComponent(index, UUIDComponent.getComponentType()).getUuid();
        var active = statuses.inspect(id).active().keySet();
        var manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        constrain(active, role.getBodySteering(), role.getHeadSteering(), () -> { if (manager != null) manager.clear(); });

        }
    }
    /** Testable policy over the actual native Steering type; does not establish connected NPC behavior. */
    public static void constrain(Set<RpgStatusType> active, Steering body, Steering head, Runnable interrupt) {
        boolean frozen = active.contains(RpgStatusType.FROZEN);
        if (frozen) { body.clear(); head.clear(); }
        else if (active.contains(RpgStatusType.ROOT)) body.clearTranslation();
        if (frozen || active.contains(RpgStatusType.STAGGER)) interrupt.run();
    }
}
