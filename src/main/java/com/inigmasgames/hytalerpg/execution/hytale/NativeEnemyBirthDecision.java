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
public final class NativeEnemyBirthDecision {
    public record Selected(EnemyBirthRoot root,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group additional,EnemyNativeBindings.Role role,
            List<EnemyNativeGroupPreparation.MemberSource> sources,
            EnemyPackCapacity.Reservation preLease){
        public Selected{
            Objects.requireNonNull(root);Objects.requireNonNull(original);Objects.requireNonNull(role);
            sources=List.copyOf(sources);
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
            var root=new EnemyBirthRoot(result.plan(),sources.get().stream()
                    .map(EnemyNativeGroupPreparation.MemberSource::spawn).toList());
            var allSources=new ArrayList<>(sources.get());allSources.addAll(additionalSources);
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
            if(ref==null||!ref.isValid())return Optional.empty();
            var source=rewards.classifyStagedNative(store,ref,role);
            if(source.isEmpty())return Optional.empty();
            result.add(source.get());
        }
        return Optional.of(List.copyOf(result));
    }
}
