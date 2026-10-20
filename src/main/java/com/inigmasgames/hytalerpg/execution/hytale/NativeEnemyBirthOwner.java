package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.world.component.SpawnJobData;
import com.inigmasgames.hytalerpg.enemies.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Serializes one captured native flock through the existing durable birth and pack owners. */
public final class NativeEnemyBirthOwner implements NativeEnemySpawnGroups.Owner {
    private final EnemyNativeBindings bindings;
    private final EnemyWorldAdmission admission;
    private final HytaleEncounterRewards rewards;
    private final HytaleDifficultyCombat combat;
    private final NativeEnemyActions actions;
    private final NativeEnemyBirthReservation reservation;
    private final NativeEnemyBirthAttachment attachment;
    private final NativeEnemyBirthPublication publication;
    private final boolean nativeHooksReady;
    private final Map<UUID,NativeEnemyBirthAttachment.Prepared> liveActors=new HashMap<>();

    public NativeEnemyBirthOwner(EnemyNativeBindings bindings,EnemyWorldAdmission admission,
            HytaleEncounterRewards rewards,HytaleDifficultyCombat combat,NativeEnemyActions actions,
            NativeEnemyBirthReservation reservation,
            NativeEnemyBirthAttachment attachment,NativeEnemyBirthPublication publication,
            boolean nativeHooksReady){
        this.bindings=Objects.requireNonNull(bindings);this.admission=Objects.requireNonNull(admission);
        this.rewards=Objects.requireNonNull(rewards);this.combat=Objects.requireNonNull(combat);
        this.actions=Objects.requireNonNull(actions);
        this.reservation=Objects.requireNonNull(reservation);
        this.attachment=Objects.requireNonNull(attachment);this.publication=Objects.requireNonNull(publication);
        this.nativeHooksReady=nativeHooksReady;
    }

    @Override public boolean eligible(NativeEnemySpawnGroups.Job job){
        return nativeHooksReady&&job.expectedMembers()<=8&&admission.admits(job.world())
                &&bindings.role(job.nativeRole()).map(EnemyNativeBindings.Role::productionPromotionEnabled).orElse(false);
    }

    @Override public Optional<NativeEnemySpawnGroups.Reservation> reserve(NativeEnemySpawnGroups.Job job,
            UUID firstNativeEntity,String nativeRole){
        if(!job.nativeRole().equals(nativeRole)||!eligible(job))return Optional.empty();
        // The native UUID is minted before its holder enters the store. It is stable across replay.
        var encounter=UUID.nameUUIDFromBytes(("me.encounter/"+job.world()+"/"+job.nativeJobId()+"/"
                +firstNativeEntity).getBytes(StandardCharsets.UTF_8));
        return Optional.of(new NativeEnemySpawnGroups.Reservation(job.world(),encounter,1));
    }

    @Override public void captured(Store<EntityStore> store,NativeEnemySpawnGroups.Group group){
        throw new IllegalStateException("ENEMY_BIRTH_NATIVE_JOB_REQUIRED");
    }

    @Override public void captured(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,SpawnJobData nativeJob){
        if(!store.isInThread()||nativeJob==null||!eligible(group.job()))
            throw new IllegalStateException("ENEMY_BIRTH_CAPTURE_OWNER");
        try{processBirth(store,group,reservation.begin(store,group,nativeJob),null);}
        catch(RuntimeException rejected){fail(group.job().world(),"RESERVE",rejected);throw rejected;}
    }

    /** The command has already spawned and staged the exact native roster on this world thread. */
    public java.util.concurrent.CompletionStage<UUID> qaCaptured(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group group,EnemyQaSpawnRequest qa){
        if(!store.isInThread()||!nativeHooksReady||!admission.admits(group.job().world()))
            throw new IllegalStateException("ENEMY_QA_BIRTH_UNAVAILABLE");
        var done=new java.util.concurrent.CompletableFuture<UUID>();
        processBirth(store,group,reservation.beginQa(store,group,qa),done);
        return done.minimalCompletionStage();
    }
    /** Operator placement keeps the native NPC factory and the ordinary ME birth publication chain. */
    public java.util.concurrent.CompletionStage<UUID> qaSpawn(Store<EntityStore> store,
            com.hypixel.hytale.server.core.universe.world.World world,org.joml.Vector3dc playerPosition,
            com.hypixel.hytale.math.vector.Rotation3fc facing,EnemyQaSpawnRequest qa){
        if(!store.isInThread()||!world.getWorldConfig().getUuid().equals(store.getExternalData().getWorld().getWorldConfig().getUuid())
                ||!nativeHooksReady||!admission.admits(world.getWorldConfig().getUuid()))
            throw new IllegalStateException("ENEMY_QA_WORLD_UNAVAILABLE");
        var role=bindings.role(qa.nativeRoleId()).orElseThrow(()->new IllegalArgumentException(
                "Role has no Master Enemies binding: "+qa.nativeRoleId()));
        var npc=com.hypixel.hytale.server.npc.NPCPlugin.get();
        npc.validateSpawnableRole(qa.nativeRoleId());
        int count=qa.rarity()==EnemyRarity.CHAMPION?1:3+(qa.affixIds().contains("ME-022")?2:0);
        int roleIndex=npc.getIndex(qa.nativeRoleId());
        UUID worldId=world.getWorldConfig().getUuid(),encounter=UUID.randomUUID();
        var reservationId=new NativeEnemySpawnGroups.Reservation(worldId,encounter,1);
        var members=new ArrayList<NativeEnemySpawnGroups.Member>();
        try{
            double forwardX=-Math.sin(facing.yaw()),forwardZ=-Math.cos(facing.yaw());
            var anchor=com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,
                    playerPosition.x()+forwardX*7,playerPosition.y(),playerPosition.z()+forwardZ*7);
            for(int index=0;index<count;index++){
                double angle=2*Math.PI*(index-1)/Math.max(1,count-1);
                double x=index==0?anchor.x():anchor.x()+Math.cos(angle)*5;
                double z=index==0?anchor.z():anchor.z()+Math.sin(angle)*5;
                var at=index==0?anchor:com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,x,anchor.y(),z);
                var result=npc.spawnEntity(store,roleIndex,at,facing,null,(entity,holder,current)->{
                    var model=holder.getComponent(com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType());
                    if(model==null)throw new IllegalStateException("ENEMY_QA_NATIVE_MODEL_MISSING");
                    com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.requireClear(world,at,model.getModel().getBoundingBox());
                    var staged=EnemyStaging.prepare(holder,worldId,encounter,1);
                    members.add(new NativeEnemySpawnGroups.Member(staged.entity(),qa.nativeRoleId(),staged));
                },null);
                if(result==null||members.size()!=index+1)throw new IllegalStateException("ENEMY_QA_NATIVE_SPAWN_FAILED");
            }
            var job=new NativeEnemySpawnGroups.Job(worldId,-1,roleIndex,qa.nativeRoleId(),-1,-1,count);
            var group=new NativeEnemySpawnGroups.Group(job,reservationId,members,false);
            return qaCaptured(store,group,qa);
        }catch(RuntimeException rejected){
            for(var member:members){
                var ref=store.getExternalData().getRefFromUUID(member.entity());
                if(ref!=null&&ref.isValid()&&store.getComponent(ref,EnemyStaging.getComponentType())!=null)
                    store.removeEntity(ref,RemoveReason.REMOVE);
            }
            throw rejected;
        }
    }
    private void processBirth(Store<EntityStore> store,NativeEnemySpawnGroups.Group group,
            java.util.concurrent.CompletionStage<Optional<NativeEnemyBirthReservation.Sealed>> start,
            java.util.concurrent.CompletableFuture<UUID> done){
        var world=store.getExternalData().getWorld();
        try{start.whenComplete((maybe,error)->onWorld(world,group.job().world(),()->{
            if(error!=null){fail(group.job().world(),"RESERVE",error);if(done!=null)done.completeExceptionally(error);return;}
            if(maybe.isEmpty()){
                if(done!=null){discardQaStaged(world.getEntityStore().getStore(),group);
                    done.completeExceptionally(new IllegalStateException("ENEMY_QA_CAPACITY_UNAVAILABLE"));}
                return; // Natural decision/capacity owner has restored its ordinary native group.
            }
            var sealed=maybe.get();
            try{attachment.prepare(world.getEntityStore().getStore(),sealed).whenComplete((prepared,attachError)->
                    onWorld(world,group.job().world(),()->{
                        if(attachError!=null){compensate(world.getEntityStore().getStore(),sealed,attachError);
                            if(done!=null)done.completeExceptionally(attachError);return;}
                        try{
                            prepared.activate(hit->{},rejected->fail(group.job().world(),"ACTION",rejected));
                        }catch(RuntimeException activationFailure){
                            try{prepared.close();}catch(Exception cleanup){activationFailure.addSuppressed(cleanup);}
                            compensate(world.getEntityStore().getStore(),sealed,activationFailure);
                            if(done!=null)done.completeExceptionally(activationFailure);return;
                        }
                        try{publication.publish(world.getEntityStore().getStore(),sealed,prepared)
                                .whenComplete((ignored,publishError)->onWorld(world,group.job().world(),()->{
                                    if(publishError!=null){
                                        try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                                        catch(RuntimeException staging){publishError.addSuppressed(staging);}
                                        try{prepared.close();}catch(Exception cleanup){publishError.addSuppressed(cleanup);}
                                        fail(group.job().world(),"PUBLISH",publishError);
                                        if(done!=null)done.completeExceptionally(publishError);return;
                                    }
                                    try{registerPublished(world.getEntityStore().getStore(),prepared);
                                        if(done!=null)done.complete(sealed.selected().root().encounter());}
                                    catch(RuntimeException lifetimeFailure){
                                        try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                                        catch(RuntimeException staging){lifetimeFailure.addSuppressed(staging);}
                                        try{prepared.close();}catch(Exception cleanup){lifetimeFailure.addSuppressed(cleanup);}
                                        fail(group.job().world(),"LIFETIME",lifetimeFailure);
                                        if(done!=null)done.completeExceptionally(lifetimeFailure);
                                    }
                                }));}
                        catch(RuntimeException publishRejection){
                            try{stagePrepared(world.getEntityStore().getStore(),prepared);}
                            catch(RuntimeException staging){publishRejection.addSuppressed(staging);}
                            try{prepared.close();}catch(Exception cleanup){publishRejection.addSuppressed(cleanup);}
                            fail(group.job().world(),"PUBLISH",publishRejection);
                            if(done!=null)done.completeExceptionally(publishRejection);
                        }
                    }));}
            catch(RuntimeException attachRejection){compensate(world.getEntityStore().getStore(),sealed,attachRejection);
                if(done!=null)done.completeExceptionally(attachRejection);}
        }));}catch(RuntimeException rejection){fail(group.job().world(),"RESERVE",rejection);
            if(done!=null)done.completeExceptionally(rejection);throw rejection;}
    }

    private void compensate(Store<EntityStore> store,NativeEnemyBirthReservation.Sealed sealed,Throwable cause){
        var selected=sealed.selected();var root=selected.root();
        try{rewards.compensateStagedNativeGroup(store,selected.original(),selected.additional(),root)
                .whenComplete((ignored,error)->{
                    if(error!=null){error.addSuppressed(cause);fail(root.world(),"COMPENSATE",error);return;}
                    if(root.plan().actors().stream().allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA)){
                        var world=store.getExternalData().getWorld();
                        onWorld(world,root.world(),()->{
                            for(var member:selected.original().members()){
                                var ref=world.getEntityStore().getStore().getExternalData().getRefFromUUID(member.entity());
                                if(ref!=null&&ref.isValid())world.getEntityStore().getStore().removeEntity(ref,RemoveReason.REMOVE);
                            }
                        });
                    }
                });}
        catch(RuntimeException rejected){rejected.addSuppressed(cause);fail(root.world(),"COMPENSATE",rejected);}
    }
    private void discardQaStaged(Store<EntityStore> store,NativeEnemySpawnGroups.Group group){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_QA_DISCARD_WORLD_THREAD");
        for(var member:group.members()){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            if(ref!=null&&ref.isValid()){
                var marker=store.getComponent(ref,EnemyStaging.getComponentType());
                if(marker==null||!marker.state().equals(member.staging()))
                    throw new IllegalStateException("ENEMY_QA_DISCARD_IDENTITY_CHANGED");
                store.removeEntity(ref,RemoveReason.REMOVE);
            }
        }
    }

    private void onWorld(com.hypixel.hytale.server.core.universe.world.World world,UUID id,Runnable action){
        var guarded=new Runnable(){@Override public void run(){
            try{action.run();}
            catch(RuntimeException|Error failure){fail(id,"WORLD_CALLBACK",failure);}
        }};
        try{
            // Publication completes on the world thread. Register its lifetime before a later native removal.
            if(world.getEntityStore().getStore().isInThread())guarded.run();
            else world.execute(guarded);
        }
        catch(RuntimeException queueFailure){
            fail(id,"WORLD_QUEUE",queueFailure);
        }
    }

    void fail(UUID world,String phase,Throwable error){
        admission.failClosed(world);
        combat.quarantineEnemyWorld(world);
        actions.quarantineWorld(world);
        var nativeWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(world);
        if(nativeWorld!=null){
            var pause=new Runnable(){@Override public void run(){
                var store=nativeWorld.getEntityStore().getStore();
                List<Map.Entry<UUID,NativeEnemyBirthAttachment.Prepared>> targets;
                synchronized(NativeEnemyBirthOwner.this){targets=List.copyOf(liveActors.entrySet());}
                for(var entry:targets){
                    var birth=entry.getValue().birth();if(!birth.world().equals(world))continue;
                    var actor=birth.actors().stream().filter(a->a.entityId().equals(entry.getKey())).findFirst().orElse(null);
                    if(actor==null)continue;
                    var ref=store.getExternalData().getRefFromUUID(actor.entityId());
                    if(ref==null||!ref.isValid())continue;
                    try{
                        var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
                        if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))
                            throw new IllegalStateException("ENEMY_QUARANTINE_ACTOR_IDENTITY");
                        var existing=store.getComponent(ref,EnemyStaging.getComponentType());
                        EnemyStaging.prepareExisting(store,ref,world,birth.encounter(),birth.generation(),
                                existing!=null?existing.state().additional():
                                        !birth.originalNativeEntities().contains(actor.entityId()));
                    }catch(RuntimeException stagingFailure){
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(stagingFailure)
                                .log("RPG_ENEMY_QUARANTINE_STAGING_FAILED world=%s entity=%s",world,actor.entityId());
                    }
                }
            }};
            try{if(nativeWorld.getEntityStore().getStore().isInThread())pause.run();else nativeWorld.execute(pause);}
            catch(RuntimeException queueFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(queueFailure)
                        .log("RPG_ENEMY_QUARANTINE_QUEUE_FAILED world=%s",world);
            }
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(error)
                .log("RPG_ENEMY_BIRTH_FAILED phase=%s world=%s admission=false",phase,world);
    }

    /** Covers the interval after native release but before the lifetime map accepts the group. */
    void stagePrepared(Store<EntityStore> store,NativeEnemyBirthAttachment.Prepared prepared){
        var birth=prepared.birth();
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(birth.world()))
            throw new IllegalStateException("ENEMY_QUARANTINE_PREPARED_WORLD");
        RuntimeException failures=null;
        for(var actor:prepared.active())try{
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            if(ref==null||!ref.isValid())continue;
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))
                throw new IllegalStateException("ENEMY_QUARANTINE_PREPARED_IDENTITY");
            var existing=store.getComponent(ref,EnemyStaging.getComponentType());
            boolean additional=existing!=null?existing.state().additional():
                    !birth.originalNativeEntities().contains(actor.entityId());
            EnemyStaging.prepareExisting(store,ref,birth.world(),birth.encounter(),birth.generation(),additional);
        }catch(RuntimeException failure){
            if(failures==null)failures=failure;else failures.addSuppressed(failure);
        }
        if(failures!=null)throw failures;
    }

    /** A later chunk LOAD may rebind one surviving member while its packmates remain live. */
    public synchronized void registerPublished(Store<EntityStore> store,NativeEnemyBirthAttachment.Prepared prepared){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid()
                .equals(prepared.birth().world())||prepared.active().isEmpty())
            throw new IllegalStateException("ENEMY_BIRTH_LIFETIME_WORLD");
        for(var actor:prepared.active())if(liveActors.containsKey(actor.entityId()))
            throw new IllegalStateException("ENEMY_BIRTH_DUPLICATE_LIVE_ACTOR");
        if(liveActors.size()+prepared.active().size()>4096)throw new IllegalStateException("ENEMY_BIRTH_LIFETIME_CAPACITY");
        for(var actor:prepared.active())liveActors.put(actor.entityId(),prepared);
    }
    public synchronized boolean bound(UUID nativeEntity){return liveActors.containsKey(nativeEntity);}

    private synchronized void removed(Store<EntityStore> store,EnemyActorIdentity.State identity,RemoveReason reason){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_BIRTH_REMOVE_WORLD_THREAD");
        var prepared=liveActors.get(identity.nativeEntity());if(prepared==null)return;
        var birth=prepared.birth();
        if(!birth.world().equals(identity.world())||!birth.encounter().equals(identity.encounter())
                ||birth.generation()!=identity.generation()
                ||birth.actors().stream().noneMatch(actor->EnemyActorIdentity.State.of(actor).equals(identity))){
            fail(identity.world(),"REMOVE_IDENTITY",new IllegalStateException("ENEMY_BIRTH_REMOVE_GENERATION"));return;
        }
        if(reason==RemoveReason.UNLOAD){
            try{
                var pack=combat.enemyPack(identity.world(),identity.nativeEntity()).orElse(null);
                if(pack==null){
                    fail(identity.world(),"UNLOAD_PACK_MISSING",new IllegalStateException("ENEMY_UNLOAD_PACK_NOT_BOUND"));
                }else if(pack.state()==EnemyPackRecord.State.GUARDED||pack.state()==EnemyPackRecord.State.RELEASED){
                    var suspended=pack.suspend();
                    // Close action/protection admission for surviving loaded packmates on this world thread.
                    combat.refreshEnemyPack(store,suspended);
                    try{rewards.transitionEnemyPack(identity.world(),identity.pack(),current->{
                        if(!current.worldId().equals(identity.world())||current.generation()!=identity.generation()
                                ||!current.encounterId().equals(identity.encounter())
                                ||!current.deadMemberReceipts().equals(pack.deadMemberReceipts()))
                            throw new IllegalStateException("ENEMY_UNLOAD_PACK_CHANGED");
                        return current.state()==EnemyPackRecord.State.SUSPENDED?current:current.suspend();
                    }).whenComplete((persisted,error)->{
                        if(error!=null)fail(identity.world(),"UNLOAD_SUSPEND",error);
                    });}catch(RuntimeException rejected){fail(identity.world(),"UNLOAD_SUSPEND",rejected);}
                }
            }catch(RuntimeException suspensionFailure){fail(identity.world(),"UNLOAD_PROTECTION",suspensionFailure);}
        }
        try{
            prepared.retireActor(identity.nativeEntity());
            liveActors.remove(identity.nativeEntity());
            if(prepared.allRetired())prepared.close();
        }catch(Exception failure){fail(identity.world(),"REMOVE_LIFETIME",failure);}
    }

    /** Native removal/unload is a lifetime event, never a defeat or compensation decision. */
    public static final class Removal extends RefSystem<EntityStore> {
        private final NativeEnemyBirthOwner owner;
        public Removal(NativeEnemyBirthOwner owner){this.owner=Objects.requireNonNull(owner);}
        @Override public Query<EntityStore> getQuery(){return EnemyActorIdentity.getComponentType();}
        /** Suspend a live pack before encounter tracking detaches its native actor context. */
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.BEFORE,HytaleEncounterRewards.Tracking.class));}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity!=null)owner.removed(store,identity.state(),reason);
        }
    }
}
