package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.util.*;

/** Acknowledges recovered capacity only after inspecting every currently loaded saved actor. */
public final class NativeEnemyWorldRebind {
    /** A loaded staged actor awaits its existing birth or compensation recovery owner. */
    static final class PendingStaging extends RuntimeException {
        PendingStaging(){super("ENEMY_WORLD_REBIND_PENDING_STAGING");}
    }
    private final EnemyWorldAdmission admission;
    private final NativeEnemyBirthOwner owner;
    private final boolean nativePackboundHookReady;
    private final boolean nativeDamageReceiptHookReady;

    public NativeEnemyWorldRebind(EnemyWorldAdmission admission,NativeEnemyBirthOwner owner,
            boolean nativePackboundHookReady){
        this(admission,owner,nativePackboundHookReady,false);
    }
    public NativeEnemyWorldRebind(EnemyWorldAdmission admission,NativeEnemyBirthOwner owner,
            boolean nativePackboundHookReady,boolean nativeDamageReceiptHookReady){
        this.admission=Objects.requireNonNull(admission);this.owner=Objects.requireNonNull(owner);
        this.nativePackboundHookReady=nativePackboundHookReady;
        this.nativeDamageReceiptHookReady=nativeDamageReceiptHookReady;
    }

    public void begin(World world){
        UUID worldId=world.getWorldConfig().getUuid();
        var startup=admission.begin(worldId);
        Object lifetime=admission.lifetime(worldId);
        startup.whenComplete((inventory,error)->{
            if(!admission.current(worldId,lifetime))return;
            if(error!=null){owner.fail(worldId,"WORLD_INVENTORY",error);return;}
            if(!nativePackboundHookReady&&inventory.packs().stream().anyMatch(pack->
                    pack.state()!=EnemyPackRecord.State.ABORTED&&pack.state()!=EnemyPackRecord.State.DEFEATED
                    &&inventory.births().stream().filter(birth->birth.encounter().equals(pack.encounterId()))
                            .flatMap(birth->birth.activeActors(pack).stream()).anyMatch(actor->actor.ownAffixes().stream()
                                    .anyMatch(affix->affix.affixId().equals("ME-024"))))){
                owner.fail(worldId,"PACKBOUND_NATIVE_PATCH",
                        new IllegalStateException("ENEMY_PACKBOUND_NATIVE_PATCH_REQUIRED"));return;
            }
            try{world.execute(()->{
                if(!admission.initialRebindPending(worldId,lifetime))return;
                try{
                    var reconciled=inspect(world.getEntityStore().getStore(),inventory,owner);
                    admission.rebindComplete(worldId,reconciled);
                    com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                            "RPG_ENEMY_WORLD_REBIND_COMPLETE world=%s recovered=%s freshAdmission=%s",
                            world.getName(),reconciled.size(),admission.admits(worldId));
                }
                catch(PendingStaging pending){/* Existing recovery owners retry after publication or restore/remove. */}
                catch(RuntimeException failure){owner.fail(worldId,"WORLD_INVENTORY_REBIND",failure);}
            });}
            catch(RuntimeException failure){owner.fail(worldId,"WORLD_INVENTORY_QUEUE",failure);}
        });
    }

    /** LOAD owners validate each actor; only an unfinished startup audit needs a retry. */
    public void retryInitial(World world){
        UUID worldId=world.getWorldConfig().getUuid();
        Object lifetime=admission.lifetime(worldId);
        if(admission.initialRebindPending(worldId,lifetime)
                &&com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldId)==world)
            begin(world);
    }

    /** Unloaded members retain durable identity and must pass the saved-identity LOAD owner. */
    static Set<UUID> inspect(Store<EntityStore> store,FileEncounterStore.EnemyWorldInventory inventory,
            NativeEnemyBirthOwner owner){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_WORLD_REBIND_THREAD");
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        var births=new HashMap<UUID,EnemyBirthPlan>();
        var expectedStaged=new HashMap<UUID,EnemyStaging.State>();
        var expectedIdentities=new HashSet<UUID>();
        for(var birth:inventory.births()){
            if(!world.equals(birth.world())||births.putIfAbsent(birth.encounter(),birth)!=null)
                throw new IllegalStateException("ENEMY_WORLD_REBIND_BIRTH");
        }
        var required=new HashSet<UUID>();
        for(var pack:inventory.packs()){
            if(pack.state()==EnemyPackRecord.State.ABORTED||pack.state()==EnemyPackRecord.State.DEFEATED)continue;
            var birth=births.get(pack.encounterId());
            if(birth==null||birth.pack()==null||!birth.pack().packId().equals(pack.packId()))
                throw new IllegalStateException("ENEMY_WORLD_REBIND_PACK");
            required.add(pack.encounterId());
            for(var actor:birth.activeActors(pack)){
                var ref=loaded(store,actor.entityId());if(ref==null)continue;
                expectedIdentities.add(actor.entityId());
                var saved=store.getComponent(ref,EnemyActorIdentity.getComponentType());
                var marker=store.getComponent(ref,EnemyStaging.getComponentType());
                if(marker!=null)expectedStaged.put(actor.entityId(),marker.state());
                boolean bound=owner.bound(actor.entityId());
                if(saved!=null&&!saved.state().equals(EnemyActorIdentity.State.of(actor))
                        ||bound&&(saved==null||marker!=null)
                        ||!bound&&(marker==null||!NativeEnemyWholeBirthRecovery.validStagingProvenance(
                                birth,actor,marker.state(),saved==null?null:saved.state()))
                        ||saved==null&&pack.state()!=EnemyPackRecord.State.RESERVED
                            &&pack.state()!=EnemyPackRecord.State.STAGED)
                    throw new IllegalStateException("ENEMY_WORLD_REBIND_LOADED_ACTOR:"+actor.entityId());
            }
        }
        for(var compensation:inventory.compensations()){
            required.add(compensation.encounter());
            var birth=births.get(compensation.encounter());
            if(birth==null)throw new IllegalStateException("ENEMY_WORLD_REBIND_COMPENSATION");
            for(var actor:birth.actors()){
                var ref=loaded(store,actor.entityId());if(ref==null)continue;
                expectedIdentities.add(actor.entityId());
                var saved=store.getComponent(ref,EnemyActorIdentity.getComponentType());
                var marker=store.getComponent(ref,EnemyStaging.getComponentType());
                if(marker!=null)expectedStaged.put(actor.entityId(),marker.state());
                if(owner.bound(actor.entityId())||marker==null&&saved!=null
                        ||marker==null&&!birth.originalNativeEntities().contains(actor.entityId())
                        ||marker!=null&&!NativeEnemyStagingRecovery.recoveryMatches(
                                marker.state(),birth,compensation,saved==null?null:saved.state()))
                    throw new IllegalStateException("ENEMY_WORLD_REBIND_COMPENSATED_ACTOR:"+actor.entityId());
            }
        }
        for(var anchor:inventory.anchors())if(anchor.state()==SuperUniqueAnchor.State.RESERVED
                ||anchor.state()==SuperUniqueAnchor.State.LIVE)required.add(anchor.encounterId());
        // A pre-root actor may also be loaded. Keep admission shut until its existing
        // staging-recovery owner restores/removes it; it owns no durable pack slot yet.
        store.forEachChunk(EnemyStaging.getComponentType(),(chunk,buffer)->{
            for(int index=0;index<chunk.size();index++){
                if(store.getComponent(chunk.getReferenceTo(index),QaTransientMarker.getComponentType())!=null)continue;
                var marker=chunk.getComponent(index,EnemyStaging.getComponentType()).state();
                var id=chunk.getComponent(index,UUIDComponent.getComponentType());
                if(id==null||!world.equals(marker.world())||!marker.entity().equals(id.getUuid()))
                    throw new IllegalStateException("ENEMY_WORLD_REBIND_UNACCOUNTED_STAGING");
                if(marker.equals(expectedStaged.get(id.getUuid())))continue;
                var saved=chunk.getComponent(index,EnemyActorIdentity.getComponentType());
                if(!births.containsKey(marker.encounter())&&saved==null)throw new PendingStaging();
                throw new IllegalStateException("ENEMY_WORLD_REBIND_UNACCOUNTED_STAGING");
            }
        });
        store.forEachChunk(EnemyActorIdentity.getComponentType(),(chunk,buffer)->{
            for(int index=0;index<chunk.size();index++){
                if(store.getComponent(chunk.getReferenceTo(index),QaTransientMarker.getComponentType())!=null)continue;
                var id=chunk.getComponent(index,UUIDComponent.getComponentType());
                if(id==null||!expectedIdentities.contains(id.getUuid()))
                    throw new IllegalStateException("ENEMY_WORLD_REBIND_ORPHAN_IDENTITY");
            }
        });
        // Inspection is only a prerequisite. A loaded staged actor still has its
        // native invulnerability flag and no published runtime; fresh births must
        // wait for the existing whole-birth or compensation owner to finish.
        if(!expectedStaged.isEmpty())throw new PendingStaging();
        return Set.copyOf(required);
    }
    private static Ref<EntityStore> loaded(Store<EntityStore> store,UUID id){
        var ref=store.getExternalData().getRefFromUUID(id);
        return ref!=null&&ref.isValid()?ref:null;
    }
}
