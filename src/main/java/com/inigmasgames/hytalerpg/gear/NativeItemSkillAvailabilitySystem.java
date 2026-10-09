package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.Set;

/** Publishes WA-144 availability from the same validated native equipment owner as combat. */
public final class NativeItemSkillAvailabilitySystem extends EntityTickingSystem<EntityStore> {
    private final RpgLoadoutService loadouts;
    public NativeItemSkillAvailabilitySystem(RpgLoadoutService loadouts){this.loadouts=java.util.Objects.requireNonNull(loadouts);}
    @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemDependency<>(Order.AFTER,HytaleGearEquipment.Tick.class),
            new SystemDependency<>(Order.BEFORE,HytaleSkillExecutionSystem.class));}
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                               CommandBuffer<EntityStore> buffer){
        var actor=chunk.getReferenceTo(index);
        var player=chunk.getComponent(index,PlayerRef.getComponentType());
        if(!loadouts.ready(player.getUuid()))return;
        loadouts.publishItemSkillAvailability(player.getUuid(),GearNativeItems.effects(actor,store).snapshot());
    }
}
