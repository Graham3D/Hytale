package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Whole-roster durable Health, saved-state and native-action preparation before pack publication. */
public final class NativeEnemyBirthAttachment {
    private final HytaleEncounterRewards rewards;
    private final NativeEnemyStateAttachment state;
    private final NativeEnemyActionAttachment actions;
    private final EnemyBalance balance;
    private final EnemyNativeBindings nativeBindings;
    public NativeEnemyBirthAttachment(HytaleEncounterRewards rewards,NativeEnemyStateAttachment state,
            NativeEnemyActionAttachment actions,EnemyBalance balance,EnemyNativeBindings nativeBindings){
        this.rewards=Objects.requireNonNull(rewards);this.state=Objects.requireNonNull(state);
        this.actions=Objects.requireNonNull(actions);this.balance=Objects.requireNonNull(balance);
        this.nativeBindings=Objects.requireNonNull(nativeBindings);
    }
    /** An exceptional result leaves the sealed group staged for writer-owned compensation/rebind. */
    public CompletionStage<Prepared> prepare(Store<EntityStore> store,NativeEnemyBirthReservation.Sealed sealed){
        var birth=sealed.selected().root().plan();
        var balance=EnemyBalance.forRevision(birth.actors().getFirst().balanceRevision());
        return prepare(store,birth,birth.actors(),true,false,false,null,
                ()->rewards.attachStagedGroup(store,sealed.selected(),balance,nativeBindings));
    }
    /** Rebuild the same runtime owners from saved native Health and the current durable pack. */
    public CompletionStage<Prepared> prepareRecovered(Store<EntityStore> store,EnemyBirthPlan birth,
            EnemyPackRecord pack){
        return prepareRecovered(store,birth,pack,birth.activeActors(pack));
    }
    public CompletionStage<Prepared> prepareRecovered(Store<EntityStore> store,EnemyBirthPlan birth,
            EnemyPackRecord pack,List<EnemyDescriptor> requested){
        var active=birth.activeSubset(pack,requested);
        var balance=EnemyBalance.forRevision(birth.actors().getFirst().balanceRevision());
        return prepare(store,birth,active,false,false,false,null,
                ()->rewards.reattachStagedEnemyGroup(store,birth,pack,balance,nativeBindings,active));
    }
    /** Root already committed, but publication has never committed. Reuse frozen sources and initialize only absent state. */
    public CompletionStage<Prepared> prepareUnpublished(Store<EntityStore> store,EnemyBirthRoot root,EnemyPackRecord pack){
        var birth=root.plan();
        if(pack.state()!=EnemyPackRecord.State.RESERVED&&pack.state()!=EnemyPackRecord.State.STAGED)
            throw new IllegalArgumentException("ENEMY_REBIND_NOT_UNPUBLISHED");
        var balance=EnemyBalance.forRevision(birth.actors().getFirst().balanceRevision());
        return prepare(store,birth,birth.activeActors(pack),false,false,true,null,()->{
            var sources=birth.actors().stream().map(a->new EnemyNativeGroupPreparation.MemberSource(root.attachmentSpawn(a),a.nativeRoleId().replace('_',' '))).toList();
            return rewards.attachFrozenBirthGroup(store,birth,sources,balance,nativeBindings);
        });
    }
    /** QA consumes already-bound native Health/pack state without a durable encounter transaction. */
    public CompletionStage<Prepared> prepareQa(Store<EntityStore> store,EnemyBirthPlan birth,
            java.util.function.BiFunction<EnemyDescriptor,Integer,CompletionStage<com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock>> roots){
        if(birth.actors().stream().anyMatch(actor->actor.spawnOrigin()!=EnemyRewardContext.Origin.QA))
            throw new IllegalArgumentException("QA_ATTACHMENT_PROVENANCE");
        return prepare(store,birth,birth.actors(),true,true,false,Objects.requireNonNull(roots),
                ()->CompletableFuture.completedStage(null));
    }
    private CompletionStage<Prepared> prepare(Store<EntityStore> store,EnemyBirthPlan birth,List<EnemyDescriptor> active,
            boolean fresh,boolean qa,boolean unpublishedRecovery,
            java.util.function.BiFunction<EnemyDescriptor,Integer,CompletionStage<com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock>> roots,
            Supplier<CompletionStage<Void>> bind){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_ATTACH_WORLD_THREAD");
        var nativeWorld=store.getExternalData().getWorld();var result=new CompletableFuture<Prepared>();
        var lifetime=qa?null:rewards.watchBirth(birth.world(),result);
        try{bind.get().whenComplete((ignored,error)->{
            if(error!=null){result.completeExceptionally(error);return;}
            try{nativeWorld.execute(()->{
                if(!qa&&!rewards.birthCurrent(birth.world(),lifetime))return;
                NativeEnemyStateAttachment.Prepared saved=null;
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    saved=state.prepare(current,birth,active,fresh,unpublishedRecovery);saved.attach();
                    var preparedState=saved;
                    (qa?actions.prepareQa(current,birth,roots):actions.prepare(current,birth,active)).whenComplete((prepared,actionError)->{
                        if(!qa&&!rewards.birthCurrent(birth.world(),lifetime))return;
                        if(actionError!=null){
                            try{nativeWorld.execute(()->{
                                if(!qa&&!rewards.birthCurrent(birth.world(),lifetime))return;
                                try{preparedState.close();}catch(RuntimeException cleanup){actionError.addSuppressed(cleanup);}
                                result.completeExceptionally(actionError);
                            });}catch(RuntimeException closing){actionError.addSuppressed(closing);result.completeExceptionally(actionError);}
                            return;
                        }
                        try{nativeWorld.execute(()->{if(qa||rewards.birthCurrent(birth.world(),lifetime))result.complete(new Prepared(current,birth,active,preparedState,prepared));});}
                        catch(RuntimeException closing){
                            // A committed root/lease remains spent; world rebind is required after queue failure.
                            result.completeExceptionally(closing);
                        }
                    });
                }catch(RuntimeException failure){
                    if(saved!=null)try{saved.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
                    result.completeExceptionally(failure);
                }
            });}catch(RuntimeException closing){result.completeExceptionally(closing);}
        });}catch(RuntimeException rejected){result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }
    public static final class Prepared implements AutoCloseable {
        private final Store<EntityStore> store;private final EnemyBirthPlan birth;
        private final List<EnemyDescriptor> roster;
        private final NativeEnemyStateAttachment.Prepared saved;
        private final NativeEnemyActionAttachment.Prepared action;
        private final Set<UUID> retired=new HashSet<>();
        private boolean active,closed;
        private volatile EnemyPackRecord publicationReceipt;
        void publicationReceipt(EnemyPackRecord receipt){
            birth.activeActors(receipt);publicationReceipt=receipt;
        }
        EnemyPackRecord publicationReceipt(){return publicationReceipt;}
        private Prepared(Store<EntityStore> store,EnemyBirthPlan birth,List<EnemyDescriptor> active,
                NativeEnemyStateAttachment.Prepared saved,
                NativeEnemyActionAttachment.Prepared action){
            this.store=store;this.birth=birth;this.roster=List.copyOf(active);this.saved=saved;this.action=action;
        }
        public EnemyBirthPlan birth(){return birth;}
        public List<EnemyDescriptor> active(){return roster;}
        /** Attach after all durable root leases exist; actors remain staged until the pack state is published. */
        public void activate(Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
            if(closed||active||!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_ACTIVATE_PHASE");
            action.attach(accepted,rejected);active=true;
        }
        /** Preserve saved components after the durable pack transition, before native staged flags release. */
        public void published(){
            if(closed||!active||!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_PUBLISH_PHASE");
            saved.published();
        }
        public void retireActor(UUID nativeEntity) throws Exception{
            if(closed||!active||!store.isInThread()||roster.stream().noneMatch(a->a.entityId().equals(nativeEntity)))
                throw new IllegalStateException("ENEMY_BIRTH_RETIRE_ACTOR_PHASE");
            if(!retired.add(nativeEntity))return;
            action.detachActor(nativeEntity);
            saved.detachActor(nativeEntity);
        }
        public boolean allRetired(){return retired.size()==roster.size();}
        @Override public void close() throws Exception{
            if(closed)return;if(!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_CLOSE_THREAD");
            closed=true;Exception failed=null;
            try{action.close();}catch(Exception error){failed=error;}
            try{saved.close();}catch(RuntimeException error){if(failed==null)failed=error;else failed.addSuppressed(error);}
            if(failed!=null)throw failed;
        }
    }
}
