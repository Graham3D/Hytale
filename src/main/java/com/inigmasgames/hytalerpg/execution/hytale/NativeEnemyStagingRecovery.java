package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity;
import com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation;
import com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan;
import com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot;
import com.inigmasgames.hytalerpg.enemies.EnemyDescriptor;
import com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission;
import java.util.*;

/** Restores/removes pre-root staged native actors only after the durable root lookup proves absence. */
public final class NativeEnemyStagingRecovery extends RefSystem<EntityStore> {
    private record Decision(Optional<EnemyBirthRoot> root,Optional<EnemyBirthCompensation> compensation){}
    private final HytaleEncounterRewards rewards;
    private final EnemyWorldAdmission admission;
    private volatile java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> reconciled=world->{};
    public NativeEnemyStagingRecovery(HytaleEncounterRewards rewards,EnemyWorldAdmission admission){
        this.rewards=Objects.requireNonNull(rewards);this.admission=Objects.requireNonNull(admission);
    }
    public void onReconciled(java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> callback){
        reconciled=Objects.requireNonNull(callback);
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(EnemyStaging.getComponentType(),NPCEntity.getComponentType());}
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        if(reason!=AddReason.LOAD)return;
        var marker=store.getComponent(ref,EnemyStaging.getComponentType());
        var id=store.getComponent(ref,UUIDComponent.getComponentType());
        if(marker==null||id==null||!marker.state().entity().equals(id.getUuid()))
            throw new IllegalStateException("ENEMY_ORPHAN_STAGING_IDENTITY");
        var state=marker.state();var nativeWorld=store.getExternalData().getWorld();
        if(!state.world().equals(nativeWorld.getWorldConfig().getUuid()))
            throw new IllegalStateException("ENEMY_ORPHAN_STAGING_WORLD");
        admission.begin(state.world()).thenCompose(ignored->rewards.enemyBirthRoot(state.world(),state.encounter())
                .thenCombine(rewards.enemyBirthCompensation(state.world(),state.encounter()),Decision::new))
                .whenComplete((decision,error)->{
            if(error!=null){warn("LOOKUP",state,error);return;}
            var birth=decision.root().map(EnemyBirthRoot::plan).orElse(null);
            var compensation=decision.compensation().orElse(null);
            // An uncompensated sealed birth belongs to the whole-group rebind owner.
            if(birth!=null&&compensation==null)return;
            try{nativeWorld.execute(()->{
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    var actor=current.getExternalData().getRefFromUUID(state.entity());
                    var staged=actor==null||!actor.isValid()?null:current.getComponent(actor,EnemyStaging.getComponentType());
                    if(staged==null||!staged.state().equals(state))return;
                    var saved=current.getComponent(actor,EnemyActorIdentity.getComponentType());
                    if(!recoveryMatches(state,birth,compensation,saved==null?null:saved.state()))
                        throw new IllegalStateException("ENEMY_ORPHAN_STAGING_DURABLE_IDENTITY");
                    if(saved!=null)current.removeComponent(actor,EnemyActorIdentity.getComponentType());
                    if(state.additional())discard(current,actor,state);
                    else if(birth==null)rewards.releasePreRootNativeGroup(current,List.of(state));
                    else rewards.restoreStagedNativeGroup(current,List.of(state));
                    reconciled.accept(nativeWorld);
                }catch(RuntimeException failure){warn("RECONCILE",state,failure);}
            });}catch(RuntimeException closing){warn("WORLD_QUEUE",state,closing);}
        });
    }
    /** A compensated birth still has its immutable plan; only the exact frozen actor may be restored. */
    static boolean recoveryMatches(EnemyStaging.State state,EnemyBirthPlan birth,
            EnemyBirthCompensation compensation,EnemyActorIdentity.State identity){
        if(birth==null)return compensation==null&&identity==null;
        if(compensation==null||!birth.world().equals(state.world())
                ||!birth.encounter().equals(state.encounter())||birth.generation()!=state.generation()
                ||!compensation.world().equals(state.world())||!compensation.encounter().equals(state.encounter())
                ||compensation.generation()!=birth.generation()||!compensation.seed().equals(birth.seed()))return false;
        EnemyDescriptor descriptor=null;
        for(var candidate:birth.actors())if(candidate.entityId().equals(state.entity())){
            descriptor=candidate;break;
        }
        if(descriptor==null||birth.originalNativeEntities().contains(state.entity())==state.additional())return false;
        return identity==null||identity.equals(EnemyActorIdentity.State.of(descriptor));
    }
    private static void discard(Store<EntityStore> store,Ref<EntityStore> actor,EnemyStaging.State state){
        var npc=store.getComponent(actor,NPCEntity.getComponentType());
        var population=store.getResource(WorldSpawnData.getResourceType());
        var environment=npc==null||population==null?null:population.getWorldEnvironmentSpawnData(npc.getEnvironment());
        if(!state.additional()||npc==null||environment==null)
            throw new IllegalStateException("ENEMY_ORPHAN_EXTRA_TRACKER_MISSING");
        int worldBefore=population.getActualNPCs(),environmentBefore=environment.getActualNPCs();
        store.removeEntity(actor,RemoveReason.REMOVE);
        if(population.getActualNPCs()!=worldBefore-1||environment.getActualNPCs()!=environmentBefore-1)
            throw new IllegalStateException("ENEMY_ORPHAN_EXTRA_POPULATION_ROLLBACK");
    }
    private static void warn(String phase,EnemyStaging.State state,Throwable error){
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(error)
                .log("RPG_ENEMY_ORPHAN_STAGING_RECOVERY_FAILED phase=%s world=%s encounter=%s entity=%s",
                        phase,state.world(),state.encounter(),state.entity());
    }
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
}
