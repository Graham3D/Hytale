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
public final class HytalePlayerPersistenceReady extends EntityTickingSystem<EntityStore> implements AutoCloseable {
    private record Ready(UUID world,long generation){}
    private final Map<UUID,Ready> pending=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generations=new java.util.concurrent.atomic.AtomicLong();
    private final RpgLoadoutService loadouts;
    private final com.inigmasgames.hytalerpg.readypath.PlayerEntryPreparation preparation;
    private final Map<UUID,PlayerRef> connections=new java.util.concurrent.ConcurrentHashMap<>();
    @FunctionalInterface public interface OwnerInstall {boolean install(Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,Player entity,EntityStatMap stats);}
    private final OwnerInstall install;
    public HytalePlayerPersistenceReady(RpgLoadoutService loadouts,OwnerInstall install){this.loadouts=loadouts;this.install=install;this.preparation=new com.inigmasgames.hytalerpg.readypath.PlayerEntryPreparation(loadouts);}
    /** The SDK awaits this future before World.addPlayer; no world handles enter worker jobs. */
    public java.util.concurrent.CompletableFuture<Void> preConnect(PlayerRef player,UUID world){
        if(Boolean.getBoolean("hywind.readypath.legacy"))return java.util.concurrent.CompletableFuture.completedFuture(null);
        connections.put(player.getUuid(),player);
        return preparation.prepare(player.getUuid(),world,com.inigmasgames.hytalerpg.content.RpgCatalog.prepared().revision())
                .thenApply(view->(Void)null).whenComplete((ignored,error)->{
                    if(error!=null&&connections.remove(player.getUuid(),player))preparation.detach(player.getUuid());
                });
    }
    public synchronized void begin(PlayerRef connection,UUID world){
        UUID player=connection.getUuid();
        var current=connections.get(player);if(current!=null&&current!=connection)return;
        try(var readyPathSpan=com.inigmasgames.hywind.readypath.ReadyPathProbe.span("RPG_PRELOAD_SUBMIT",player)) {
        com.inigmasgames.hywind.readypath.ReadyPathProbe.ready(player);
        if(pending.size()>=256&&!pending.containsKey(player))throw new IllegalStateException("PLAYER_READINESS_CAPACITY");
        loadouts.preload(player);pending.put(player,new Ready(world,generations.incrementAndGet()));

        }
    }
    public void detach(PlayerRef player){
        var current=connections.get(player.getUuid());
        if(current!=null&&current!=player)return; // A delayed old disconnect cannot cancel a newer session.
        connections.remove(player.getUuid(),player);pending.remove(player.getUuid());preparation.detach(player.getUuid());
        com.inigmasgames.hywind.readypath.ReadyPathProbe.disconnect(player.getUuid());
    }
    public Map<String,Object> preparationStatus(){return preparation.inspect();}
    public void drain(PlayerRef player){
        var current=connections.get(player.getUuid());if(current!=null&&current!=player)return;
        pending.remove(player.getUuid());preparation.detach(player.getUuid());
    }
    @Override public void close(){pending.clear();connections.clear();preparation.close();}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),Player.getComponentType(),EntityStatMap.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,HytaleSupportSystem.class),new SystemDependency<>(Order.BEFORE,HytaleSkillExecutionSystem.class));}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.LIFECYCLE)){
        var player=chunk.getComponent(index,PlayerRef.getComponentType());var ready=pending.get(player.getUuid());
        var connection=connections.get(player.getUuid());if(connection!=null&&connection!=player)return;
        if(com.inigmasgames.hywind.readypath.ReadyPathProbe.ENABLED)
            com.inigmasgames.hywind.readypath.ReadyPathProbe.observeTick(player.getUuid(),store.getExternalData().getWorld().getBufferedTickLengthMetricSet().getLastValue());
        if(ready==null||!ready.world().equals(player.getWorldUuid())||!loadouts.ready(player.getUuid()))return;
        try(var span=com.inigmasgames.hywind.readypath.ReadyPathProbe.span("RPG_READY_INSTALL_ATTEMPT_NOT_J4",player.getUuid())){
            if(install.install(store,chunk.getReferenceTo(index),player,chunk.getComponent(index,Player.getComponentType()),chunk.getComponent(index,EntityStatMap.getComponentType()))){
                pending.remove(player.getUuid(),ready);
                com.inigmasgames.hywind.readypath.ReadyPathProbe.mark("RPG_READY_INSTALL_COMPLETE_EQUIPMENT_UNCONFIRMED",player.getUuid());
            }
        }

        }
    }
}
