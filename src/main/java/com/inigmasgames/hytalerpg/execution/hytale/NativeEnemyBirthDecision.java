package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** World-thread decision over the actual staged native roster; no IO or actor publication. */
public final class NativeEnemyBirthDecision implements AutoCloseable {
    /** Supplied by the exact role/spawn certificate owner, never by a name or role-catalog guess. */
    public record V2Capture(EnemyBirthPlanner.Request request,Me12Planner.Context context,Me12Policies policies,
                            EnemyNativeBindings.Role role,List<EnemyNativeGroupPreparation.MemberSource> sources,
                            NativeEnemySpawnGroups.Group reserved,List<EnemyNativeGroupPreparation.MemberSource> reservedSources){
        public V2Capture(EnemyBirthPlanner.Request request,Me12Planner.Context context,Me12Policies policies,
                         EnemyNativeBindings.Role role,List<EnemyNativeGroupPreparation.MemberSource> sources){
            this(request,context,policies,role,sources,null,List.of());
        }
        public V2Capture{Objects.requireNonNull(request);Objects.requireNonNull(context);Objects.requireNonNull(policies);Objects.requireNonNull(role);sources=List.copyOf(sources);
            reservedSources=List.copyOf(reservedSources);
            if(!request.nativePlanReceipt().equals(context.sourceReceipt())||request.originals().size()!=sources.size()
                    ||(reserved==null)!=reservedSources.isEmpty()
                    ||reserved!=null&&reserved.members().size()!=reservedSources.size())throw new IllegalArgumentException("ME12_CAPTURE_SOURCE_MISMATCH");
            if(reserved!=null){
                var leader=request.originals().getFirst().nativeBaseline();
                if(!reserved.reservation().world().equals(leader.worldId())
                        ||!reserved.reservation().encounter().equals(leader.encounterId())
                        ||reserved.reservation().generation()!=leader.encounterGeneration())
                    throw new IllegalArgumentException("ME12_CAPTURE_RESERVED_BIRTH_MISMATCH");
            }
            if(reserved!=null)for(int i=0;i<reserved.members().size();i++){
                var member=reserved.members().get(i);var source=reservedSources.get(i).spawn();
                if(!source.enemy().equals(member.entity())||!source.roleId().equals(member.nativeRole()))
                    throw new IllegalArgumentException("ME12_CAPTURE_RESERVED_ROLE_MISMATCH");
            }
        }
    }
    @FunctionalInterface public interface V2ContextSource {
        V2Capture capture(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,SpawnJobData job);
    }
    private final Me12PlannerCache v2Cache=new Me12PlannerCache();
    private final Map<UUID,Object> v2Lifetimes=new HashMap<>();
    private final Map<UUID,Long> v2Generations=new HashMap<>();
    private long nextV2Generation;
    private volatile java.util.function.BooleanSupplier nativeHooksReady=()->false;
    private volatile V2ContextSource v2ContextSource=this::captureProduction;
    public void configureNativeHooks(java.util.function.BooleanSupplier ready){nativeHooksReady=Objects.requireNonNull(ready);}
    /** Installation cannot bypass the feature or evidence gates. */
    public synchronized void configureV2ContextSource(V2ContextSource source){
        if(!v2Lifetimes.isEmpty())throw new IllegalStateException("ME12_CONTEXT_SOURCE_ALREADY_IN_USE");v2ContextSource=Objects.requireNonNull(source);
    }
    private synchronized long v2World(UUID world,Object lifetime){
        if(lifetime==null)throw new IllegalStateException("ME12_WORLD_NOT_ADMITTED");
        if(v2Lifetimes.get(world)!=lifetime){long generation=++nextV2Generation;v2Cache.replaceWorld(world,generation);v2Lifetimes.put(world,lifetime);v2Generations.put(world,generation);}
        return v2Generations.get(world);
    }
    public synchronized void worldUnload(UUID world){v2Cache.forgetWorld(world);v2Lifetimes.remove(world);v2Generations.remove(world);}
    @Override public synchronized void close(){v2Cache.close();v2Lifetimes.clear();v2Generations.clear();}
    public Me12PlannerCache.Metrics v2Metrics(){return v2Cache.metrics();}
    /** Real native owner capture. Action graph validation is API evidence, not connected acceptance. */
    public V2Capture captureProduction(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,SpawnJobData job){
        if(!store.isInThread()||admission==null||!admission.admits(original.job().world())||!nativeHooksReady.getAsBoolean())
            throw new IllegalStateException("ME12_NATIVE_OWNERS_UNAVAILABLE");
        if(job.getJobId()!=original.job().nativeJobId()||job.getRoleIndex()!=original.job().roleIndex()
                ||job.getEnvironmentIndex()!=original.job().environment()||job.getSpawnConfigIndex()!=original.job().spawnConfiguration())throw new IllegalStateException("ME12_NATIVE_JOB_CHANGED");
        var policy=currentPolicy();var role=bindings.role(original.job().nativeRole()).orElseThrow(()->new IllegalStateException("ME12_UNKNOWN_ROLE_DEPENDENCY"));
        var sources=classify(store,original,role).orElseThrow(()->new IllegalStateException("ME12_NATIVE_CLASSIFICATION"));
        for(var member:original.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            var npc=ref==null||!ref.isValid()?null:store.getComponent(ref,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
            var bound=bindings.role(member.nativeRole()).orElseThrow();
            var certified=bound.certify(bindings.revision(),npc);
            if(certified.isEmpty()&&!EnemyIncomingOnlyAttachment.certified(bound))throw new IllegalStateException("ME12_NATIVE_ACTION_CERTIFICATE_MISSING");
        }
        var ref=store.getExternalData().getRefFromUUID(original.members().getFirst().entity());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(transform==null)throw new IllegalStateException("ME12_NATIVE_TRANSFORM");
        var pos=transform.getPosition();
        var request=EnemyNativeGroupPreparation.prepare(original,sources,bindings::role,new Vec3(pos.x,pos.y,pos.z),policy.balance(),bindings.revision(),true).orElseThrow(()->new IllegalStateException("ME12_NATIVE_PREPARATION"));
        if(planner(policy).rarity(request.seed(),request.originals().getFirst().nativeBaseline().difficulty())!=EnemyRarity.UNIQUE)throw new IllegalStateException("ME12_NOT_PROCEDURAL_UNIQUE");
        var npc=store.getComponent(ref,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
        int[] variants=npc.getRole().getFlockSpawnTypes();
        var population=store.getResource(com.hypixel.hytale.server.spawning.world.component.WorldSpawnData.getResourceType());
        if(population==null)throw new IllegalStateException("ME12_NATIVE_POPULATION_MISSING");
        var headroom=NativeEnemyFlockExtension.headroom(store,pos,population,original.job().environment()).orElseThrow(()->new IllegalStateException("ME12_NATIVE_HEADROOM_MISSING"));
        int capacity=0;while(capacity<8-original.members().size()&&headroom.admits(capacity+1))capacity++;
        var promotion=policy.balance().promotion();
        int base=Math.max(Math.min(original.members().size()-1,promotion.minionMaximum()),new com.inigmasgames.hytalerpg.gear.GearRandom("master-enemies-v1.1/"+policy.balance().revision()+"/"+request.seed()).stream("me.minion-count").nextInt(promotion.minionMinimum(),promotion.minionMaximum()+1));
        if(1+base<original.members().size()||1+base>original.members().size()+capacity)throw new IllegalStateException("ME12_NATIVE_MINION_CAPACITY");
        var docs=new LinkedHashMap<String,String>();Me12Policies.canonical().documents().forEach(d->docs.put(d.reference().asset(),d.utf8()));
        docs.put("master-enemies-v1.json",policy.balance().export().toString());var policies=Me12Policies.load(docs);
        var originals=request.originals();var members=new ArrayList<Me12Planner.Member>();
        int offered=1+base+(original.members().size()+capacity>=3+base?2:0);
        NativeEnemySpawnGroups.Group reserved=null;
        List<EnemyNativeGroupPreparation.MemberSource> reservedSources=List.of();
        List<EnemyBirthPlanner.Candidate> additions=List.of();
        // Native random FlockSpawnTypes cannot be predicted from the leader. A real
        // v2 birth stages the exact native-selected roles before graph compilation.
        boolean production=settings!=null&&settings.get().me12FeatureState().allowsNewV2Births();
        try{
        if(offered>original.members().size()&&production){
            NativeEnemyFlockExtension.Attempt attempt;
            try{attempt=NativeEnemyFlockExtension.attempt(store,original,NativeEnemyFlockExtension.Source.capture(job),offered-original.members().size());}
            catch(RuntimeException|Error uncertain){throw new IllegalStateException("ME12_NATIVE_RESERVATION_UNCERTAIN",uncertain);}
            reserved=attempt.group();
            if(reserved==null)throw new IllegalStateException("ME12_NATIVE_RESERVATION_"+attempt.rejection());
            reservedSources=classify(store,reserved,role).orElseThrow(()->new IllegalStateException("ME12_NATIVE_RESERVED_CLASSIFICATION"));
            for(var member:reserved.members()){
                var ref2=store.getExternalData().getRefFromUUID(member.entity());
                var npc2=ref2==null||!ref2.isValid()?null:store.getComponent(ref2,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                var bound=bindings.role(member.nativeRole()).orElseThrow(()->new IllegalStateException("ME12_NATIVE_RESERVED_ROLE"));
                if(bound.certify(bindings.revision(),npc2).isEmpty()&&!EnemyIncomingOnlyAttachment.certified(bound))
                    throw new IllegalStateException("ME12_NATIVE_RESERVED_ACTION");
            }
            additions=EnemyNativeGroupPreparation.completeAdditional(original,request,reserved,reservedSources,
                    bindings::role,policy.balance(),bindings.revision())
                    .orElseThrow(()->new IllegalStateException("ME12_NATIVE_RESERVED_PREPARATION")).additionalSlots();
        }else if(offered>original.members().size()&&variants!=null
                &&Arrays.stream(variants).anyMatch(index->index!=original.job().roleIndex()))
            throw new IllegalStateException("ME12_NATIVE_MIXED_EXTENSION_DEPENDENCY");
        double guardRadius=policies.registry().require(EnemyAffixRegistry.Operator.PACKBOUND).constant("guardAreaMeters");
        for(int i=0;i<offered;i++){
            var candidate=i<originals.size()?originals.get(i):production?additions.get(i-originals.size()):originals.get(0);
            var source=candidate.nativeBaseline();var b=candidate.binding();
            var binding=new EnemyAffixSelection.Binding(b.revision(),b.capabilities(),b.usableDefense(),b.improvesElementalResistance(),b.nativeStunStaggerImmune(),b.nativeSlowImmune(),base,b.distanceDisplacementDelivery(),b.supportedAffixIds());
            boolean reachable=true;
            if(i<original.members().size()||production&&reserved!=null){
                var member=i<original.members().size()?original.members().get(i):reserved.members().get(i-original.members().size());
                var memberRef=store.getExternalData().getRefFromUUID(member.entity());
                var memberTransform=memberRef==null||!memberRef.isValid()?null:store.getComponent(memberRef,TransformComponent.getComponentType());
                reachable=memberTransform!=null&&pos.distanceSquared(memberTransform.getPosition())<=guardRadius*guardRadius;
            }
            members.add(new Me12Planner.Member(source.canonicalRoleId(),source.sourceValidationId(),binding,source.nativeImmunityChannels(),reachable,source.nativeRoleId()));
        }
        request=new EnemyBirthPlanner.Request(request.seed(),request.packId(),request.anchor(),request.nativePlanReceipt(),originals,List.of(),true,capacity,true);
        var digest=com.inigmasgames.hytalerpg.progress.RewardIntent.digest(bindings.revision()+"/"+bindings.serverJarSha256()+"/"+bindings.assetsSha256());
        var baseRoles=new HashSet<String>();for(int i=0;i<1+base;i++)baseRoles.add(members.get(i).role());
        var horde=members.subList(1+base,offered);
        // The existing Horde contract allows only already approved pack roles.
        // A different native random selection removes Horde from this context,
        // without rejecting an otherwise legal mixed base roster.
        if(horde.stream().anyMatch(member->!baseRoles.contains(member.role())))horde=List.<Me12Planner.Member>of();
        var context=new Me12Planner.Context(digest,request.nativePlanReceipt(),original.reservation().generation(),originals.getFirst().nativeBaseline().difficulty(),
                Me12Planner.Evidence.NATIVE_OWNER_CAPTURE,members.getFirst(),members.subList(1,1+base),horde,Me12Planner.weights(policies.registry(),originals.getFirst().nativeBaseline().difficulty(),policy.weights()));
        return new V2Capture(request,context,policies,role,sources,reserved,reservedSources);
        }catch(RuntimeException failure){
            if(reserved!=null)try{NativeEnemyFlockExtension.discard(store,original,reserved);}
            catch(RuntimeException rollback){failure.addSuppressed(rollback);throw new IllegalStateException("ME12_NATIVE_RESERVATION_ROLLBACK",failure);}
            throw failure;
        }
    }
    /** Nonblocking native entry point. Disabled/DRAIN retain v1; SHADOW cannot acquire a lease or write a root. */
    public java.util.concurrent.CompletionStage<Optional<Selected>> selectAsync(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,SpawnJobData nativeJob){
        var state=settings==null?Me12FeatureState.DISABLED:settings.get().me12FeatureState();state.validateStage2();
        if(!state.evaluatesShadow()&&!state.allowsNewV2Births())return java.util.concurrent.CompletableFuture.completedStage(select(store,original,nativeJob));
        if(!store.isInThread()||admission==null||!admission.admits(original.job().world()))throw new IllegalStateException("ME12_CAPTURE_WORLD_THREAD");
        V2Capture capture;
        try{capture=v2ContextSource.capture(store,original,nativeJob);
            // A successful static graph is explicitly not a native certificate.
            if(capture.context().evidence()==Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE)throw new IllegalStateException("ME12_STATIC_FIXTURE_NOT_NATIVE_CERTIFICATE");
        }catch(RuntimeException blocked){
            if("ME12_NATIVE_RESERVATION_UNCERTAIN".equals(blocked.getMessage())
                    ||"ME12_NATIVE_RESERVATION_ROLLBACK".equals(blocked.getMessage())){
                admission.failClosed(original.job().world());throw blocked;
            }
            if("ME12_NOT_PROCEDURAL_UNIQUE".equals(blocked.getMessage()))return java.util.concurrent.CompletableFuture.completedStage(select(store,original,nativeJob));
            com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.spawn(original.job().world(),original.reservation().encounter(),"NATURAL","SPAWN.ADMISSION","ME12_DEPENDENCY",Map.of("reason",String.valueOf(blocked.getMessage()),"nativeRole",original.job().nativeRole(),"featureMode",state));
            if(state.evaluatesShadow())return java.util.concurrent.CompletableFuture.completedStage(select(store,original,nativeJob));
            return java.util.concurrent.CompletableFuture.completedStage(new Rollback(store,original).fallback("ME12_DEPENDENCY_UNCERTIFIED_PRODUCTION_CONTEXT"));
        }
        UUID worldId=original.job().world();Object lifetime;long generation;
        NativeEnemyFlockExtension.Source frozenJob;
        try{lifetime=admission.lifetime(worldId);generation=v2World(worldId,lifetime);
            frozenJob=NativeEnemyFlockExtension.Source.capture(nativeJob);}
        catch(RuntimeException unavailable){
            var rollback=new Rollback(store,original);rollback.additional=capture.reserved();
            return java.util.concurrent.CompletableFuture.completedStage(state.evaluatesShadow()
                    ?select(store,original,nativeJob):rollback.fallback("ME12_NATIVE_SNAPSHOT_UNAVAILABLE"));
        }
        long requestedAt=System.nanoTime();
        java.util.concurrent.CompletionStage<Me12PlannerCache.Resolved> requested;
        try{requested=v2Cache.requestResolved(worldId,generation,capture.context(),capture.policies(),capture.request().seed());}
        catch(RuntimeException capacity){
            com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.spawn(worldId,original.reservation().encounter(),"NATURAL","SPAWN.ADMISSION","ME12_DEPENDENCY",Map.of("reason",String.valueOf(capacity.getMessage())));
            var rollback=new Rollback(store,original);rollback.additional=capture.reserved();
            return java.util.concurrent.CompletableFuture.completedStage(state.evaluatesShadow()?select(store,original,nativeJob):rollback.fallback("ME12_COMPILER_ADMISSION"));
        }
        if(state.evaluatesShadow()){
            requested.whenComplete((value,error)->{try{com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.spawn(worldId,original.reservation().encounter(),"NATURAL","SPAWN.ADMISSION","ME12_SHADOW",Map.of("result",error==null?"PLAN_ONLY":"BLOCKED","durable",false));}finally{if(value!=null)value.close();}});
            return java.util.concurrent.CompletableFuture.completedStage(select(store,original,nativeJob));
        }
        var result=new java.util.concurrent.CompletableFuture<Optional<Selected>>();admission.watch(worldId,result);
        var world=store.getExternalData().getWorld();
        requested.whenComplete((resolved,error)->{
            if(!admission.current(worldId,lifetime)){if(resolved!=null)resolved.close();return;}
            try{world.execute(()->{
                if(!admission.current(worldId,lifetime)){if(resolved!=null)resolved.close();return;}
                try{var current=world.getEntityStore().getStore();
                    if(error!=null){var rollback=new Rollback(current,original);rollback.additional=capture.reserved();
                        result.complete(rollback.fallback("ME12_COMPILER_DEPENDENCY"));return;}
                    com.inigmasgames.hytalerpg.diagnostics.hro.HroMasterEnemies.planner(worldId,original.reservation().encounter(),resolved,v2Cache.metrics(),System.nanoTime()-requestedAt);
                    result.complete(selectPreparedV2(current,original,frozenJob,capture,resolved));
                }catch(RuntimeException failure){admission.failClosed(worldId);result.completeExceptionally(failure);}finally{if(resolved!=null)resolved.close();}
            });}catch(RuntimeException unavailable){if(resolved!=null)resolved.close();admission.failClosed(worldId);result.completeExceptionally(unavailable);}
        });
        return result.minimalCompletionStage();
    }
    private Optional<Selected> selectPreparedV2(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,NativeEnemyFlockExtension.Source job,V2Capture capture,Me12PlannerCache.Resolved resolved){
        var rollback=new Rollback(store,original);rollback.additional=capture.reserved();
        try{
            if(!store.isInThread()||!admission.admits(original.job().world()))return rollback.fallback("ME12_WORLD_CHANGED");
            if(job.jobId()!=original.job().nativeJobId()||job.roleIndex()!=original.job().roleIndex()
                    ||job.environmentIndex()!=original.job().environment()
                    ||job.spawnConfigIndex()!=original.job().spawnConfiguration())
                return rollback.fallback("ME12_NATIVE_JOB_SNAPSHOT_CHANGED");
            var sources=classify(store,original,capture.role()).orElseThrow(()->new IllegalStateException("ME12_NATIVE_ROSTER_CHANGED"));
            if(!sameSources(capture.sources(),sources))return rollback.fallback("ME12_NATIVE_SOURCE_CHANGED");
            sources=capture.sources(); // Preserve the capture timestamp; reclassification is a later observation.
            var request=EnemyNativeGroupPreparation.prepare(original,sources,bindings::role,capture.request().anchor(),capture.policies().balance(),bindings.revision(),true).orElseThrow();
            if(!request.originals().equals(capture.request().originals())||!request.nativePlanReceipt().equals(capture.request().nativePlanReceipt())
                    ||!request.anchor().equals(capture.request().anchor()))return rollback.fallback("ME12_NATIVE_SOURCE_CHANGED");
            request=new EnemyBirthPlanner.Request(request.seed(),request.packId(),request.anchor(),request.nativePlanReceipt(),request.originals(),List.of(),true,capture.request().additionalNativeCapacity(),true);
            int count=1+capture.context().minions().size()+(resolved.plan().choices().stream().anyMatch(c->c.affixId().equals(EnemyAffixRegistry.Operator.HORDE.id()))?2:0);
            int extra=count-original.members().size();if(extra<0||extra>request.additionalNativeCapacity())return rollback.fallback("ME12_NATIVE_CAPACITY_CHANGED");
            var lease=new EnemyPackCapacity.Reservation(original.job().world(),original.reservation().encounter(),request.packId(),original.reservation().generation(),request.anchor());
            if(admission.reserve(lease).gate()!=EnemyPackCapacity.Gate.RESERVED)return rollback.fallback("ME12_PACK_CAPACITY_CHANGED");rollback.lease=lease;
            var allSources=new ArrayList<>(sources);
            if(capture.reserved()!=null){
                var current=classify(store,capture.reserved(),capture.role()).orElseThrow(()->new IllegalStateException("ME12_RESERVED_ROLE_CHANGED"));
                if(!sameSources(capture.reservedSources(),current))return rollback.fallback("ME12_RESERVED_SOURCE_CHANGED");
                if(extra>capture.reserved().members().size())return rollback.fallback("ME12_RESERVED_CAPACITY_CHANGED");
                rollback.failClosed=true;
                rollback.additional=NativeEnemyFlockExtension.retain(store,original,capture.reserved(),extra);
                rollback.failClosed=false;
                if(extra>0){var additions=capture.reservedSources().subList(0,extra);allSources.addAll(additions);
                    request=EnemyNativeGroupPreparation.completeAdditional(original,request,rollback.additional,additions,
                            bindings::role,capture.policies().balance(),bindings.revision()).orElseThrow();}
            }else if(extra>0)return rollback.fallback("ME12_NATIVE_RESERVATION_MISSING");
            if(resolved.plan().choices().stream().anyMatch(c->c.affixId().equals(EnemyAffixRegistry.Operator.PACKBOUND.id()))){
                var selectedMembers=new ArrayList<>(original.members());if(rollback.additional!=null)selectedMembers.addAll(rollback.additional.members());
                double radius=capture.policies().registry().require(EnemyAffixRegistry.Operator.PACKBOUND).constant("guardAreaMeters");
                var leaderRef=store.getExternalData().getRefFromUUID(selectedMembers.getFirst().entity());
                var leader=leaderRef==null||!leaderRef.isValid()?null:store.getComponent(leaderRef,TransformComponent.getComponentType());
                if(leader==null)return rollback.fallback("ME12_PACKBOUND_POSITION_CHANGED");
                for(var member:selectedMembers){var memberRef=store.getExternalData().getRefFromUUID(member.entity());
                    var position=memberRef==null||!memberRef.isValid()?null:store.getComponent(memberRef,TransformComponent.getComponentType());
                    if(position==null||leader.getPosition().distanceSquared(position.getPosition())>radius*radius)return rollback.fallback("ME12_PACKBOUND_REACHABILITY_CHANGED");
                }
            }
            var finalizer=new EnemyBirthPlanner(capture.policies().balance(),capture.policies().registry(),capture.policies().affinities(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
            var birth=finalizer.planV2Offline(request,resolved.compiled(),resolved.plan());
            for(var actor:birth.actors()){
                var actorRole=bindings.requireActorRole(actor);
                if(EnemyIncomingOnlyAttachment.certified(actorRole)
                        &&!EnemyIncomingOnlyAttachment.accepts(actorRole,actor))
                    return rollback.fallback("ME12_INCOMING_ONLY_PLAN_UNSAFE");
            }
            return Optional.of(new Selected(new EnemyBirthRoot(birth,sources.stream().map(EnemyNativeGroupPreparation.MemberSource::spawn).toList(),allSources.stream().map(EnemyNativeGroupPreparation.MemberSource::spawn).toList()),original,rollback.additional,capture.role(),allSources,lease,capture.policies()));
        }catch(RuntimeException failure){if(!rollback.failClosed){rollback.restore();return Optional.empty();}throw failure;}
    }
    static boolean sameSources(List<EnemyNativeGroupPreparation.MemberSource> frozen,List<EnemyNativeGroupPreparation.MemberSource> current){
        if(frozen.size()!=current.size())return false;
        for(int i=0;i<frozen.size();i++){
            var before=frozen.get(i);var after=current.get(i);var s=after.spawn();
            var normalized=new com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn(s.world(),s.enemy(),s.roleId(),s.combatIdentity(),s.biomeKey(),s.level(),s.rank(),s.rarity(),s.registryProfile(),before.spawn().spawnedAtMillis(),s.milestone(),s.combat(),s.enemyRewards());
            if(!before.nativeName().equals(after.nativeName())||!before.spawn().equals(normalized))return false;
        }return true;
    }
    public record Selected(EnemyBirthRoot root,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group additional,EnemyNativeBindings.Role role,
            List<EnemyNativeGroupPreparation.MemberSource> sources,
            EnemyPackCapacity.Reservation preLease,Me12Policies archivedPolicies){
        public Selected(EnemyBirthRoot root,NativeEnemySpawnGroups.Group original,NativeEnemySpawnGroups.Group additional,EnemyNativeBindings.Role role,
                        List<EnemyNativeGroupPreparation.MemberSource> sources,EnemyPackCapacity.Reservation preLease){this(root,original,additional,role,sources,preLease,null);}
        public Selected{
            Objects.requireNonNull(root);Objects.requireNonNull(original);Objects.requireNonNull(role);
            sources=List.copyOf(sources);
            if(root.plan().schemaVersion()==2&&(archivedPolicies==null||!root.plan().actors().getFirst().v2().policyDigest().equals(archivedPolicies.digest())))throw new IllegalArgumentException("ME12_SELECTED_ARCHIVE_REQUIRED");
            if(sources.size()!=root.plan().actors().size())throw new IllegalArgumentException("ENEMY_BIRTH_SOURCE_ROSTER");
            for(int i=0;i<sources.size();i++)if(!sources.get(i).spawn().enemy().equals(root.plan().actors().get(i).entityId()))
                throw new IllegalArgumentException("ENEMY_BIRTH_SOURCE_ACTOR_ORDER");
            if(preLease!=null&&!preLease.equals(EnemyPackCapacity.Reservation.of(root.plan().pack())))
                throw new IllegalArgumentException("ENEMY_BIRTH_PRELEASE_IDENTITY");
        }
    }
    private final HytaleEncounterRewards rewards;
    private final EnemyNativeBindings bindings;
    private final java.util.function.Supplier<com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration.Snapshot> settings;
    private final EnemyBalance fallbackBalance;
    private final EnemyWorldAdmission admission;
    private volatile PlannerCache plannerCache;
    private record Policy(EnemyBalance balance,EnemyAffixSelection.WeightPolicy weights){}
    private record PlannerCache(String revision,EnemyBirthPlanner planner){}
    public NativeEnemyBirthDecision(HytaleEncounterRewards rewards,EnemyNativeBindings bindings,EnemyBalance balance){
        this(rewards,bindings,balance,null,null);
    }
    public NativeEnemyBirthDecision(HytaleEncounterRewards rewards,EnemyNativeBindings bindings,EnemyBalance balance,
            java.util.function.Supplier<com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration.Snapshot> settings){
        this(rewards,bindings,balance,settings,null);
    }
    public NativeEnemyBirthDecision(HytaleEncounterRewards rewards,EnemyNativeBindings bindings,EnemyBalance balance,
            java.util.function.Supplier<com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration.Snapshot> settings,
            EnemyWorldAdmission admission){
        this.rewards=Objects.requireNonNull(rewards);this.bindings=Objects.requireNonNull(bindings);
        this.fallbackBalance=Objects.requireNonNull(balance);this.settings=settings;this.admission=admission;
    }
    private Policy currentPolicy(){
        if(settings==null)return new Policy(fallbackBalance,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of()));
        var snapshot=settings.get();
        return new Policy(snapshot.enemyBalance(),new EnemyAffixSelection.WeightPolicy(
                snapshot.affixWeightMultipliers(),snapshot.randomAffixesDisabled()));
    }
    private synchronized EnemyBirthPlanner planner(Policy policy){
        var balance=policy.balance();
        var cached=plannerCache;
        if(cached!=null&&cached.revision().equals(balance.revision()))return cached.planner();
        var planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),
                EnemyNamePools.canonical(),EnemyVisualVariants.canonical(),policy.weights());
        plannerCache=new PlannerCache(balance.revision(),planner);
        return planner;
    }
    /** Empty means ordinary native spawn is restored, including any declined native additions. */
    public Optional<Selected> select(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,SpawnJobData nativeJob){
        Objects.requireNonNull(store);Objects.requireNonNull(original);Objects.requireNonNull(nativeJob);
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(original.job().world()))
            throw new IllegalStateException("ENEMY_BIRTH_DECISION_WORLD_THREAD");
        var rollback=new Rollback(store,original);
        try{
            var policy=currentPolicy();var balance=policy.balance();var planner=planner(policy);
            List<EnemyNativeGroupPreparation.MemberSource> additionalSources=List.of();
            var role=bindings.role(original.job().nativeRole()).orElse(null);
            if(role==null||!role.productionPromotionEnabled()||original.nativeFailed()
                    ||original.members().size()!=original.job().expectedMembers())return rollback.fallback("NATIVE_ROSTER_OR_BINDING");
            var sources=classify(store,original,role);
            if(sources.isEmpty())return rollback.fallback("NATIVE_CLASSIFICATION");
            var ref=store.getExternalData().getRefFromUUID(original.members().getFirst().entity());
            var transform=ref==null||!ref.isValid()?null:store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)return rollback.fallback("NATIVE_TRANSFORM");
            var position=transform.getPosition();
            var request=EnemyNativeGroupPreparation.prepare(original,sources.get(),bindings::role,
                    new Vec3(position.x,position.y,position.z),balance,bindings.revision(),true).orElse(null);
            if(request==null)return rollback.fallback("NATIVE_PREPARATION");
            var rarity=planner.rarity(request.seed(),request.originals().getFirst().nativeBaseline().difficulty());
            if(com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.ownerInWorld(original.job().world())!=null)com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.spawn(original.job().world(),original.reservation().encounter(),
                    "NATURAL","SPAWN.ADMISSION","RARITY_DECISION",Map.of("rarity",rarity));
            MonsterSpawnTrace.event("RARITY_DECISION",original.job().world(),original.job().environment(),original.job().nativeRole(),
                    "job="+original.job().nativeJobId()+" encounter="+original.reservation().encounter()+" rarity="+rarity
                    +" originals="+original.members().size());
            if(rarity==EnemyRarity.NORMAL)return rollback.fallback("RARITY_NORMAL");
            var need=rarity==EnemyRarity.CHAMPION?planner.additionalChampionMembers(request):planner.additionalUniqueMembers(request);
            if(need.isEmpty())return rollback.fallback("ELITE_DEMAND_OR_CAPACITY");
            if(admission!=null){
                var lease=new EnemyPackCapacity.Reservation(original.job().world(),original.reservation().encounter(),
                        request.packId(),original.reservation().generation(),request.anchor());
                var capacity=admission.reserve(lease);
                if(capacity.gate()!=EnemyPackCapacity.Gate.RESERVED)return rollback.fallback("PACK_"+capacity.gate());
                rollback.lease=lease;
            }
            if(need.getAsInt()>0){
                rollback.failClosed=true;
                var extension=NativeEnemyFlockExtension.attempt(store,original,nativeJob,need.getAsInt());
                rollback.additional=extension.group();
                rollback.failClosed=false;
                if(rollback.additional==null){
                    MonsterSpawnTrace.event("NATIVE_EXTENSION_REJECTED",original.job().world(),original.job().environment(),
                            original.job().nativeRole(),"job="+original.job().nativeJobId()
                            +" encounter="+original.reservation().encounter()+" subreason="+extension.rejection());
                    return rollback.fallback("NATIVE_EXTENSION_UNAVAILABLE",extension.rejection());
                }
                var addedSources=classify(store,rollback.additional,role);
                if(addedSources.isEmpty())return rollback.fallback("EXTENSION_CLASSIFICATION");
                additionalSources=addedSources.get();
                request=EnemyNativeGroupPreparation.completeAdditional(original,request,rollback.additional,addedSources.get(),
                        bindings::role,balance,bindings.revision()).orElse(null);
                if(request==null)return rollback.fallback("EXTENSION_PREPARATION");
            }
            var result=planner.plan(request);
            MonsterSpawnTrace.event("ELITE_PLAN_RESULT",original.job().world(),original.job().environment(),original.job().nativeRole(),
                    "job="+original.job().nativeJobId()+" encounter="+original.reservation().encounter()
                    +" promoted="+result.promoted()+" reason="+result.fallbackReason()
                    +" actors="+result.plan().actors().size());
            if(!result.promoted()||result.plan().actors().size()!=original.members().size()
                    +(rollback.additional==null?0:rollback.additional.members().size()))return rollback.fallback(
                            result.fallbackReason()==null?"PLAN_ROSTER":result.fallbackReason());
            for(var actor:result.plan().actors())bindings.requireActorRole(actor);
            var allSources=new ArrayList<>(sources.get());allSources.addAll(additionalSources);
            var root=new EnemyBirthRoot(result.plan(),sources.get().stream()
                    .map(EnemyNativeGroupPreparation.MemberSource::spawn).toList(),allSources.stream()
                    .map(EnemyNativeGroupPreparation.MemberSource::spawn).toList());
            return Optional.of(new Selected(root,original,rollback.additional,role,allSources,rollback.lease));
        }catch(RuntimeException failure){
            if(!rollback.started&&!rollback.failClosed){
                // No durable root exists at this decision boundary. A locally
                // rejected extension/planning pass is an ordinary native spawn.
                rollback.restore();
                MonsterSpawnTrace.event("ELITE_FALLBACK_EXCEPTION",original.job().world(),original.job().environment(),original.job().nativeRole(),
                        "job="+original.job().nativeJobId()+" encounter="+original.reservation().encounter()
                        +" error="+failure.getClass().getSimpleName()+" detail="+failure.getMessage());
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_BIRTH_DECLINED world=%s reason=%s nativeGroupRestored=true",
                        original.job().world(),String.valueOf(failure.getMessage()));
                return Optional.empty();
            }
            throw failure;
        }
    }
    /** The staged actors are command-spawned native NPCs; no world-spawn job is claimed. */
    public EnemyBirthPlan preflightQa(UUID world,UUID encounter,com.inigmasgames.hytalerpg.execution.math.Vec3 anchor,
            List<com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn> frozenSources,EnemyQaSpawnRequest qa){
        var policy=currentPolicy();var balance=policy.balance();var planner=planner(policy);
        var role=bindings.qaRole(qa.nativeRoleId()).orElseThrow(()->new IllegalArgumentException(
                "QA_ROLE_NOT_BOUND:"+qa.nativeRoleId()));
        String seed="qa/"+encounter,cycle="qa-command/"+encounter;
        var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(var source:frozenSources)candidates.add(role.qaBaseline(source,encounter,1,cycle,
                qa.nativeRoleId().replace('_',' '),balance,bindings.revision()));
        var request=new EnemyBirthPlanner.Request(seed,
                UUID.nameUUIDFromBytes(("me.pack/"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                anchor,"qa-command/"+seed,candidates,List.of(),true,0,true);
        return planner.planQa(request,qa);
    }
    public Selected selectQa(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,EnemyQaSpawnRequest qa){
        return selectQa(store,group,qa,null);
    }
    public Selected selectQa(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,EnemyQaSpawnRequest qa,
            Map<UUID,com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn> frozenProfiles){
        var policy=currentPolicy();var balance=policy.balance();var planner=planner(policy);
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(group.job().world())
                ||group.nativeFailed()||group.members().isEmpty()||group.members().size()!=group.job().expectedMembers()
                ||!group.job().nativeRole().equals(qa.nativeRoleId()))
            throw new IllegalArgumentException("QA_NATIVE_ROSTER_INCOMPLETE");
        var role=bindings.qaRole(qa.nativeRoleId()).orElseThrow(()->new IllegalArgumentException(
                "QA_ROLE_NOT_BOUND:"+qa.nativeRoleId()));
        var sources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
        var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        String cycle="qa-command/"+group.reservation().encounter();
        for(var member:group.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            var nativeSupport=ref==null||!ref.isValid()?null:
                    store.getComponent(ref,com.hypixel.hytale.server.npc.role.support.WorldSupport.getComponentType());
            if(nativeSupport==null||nativeSupport.getDefaultPlayerAttitude()
                    !=com.hypixel.hytale.server.core.asset.type.attitude.Attitude.HOSTILE)
                throw new IllegalArgumentException("QA_NATIVE_ROLE_NOT_HOSTILE:"+member.nativeRole());
            Optional<EnemyNativeGroupPreparation.MemberSource> source;
            if(frozenProfiles==null)source=ref==null||!ref.isValid()
                    ?Optional.empty():rewards.classifyStagedQa(store,ref,role);
            else {
                var profile=frozenProfiles.get(member.entity());
                var npc=ref==null||!ref.isValid()?null:store.getComponent(ref,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                var qaMarker=ref==null||!ref.isValid()?null:store.getComponent(ref,QaTransientMarker.getComponentType());
                if(profile==null||npc==null||qaMarker==null||!qaMarker.actor().equals(member.entity())
                        ||!profile.enemy().equals(member.entity())||!profile.world().equals(group.job().world())
                        ||!profile.roleId().equals(member.nativeRole()))source=Optional.empty();
                else source=Optional.of(new EnemyNativeGroupPreparation.MemberSource(profile,
                        HytaleDifficultyCombat.nativeDisplayName(store,ref,npc)));
            }
            if(source.isEmpty())throw new IllegalArgumentException("QA_NATIVE_PROFILE_UNAVAILABLE:"+member.nativeRole());
            sources.add(source.get());
            candidates.add(role.qaBaseline(source.get().spawn(),group.reservation().encounter(),group.reservation().generation(),
                    cycle,source.get().nativeName(),balance,bindings.revision()));
        }
        var ref=store.getExternalData().getRefFromUUID(group.members().getFirst().entity());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(transform==null)throw new IllegalArgumentException("QA_NATIVE_POSITION_UNAVAILABLE");
        var pos=transform.getPosition();String seed="qa/"+group.reservation().encounter();
        var request=new EnemyBirthPlanner.Request(seed,UUID.nameUUIDFromBytes(("me.pack/"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new Vec3(pos.x,pos.y,pos.z),"qa-command/"+seed,candidates,List.of(),true,0,true);
        var plan=planner.planQa(request,qa);
        if(plan.actors().size()<group.members().size()){
            for(var unused:group.members().subList(plan.actors().size(),group.members().size())){
                var extra=store.getExternalData().getRefFromUUID(unused.entity());
                var marker=extra==null||!extra.isValid()?null:store.getComponent(extra,EnemyStaging.getComponentType());
                if(marker==null||!marker.state().equals(unused.staging()))
                    throw new IllegalStateException("QA_UNUSED_NATIVE_MEMBER_CHANGED");
                store.removeEntity(extra,RemoveReason.REMOVE);
            }
            int count=plan.actors().size();
            var job=group.job();
            group=new NativeEnemySpawnGroups.Group(new NativeEnemySpawnGroups.Job(job.world(),job.nativeJobId(),
                    job.roleIndex(),job.nativeRole(),job.environment(),job.spawnConfiguration(),count),
                    group.reservation(),group.members().subList(0,count),false);
            sources=new ArrayList<>(sources.subList(0,count));
        }
        var savedSources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
        for(int i=0;i<sources.size();i++){
            var source=sources.get(i).spawn();var actor=plan.actors().get(i);
            var qaSpawn=new com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),
                    source.combatIdentity(),source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),
                    source.spawnedAtMillis(),null,source.combat(),actor.immutableRewardContext());
            savedSources.add(new EnemyNativeGroupPreparation.MemberSource(qaSpawn,sources.get(i).nativeName()));
        }
        var root=new EnemyBirthRoot(plan,savedSources.stream().map(EnemyNativeGroupPreparation.MemberSource::spawn).toList());
        return new Selected(root,group,null,role,savedSources,null);
    }
    /** Capacity denial or a pre-root persistence rejection also returns the actual native group. */
    public void restore(Store<EntityStore> store,Selected selected){
        restore(store,selected.original(),selected.additional());
    }
    private final class Rollback {
        final Store<EntityStore> store;final NativeEnemySpawnGroups.Group original;
        NativeEnemySpawnGroups.Group additional;EnemyPackCapacity.Reservation lease;boolean started,failClosed;
        Rollback(Store<EntityStore> store,NativeEnemySpawnGroups.Group original){this.store=store;this.original=original;}
        Optional<Selected> fallback(String reason){
            return fallback(reason,null);
        }
        Optional<Selected> fallback(String reason,String subreason){
            if(!reason.equals("RARITY_NORMAL")&&com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.ownerInWorld(original.job().world())!=null)com.inigmasgames.hytalerpg.diagnostics.hro.HroRuntime.spawn(original.job().world(),original.reservation().encounter(),
                    "NATURAL","SPAWN.ADMISSION","FALLBACK",Map.of("reason",reason));
            MonsterSpawnTrace.event("ELITE_FALLBACK",original.job().world(),original.job().environment(),original.job().nativeRole(),
                    "job="+original.job().nativeJobId()+" encounter="+original.reservation().encounter()+" reason="+reason
                    +(subreason==null?"":" subreason="+subreason));
            restore();return Optional.empty();
        }
        void restore(){
            if(started)throw new IllegalStateException("ENEMY_BIRTH_ROLLBACK_REPEATED");
            started=true;
            try{NativeEnemyBirthDecision.this.restore(store,original,additional);}
            finally{if(lease!=null)admission.releaseUnsealed(lease);}
        }
    }
    private void restore(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group additional){
        if(additional!=null)NativeEnemyFlockExtension.discard(store,original,additional);
        if(!original.members().isEmpty())rewards.releasePreRootNativeGroup(store,original.members().stream()
                .map(NativeEnemySpawnGroups.Member::staging).toList());
        if(MonsterSpawnTrace.enabled())try{
            int alive=0,staged=0;
            for(var member:original.members()){
                var ref=store.getExternalData().getRefFromUUID(member.entity());
                if(ref!=null&&ref.isValid()){
                    alive++;
                    if(store.getComponent(ref,EnemyStaging.getComponentType())!=null)staged++;
                }
            }
            MonsterSpawnTrace.event("ORIGINAL_GROUP_RESTORED",original.job().world(),original.job().environment(),original.job().nativeRole(),
                    "job="+original.job().nativeJobId()+" encounter="+original.reservation().encounter()
                    +" originals="+original.members().size()+" alive="+alive+" stillStaged="+staged
                    +" additionalDiscarded="+(additional==null?0:additional.members().size()));
        }catch(RuntimeException ignored){/* Diagnostics cannot affect native rollback. */}
    }
    private Optional<List<EnemyNativeGroupPreparation.MemberSource>> classify(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group group,EnemyNativeBindings.Role role){
        var result=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
        for(var member:group.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            if(ref==null||!ref.isValid()){
                MonsterSpawnTrace.event("NATIVE_CLASSIFY_REJECT",group.job().world(),group.job().environment(),
                        member.nativeRole(),"reason=memberUnavailable");
                return Optional.empty();
            }
            // Native FlockPlugin may choose a different authored FlockSpawnType for
            // a member. The exact job provenance is captured by NativeEnemySpawnGroups;
            // classify each concrete role through its own certified binding.
            var memberRole=bindings.role(member.nativeRole()).orElse(null);
            if(memberRole==null||!memberRole.productionPromotionEnabled()){
                MonsterSpawnTrace.event("NATIVE_CLASSIFY_REJECT",group.job().world(),group.job().environment(),
                        member.nativeRole(),"reason="+(memberRole==null?"bindingUnavailable":"promotionDisabled"));
                return Optional.empty();
            }
            var source=rewards.classifyStagedNative(store,ref,memberRole);
            if(source.isEmpty())return Optional.empty();
            result.add(source.get());
        }
        return Optional.of(List.copyOf(result));
    }
}
