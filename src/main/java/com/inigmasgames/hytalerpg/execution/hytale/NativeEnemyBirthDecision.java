package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** World-thread decision over the actual staged native roster; no IO or actor publication. */
public final class NativeEnemyBirthDecision {
    public record Selected(EnemyBirthRoot root,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group additional,EnemyNativeBindings.Role role,
            List<EnemyNativeGroupPreparation.MemberSource> sources){
        public Selected{
            Objects.requireNonNull(root);Objects.requireNonNull(original);Objects.requireNonNull(role);
            sources=List.copyOf(sources);
            if(sources.size()!=root.plan().actors().size())throw new IllegalArgumentException("ENEMY_BIRTH_SOURCE_ROSTER");
            for(int i=0;i<sources.size();i++)if(!sources.get(i).spawn().enemy().equals(root.plan().actors().get(i).entityId()))
                throw new IllegalArgumentException("ENEMY_BIRTH_SOURCE_ACTOR_ORDER");
        }
    }
    private final HytaleEncounterRewards rewards;
    private final EnemyNativeBindings bindings;
    private final EnemyBalance balance;
    private final EnemyBirthPlanner planner;
    public NativeEnemyBirthDecision(HytaleEncounterRewards rewards,EnemyNativeBindings bindings,EnemyBalance balance){
        this.rewards=Objects.requireNonNull(rewards);this.bindings=Objects.requireNonNull(bindings);
        this.balance=Objects.requireNonNull(balance);
        this.planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),
                EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
    }
    /** Empty means ordinary native spawn is restored, including any declined native additions. */
    public Optional<Selected> select(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,SpawnJobData nativeJob){
        Objects.requireNonNull(store);Objects.requireNonNull(original);Objects.requireNonNull(nativeJob);
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(original.job().world()))
            throw new IllegalStateException("ENEMY_BIRTH_DECISION_WORLD_THREAD");
        var rollback=new Rollback(store,original);
        try{
            List<EnemyNativeGroupPreparation.MemberSource> additionalSources=List.of();
            var role=bindings.role(original.job().nativeRole()).orElse(null);
            if(role==null||!role.productionPromotionEnabled()||original.nativeFailed()
                    ||original.members().size()!=original.job().expectedMembers())return rollback.fallback();
            var sources=classify(store,original,role);
            if(sources.isEmpty())return rollback.fallback();
            var ref=store.getExternalData().getRefFromUUID(original.members().getFirst().entity());
            var transform=ref==null||!ref.isValid()?null:store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)return rollback.fallback();
            var position=transform.getPosition();
            var request=EnemyNativeGroupPreparation.prepare(original,sources.get(),bindings::role,
                    new Vec3(position.x,position.y,position.z),balance,bindings.revision(),true).orElse(null);
            if(request==null)return rollback.fallback();
            var rarity=planner.rarity(request.seed());
            if(rarity==EnemyRarity.NORMAL)return rollback.fallback();
            var need=rarity==EnemyRarity.CHAMPION?planner.additionalChampionMembers(request):planner.additionalUniqueMembers(request);
            if(need.isEmpty())return rollback.fallback();
            if(need.getAsInt()>0){
                rollback.failClosed=true;
                rollback.additional=NativeEnemyFlockExtension.extend(store,original,nativeJob,need.getAsInt()).orElse(null);
                rollback.failClosed=false;
                if(rollback.additional==null)return rollback.fallback();
                var addedSources=classify(store,rollback.additional,role);
                if(addedSources.isEmpty())return rollback.fallback();
                additionalSources=addedSources.get();
                request=EnemyNativeGroupPreparation.completeAdditional(original,request,rollback.additional,addedSources.get(),
                        bindings::role,balance,bindings.revision()).orElse(null);
                if(request==null)return rollback.fallback();
            }
            var result=planner.plan(request);
            if(!result.promoted()||result.plan().actors().size()!=original.members().size()
                    +(rollback.additional==null?0:rollback.additional.members().size()))return rollback.fallback();
            for(var actor:result.plan().actors())bindings.requireActorRole(actor);
            var root=new EnemyBirthRoot(result.plan(),sources.get().stream()
                    .map(EnemyNativeGroupPreparation.MemberSource::spawn).toList());
            var allSources=new ArrayList<>(sources.get());allSources.addAll(additionalSources);
            return Optional.of(new Selected(root,original,rollback.additional,role,allSources));
        }catch(RuntimeException|Error failure){
            if(!rollback.started&&!rollback.failClosed){
                try{rollback.restore();}catch(RuntimeException|Error restoreFailure){failure.addSuppressed(restoreFailure);}
            }
            throw failure;
        }
    }
    /** The staged actors are command-spawned native NPCs; no world-spawn job is claimed. */
    public Selected selectQa(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,EnemyQaSpawnRequest qa){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(group.job().world())
                ||group.nativeFailed()||group.members().isEmpty()||group.members().size()!=group.job().expectedMembers()
                ||!group.job().nativeRole().equals(qa.nativeRoleId()))
            throw new IllegalArgumentException("QA_NATIVE_ROSTER_INCOMPLETE");
        var role=bindings.role(qa.nativeRoleId()).orElseThrow(()->new IllegalArgumentException(
                "QA_ROLE_NOT_BOUND:"+qa.nativeRoleId()));
        var sources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
        var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        String cycle="qa-command/"+group.reservation().encounter();
        for(var member:group.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            var source=ref==null||!ref.isValid()?Optional.<EnemyNativeGroupPreparation.MemberSource>empty():
                    rewards.classifyStagedQa(store,ref,role);
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
        return new Selected(root,group,null,role,savedSources);
    }
    /** Capacity denial or a pre-root persistence rejection also returns the actual native group. */
    public void restore(Store<EntityStore> store,Selected selected){
        restore(store,selected.original(),selected.additional());
    }
    private final class Rollback {
        final Store<EntityStore> store;final NativeEnemySpawnGroups.Group original;
        NativeEnemySpawnGroups.Group additional;boolean started,failClosed;
        Rollback(Store<EntityStore> store,NativeEnemySpawnGroups.Group original){this.store=store;this.original=original;}
        Optional<Selected> fallback(){restore();return Optional.empty();}
        void restore(){started=true;NativeEnemyBirthDecision.this.restore(store,original,additional);}
    }
    private void restore(Store<EntityStore> store,NativeEnemySpawnGroups.Group original,
            NativeEnemySpawnGroups.Group additional){
        if(additional!=null)NativeEnemyFlockExtension.discard(store,original,additional);
        if(!original.members().isEmpty())rewards.restoreStagedNativeGroup(store,original.members().stream()
                .map(NativeEnemySpawnGroups.Member::staging).toList());
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
