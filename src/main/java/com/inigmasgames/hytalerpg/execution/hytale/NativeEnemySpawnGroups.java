package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.SpawningPlugin;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawnJobSystems;
import com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Captures one real native world-spawn invocation; native flock, placement and population accounting run unchanged. */
public final class NativeEnemySpawnGroups extends WorldSpawnJobSystems.Ticking {
    public record Job(UUID world,int nativeJobId,int roleIndex,String nativeRole,int environment,int spawnConfiguration,int expectedMembers){
        public Job{Objects.requireNonNull(world);Objects.requireNonNull(nativeRole);
            if(nativeRole.isBlank()||expectedMembers<1)throw new IllegalArgumentException("NATIVE_SPAWN_GROUP_SIZE");}
    }
    public record Reservation(UUID world,UUID encounter,long generation){
        public Reservation{Objects.requireNonNull(world);Objects.requireNonNull(encounter);if(generation<0)throw new IllegalArgumentException("SPAWN_RESERVATION_GENERATION");}
    }
    public record Member(UUID entity,String nativeRole,EnemyStaging.State staging){
        public Member{Objects.requireNonNull(entity);Objects.requireNonNull(nativeRole);Objects.requireNonNull(staging);}
    }
    public record Group(Job job,Reservation reservation,List<Member> members,boolean nativeFailed,boolean extensionCreatedFlock){
        public Group{members=List.copyOf(members);}
        public Group(Job job,Reservation reservation,List<Member> members,boolean nativeFailed){
            this(job,reservation,members,nativeFailed,false);
        }
    }
    public interface Owner {
        /** Cheap certified-role/world eligibility only. No IO, actor changes or random promotion here. */
        boolean eligible(Job job);
        /** At most once for this actual spawn, before the first actor enters the native store. */
        Optional<Reservation> reserve(Job job,UUID firstNativeEntity,String nativeRole);
        /** World-thread handoff after the native invocation. Owns durable completion or complete rollback. */
        void captured(Store<EntityStore> store,Group group);
        /** The native job is valid only during this synchronous handoff; never send it to IO. */
        default void captured(Store<EntityStore> store,Group group,SpawnJobData nativeJob){captured(store,group);}
    }
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<TraceObservation> TRACE_ONLY_JOB=new ThreadLocal<>();
    private static final Owner NO_NEW_RESERVATION=new Owner(){
        @Override public boolean eligible(Job job){return false;}
        @Override public Optional<Reservation> reserve(Job job,UUID first,String role){throw new IllegalStateException("ENEMY_ADDITIONAL_RESERVATION_REENTRY");}
        @Override public void captured(Store<EntityStore> store,Group group){throw new IllegalStateException("ENEMY_ADDITIONAL_CAPTURE_REENTRY");}
    };
    private static final class TraceObservation {
        final Job job;
        final Set<UUID> observed=new HashSet<>();
        final Map<String,Integer> categories=new TreeMap<>();
        TraceObservation(Job job){this.job=job;}
        void added(Holder<EntityStore> holder){
            var npc=holder.getComponent(NPCEntity.getComponentType());
            var id=holder.getComponent(UUIDComponent.getComponentType());
            if(npc==null||id==null||npc.getEnvironment()!=job.environment()
                    ||npc.getSpawnConfiguration()!=job.spawnConfiguration()||npc.getSpawnRoleIndex()!=job.roleIndex()
                    ||!observed.add(id.getUuid()))return;
            String category="UNKNOWN";
            var support=holder.getComponent(com.hypixel.hytale.server.npc.role.support.WorldSupport.getComponentType());
            if(support!=null)category=String.valueOf(support.getDefaultPlayerAttitude());
            categories.merge(category,1,Integer::sum);
        }
    }
    private static final class Frame {
        final Job job;final Owner owner;final Store<EntityStore> store;final boolean additional;
        final List<Member> members=new ArrayList<>();final Set<UUID> observed=new HashSet<>();
        final Map<String,Integer> categories=new TreeMap<>();
        Reservation reservation;boolean attempted,overflow;
        Frame(Job job,Owner owner,Store<EntityStore> store,boolean additional){
            this.job=job;this.owner=owner;this.store=store;this.additional=additional;
        }
        void added(Holder<EntityStore> holder,Store<EntityStore> actual){
            if(actual!=store)throw new IllegalStateException("ENEMY_SPAWN_CROSS_WORLD_CAPTURE");
            var npc=holder.getComponent(NPCEntity.getComponentType());
            // Nested/manual spawns with no world-job provenance never join the incoming group.
            if(npc==null||npc.getEnvironment()!=job.environment()||npc.getSpawnConfiguration()!=job.spawnConfiguration()
                    ||npc.getSpawnRoleIndex()!=job.roleIndex())return;
            var id=holder.getComponent(UUIDComponent.getComponentType());if(id==null)throw new IllegalStateException("NATIVE_SPAWN_UUID_MISSING");
            if(!observed.add(id.getUuid()))return;
            if(MonsterSpawnTrace.enabled()){
                String category="UNKNOWN";
                try{
                    var support=holder.getComponent(com.hypixel.hytale.server.npc.role.support.WorldSupport.getComponentType());
                    if(support!=null)category=String.valueOf(support.getDefaultPlayerAttitude());
                }catch(RuntimeException ignored){/* Trace cannot alter a native spawn. */}
                categories.merge(category,1,Integer::sum);
            }
            if(!attempted){
                attempted=true;reservation=owner.reserve(job,id.getUuid(),npc.getRoleName()).orElse(null);
                if(reservation!=null&&!reservation.world().equals(job.world()))throw new IllegalStateException("ENEMY_SPAWN_RESERVATION_WORLD");
            }
            if(reservation==null)return;
            if(members.size()>=8){overflow=true;return;}
            var staged=additional?EnemyStaging.prepareAdditional(holder,reservation.world(),reservation.encounter(),reservation.generation())
                    :EnemyStaging.prepare(holder,reservation.world(),reservation.encounter(),reservation.generation());
            members.add(new Member(id.getUuid(),npc.getRoleName(),staged));
        }
    }
    private final Owner owner;
    // Installed role assets for these three point at Template_Swimming_Passive.
    // Include them even when an older native spawn table omits SpawnFluidTag.
    private static final Set<String> WATER_ONLY_NATIVE_ROLES=Set.of("Frostgill","Snapjaw","Trilobite");
    private static String region(SpawnJobData job){
        try{
            var context=job.getSpawningContext();
            if(context==null||!Double.isFinite(context.xSpawn)||!Double.isFinite(context.zSpawn))return "unavailable";
            return ((long)Math.floor(context.xSpawn/64))+":"+((long)Math.floor(context.zSpawn/64));
        }catch(RuntimeException ignored){return "unavailable";}
    }
    public NativeEnemySpawnGroups(SpawningPlugin spawning,Owner owner){
        super(spawning.getWorldSpawnDataResourceType(),spawning.getSpawnSuppressionControllerResourceType(),
                spawning.getSpawnJobDataComponentType(),spawning.getChunkSpawnDataComponentType(),spawning.getChunkSpawnedNPCDataComponentType());
        this.owner=Objects.requireNonNull(owner);
    }
    /** An absent/unloaded section is unknown, never proof that a native fluid placement is impossible. */
    static boolean definitelyDry(int sections,IntFunction<Boolean> sectionContainsFluid){
        for(int section=0;section<sections;section++){
            var containsFluid=sectionContainsFluid.apply(section);
            if(containsFluid==null||containsFluid)return false;
        }
        return sections>0;
    }
    private static boolean definitelyDry(ArchetypeChunk<ChunkStore> archetype,int index,Store<ChunkStore> store){
        var worldChunk=archetype.getComponent(index,WorldChunk.getComponentType());
        if(worldChunk==null)return false;
        var chunks=store.getExternalData().getWorld().getChunkStore();
        return definitelyDry(ChunkUtil.HEIGHT_SECTIONS,section->{
            var ref=chunks.getChunkSectionReference(worldChunk.getX(),ChunkUtil.MIN_SECTION+section,worldChunk.getZ());
            if(ref==null||!ref.isValid())return null;
            var fluids=store.getComponent(ref,FluidSection.getComponentType());
            return fluids!=null&&!fluids.isEmpty();
        });
    }
    @Override public void tick(float dt,int index,ArchetypeChunk<ChunkStore> chunk,Store<ChunkStore> store,CommandBuffer<ChunkStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enterChunk(store,
                com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.LIFECYCLE)){
        var data=chunk.getComponent(index,SpawnJobData.getComponentType());
        if(data==null||data.getFlockSize()<1){super.tick(dt,index,chunk,store,buffer);return;}
        var role=data.getSpawnConfig()==null?null:data.getSpawnConfig().getRoles().get(data.getRoleIndex());
        if(role==null||role.getId()==null||role.getId().isBlank()){super.tick(dt,index,chunk,store,buffer);return;}
        var world=store.getExternalData().getWorld();
        var job=new Job(world.getWorldConfig().getUuid(),data.getJobId(),data.getRoleIndex(),role.getId(),data.getEnvironmentIndex(),data.getSpawnConfigIndex(),data.getFlockSize());
        // The native spawn table owns fluid eligibility. If every loaded section of this
        // job's column is fluid-free, no column probe can satisfy that table's fluid tag.
        // Terminate through native Ticking so its failed-job and population accounting run.
        int fluidTag=data.getSpawnConfig().getSpawnFluidTag(data.getRoleIndex());
        if(!data.isTerminated()&&(fluidTag!=Integer.MIN_VALUE||WATER_ONLY_NATIVE_ROLES.contains(job.nativeRole()))
                &&definitelyDry(chunk,index,store)){
            MonsterSpawnTrace.event("NATIVE_FLUID_JOB_DRY_COLUMN",job.world(),job.environment(),job.nativeRole(),
                    "job="+job.nativeJobId()+" fluidTag="+fluidTag);
            data.terminate();
            super.tick(dt,index,chunk,store,buffer);
            return;
        }
        if(MonsterSpawnTrace.enabled()){
            String population="unavailable";
            try{
                var nativePopulation=world.getEntityStore().getStore().getResource(WorldSpawnData.getResourceType());
                var environment=nativePopulation==null?null:nativePopulation.getWorldEnvironmentSpawnData(job.environment());
                var roleStat=environment==null?null:environment.getNpcStatMap().get(job.roleIndex());
                if(nativePopulation!=null)population="worldActual="+nativePopulation.getActualNPCs()
                        +" worldExpected="+nativePopulation.getExpectedNPCs()+" activeJobs="+nativePopulation.getActiveSpawnJobs()
                        +" jobBudgetUsed="+data.getBudgetUsed()+" envActual="+(environment==null?"unavailable":environment.getActualNPCs())
                        +" envExpected="+(environment==null?"unavailable":environment.getExpectedNPCs())
                        +" roleActual="+(roleStat==null?"unavailable":roleStat.getActual())
                        +" roleExpected="+(roleStat==null?"unavailable":roleStat.getExpected())
                        +" roleAvailableSlots="+(roleStat==null?"unavailable":roleStat.getAvailableSlots());
            }catch(RuntimeException ignored){/* Native diagnostics may be unavailable during world changes. */}
            MonsterSpawnTrace.job(job.world(),job.environment(),job.nativeRole(),job.nativeJobId(),
                    "requestedFlock="+job.expectedMembers()+" spawnConfig="+job.spawnConfiguration()+" "+population);
        }
        boolean eligible=job.expectedMembers()<=8&&owner.eligible(job);
        if(MonsterSpawnTrace.enabled())MonsterSpawnTrace.event("ELITE_CAPTURE_ELIGIBILITY",job.world(),job.environment(),job.nativeRole(),
                "job="+job.nativeJobId()+" eligible="+eligible+" requested="+job.expectedMembers());
        if(!eligible){
            if(!MonsterSpawnTrace.enabled()){super.tick(dt,index,chunk,store,buffer);return;}
            var observation=new TraceObservation(job);var previousObservation=TRACE_ONLY_JOB.get();TRACE_ONLY_JOB.set(observation);
            var prior=new HashMap<String,Integer>();
            try{data.getRejectionMap().forEach((reason,count)->prior.put(String.valueOf(reason),count));}
            catch(RuntimeException ignored){/* Diagnostic state may be unavailable. */}
            try{super.tick(dt,index,chunk,store,buffer);}
            finally{
                if(previousObservation==null)TRACE_ONLY_JOB.remove();else TRACE_ONLY_JOB.set(previousObservation);
                try{MonsterSpawnTrace.event("NATIVE_JOB_TICK_RESULT",job.world(),job.environment(),job.nativeRole(),
                        "job="+job.nativeJobId()+" requested="+job.expectedMembers()+" actual="+observation.observed.size()
                        +" categories="+observation.categories+" staged=0 reserved=false budgetUsed="+data.getBudgetUsed()
                        +" region64="+region(data));}
                catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
                try{data.getRejectionMap().forEach((reason,count)->{
                    int delta=count-prior.getOrDefault(String.valueOf(reason),0);
                    if(delta>0)MonsterSpawnTrace.count("NATIVE_REJECTION_"+reason,job.world(),job.environment(),job.nativeRole(),
                            delta,"job="+job.nativeJobId()+" count="+delta);
                });}catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
            }
            return;
        }
        capture(job,owner,world.getEntityStore().getStore(),()->super.tick(dt,index,chunk,store,buffer),data);
        }
    }
    /** The supplied operation is the original native job invocation, not a reconstructed spawn algorithm. */
    public static void capture(Job job,Owner owner,Store<EntityStore> store,Runnable nativeTick){
        capture(job,owner,store,nativeTick,null);
    }
    private static void capture(Job job,Owner owner,Store<EntityStore> store,Runnable nativeTick,SpawnJobData nativeJob){
        if(!store.isInThread()||job.expectedMembers()>8)throw new IllegalStateException("ENEMY_SPAWN_CAPTURE_BOUNDARY");
        if(CURRENT.get()!=null)throw new IllegalStateException("NESTED_NATIVE_WORLD_SPAWN_JOB");
        var frame=new Frame(job,owner,store,false);CURRENT.set(frame);
        var priorRejections=new HashMap<String,Integer>();
        if(nativeJob!=null&&MonsterSpawnTrace.enabled())try{
            nativeJob.getRejectionMap().forEach((reason,count)->priorRejections.put(String.valueOf(reason),count));
        }catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
        Throwable failure=null;
        try{nativeTick.run();}
        catch(RuntimeException|Error error){failure=error;throw error;}
        finally{
            CURRENT.remove();
            if(MonsterSpawnTrace.enabled())try{
                MonsterSpawnTrace.event("NATIVE_JOB_TICK_RESULT",job.world(),job.environment(),job.nativeRole(),
                        "job="+job.nativeJobId()+" requested="+job.expectedMembers()+" actual="+frame.observed.size()
                        +" categories="+frame.categories+" staged="+frame.members.size()+" reserved="+(frame.reservation!=null)
                        +" budgetUsed="+(nativeJob==null?"unknown":nativeJob.getBudgetUsed())
                        +" region64="+(nativeJob==null?"unavailable":region(nativeJob))
                        +" rejections="+(nativeJob==null?"unknown":nativeJob.getRejectionMap())
                        +" nativeError="+(failure==null?"none":failure.getClass().getSimpleName()));
            }catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
            if(nativeJob!=null&&MonsterSpawnTrace.enabled())try{
                nativeJob.getRejectionMap().forEach((reason,count)->{
                    int delta=count-priorRejections.getOrDefault(String.valueOf(reason),0);
                    if(delta>0)MonsterSpawnTrace.count("NATIVE_REJECTION_"+reason,job.world(),job.environment(),job.nativeRole(),
                            delta,"job="+job.nativeJobId()+" count="+delta);
                });
            }catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
            if(frame.reservation!=null){
                try{owner.captured(frame.store,new Group(job,frame.reservation,frame.members,failure!=null||frame.overflow),nativeJob);}
                catch(RuntimeException|Error handoff){if(failure!=null)failure.addSuppressed(handoff);else throw handoff;}
            }
        }
    }
    /** Capture a native extension of the same flock, never minting a second pack reservation. */
    public static void captureAdditional(Job job,Reservation reservation,Store<EntityStore> store,
            Runnable nativeAddition,Consumer<Group> captured){
        Objects.requireNonNull(job);Objects.requireNonNull(reservation);Objects.requireNonNull(nativeAddition);Objects.requireNonNull(captured);
        if(!store.isInThread()||CURRENT.get()!=null||job.expectedMembers()>8
                ||!job.world().equals(reservation.world()))throw new IllegalStateException("ENEMY_ADDITIONAL_CAPTURE_BOUNDARY");
        var frame=new Frame(job,NO_NEW_RESERVATION,store,true);frame.reservation=reservation;frame.attempted=true;
        CURRENT.set(frame);Throwable failure=null;
        try{nativeAddition.run();}
        catch(RuntimeException|Error error){failure=error;throw error;}
        finally{
            CURRENT.remove();
            try{captured.accept(new Group(job,reservation,frame.members,failure!=null||frame.overflow));}
            catch(RuntimeException|Error handoff){if(failure!=null)failure.addSuppressed(handoff);else throw handoff;}
        }
    }
    public NativeSystemReplacement<ChunkStore> install(ComponentRegistry<ChunkStore> registry){
        return NativeSystemReplacement.install(registry,WorldSpawnJobSystems.Ticking.class,NativeEnemySpawnGroups.class,this,"WORLD_SPAWN_GROUP");
    }
    public static final class Capture extends HolderSystem<EntityStore>{
        @Override public Query<EntityStore> getQuery(){return NPCEntity.getComponentType();}
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){
            var frame=CURRENT.get();if(frame!=null&&reason==AddReason.SPAWN)frame.added(holder,store);
            var observation=TRACE_ONLY_JOB.get();
            if(observation!=null&&reason==AddReason.SPAWN)try{observation.added(holder);}
            catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
            if(MonsterSpawnTrace.enabled()&&reason==AddReason.SPAWN)try{
                var npc=holder.getComponent(NPCEntity.getComponentType());
                var id=holder.getComponent(UUIDComponent.getComponentType());
                if(npc!=null)MonsterSpawnTrace.event(frame!=null||observation!=null?"NPC_ADDED_NATIVE_JOB":"NPC_ADDED_OTHER",
                        store.getExternalData().getWorld().getWorldConfig().getUuid(),npc.getEnvironment(),npc.getRoleName(),
                        "entity="+(id==null?"unknown":id.getUuid())+" spawnConfig="+npc.getSpawnConfiguration()
                        +" job="+(frame!=null?frame.job.nativeJobId():observation!=null?observation.job.nativeJobId():"none")
                        +" addReason="+reason);
            }catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
        }
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){
            if(!MonsterSpawnTrace.enabled())return;
            try{
            var npc=holder.getComponent(NPCEntity.getComponentType());
            var id=holder.getComponent(UUIDComponent.getComponentType());
            if(npc!=null)MonsterSpawnTrace.event("NPC_REMOVED",store.getExternalData().getWorld().getWorldConfig().getUuid(),
                    npc.getEnvironment(),npc.getRoleName(),"entity="+(id==null?"unknown":id.getUuid())
                            +" spawnConfig="+npc.getSpawnConfiguration()+" reason="+reason);
            }catch(RuntimeException ignored){/* Diagnostics cannot affect native execution. */}
        }
    }
}
