package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.flock.FlockPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.world.component.*;
import java.util.*;
import org.joml.Vector3d;

/** Native flock extension for a selected Unique demand. No separate placement or population writer. */
public final class NativeEnemyFlockExtension {
    public record Headroom(int worldActual,double worldExpected,int worldMaximum,
            int environmentActual,double environmentExpected,List<Chunk> nearby){
        public record Chunk(double actual,double expected){
            public Chunk{if(!Double.isFinite(actual)||!Double.isFinite(expected)||actual<0||expected<0)
                throw new IllegalArgumentException("ENEMY_NATIVE_CHUNK_POPULATION");}
        }
        public Headroom{nearby=List.copyOf(nearby);}
        public boolean admits(int additional){
            return additional>0&&additional<=7&&worldActual>=0&&environmentActual>=0
                    &&Double.isFinite(worldExpected)&&Double.isFinite(environmentExpected)
                    &&worldActual+additional<=worldMaximum&&worldActual+additional<=worldExpected
                    &&environmentActual+additional<=environmentExpected&&!nearby.isEmpty()
                    &&nearby.stream().allMatch(chunk->chunk.actual()+additional<=chunk.expected());
        }
    }
    private NativeEnemyFlockExtension(){}

    /** A failed extension removes only its newly captured actors; originals stay staged for ordinary fallback. */
    public static Optional<NativeEnemySpawnGroups.Group> extend(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group original,SpawnJobData nativeJob,int additional){
        Objects.requireNonNull(store);Objects.requireNonNull(original);Objects.requireNonNull(nativeJob);
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(original.job().world())
                ||original.nativeFailed()||original.members().isEmpty()||additional<1
                ||additional>8-original.members().size()||nativeJob.getJobId()!=original.job().nativeJobId()
                ||nativeJob.getRoleIndex()!=original.job().roleIndex()
                ||nativeJob.getEnvironmentIndex()!=original.job().environment()
                ||nativeJob.getSpawnConfigIndex()!=original.job().spawnConfiguration())
            throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_BOUNDARY");
        var leader=store.getExternalData().getRefFromUUID(original.members().getFirst().entity());
        var npc=leader==null||!leader.isValid()?null:store.getComponent(leader,NPCEntity.getComponentType());
        var position=leader==null||!leader.isValid()?null:store.getComponent(leader,TransformComponent.getComponentType());
        var flock=leader==null||!leader.isValid()?null:store.getComponent(leader,FlockMembership.getComponentType());
        if(npc==null||position==null||flock==null||flock.getFlockRef()==null||!flock.getFlockRef().isValid()
                ||npc.getSpawnRoleIndex()!=original.job().roleIndex()
                ||!npc.getRoleName().equals(original.job().nativeRole()))return Optional.empty();
        var nativeTypes=npc.getRole()==null?null:npc.getRole().getFlockSpawnTypes();
        if(nativeTypes!=null&&Arrays.stream(nativeTypes).anyMatch(index->index!=original.job().roleIndex()))
            return Optional.empty();
        var population=store.getResource(WorldSpawnData.getResourceType());
        if(population==null)return Optional.empty();
        int environment=original.job().environment();
        var nativeEnvironment=population.getWorldEnvironmentSpawnData(environment);
        if(nativeEnvironment==null)return Optional.empty();
        var headroom=headroom(store,position.getPosition(),population,environment);
        if(headroom.isEmpty()||!headroom.get().admits(additional))return Optional.empty();
        int beforeWorld=population.getActualNPCs(),beforeEnvironment=nativeEnvironment.getActualNPCs();
        var group=new NativeEnemySpawnGroups.Group[1];
        Throwable failure=null;
        try{
            var origin=new Vector3d(position.getPosition());var rotation=new Rotation3f(position.getRotation());
            var job=new NativeEnemySpawnGroups.Job(original.job().world(),original.job().nativeJobId(),
                    original.job().roleIndex(),original.job().nativeRole(),environment,
                    original.job().spawnConfiguration(),additional);
            NativeEnemySpawnGroups.captureAdditional(job,original.reservation(),store,()->
                    FlockPlugin.trySpawnFlock(leader,npc,nativeJob.getRoleIndex(),origin,rotation,
                            additional+1,nativeJob.getFlockAsset(),(created,holder,current)->{
                                created.setSpawnRoleIndex(nativeJob.getRoleIndex());
                                if(nativeJob.isSpawnFrozen())holder.ensureComponent(Frozen.getComponentType());
                                created.setEnvironment(environment);
                                created.setSpawnConfiguration(nativeJob.getSpawnConfigIndex());
                                created.setActiveMotionControllerName(npc.getActiveMotionControllerName());
                            },null,store),captured->group[0]=captured);
        }catch(RuntimeException|Error error){failure=error;}
        var result=group[0];
        boolean complete=failure==null&&result!=null&&!result.nativeFailed()&&result.members().size()==additional
                &&population.getActualNPCs()==beforeWorld+additional
                &&nativeEnvironment.getActualNPCs()==beforeEnvironment+additional;
        if(complete)return Optional.of(result);
        if(result!=null){
            for(var member:result.members()){
                var ref=store.getExternalData().getRefFromUUID(member.entity());
                if(ref==null||!ref.isValid()||store.getComponent(ref,EnemyStaging.getComponentType())==null)
                    throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_PARTIAL_UNOWNED",failure);
                store.removeEntity(ref,RemoveReason.REMOVE);
            }
        }
        if(population.getActualNPCs()!=beforeWorld||nativeEnvironment.getActualNPCs()!=beforeEnvironment)
            throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_POPULATION_ROLLBACK",failure);
        if(failure instanceof Error error)throw error;
        return Optional.empty();
    }

    /** Discard a fully captured but unused extension before releasing the original native flock. */
    public static void discard(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group added){
        Objects.requireNonNull(store);Objects.requireNonNull(original);Objects.requireNonNull(added);
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(original.job().world())
                ||!original.reservation().equals(added.reservation())||!original.job().world().equals(added.job().world())
                ||original.job().nativeJobId()!=added.job().nativeJobId()
                ||original.job().roleIndex()!=added.job().roleIndex()
                ||original.job().environment()!=added.job().environment()
                ||original.job().spawnConfiguration()!=added.job().spawnConfiguration()
                ||!original.job().nativeRole().equals(added.job().nativeRole())
                ||added.nativeFailed()||added.members().size()!=added.job().expectedMembers())
            throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_DISCARD_IDENTITY");
        var originals=new HashSet<UUID>();
        for(var member:original.members())originals.add(member.entity());
        var references=new ArrayList<Ref<EntityStore>>();var unique=new HashSet<UUID>();
        for(var member:added.members()){
            if(originals.contains(member.entity())||!unique.add(member.entity()))
                throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_DISCARD_ROSTER");
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            if(marker==null||!marker.state().equals(member.staging()))
                throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_DISCARD_STAGING");
            references.add(ref);
        }
        var population=store.getResource(WorldSpawnData.getResourceType());
        var environment=population==null?null:population.getWorldEnvironmentSpawnData(original.job().environment());
        if(environment==null)throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_DISCARD_TRACKER");
        int worldBefore=population.getActualNPCs(),environmentBefore=environment.getActualNPCs();
        for(var ref:references)store.removeEntity(ref,RemoveReason.REMOVE);
        if(population.getActualNPCs()!=worldBefore-references.size()
                ||environment.getActualNPCs()!=environmentBefore-references.size())
            throw new IllegalStateException("ENEMY_NATIVE_EXTENSION_DISCARD_POPULATION");
    }

    private static Optional<Headroom> headroom(Store<EntityStore> store,Vector3d position,
            WorldSpawnData population,int environment){
        var world=store.getExternalData().getWorld();var data=population.getWorldEnvironmentSpawnData(environment);
        if(data==null)return Optional.empty();
        var chunks=world.getChunkStore();var chunkStore=chunks.getStore();
        // The native flock implementation offsets x/z by at most 0.5 from its leader.
        int minX=ChunkUtil.chunkCoordinate(position.x-.5),maxX=ChunkUtil.chunkCoordinate(position.x+.5);
        int minZ=ChunkUtil.chunkCoordinate(position.z-.5),maxZ=ChunkUtil.chunkCoordinate(position.z+.5);
        var nearby=new ArrayList<Headroom.Chunk>();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            var ref=chunks.getChunkReference(ChunkUtil.indexChunk(x,z));
            if(ref==null||!ref.isValid())return Optional.empty();
            // Native tracking first fills the spawn chunk, then spreads the remainder through
            // SpiralIterator radius 3. Require the whole native neighborhood to have room for
            // every added actor, for every possible spawn chunk at the flock's +/-0.5 offset.
            double available=0;
            for(int nx=x-3;nx<=x+3;nx++)for(int nz=z-3;nz<=z+3;nz++){
                var nearbyRef=chunks.getChunkReference(ChunkUtil.indexChunk(nx,nz));
                if(nearbyRef==null||!nearbyRef.isValid())continue;
                var nativeChunk=chunkStore.getComponent(nearbyRef,ChunkSpawnData.getComponentType());
                var counted=chunkStore.getComponent(nearbyRef,ChunkSpawnedNPCData.getComponentType());
                if(nativeChunk==null||counted==null)continue;
                available+=environmentHeadroom(nativeChunk,counted,environment);
            }
            nearby.add(new Headroom.Chunk(0,available));
        }
        return Optional.of(new Headroom(population.getActualNPCs(),population.getExpectedNPCs(),
                world.getGameplayConfig().getMaxEnvironmentalNPCSpawns(),data.getActualNPCs(),data.getExpectedNPCs(),nearby));
    }
    /** A loaded neighboring chunk need not contain this spawn environment. Native's
     *  getEnvironmentSpawnData throws for that ordinary absence; it offers no capacity. */
    static double environmentHeadroom(ChunkSpawnData chunk,ChunkSpawnedNPCData counted,int environment){
        var nativeEnvironment=chunk.getChunkEnvironmentSpawnDataMap().get(environment);
        return nativeEnvironment==null?0:Math.max(0,nativeEnvironment.getExpectedNPCs()
                -counted.getEnvironmentSpawnCount(environment));
    }
}
