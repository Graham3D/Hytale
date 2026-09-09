package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.*;

/** Ready events enqueue IDs only. The existing native entity tick resolves the current generation. */
public final class HytalePlayerPersistenceReady extends EntityTickingSystem<EntityStore> {
    private record Ready(UUID world,long generation){}
    private final Map<UUID,Ready> pending=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generations=new java.util.concurrent.atomic.AtomicLong();
    private final RpgLoadoutService loadouts;
    @FunctionalInterface public interface OwnerInstall {boolean install(Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,Player entity,EntityStatMap stats);}
    private final OwnerInstall install;
    public HytalePlayerPersistenceReady(RpgLoadoutService loadouts,OwnerInstall install){this.loadouts=loadouts;this.install=install;}
    public synchronized void begin(UUID player,UUID world){
        if(pending.size()>=256&&!pending.containsKey(player))throw new IllegalStateException("PLAYER_READINESS_CAPACITY");
        loadouts.preload(player);pending.put(player,new Ready(world,generations.incrementAndGet()));
    }
    public void detach(UUID player){pending.remove(player);}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),Player.getComponentType(),EntityStatMap.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,HytaleSupportSystem.class),new SystemDependency<>(Order.BEFORE,HytaleSkillExecutionSystem.class));}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.LIFECYCLE)){
        var player=chunk.getComponent(index,PlayerRef.getComponentType());var ready=pending.get(player.getUuid());
        if(ready==null||!ready.world().equals(player.getWorldUuid())||!loadouts.ready(player.getUuid()))return;
        if(install.install(store,chunk.getReferenceTo(index),player,chunk.getComponent(index,Player.getComponentType()),chunk.getComponent(index,EntityStatMap.getComponentType())))pending.remove(player.getUuid(),ready);

        }
    }
}
