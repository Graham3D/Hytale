package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Opens durable root leases and certifies the complete staged native action roster before release. */
public final class NativeEnemyActionAttachment {
    private record Candidate(EnemyDescriptor descriptor,Ref<EntityStore> ref,List<NativeEnemyAction.Binding> bindings,
            CompletableFuture<EnemyActionRootLease> lease){}
    private final HytaleEncounterRewards rewards;
    private final HytaleDifficultyCombat combat;
    private final NativeEnemyActions actions;
    private final FiniteSupportEffects effects;
    private final EnemyNativeBindings nativeBindings;
    public NativeEnemyActionAttachment(HytaleEncounterRewards rewards,HytaleDifficultyCombat combat,
            NativeEnemyActions actions,FiniteSupportEffects effects,EnemyNativeBindings nativeBindings){
        this.rewards=Objects.requireNonNull(rewards);this.combat=Objects.requireNonNull(combat);
        this.actions=Objects.requireNonNull(actions);this.effects=Objects.requireNonNull(effects);
        this.nativeBindings=Objects.requireNonNull(nativeBindings);
    }
    /** The installed-role manifest pins every native status chance used by the shared admission owner. */
    public CompletionStage<Prepared> prepare(Store<EntityStore> store,NativeEnemyBirthDecision.Selected selected){
        Objects.requireNonNull(store);Objects.requireNonNull(selected);
        return prepare(store,selected.root().plan());
    }
    /** LOAD rebind consumes the same sealed descriptor and installed native role, without a spawn job. */
    public CompletionStage<Prepared> prepare(Store<EntityStore> store,EnemyBirthPlan birth){
        return prepare(store,birth,birth.actors());
    }
    public CompletionStage<Prepared> prepare(Store<EntityStore> store,EnemyBirthPlan birth,List<EnemyDescriptor> active){
        return prepareWithRoots(store,birth,active,(actor,count)->rewards.reserveEnemyActionRoots(
                birth.world(),birth.encounter(),birth.generation(),actor.logicalActorId(),count));
    }
    /** QA supplies session-only root blocks to this same native action and affix owner. */
    public CompletionStage<Prepared> prepareQa(Store<EntityStore> store,EnemyBirthPlan birth,
            java.util.function.BiFunction<EnemyDescriptor,Integer,CompletionStage<com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock>> roots){
        if(birth.actors().stream().anyMatch(actor->actor.spawnOrigin()!=EnemyRewardContext.Origin.QA))
            throw new IllegalArgumentException("QA_ACTION_ROOT_PROVENANCE");
        return prepareWithRoots(store,birth,birth.actors(),roots);
    }
    private CompletionStage<Prepared> prepareWithRoots(Store<EntityStore> store,EnemyBirthPlan birth,
            List<EnemyDescriptor> active,
            java.util.function.BiFunction<EnemyDescriptor,Integer,CompletionStage<com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock>> roots){
        Objects.requireNonNull(store);Objects.requireNonNull(birth);
        if(active.isEmpty()||active.size()>birth.actors().size()||new HashSet<>(active).size()!=active.size()
                ||!birth.actors().containsAll(active))throw new IllegalArgumentException("ENEMY_ACTION_ACTIVE_ROSTER");
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(birth.world()))
            throw new IllegalStateException("ENEMY_ACTION_GROUP_WORLD_THREAD");
        var certificates=new ArrayList<Map.Entry<EnemyDescriptor,List<NativeEnemyAction.Binding>>>();
        var refs=new ArrayList<Ref<EntityStore>>();
        for(var actor:active){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            if(ref==null||!ref.isValid())throw new NativeBirthAwaitingLoad();
            var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            var npc=ref==null||!ref.isValid()?null:store.getComponent(ref,NPCEntity.getComponentType());
            if(marker==null||npc==null||!marker.state().world().equals(birth.world())
                    ||!marker.state().entity().equals(actor.entityId())
                    ||!marker.state().encounter().equals(birth.encounter())
                    ||marker.state().generation()!=birth.generation()
                    ||!npc.getRoleName().equals(actor.nativeRoleId())
                    ||!combat.enemyState(birth.world(),actor.entityId()).map(HytaleDifficultyCombat.EnemyState::descriptor).filter(actor::equals).isPresent()
                    ||!combat.enemyPack(birth.world(),actor.entityId()).filter(pack->pack.packId().equals(birth.pack().packId())
                            &&pack.generation()==birth.generation()).isPresent())
                throw new IllegalStateException("ENEMY_ACTION_GROUP_NOT_STAGED_OR_BOUND");
            var actorRole=nativeBindings.requireActorRole(actor);
            var certified=actorRole.certify(actor.nativeBindingRevision(),npc);
            if(certified.isEmpty()){
                if(actor.spawnOrigin()!=EnemyRewardContext.Origin.QA||!actorRole.actions().isEmpty())
                    throw new IllegalStateException("ENEMY_ACTION_GROUP_NO_CERTIFIED_STRIKES");
                continue; // Passive-only QA role; native outgoing damage retains the existing difficulty owner.
            }
            certificates.add(Map.entry(actor,certified));refs.add(ref);
        }
        var candidates=new ArrayList<Candidate>();
        for(int i=0;i<certificates.size();i++){
            var actor=certificates.get(i).getKey();
            var lease=EnemyActionRootLease.open(birth.world(),actor.logicalActorId(),birth.generation(),
                    count->roots.apply(actor,count)).toCompletableFuture();
            candidates.add(new Candidate(actor,refs.get(i),certificates.get(i).getValue(),lease));
        }
        var result=new CompletableFuture<Prepared>();
        CompletableFuture.allOf(candidates.stream().map(Candidate::lease).toArray(CompletableFuture[]::new))
                .whenComplete((ignored,error)->{
                    if(error!=null){
                        for(var candidate:candidates)if(candidate.lease().isDone()&&!candidate.lease().isCompletedExceptionally())
                            candidate.lease().join().close();
                        result.completeExceptionally(error);return;
                    }
                    result.complete(new Prepared(store,birth,candidates));
                });
        return result.minimalCompletionStage();
    }
    public final class Prepared implements AutoCloseable {
        private final Store<EntityStore> store;private final EnemyBirthPlan birth;private final List<Candidate> candidates;
        private final List<AutoCloseable> attached=new ArrayList<>();private boolean closed,published;
        private Prepared(Store<EntityStore> store,EnemyBirthPlan birth,List<Candidate> candidates){
            this.store=store;this.birth=birth;this.candidates=List.copyOf(candidates);
        }
        /** The publication owner calls this once, on the same world thread, while every actor is staged. */
        public void attach(Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
            Objects.requireNonNull(accepted);Objects.requireNonNull(rejected);
            if(closed||published||!store.isInThread())throw new IllegalStateException("ENEMY_ACTION_GROUP_ATTACH_STATE");
            try{
                for(var candidate:candidates){
                    var actor=candidate.descriptor();var ref=candidate.ref();
                    var marker=ref.isValid()?store.getComponent(ref,EnemyStaging.getComponentType()):null;
                    if(marker==null||!marker.state().world().equals(birth.world())
                            ||!marker.state().entity().equals(actor.entityId())
                            ||!marker.state().encounter().equals(birth.encounter())
                            ||marker.state().generation()!=birth.generation())
                        throw new IllegalStateException("ENEMY_ACTION_GROUP_ACTOR_CHANGED");
                    var lease=candidate.lease().join();
                    attached.add(actions.attach(store,ref,actor,candidate.bindings(),
                            ()->combat.enemySourceState(store,ref,effects),lease::issue,
                            birth.seed()+"/"+actor.logicalActorId(),accepted,rejected));
                }
                published=true;
            }catch(RuntimeException failure){
                try{close();}catch(Exception closing){failure.addSuppressed(closing);}
                throw failure;
            }
        }
        /** Removal/unload retires only this native actor; the rest of the pack keeps its action routes. */
        public void detachActor(UUID nativeEntity) throws Exception{
            if(closed||!published||!store.isInThread())throw new IllegalStateException("ENEMY_ACTION_ACTOR_DETACH_PHASE");
            for(int i=0;i<candidates.size();i++)if(candidates.get(i).descriptor().entityId().equals(nativeEntity)){
                var handle=attached.get(i);
                if(handle==null)return;
                attached.set(i,null);
                try{handle.close();}finally{candidates.get(i).lease().join().close();}
                return;
            }
            if(birth.actors().stream().anyMatch(actor->actor.entityId().equals(nativeEntity)
                    &&actor.spawnOrigin()==EnemyRewardContext.Origin.QA
                    &&nativeBindings.requireActorRole(actor).actions().isEmpty()))return;
            throw new IllegalArgumentException("ENEMY_ACTION_ACTOR_NOT_IN_PACK");
        }
        /** Close only on the owning world thread; unused committed root IDs remain spent. */
        @Override public void close() throws Exception{
            if(closed)return;
            if(!store.isInThread())throw new IllegalStateException("ENEMY_ACTION_GROUP_CLOSE_THREAD");
            closed=true;Exception error=null;
            for(int i=attached.size()-1;i>=0;i--)try{if(attached.get(i)!=null)attached.get(i).close();}
            catch(Exception failure){if(error==null)error=failure;else error.addSuppressed(failure);}
            for(var candidate:candidates)candidate.lease().join().close();
            attached.clear();if(error!=null)throw error;
        }
    }
}
