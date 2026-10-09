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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.systems.AvoidanceSystem;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.Set;
import java.util.UUID;

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
        if(active.contains(RpgStatusType.FEAR)){
            UUID sourceId=statuses.fearSource(id);
            var source=sourceId==null?null:store.getExternalData().getRefFromUUID(sourceId);
            var victimPosition=store.getComponent(ref,TransformComponent.getComponentType());
            var sourcePosition=source==null||!source.isValid()?null:store.getComponent(source,TransformComponent.getComponentType());
            var motion=role.getActiveMotionController();
            if(sourcePosition!=null&&victimPosition!=null&&motion!=null&&motion.canSteer(ref,store)){
                var from=victimPosition.getPosition();var away=sourcePosition.getPosition();
                double step=Math.max(.05,Math.min(1,motion.getMaximumSpeed()*Math.max(.01,Math.min(.1,delta))));
                var candidate=SupportNativeEffects.safeRetreat(from,away,motion.getComponentSelector(),step,displacement->{
                    var probe=new ProbeMoveData();double allowed=motion.probeMove(ref,from,displacement,probe,store);
                    return Double.isFinite(allowed)&&allowed>=step-1e-5&&!probe.edgeBlocked;
                });
                role.getBodySteering().assign(candidate);
            }
        }
        var targetGear=com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(ref,store);
        double slow=statuses.strongestSlow(id,targetGear).magnitude();
        if(slow>0&&!active.contains(RpgStatusType.FROZEN)&&!active.contains(RpgStatusType.ROOT))
            role.getBodySteering().scaleTranslation(1-slow);

        }
    }
    /** Testable policy over the actual native Steering type; does not establish connected NPC behavior. */
    public static void constrain(Set<RpgStatusType> active, Steering body, Steering head, Runnable interrupt) {
        boolean frozen = active.contains(RpgStatusType.FROZEN)||active.contains(RpgStatusType.STUN);
        if (frozen) { body.clear(); head.clear(); }
        else if (active.contains(RpgStatusType.ROOT)) body.clearTranslation();
        if (frozen || active.contains(RpgStatusType.STAGGER)||active.contains(RpgStatusType.SILENCE)
                ||active.contains(RpgStatusType.FEAR)) interrupt.run();
    }
}
