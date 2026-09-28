package com.inigmasgames.hytalerpg.difficulty;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Transform;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-entrant admission; a party member never transfers another character or lends an unlock. */
public final class HytaleDifficultyPortals extends EntityTickingSystem<EntityStore> {
    private final DifficultyPortals portals;private final DifficultyTravel travel;
    private final Map<UUID,List<Ref<EntityStore>>> visuals=new ConcurrentHashMap<>();
    private final Map<UUID,Long> projectionAt=new ConcurrentHashMap<>(),attemptAt=new ConcurrentHashMap<>(),recoverAt=new ConcurrentHashMap<>();
    private final Set<UUID> mustExit=ConcurrentHashMap.newKeySet();
    public HytaleDifficultyPortals(DifficultyPortals portals,DifficultyTravel travel){this.portals=portals;this.travel=travel;}
    public void reconnect(UUID player){mustExit.add(player);if(travel.pending(player).isPresent())recoverAt.put(player,System.nanoTime()+12_000_000_000L);}
    public void disconnect(UUID player){recoverAt.remove(player);attemptAt.remove(player);mustExit.remove(player);}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),TransformComponent.getComponentType());}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var player=chunk.getComponent(index,PlayerRef.getComponentType());var id=player.getUuid();long now=System.nanoTime();
        var recovery=recoverAt.get(id);if(recovery!=null&&now>=recovery&&recoverAt.remove(id,recovery)){
            travel.recover(id).whenComplete((v,e)->message(player,e==null?"Difficulty transfer recovery complete.":"Transfer recovery needs attention: "+failure(e)));return;}
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        if(now>=projectionAt.getOrDefault(world,0L)){projectionAt.put(world,now+1_000_000_000L);project(world,store,buffer);}
        var pos=chunk.getComponent(index,TransformComponent.getComponentType()).getPosition();
        var touching=portals.all().stream().filter(p->p.world().equals(world)&&p.touches(pos.x(),pos.y(),pos.z())).findFirst();
        if(touching.isEmpty()){mustExit.remove(id);return;}
        if(travel.busy(id)||mustExit.contains(id)||now<attemptAt.getOrDefault(id,0L))return;
        var portal=touching.orElseThrow();{
            mustExit.add(id);
            attemptAt.put(id,now+10_000_000_000L);
            travel.request(id,portal.target(),false).whenComplete((v,e)->message(player,e==null?"Entered "+portal.target()+".":"Portal: "+failure(e)+(travel.pending(id).isPresent()?". Reconnect to recover safely.":". Step out before retrying.")));
        }
    }
    private void project(UUID world,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var nativeWorld=store.getExternalData().getWorld();
        var current=portals.all().stream().filter(p->p.world().equals(world)).filter(p->nativeWorld.getPlayerRefs().stream().anyMatch(viewer->{
            var ref=nativeWorld.getEntityRef(viewer.getUuid());if(ref==null||!ref.isValid())return false;
            var t=store.getComponent(ref,TransformComponent.getComponentType());return t!=null&&t.getPosition().distanceSquared(p.x(),p.y(),p.z())<=64*64;
        })).toList();
        visuals.entrySet().removeIf(e->{var refs=e.getValue();
            boolean owned=refs.stream().anyMatch(ref->ref.isValid()&&ref.getStore()==store);
            boolean expired=refs.stream().noneMatch(Ref::isValid);
            if(owned&&current.stream().noneMatch(p->p.id().equals(e.getKey()))){for(var ref:refs)if(ref.isValid())buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);return true;}return expired;
        });
        for(var p:current){var old=visuals.get(p.id());if(old!=null&&old.stream().allMatch(Ref::isValid))continue;
            if(old!=null)for(var ref:old)if(ref.isValid())buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
            String title=p.target()==DifficultyId.NORMAL?"Normal Campaign — Return":(p.target()==DifficultyId.NIGHTMARE?"Nightmare":"Hell")+" Difficulty";
            String recommendation=p.target()==DifficultyId.NORMAL?"Initial Campaign":"Recommended Level: "+p.target().recommendedLevel()+"+";
            visuals.put(p.id(),List.of(carrier("RPG_Difficulty_Portal",p.x(),p.y(),p.z(),null,store,buffer),
                    carrier("Invisible_Projectile",p.x(),p.y()+3.5,p.z(),title,store,buffer),
                    carrier("Invisible_Projectile",p.x(),p.y()+3.05,p.z(),recommendation,store,buffer)));
        }
    }
    private static Ref<EntityStore> carrier(String assetId,double x,double y,double z,String label,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var asset=ModelAsset.getAssetMap().getAsset(assetId);if(asset==null)throw new IllegalStateException("PORTAL_MODEL_MISSING:"+assetId);
        var model=Model.createStaticScaledModel(asset,1);var h=EntityStore.REGISTRY.newHolder();
        h.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
        h.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        h.addComponent(TransformComponent.getComponentType(),new TransformComponent(new org.joml.Vector3d(x,y,z),new com.hypixel.hytale.math.vector.Rotation3f()));
        h.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));h.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));
        if(label!=null)h.addComponent(Nameplate.getComponentType(),new Nameplate(label));h.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        return buffer.addEntity(h,AddReason.SPAWN);
    }
    public static String failure(Throwable error){while(error.getCause()!=null)error=error.getCause();return String.valueOf(error.getMessage());}
    private static void message(PlayerRef player,String text){player.sendMessage(Message.raw(text));}
}
