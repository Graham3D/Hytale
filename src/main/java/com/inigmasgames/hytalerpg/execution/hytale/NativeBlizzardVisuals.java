package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.joml.Vector3d;

/** Unsaved native model carriers follow AreaRuntime's swept, server-owned transforms.
 * There is deliberately no native damage interaction, physics integrator or second hit authority. */
public final class NativeBlizzardVisuals {
    private record Key(UUID owner,String instance,int index){}
    private record Carrier(Ref<EntityStore> ref,Store<EntityStore> store){}
    private final Map<Key,Carrier> carriers=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Key,Double> storms=new java.util.concurrent.ConcurrentHashMap<>();
    void shard(SkillExecutionContext c,int index,Vec3 p,boolean terminal,CommandBuffer<EntityStore> buffer){
        var key=new Key(c.request().actorId(),c.skillInstanceId(),index);
        if(terminal){remove(key,buffer);return;}
        if(buffer==null)return;
        var store=buffer.getStore();var old=carriers.get(key);
        if(old==null){
            var asset=ModelAsset.getAssetMap().getAsset("RPG_Blizzard_Shard");
            if(asset==null)throw new IllegalStateException("BLIZZARD_SHARD_MODEL_UNRESOLVED");
            var model=Model.createStaticScaledModel(asset,1);
            var holder=EntityStore.REGISTRY.newHolder();
            holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
            holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),transform(p));
            holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(),new BoundingBox(model.getBoundingBox()));
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            holder.addComponent(com.hypixel.hytale.server.core.modules.entity.DespawnComponent.getComponentType(),
                    com.hypixel.hytale.server.core.modules.entity.DespawnComponent.despawnInSeconds(
                            store.getResource(com.hypixel.hytale.server.core.modules.time.TimeResource.getResourceType()),.6f));
            carriers.put(key,new Carrier(buffer.addEntity(holder,AddReason.SPAWN),store));
        }else if(old.ref.isValid()){
            // TransformComponent owns native sectionRef bookkeeping. Replacing it loses that
            // membership and leaves a stale entity reference in chunk serialization on removal.
            buffer.getComponent(old.ref,TransformComponent.getComponentType()).setPosition(new Vector3d(p.x(),p.y(),p.z()));
        }
    }
    private static TransformComponent transform(Vec3 p){
        // Portal shard's long local Y axis points down; model remains a connected-QA candidate.
        return new TransformComponent(new Vector3d(p.x(),p.y(),p.z()),new Rotation3f((float)Math.PI,0,0));
    }
    /** Invoked only inside the existing opt-in, empty isolated world audit. */
    static void audit(Store<EntityStore> store,com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk,SkillExecutionContext context){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var visuals=new NativeBlizzardVisuals();var once=new java.util.concurrent.atomic.AtomicBoolean();
        var key=new Key(context.request().actorId(),context.skillInstanceId(),0);
        try{
            store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(archetype,buffer)->{
                if(once.compareAndSet(false,true))visuals.shard(context,0,new Vec3(4.5,204,4.5),false,buffer);
            });
            var carrier=Objects.requireNonNull(visuals.carriers.get(key));
            if(!carrier.ref.isValid()||store.getComponent(carrier.ref,ModelComponent.getComponentType())==null
                    ||store.getComponent(carrier.ref,EntityStore.REGISTRY.getNonSerializedComponentType())==null
                    ||store.getComponent(carrier.ref,com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider.getComponentType())!=null)
                throw new IllegalStateException("BLIZZARD_NATIVE_CARRIER_CONTRACT");
            // A real installed native solid cube; this fixture is forbidden in any connected/live world.
            if(chunk.getBlock(4,200,4)!=0)throw new IllegalStateException("BLIZZARD_AUDIT_REQUIRES_AIR");
            try{
                if(!chunk.setBlock(4,200,4,"Rock_Stone"))throw new IllegalStateException("BLIZZARD_AUDIT_SOLID_PLACEMENT");
                var hit=HytaleAreaQueries.shardContact(store,new Vec3(4.5,204,4.5),new Vec3(4.5,198,4.5),.1).orElseThrow();
                if(Math.abs(hit.y()-201)>.025)throw new IllegalStateException("BLIZZARD_FIRST_SURFACE_WRONG:"+hit.y());
                var originalTransform=store.getComponent(carrier.ref,TransformComponent.getComponentType());
                var section=originalTransform.getSectionRef();
                once.set(false);
                store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(archetype,buffer)->{
                    if(once.compareAndSet(false,true))visuals.shard(context,0,hit,false,buffer);
                });
                if(store.getComponent(carrier.ref,TransformComponent.getComponentType()).getPosition().distance(new Vector3d(hit.x(),hit.y(),hit.z()))>1e-6)
                    throw new IllegalStateException("BLIZZARD_TRANSFORM_UPDATE_FAILED");
                if(store.getComponent(carrier.ref,TransformComponent.getComponentType())!=originalTransform||originalTransform.getSectionRef()!=section)
                    throw new IllegalStateException("BLIZZARD_NATIVE_SECTION_MEMBERSHIP_LOST");
            }finally{chunk.setBlock(4,200,4,0);}
            once.set(false);
            store.forEachChunk((java.util.function.BiConsumer<ArchetypeChunk<EntityStore>,CommandBuffer<EntityStore>>)(archetype,buffer)->{
                if(once.compareAndSet(false,true))visuals.end(context,buffer);
            });
            if(carrier.ref.isValid()||!visuals.carriers.isEmpty())throw new IllegalStateException("BLIZZARD_NATIVE_CLEANUP_FAILED");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_BLIZZARD_NATIVE_INTEGRATION result=PASS model=RPG_Blizzard_Shard unsaved=true nativePhysics=false firstSolidSurface=201 transformUpdated=true cleaned=true connectedProof=false");
        }finally{
            for(var carrier:visuals.carriers.values())if(carrier.ref.isValid())store.removeEntity(carrier.ref,RemoveReason.REMOVE);
        }
    }
    void storm(SkillExecutionContext c,AreaGeometry shape,double remaining,Store<EntityStore> store){
        var key=new Key(c.request().actorId(),c.skillInstanceId(),-1);double now=System.nanoTime()/1e9;
        if(now<storms.getOrDefault(key,0d)||remaining<=.2)return;
        storms.put(key,now+.1);
        var packet=stormPacket(shape,remaining);
        for(var player:store.getExternalData().getWorld().getPlayerRefs()){
            var ref=player.getReference();if(ref==null||!ref.isValid())continue;
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform!=null&&transform.getPosition().distanceSquared(new Vector3d(packet.position.x,packet.position.y,packet.position.z))<=75*75)
                player.getPacketHandler().writeNoCache(packet);
        }
    }
    /** Short root-owned leases allow cancellation without an invented particle stop API.
     * Stop emissions early enough for the derivative's <=.2s particles to expire at root end. */
    public static com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem stormPacket(AreaGeometry shape,double remaining){
        var p=shape.origin();
        return new com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem("RPG_Blizzard_Snow",
                new com.hypixel.hytale.protocol.Position(p.x(),p.y()+2,p.z()),new com.hypixel.hytale.protocol.Direction(0,0,0),
                (float)shape.radius(),null,(float)Math.clamp(remaining-.2,0,.1));
    }
    void end(SkillExecutionContext c,CommandBuffer<EntityStore> buffer){
        carriers.keySet().stream().filter(k->k.owner.equals(c.request().actorId())&&k.instance.equals(c.skillInstanceId())).toList().forEach(k->remove(k,buffer));
        storms.keySet().removeIf(k->k.owner.equals(c.request().actorId())&&k.instance.equals(c.skillInstanceId()));
    }
    void cancel(UUID owner,CommandBuffer<EntityStore> buffer){
        carriers.keySet().stream().filter(k->k.owner.equals(owner)).toList().forEach(k->remove(k,buffer));
        storms.keySet().removeIf(k->k.owner.equals(owner));
    }
    private void remove(Key key,CommandBuffer<EntityStore> buffer){
        var carrier=carriers.remove(key);if(carrier==null)return;
        if(buffer!=null&&buffer.getStore()==carrier.store){buffer.tryRemoveEntity(carrier.ref,RemoveReason.REMOVE);}
        else carrier.store.getExternalData().getWorld().execute(()->{
            if(carrier.ref.isValid())carrier.store.removeEntity(carrier.ref,RemoveReason.REMOVE);
        });
    }
}
