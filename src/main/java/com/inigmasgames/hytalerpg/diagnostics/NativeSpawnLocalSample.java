package com.inigmasgames.hytalerpg.diagnostics;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import java.util.*;

/** Ten-second trace-only local observation using the native spatial index. No world/entity scan. */
public final class NativeSpawnLocalSample extends TickingSystem<EntityStore> {
    private final Map<UUID,Long> next=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Sample> previous=new java.util.concurrent.ConcurrentHashMap<>();
    private record Sample(long at,double x,double y,double z) {}
    @Override public void tick(float dt,int index,Store<EntityStore> store){
        if(!MonsterSpawnTrace.enabled()){next.clear();previous.clear();return;}
        var world=store.getExternalData().getWorld();var worldId=world.getWorldConfig().getUuid();long now=System.nanoTime();
        if(now<next.getOrDefault(worldId,0L))return;next.put(worldId,now+10_000_000_000L);
        int players=0;
        for(var viewer:world.getPlayerRefs()){
            if(players++>=16)break;
            try{
                var ref=viewer.getReference();if(ref==null||!ref.isValid())continue;
                var transform=store.getComponent(ref,TransformComponent.getComponentType());var player=store.getComponent(ref,Player.getComponentType());
                if(transform==null||player==null)continue;var p=transform.getPosition();
                var nearby=new ArrayList<Ref<EntityStore>>();
                var spatial=store.getResource(EntityModule.get().getEntitySpatialResourceType());
                if(spatial==null)continue;spatial.getSpatialStructure().collect(p,64,nearby);
                int hostiles=0,checked=0;
                for(var actor:nearby){if(checked++>=2048)break;if(actor==null||!actor.isValid())continue;
                    var support=store.getComponent(actor,WorldSupport.getComponentType());
                    if(support!=null&&support.getDefaultPlayerAttitude()==Attitude.HOSTILE)hostiles++;
                }
                if(previous.size()>1024)previous.entrySet().removeIf(e->now-e.getValue().at()>120_000_000_000L);
                var prior=previous.put(viewer.getUuid(),new Sample(now,p.x(),p.y(),p.z()));
                double speed=prior==null?0:Math.sqrt(Math.pow(p.x()-prior.x(),2)+Math.pow(p.y()-prior.y(),2)+Math.pow(p.z()-prior.z(),2))/((now-prior.at())/1e9);
                String biome="UNSUPPORTED_GENERATOR";
                if(world.getChunkStore().getGenerator() instanceof com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator generator){
                    var b=generator.getZoneBiomeResultAt((int)world.getWorldConfig().getSeed(),(int)Math.floor(p.x()),(int)Math.floor(p.z()));
                    biome="Default/"+b.getZoneResult().getZone().name()+"/"+b.getBiome().getName();
                }
                MonsterSpawnTrace.event("PLAYER_LOCAL",worldId,-1,"all","player="+viewer.getUuid()+" position="+p.x()+":"+p.y()+":"+p.z()
                        +" biome="+biome+" sampledSpeed="+speed+" gameMode="+player.getGameMode()+" viewRadius="+player.getViewRadius()
                        +" simulationDistance=UNEXPOSED nearbyHostiles64="+hostiles+" nearbyCountLowerBound="+(nearby.size()>2048)
                        +" clientRendering=UNKNOWN");
            }catch(RuntimeException e){MonsterSpawnTrace.event("PLAYER_LOCAL_UNAVAILABLE",worldId,-1,"all",e.getClass().getSimpleName());}
        }
    }
}
