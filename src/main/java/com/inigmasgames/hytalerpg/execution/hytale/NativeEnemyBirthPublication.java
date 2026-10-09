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
        try{rewards.transitionEnemyPack(birth.world(),birth.pack().packId(),EnemyPackRecord::staged)
                .thenCompose(staged->{
                    if(!staged.equals(birth.pack().staged()))
                        throw new IllegalStateException("ENEMY_BIRTH_STAGED_PACK_MISMATCH");
                    return rewards.transitionEnemyPack(birth.world(),birth.pack().packId(),EnemyPackRecord::publish);
                }).whenComplete((published,error)->{
                    if(error!=null){admission.failClosed(birth.world());result.completeExceptionally(error);return;}
                    try{nativeWorld.execute(()->{
                        try{
                            var current=nativeWorld.getEntityStore().getStore();
                            if(!current.isInThread()||!admission.admits(birth.world())
                                    ||published==null||!published.equals(birth.pack().staged().publish()))
                                throw new IllegalStateException("ENEMY_BIRTH_PUBLISHED_PACK_MISMATCH");
                            finish(current,birth,published,attachment,
                                    selected.sources().stream().map(EnemyNativeGroupPreparation.MemberSource::nativeName).toList(),false);
                            result.complete(null);
                        }catch(RuntimeException failure){admission.failClosed(birth.world());result.completeExceptionally(failure);}
                    });}catch(RuntimeException closing){admission.failClosed(birth.world());result.completeExceptionally(closing);}
                });}catch(RuntimeException rejected){admission.failClosed(birth.world());result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }

    /** Finish a saved birth from its current durable state; no rarity, affix or actor is regenerated. */
    public CompletionStage<Void> publishRecovered(Store<EntityStore> store,EnemyBirthPlan birth,
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
        var nativeWorld=store.getExternalData().getWorld();var result=new CompletableFuture<Void>();
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
            if(error!=null){admission.failClosed(birth.world());result.completeExceptionally(error);return;}
            try{nativeWorld.execute(()->{
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    var names=new ArrayList<String>();
                    for(var actor:attachment.active()){
                        var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                        var npc=ref==null||!ref.isValid()?null:current.getComponent(ref,NPCEntity.getComponentType());
                        if(npc==null)throw new IllegalStateException("ENEMY_REBIND_DISPLAY_NATIVE_MISSING");
                        var name=HytaleDifficultyCombat.nativeDisplayName(current,ref,npc);
                        if(name==null||name.isBlank()||name.startsWith("server.")||name.contains("_"))
                            throw new IllegalStateException("ENEMY_REBIND_DISPLAY_NAME_MISSING");
                        names.add(name);
                    }
                    finish(current,birth,published,attachment,names,false);
                    result.complete(null);
                }catch(RuntimeException failure){admission.failClosed(birth.world());result.completeExceptionally(failure);}
            });}catch(RuntimeException closing){admission.failClosed(birth.world());result.completeExceptionally(closing);}
        });}catch(RuntimeException rejected){admission.failClosed(birth.world());result.completeExceptionally(rejected);}
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
        var staged=new ArrayList<EnemyStaging.State>();var displays=new ArrayList<Display>();
        for(var actor:actors){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
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
            staged.add(marker.state());
        }
        attachment.published(); // Saved clock and shield must survive an interrupted presentation/release.
        combat.refreshEnemyPack(store,pack);
        for(int i=0;i<actors.size();i++){
            var actor=actors.get(i);var state=combat.enemyState(birth.world(),actor.entityId()).orElseThrow();
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());var marker=staged.get(i);
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
        EnemyStaging.releaseGroup(store,staged);
    }
}
