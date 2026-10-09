package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Distance ledger around the installed StandardPhysicsTickSystem movement segment. */
public final class NativeAffixProjectilePathSystem extends EntityTickingSystem<EntityStore> {
    private static ComponentType<EntityStore,NativeAffixProjectileTravel.Path> pathType;
    public static void bind(ComponentType<EntityStore,NativeAffixProjectileTravel.Path> type){pathType=Objects.requireNonNull(type);}
    public static ComponentType<EntityStore,NativeAffixProjectileTravel.Path> pathType(){return pathType;}
    @Override public Query<EntityStore> getQuery(){return Query.and(pathType,TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){
        return Set.of(new SystemDependency<>(Order.AFTER,StandardPhysicsTickSystem.class));
    }
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                               CommandBuffer<EntityStore> buffer) {
        var path=chunk.getComponent(index,pathType);
        var transform=chunk.getComponent(index,TransformComponent.getComponentType());
        var terminal=path.terminalPoint(transform.getPosition());
        if(path.afterNativeMove(transform.getPosition())) {
            var ref=chunk.getReferenceTo(index);
            // Native acceleration may move past the remaining straight segment in this tick.
            // Clamp the replicated transform to that segment's endpoint before removal.
            transform.setPosition(terminal);
            var provider=store.getComponent(ref,StandardPhysicsProvider.getComponentType());
            if(provider!=null)provider.getPosition().set(terminal);
            buffer.run(s->{if(ref.isValid())s.removeEntity(ref,RemoveReason.REMOVE);});
        }
    }

    /** Bounds the provider's incoming velocity before native collision/impact resolution. */
    public static final class BeforePhysics extends EntityTickingSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return Query.and(pathType,Velocity.getComponentType(),StandardPhysicsProvider.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){
            return Set.of(new SystemDependency<>(Order.BEFORE,StandardPhysicsTickSystem.class));
        }
        @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                   CommandBuffer<EntityStore> buffer) {
            var time=store.getResource(TimeResource.getResourceType());
            float nativeSeconds=time.getTimeDilationModifier()/store.getExternalData().getWorld().getTps();
            chunk.getComponent(index,pathType).limitNativeStep(
                    chunk.getComponent(index,Velocity.getComponentType()),
                    chunk.getComponent(index,StandardPhysicsProvider.getComponentType()),nativeSeconds);
        }
    }
}
