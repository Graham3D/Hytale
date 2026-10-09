package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.*;
import com.hypixel.hytale.server.core.universe.world.chunk.section.*;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.movement.MovementMode;
import com.hypixel.hytale.server.spawning.*;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;

/** Loaded-only admission at the existing native job boundary. Never allocates a spawn/job or edits population. */
public final class NativeAquaticHabitat {
    public enum Outcome { SUITABLE_CANDIDATE, KNOWN_UNSUITABLE, UNKNOWN }
    static final int MAX_WORK=16384, MAX_ENTRIES=1024;
    public record Assessment(Outcome outcome,long signature,int work,String reason) {}
    record Key(UUID world,long chunk,int environment,String requirements) {}
    static final class Retry {
        long signature,until; int failures;
        boolean admit(long signature,long now){
            if(this.signature!=signature){this.signature=signature;failures=0;until=0;}
            return now>=until;
        }
        void completed(boolean success,long now){
            if(success){failures=0;until=0;return;}
            if(++failures>=3)until=now+2_000_000_000L;
        }
    }
    // Read-only invalidation witnesses. Never consume changed-position sets or create serialization work.
    private static Object packetVersion(Object section,String field){
        try{var f=section.getClass().getDeclaredField(field);f.setAccessible(true);
            var reference=(java.lang.ref.Reference<?>)f.get(section);return reference==null?null:reference.get();
        }catch(ReflectiveOperationException e){return null;}
    }
    private record Ticket(Key key,Retry retry,boolean rejected) {}
    private final Map<SpawnJobData,Ticket> tickets=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Key,Retry> retries=Collections.synchronizedMap(new LinkedHashMap<>());

    public void forgetWorld(UUID world){
        synchronized(retries){retries.keySet().removeIf(k->k.world().equals(world));}
        synchronized(tickets){tickets.entrySet().removeIf(e->e.getValue().key().world().equals(world));}
    }
    public void close(){retries.clear();tickets.clear();}

    /** The production evaluator and native-section fixtures use the same environmental/fluid proof. */
    interface Column {
        List<int[]> runs(int x,int z);
        FluidSection fluid(int section);
        boolean accepts(int fluid);
        boolean candidate(int x,int z);
    }
    static Assessment assess(Column column,int environment){
        int work=0;long signature=1;boolean fluid=false;
        for(int z=0;z<32;z++)for(int x=0;x<32;x++){
            for(var run:column.runs(x,z)){
                if(++work>MAX_WORK)return new Assessment(Outcome.UNKNOWN,0,work,"ASSESSMENT_BUDGET");
                if(run[2]!=environment)continue;
                signature=31*signature+Objects.hash(x,z,run[0],run[1]);
                for(int y=run[0];y<run[1];){
                    int section=Math.floorDiv(y,32),end=Math.min(run[1],(section+1)*32);
                    if(++work>MAX_WORK)return new Assessment(Outcome.UNKNOWN,0,work,"ASSESSMENT_BUDGET");
                    var liquids=column.fluid(section);
                    if(liquids==null)return new Assessment(Outcome.UNKNOWN,0,work,"RELEVANT_SECTION_UNLOADED");
                    if(liquids.isEmpty()){signature=31*signature; y=end;continue;}
                    for(;y<end;y++){
                        if(++work>MAX_WORK)return new Assessment(Outcome.UNKNOWN,0,work,"ASSESSMENT_BUDGET");
                        int id=liquids.getFluidId(x,y&31,z);
                        signature=31*signature+id;signature=31*signature+liquids.getFluidLevel(x,y&31,z);
                        if(id==0||!column.accepts(id))continue;
                        fluid=true;
                        // Native body/clearance rules (including ice ceilings), never a surface-material heuristic.
                        if(column.candidate(x,z))return new Assessment(Outcome.SUITABLE_CANDIDATE,0,work,"NATIVE_PREDICATES_PASSED");
                    }
                }
            }
        }
        return new Assessment(fluid?Outcome.UNKNOWN:Outcome.KNOWN_UNSUITABLE,signature,work,
                fluid?"NATIVE_CANDIDATES_REJECTED":"NO_REQUIRED_FLUID_IN_ENVIRONMENT");
    }
    public boolean reject(SpawnJobData job,WorldChunk chunk,Store<ChunkStore> store){
        if(job.isTerminated()||tickets.containsKey(job))return false;
        long started=System.nanoTime();
        try{
            var builder=NPCPlugin.get().tryGetCachedValidRole(job.getRoleIndex());
            if(builder==null||!builder.isSpawnable()||!(builder instanceof ISpawnableWithModel spawnable))return false;
            var context=new SpawningContext();
            try{
                if(!context.setSpawnable(spawnable)||!context.breathesInWater)return false;
                var params=job.getSpawnConfig().getRoles().get(job.getRoleIndex());
                var modes=context.getSpawnMovementModes();
                if(modes.size()!=1||!modes.contains(MovementMode.DIVE))return false;
                if(!context.setMovementMode(MovementMode.DIVE,params.getEnableSafeSpawning()))return false;
                var blocks=store.getComponent(chunk.getReference(),BlockChunk.getComponentType());
                if(blocks==null)return false;
                var world=store.getExternalData().getWorld();
                context.setEnvironmentChunk(world,chunk.getReference(),chunk,job.getEnvironmentIndex());
                int tag=job.getSpawnConfig().getSpawnFluidTag(job.getRoleIndex());
                var allowed=tag==Integer.MIN_VALUE?null:Fluid.getAssetMap().getIndexesForTag(tag);
                if(tag!=Integer.MIN_VALUE&&allowed==null)return false;
                var sections=new HashMap<Integer,FluidSection>();var absent=new HashSet<Integer>();
                var tested=new HashMap<Integer,Boolean>();var probes=new int[]{16};var probeLimited=new boolean[]{false};
                var versioned=new HashSet<Integer>();var stable=new boolean[]{true};var versions=new long[]{1};
                var assessment=assess(new Column(){
                    public List<int[]> runs(int x,int z){
                        var result=new ArrayList<int[]>();var cursor=new BlockChunk.EnvironmentRunCursor();
                        blocks.readEnvironmentRuns(x,z,cursor);
                        while(cursor.next())result.add(new int[]{cursor.getMinY(),cursor.getMaxY()+1,cursor.getEnvironmentId()});
                        return result;
                    }
                    public FluidSection fluid(int section){
                        if(absent.contains(section))return null;
                        if(sections.containsKey(section))return sections.get(section);
                        var ref=world.getChunkStore().getChunkSectionReference(chunk.getX(),section,chunk.getZ());
                        var value=ref==null||!ref.isValid()?null:store.getComponent(ref,FluidSection.getComponentType());
                        if(value==null)absent.add(section);else {
                            sections.put(section,value);
                            if(versioned.add(section)){
                                var terrain=store.getComponent(ref,BlockSection.getComponentType());
                                var fluidVersion=packetVersion(value,"cachedPacket");
                                var blockVersion=terrain==null?null:packetVersion(terrain,"cachedChunkPacket");
                                if(fluidVersion==null||blockVersion==null)stable[0]=false;
                                else {versions[0]=31*versions[0]+System.identityHashCode(fluidVersion);
                                    versions[0]=31*versions[0]+System.identityHashCode(blockVersion);}
                            }
                        }
                        return value;
                    }
                    public boolean accepts(int id){return allowed==null?id!=0:allowed.contains(id);}
                    public boolean candidate(int x,int z){return tested.computeIfAbsent(x+32*z,ignored->{
                        if(!context.setEnvironmentColumn(x,z,job.getSuppressionSpanHelper()))return false;
                        int budget=8;
                        while(budget-->0&&context.selectRandomSpawnSpan()){
                            if(probes[0]--<=0){probeLimited[0]=true;return false;}
                            boolean valid=job.getSpawnConfig().withinLightRange(context)
                                    &&context.canSpawnOnBlock(job.getSpawnConfig().getSpawnBlockSet(job.getRoleIndex()),tag)
                                    &&context.canSpawn()==SpawnTestResult.TEST_OK;
                            context.deleteCurrentSpawnSpan();if(valid)return true;
                        }
                        return false;
                    });}
                },job.getEnvironmentIndex());
                var key=new Key(world.getWorldConfig().getUuid(),chunk.getIndex(),job.getEnvironmentIndex(),
                        job.getSpawnConfigIndex()+"/"+job.getRoleIndex()+"/"+tag+"/"+System.identityHashCode(builder)+"/"+System.identityHashCode(blocks));
                Retry retry=null;boolean cached=false,backoff=false;
                boolean complete=assessment.outcome()==Outcome.KNOWN_UNSUITABLE
                        ||assessment.reason().equals("NATIVE_CANDIDATES_REJECTED")&&stable[0]&&!probeLimited[0];
                if(complete){
                    synchronized(retries){
                        if(retries.size()>=MAX_ENTRIES&&!retries.containsKey(key))retries.remove(retries.keySet().iterator().next());
                        retry=retries.computeIfAbsent(key,k->new Retry());
                        long signature=assessment.signature()+(stable[0]?versions[0]:0);
                        cached=retry.signature==signature;backoff=!retry.admit(signature,started);
                    }
                }else retries.remove(key);
                // UNKNOWN data is never proof of impossibility. A finite completed-job backoff is separately labeled.
                boolean rejected=assessment.outcome()==Outcome.KNOWN_UNSUITABLE||backoff;
                tickets.put(job,new Ticket(key,retry,rejected));
                MonsterSpawnTrace.event("NATIVE_HABITAT",key.world(),key.environment(),params.getId(),
                        "environmentAsset="+job.getEnvironment().getId()+" chunk="+chunk.getX()+":"+chunk.getZ()
                        +" outcome="+assessment.outcome()+" backoff="+backoff+" reason="+assessment.reason()+" cacheHit="+cached
                        +" work="+assessment.work()+" elapsedMicros="+(System.nanoTime()-started)/1000
                        +" job="+job.getJobId()+" nativeBudgetUsed="+job.getBudgetUsed());
                return rejected;
            }finally{context.releaseFull();}
        }catch(RuntimeException unavailable){
            MonsterSpawnTrace.event("NATIVE_HABITAT_UNKNOWN",store.getExternalData().getWorld().getWorldConfig().getUuid(),
                    job.getEnvironmentIndex(),"unknown","reason="+unavailable.getClass().getSimpleName());return false;
        }
    }
    public final class Completion extends RefChangeSystem<ChunkStore,SpawnJobData> {
        @Override public Query<ChunkStore> getQuery(){return WorldChunk.getComponentType();}
        @Override public ComponentType<ChunkStore,SpawnJobData> componentType(){return SpawnJobData.getComponentType();}
        @Override public void onComponentAdded(Ref<ChunkStore> ref,SpawnJobData value,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){}
        @Override public void onComponentSet(Ref<ChunkStore> ref,SpawnJobData old,SpawnJobData value,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){tickets.remove(old);}
        @Override public void onComponentRemoved(Ref<ChunkStore> ref,SpawnJobData value,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){
            var ticket=tickets.remove(value);if(ticket==null)return;
            if(ticket.retry()!=null&&!ticket.rejected())ticket.retry().completed(value.getSpansSuccess()>0,System.nanoTime());
            MonsterSpawnTrace.event("NATIVE_HABITAT_JOB_COMPLETED",ticket.key().world(),ticket.key().environment(),"all",
                    "job="+value.getJobId()+" nativeSuccesses="+value.getSpansSuccess()+" nativeBudget="+value.getTotalBudgetUsed()
                    +" admissionRejected="+ticket.rejected());
        }
    }
}
