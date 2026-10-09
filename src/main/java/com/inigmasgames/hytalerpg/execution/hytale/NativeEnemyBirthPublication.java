package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.*;
import java.util.*;
import java.util.concurrent.*;

/** Durably advances a fully attached native group before releasing its native staging flags. */
public final class NativeEnemyBirthPublication {
    private record Display(EnemyDescriptor actor,EnemyDisplayDto dto,Ref<EntityStore> ref){}
    private final HytaleEncounterRewards rewards;
    private final HytaleDifficultyCombat combat;
    private final EnemyWorldAdmission admission;
    private final EnemyVisualVariants visuals;
    private final EnemyNativeBindings nativeBindings;
    private final HytaleEnemyWeaponVisuals weaponVisuals;
    public NativeEnemyBirthPublication(HytaleEncounterRewards rewards,HytaleDifficultyCombat combat,
            EnemyWorldAdmission admission,EnemyVisualVariants visuals,EnemyNativeBindings nativeBindings,
            HytaleEnemyWeaponVisuals weaponVisuals){
        this.rewards=Objects.requireNonNull(rewards);this.combat=Objects.requireNonNull(combat);
        this.admission=Objects.requireNonNull(admission);this.visuals=Objects.requireNonNull(visuals);
        this.nativeBindings=Objects.requireNonNull(nativeBindings);
        this.weaponVisuals=Objects.requireNonNull(weaponVisuals);
    }
    /** Once the publish write is attempted, an error is uncertain: leave the whole group staged for replay. */
    public CompletionStage<Void> publish(Store<EntityStore> store,NativeEnemyBirthReservation.Sealed sealed,
            NativeEnemyBirthAttachment.Prepared attachment){
        var selected=sealed.selected();var birth=selected.root().plan();
        if(!store.isInThread()||!birth.equals(attachment.birth())||!admission.admits(birth.world()))
            throw new IllegalStateException("ENEMY_BIRTH_PUBLISH_WORLD_OR_BIRTH");
        var result=new CompletableFuture<Void>();var nativeWorld=store.getExternalData().getWorld();
        var lifetime=admission.watch(birth.world(),result);
        try{rewards.transitionEnemyPack(birth.world(),birth.pack().packId(),EnemyPackRecord::staged)
                .thenCompose(staged->{
                    if(!staged.equals(birth.pack().staged()))
                        throw new IllegalStateException("ENEMY_BIRTH_STAGED_PACK_MISMATCH");
                    return rewards.transitionEnemyPack(birth.world(),birth.pack().packId(),EnemyPackRecord::publish);
                }).whenComplete((published,error)->{
                    if(!admission.current(birth.world(),lifetime))return;
                    if(error!=null){admission.failClosed(birth.world(),"PUBLISH",error);result.completeExceptionally(error);return;}
                    admission.birthPhase(birth,"BIRTH_PACK_PUBLISHED_DURABLE",null);
                    try{nativeWorld.execute(()->{
                        if(!admission.current(birth.world(),lifetime))return;
                        try{
                            var current=nativeWorld.getEntityStore().getStore();
                            if(!current.isInThread()||!admission.admits(birth.world())
                                    ||published==null||!published.equals(birth.pack().staged().publish()))
                                throw new IllegalStateException("ENEMY_BIRTH_PUBLISHED_PACK_MISMATCH");
                            finish(current,birth,published,attachment,
                                    selected.sources().stream().map(EnemyNativeGroupPreparation.MemberSource::nativeName).toList(),false);
                            admission.birthPhase(birth,"BIRTH_NATIVE_FINISH",null);
                            completePublication(current,birth,published,attachment,result,lifetime);
                        }catch(RuntimeException failure){admission.failClosed(birth.world(),"PUBLISH",failure);result.completeExceptionally(failure);}
                    });}catch(RuntimeException closing){admission.failClosed(birth.world(),"PUBLISH",closing);result.completeExceptionally(closing);}
                });}catch(RuntimeException rejected){admission.failClosed(birth.world(),"PUBLISH",rejected);result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }

    private void completePublication(Store<EntityStore> store,EnemyBirthPlan birth,EnemyPackRecord published,
            NativeEnemyBirthAttachment.Prepared attachment,CompletableFuture<Void> result,Object lifetime){
        boolean anyLoaded=birth.activeActors(published).stream().anyMatch(actor->{var ref=store.getExternalData().getRefFromUUID(actor.entityId());return ref!=null&&ref.isValid();});
        if(anyLoaded){attachment.publicationReceipt(published);result.complete(null);return;}
        rewards.transitionEnemyPack(birth.world(),published.packId(),current->{
            requireSameRecoveryPack(published,current);
            return current.state()==EnemyPackRecord.State.SUSPENDED?current:current.suspend();
        }).whenComplete((suspended,error)->{
            if(!admission.current(birth.world(),lifetime))return;
            if(error!=null){admission.failClosed(birth.world(),"PUBLISH_SUSPEND",error);result.completeExceptionally(error);return;}
            attachment.publicationReceipt(suspended);result.complete(null);
        });
    }

    /** Finish a saved birth from its current durable state; no rarity, affix or actor is regenerated. */
    public CompletionStage<EnemyPackRecord> publishRecovered(Store<EntityStore> store,EnemyBirthPlan birth,
            EnemyPackRecord observed,NativeEnemyBirthAttachment.Prepared attachment,
            java.util.function.Predicate<UUID> alreadyBound){
        Objects.requireNonNull(alreadyBound);
        if(!store.isInThread()||admission.failed(birth.world())||!admission.inventory(birth.world()).isPresent()
                ||!birth.equals(attachment.birth())||birth.pack()==null
                ||!observed.packId().equals(birth.pack().packId())
                ||!observed.encounterId().equals(birth.encounter())||observed.generation()!=birth.generation()
                ||(observed.state()==EnemyPackRecord.State.RESERVED
                    ||observed.state()==EnemyPackRecord.State.STAGED)
                    &&!attachment.active().equals(birth.activeActors(observed))
                )
            throw new IllegalStateException("ENEMY_REBIND_PUBLISH_IDENTITY");
        var nativeWorld=store.getExternalData().getWorld();var result=new CompletableFuture<EnemyPackRecord>();
        var lifetime=admission.watch(birth.world(),result);
        try{rewards.transitionEnemyPack(birth.world(),observed.packId(),current->{
            requireSameRecoveryPack(observed,current);
            return current.state()==EnemyPackRecord.State.RESERVED?current.staged():current;
        }).thenCompose(staged->rewards.transitionEnemyPack(birth.world(),observed.packId(),current->{
            requireSameRecoveryPack(observed,current);
            return switch(current.state()){
                case STAGED->current.publish();
                case SUSPENDED->current.resume();
                case GUARDED,RELEASED->current;
                default->throw new IllegalStateException("ENEMY_REBIND_PACK_NOT_PUBLISHABLE");
            };
        })).whenComplete((published,error)->{
            if(!admission.current(birth.world(),lifetime))return;
            if(error!=null){admission.failClosed(birth.world(),"PUBLISH",error);result.completeExceptionally(error);return;}
            try{nativeWorld.execute(()->{
                if(!admission.current(birth.world(),lifetime))return;
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    var names=new ArrayList<String>();
                    for(var actor:attachment.active()){
                        var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                        var npc=ref==null||!ref.isValid()?null:current.getComponent(ref,NPCEntity.getComponentType());
                        if(ref==null||!ref.isValid()){names.add("");continue;}
                        if(npc==null)throw new IllegalStateException("ENEMY_REBIND_DISPLAY_NATIVE_MISSING");
                        var name=HytaleDifficultyCombat.nativeDisplayName(current,ref,npc);
                        if(name==null||name.isBlank()||name.startsWith("server.")||name.contains("_"))
                            throw new IllegalStateException("ENEMY_REBIND_DISPLAY_NAME_MISSING");
                        names.add(name);
                    }
                    finish(current,birth,published,attachment,names,false);
                    var completion=new CompletableFuture<Void>();
                    completion.whenComplete((ignored,completionError)->{
                        if(completionError!=null)result.completeExceptionally(completionError);
                        else result.complete(attachment.publicationReceipt());
                    });
                    completePublication(current,birth,published,attachment,completion,lifetime);
                }catch(RuntimeException failure){admission.failClosed(birth.world(),"PUBLISH",failure);result.completeExceptionally(failure);}
            });}catch(RuntimeException closing){admission.failClosed(birth.world(),"PUBLISH",closing);result.completeExceptionally(closing);}
        });}catch(RuntimeException rejected){admission.failClosed(birth.world(),"PUBLISH",rejected);result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }

    private static void requireSameRecoveryPack(EnemyPackRecord observed,EnemyPackRecord current){
        if(!current.worldId().equals(observed.worldId())||!current.packId().equals(observed.packId())
                ||!current.encounterId().equals(observed.encounterId())||current.generation()!=observed.generation()
                ||!current.birthRoster().equals(observed.birthRoster())
                ||!current.deadMemberReceipts().equals(observed.deadMemberReceipts()))
            throw new IllegalStateException("ENEMY_REBIND_PACK_CHANGED");
    }

    /** Same pack, palette, affix display, and staged release; only the pack backend is transient. */
    public EnemyPackRecord publishQa(Store<EntityStore> store,NativeEnemyBirthDecision.Selected selected,
            NativeEnemyBirthAttachment.Prepared attachment){
        var birth=selected.root().plan();
        if(birth.actors().stream().anyMatch(actor->actor.spawnOrigin()!=EnemyRewardContext.Origin.QA))
            throw new IllegalArgumentException("QA_PUBLICATION_PROVENANCE");
        var published=birth.pack().staged().publish();
        finish(store,birth,published,attachment,
                selected.sources().stream().map(EnemyNativeGroupPreparation.MemberSource::nativeName).toList(),true);
        return published;
    }
    private void finish(Store<EntityStore> store,EnemyBirthPlan birth,EnemyPackRecord pack,
            NativeEnemyBirthAttachment.Prepared attachment,List<String> names,boolean qa){
        var actors=attachment.active();
        birth.activeSubset(pack,actors);
        if(!store.isInThread()||!qa&&(admission.failed(birth.world())||!admission.inventory(birth.world()).isPresent())
                ||names.size()!=actors.size()
                ||pack.state()!=EnemyPackRecord.State.GUARDED&&pack.state()!=EnemyPackRecord.State.RELEASED)
            throw new IllegalStateException("ENEMY_BIRTH_PUBLISH_ACTIVE_ROSTER");
        var staged=new LinkedHashMap<UUID,EnemyStaging.State>();var displays=new ArrayList<Display>();
        for(var actor:actors){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            // A completed durable publish owns unloaded actors too. Their saved staging/identity
            // is reconciled on LOAD; only still-loaded members need native release now.
            if(ref==null||!ref.isValid())continue;
            var marker=store.getComponent(ref,EnemyStaging.getComponentType());
            var npc=ref==null||!ref.isValid()?null:store.getComponent(ref,NPCEntity.getComponentType());
            var identity=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyActorIdentity.getComponentType());
            var projected=combat.enemyState(birth.world(),actor.entityId()).orElse(null);
            if(marker==null||npc==null||identity==null||projected==null
                    ||!marker.state().world().equals(birth.world())
                    ||!marker.state().encounter().equals(birth.encounter())
                    ||marker.state().generation()!=birth.generation()
                    ||!marker.state().entity().equals(actor.entityId())
                    ||!identity.state().equals(EnemyActorIdentity.State.of(actor))
                    ||!projected.descriptor().equals(actor)
                    ||!npc.getRoleName().equals(actor.nativeRoleId()))
                throw new IllegalStateException("ENEMY_BIRTH_PUBLISH_ROSTER_CHANGED");
            nativeBindings.requireActorRole(actor);
            staged.put(actor.entityId(),marker.state());
        }
        attachment.published(); // Saved clock and shield must survive an interrupted presentation/release.
        combat.refreshEnemyPack(store,pack);
        for(int i=0;i<actors.size();i++){
            var actor=actors.get(i);if(!staged.containsKey(actor.entityId()))continue;
            var state=combat.enemyState(birth.world(),actor.entityId()).orElseThrow();
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());var marker=staged.get(actor.entityId());
            var npc=store.getComponent(ref,NPCEntity.getComponentType());
            var nativeStatuses=new TreeSet<String>();
            var actorRole=nativeBindings.requireActorRole(actor);
            if(actorRole.nativeStunStaggerImmune())nativeStatuses.add("STUN_STAGGER");
            if(actorRole.nativeSlowImmune())nativeStatuses.add("SLOW_MOVEMENT");
            boolean nativeInvulnerable=(marker.addedFlags()&2)==0
                    ||npc.getRole()!=null&&npc.getRole().isInvulnerable();
            var shield=store.getComponent(ref,EnemyShieldProjection.getComponentType());
            double shieldRemaining=shield==null?0:shield.snapshot().remaining();
            var dto=EnemyDisplayDto.project(actor,EnemyAffixRegistry.canonical(),state.providers(),pack,0,
                    actor.nameToken(),names.get(i),nativeStatuses,nativeInvulnerable,shieldRemaining);
            displays.add(new Display(actor,dto,ref));
        }
        for(var display:displays){
            try{
                HytaleEnemyPalette.apply(store,display.ref(),display.actor(),visuals);
                weaponVisuals.track(display.actor());
            }
            catch(RuntimeException presentationFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(presentationFailure).log(
                        "RPG_ENEMY_VISUAL_APPLY_FAILED role=%s entity=%s gameplayContinues=true",
                        display.actor().nativeRoleId(),display.actor().entityId());
            }
            try{
                HytaleEliteTint.applyEliteTint(display.ref(),display.actor().enemyRarity());
            }catch(RuntimeException tintFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(tintFailure).log(
                        "RPG_ENEMY_TINT_APPLY_FAILED role=%s entity=%s gameplayContinues=true",
                        display.actor().nativeRoleId(),display.actor().entityId());
            }
            combat.publishEnemyDisplay(store,display.actor(),display.dto(),true);
        }
        if(!staged.isEmpty())EnemyStaging.releaseGroup(store,List.copyOf(staged.values()));
    }
}
