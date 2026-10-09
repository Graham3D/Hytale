package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.*;
import java.util.*;

/** Rebinds saved native actors from the immutable birth; it never re-enters the rarity planner. */
public final class NativeEnemyWholeBirthRecovery extends RefSystem<EntityStore> {
    private record ActorKey(UUID world,UUID entity){}
    private record PackKey(UUID world,UUID pack){}
    private record Sealed(EnemyBirthRoot root,EnemyPackRecord pack){}
    private final EnemyWorldAdmission admission;
    private final HytaleEncounterRewards rewards;
    private final EnemyNativeBindings bindings;
    private final NativeEnemyBirthAttachment attachment;
    private final NativeEnemyBirthPublication publication;
    private final NativeEnemyBirthOwner owner;
    private final Set<ActorKey> loading=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<PackKey> groups=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile java.util.function.Consumer<World> published=world->{};
    public NativeEnemyWholeBirthRecovery(EnemyWorldAdmission admission,HytaleEncounterRewards rewards,
            EnemyNativeBindings bindings,NativeEnemyBirthAttachment attachment,
            NativeEnemyBirthPublication publication,NativeEnemyBirthOwner owner){
        this.admission=Objects.requireNonNull(admission);this.rewards=Objects.requireNonNull(rewards);
        this.bindings=Objects.requireNonNull(bindings);this.attachment=Objects.requireNonNull(attachment);
        this.publication=Objects.requireNonNull(publication);this.owner=Objects.requireNonNull(owner);
    }
    public void onPublished(java.util.function.Consumer<World> callback){published=Objects.requireNonNull(callback);}
    // A sealed root may exist before the saved actor identity is installed. The staging
    // marker survives that interruption, while a published identity is restaged on LOAD.
    @Override public Query<EntityStore> getQuery(){return EnemyStaging.getComponentType();}
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){
        if(reason!=AddReason.LOAD)return;
        var marker=store.getComponent(ref,EnemyStaging.getComponentType());
        var nativeId=store.getComponent(ref,UUIDComponent.getComponentType());
        var saved=store.getComponent(ref,EnemyActorIdentity.getComponentType());
        if(marker==null||nativeId==null)return;
        var staged=marker.state();
        var identity=saved==null?null:saved.state();
        var key=new ActorKey(staged.world(),staged.entity());
        if(!store.isInThread()||!staged.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid())
                ||!staged.entity().equals(nativeId.getUuid())
                ||identity!=null&&(!identity.world().equals(staged.world())
                    ||!identity.encounter().equals(staged.encounter())
                    ||identity.generation()!=staged.generation()
                    ||!identity.nativeEntity().equals(staged.entity())))
            throw new IllegalStateException("ENEMY_REBIND_LOAD_WORLD");
        if(admission.failed(staged.world()))return;
        if(identity!=null&&owner.bound(identity.nativeEntity())||!loading.add(key))return;
        var world=store.getExternalData().getWorld();
        try{admission.begin(staged.world()).thenCompose(ignored->rewards.enemyBirthRoot(staged.world(),staged.encounter())
                .thenCompose(root->{
                    if(root.isEmpty())return java.util.concurrent.CompletableFuture.completedStage(Optional.<Sealed>empty());
                    var birth=root.get().plan();
                    if(birth.pack()==null)throw new IllegalStateException("ENEMY_REBIND_ROOT_WITHOUT_PACK");
                    return rewards.enemyPack(staged.world(),birth.pack().packId()).thenApply(pack->{
                        if(pack.isEmpty())throw new IllegalStateException("ENEMY_REBIND_ROOT_PACK_MISSING");
                        return Optional.of(new Sealed(root.get(),pack.get()));
                    });
                }))
                .whenComplete((sealed,error)->onWorld(world,staged.world(),()->{
                    if(error!=null){failed(staged.world(),"READ",error);loading.remove(key);return;}
                    if(sealed.isEmpty()){
                        loading.remove(key);
                        if(identity!=null)failed(staged.world(),"ROOT_MISSING",
                                new IllegalStateException("ENEMY_REBIND_SAVED_IDENTITY_WITHOUT_ROOT_OR_PACK"));
                        return;
                    }
                    var birth=sealed.get().root().plan();
                    var actor=birth.actors().stream().filter(value->value.entityId().equals(staged.entity()))
                            .findFirst().orElse(null);
                    if(actor==null||!birth.world().equals(staged.world())
                            ||!birth.encounter().equals(staged.encounter())
                            ||birth.generation()!=staged.generation()
                            ||!validStagingProvenance(birth,actor,staged,identity)){
                        loading.remove(key);
                        failed(staged.world(),"STAGING_IDENTITY",
                                new IllegalStateException("ENEMY_REBIND_STAGING_ROOT_MISMATCH"));
                        return;
                    }
                    if(sealed.get().pack().state()==EnemyPackRecord.State.ABORTED
                            ||sealed.get().pack().state()==EnemyPackRecord.State.DEFEATED){
                        loading.remove(key);return; // Terminal reconciliation owns these native actors.
                    }
                    var current=world.getEntityStore().getStore();
                    var live=current.getExternalData().getRefFromUUID(staged.entity());
                    var liveMarker=live==null||!live.isValid()?null:current.getComponent(live,EnemyStaging.getComponentType());
                    var npc=live==null||!live.isValid()?null:current.getComponent(live,NPCEntity.getComponentType());
                    if(liveMarker==null||!liveMarker.state().equals(staged)||npc==null
                            ||!npc.getRoleName().equals(actor.nativeRoleId())){
                        loading.remove(key);return; // Removed or replaced during the durable read.
                    }
                    var actual=current.getComponent(live,EnemyActorIdentity.getComponentType());
                    if(actual==null)current.addComponent(live,EnemyActorIdentity.getComponentType(),
                            new EnemyActorIdentity(EnemyActorIdentity.State.of(actor)));
                    else if(!actual.state().equals(EnemyActorIdentity.State.of(actor)))
                        throw new IllegalStateException("ENEMY_REBIND_CURRENT_IDENTITY_CHANGED");
                    rebind(world,EnemyActorIdentity.State.of(actor),key,sealed.get());
                }));}
        catch(RuntimeException rejected){loading.remove(key);failed(staged.world(),"READ",rejected);}
    }

    /** A published actor is freshly restaged on LOAD, so its new marker has no extra-minion bit. */
    static boolean validStagingProvenance(EnemyBirthPlan birth,EnemyDescriptor actor,
            EnemyStaging.State marker,EnemyActorIdentity.State saved){
        if(!birth.world().equals(marker.world())||!birth.encounter().equals(marker.encounter())
                ||birth.generation()!=marker.generation()||!actor.entityId().equals(marker.entity()))return false;
        if(saved!=null)return saved.equals(EnemyActorIdentity.State.of(actor));
        return birth.originalNativeEntities().contains(marker.entity())!=marker.additional();
    }

    private void rebind(World world,EnemyActorIdentity.State identity,ActorKey key,Sealed sealed){
        if(admission.failed(identity.world())){loading.remove(key);return;}
        var birth=sealed.root().plan();var pack=sealed.pack();
        if(pack.state()==EnemyPackRecord.State.ABORTED||pack.state()==EnemyPackRecord.State.DEFEATED){
            loading.remove(key);return; // Compensation and terminal cleanup have separate durable owners.
        }
        if(!birth.world().equals(identity.world())||!birth.encounter().equals(identity.encounter())
                ||birth.generation()!=identity.generation()||birth.pack()==null
                ||!birth.pack().packId().equals(identity.pack())
                ||birth.actors().stream().noneMatch(actor->EnemyActorIdentity.State.of(actor).equals(identity))){
            loading.remove(key);failed(identity.world(),"IDENTITY",new IllegalStateException("ENEMY_REBIND_ROOT_MISMATCH"));return;
        }
        try{
            for(var actor:birth.actors())bindings.requireActorRole(actor);
        }catch(RuntimeException unresolved){
            loading.remove(key);failed(identity.world(),"ROLE",new IllegalStateException("ENEMY_REBIND_NATIVE_REVISION"));return;
        }
        boolean whole=pack.state()==EnemyPackRecord.State.RESERVED
                ||pack.state()==EnemyPackRecord.State.STAGED;
        var groupKey=new PackKey(identity.world(),identity.pack());
        if(whole&&!groups.add(groupKey)){loading.remove(key);return;}
        var actors=whole?birth.activeActors(pack)
                :List.of(birth.actors().stream()
                .filter(actor->actor.entityId().equals(identity.nativeEntity())).findFirst().orElseThrow());
        if(actors.isEmpty()){loading.remove(key);if(whole)groups.remove(groupKey);return;}
        var store=world.getEntityStore().getStore();
        for(var actor:actors){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            var saved=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(marker==null||saved==null||!saved.state().equals(EnemyActorIdentity.State.of(actor))){
                loading.remove(key);if(whole)groups.remove(groupKey);return; // Another LOAD may complete the group.
            }
        }
        try{attachment.prepareRecovered(store,birth,pack,actors).whenComplete((prepared,error)->
                onWorld(world,identity.world(),()->{
                    if(error!=null){loading.remove(key);if(whole)groups.remove(groupKey);failed(identity.world(),"ATTACH",error);return;}
                    try{prepared.activate(hit->{},rejected->failed(identity.world(),"ACTION",rejected));}
                    catch(RuntimeException rejected){
                        try{prepared.close();}catch(Exception cleanup){rejected.addSuppressed(cleanup);}
                        loading.remove(key);if(whole)groups.remove(groupKey);failed(identity.world(),"ACTIVATE",rejected);return;
                    }
                    try{publication.publishRecovered(world.getEntityStore().getStore(),birth,pack,prepared,owner::bound)
                            .whenComplete((ignored,publishError)->onWorld(world,identity.world(),()->{
                                loading.remove(key);if(whole)groups.remove(groupKey);
                                if(publishError!=null){
                                    try{owner.stagePrepared(world.getEntityStore().getStore(),prepared);}
                                    catch(RuntimeException staging){publishError.addSuppressed(staging);}
                                    try{prepared.close();}catch(Exception cleanup){publishError.addSuppressed(cleanup);}
                                    failed(identity.world(),"PUBLISH",publishError);return;
                                }
                                try{
                                    owner.registerPublished(world.getEntityStore().getStore(),prepared,pack);
                                    published.accept(world);
                                }
                                catch(RuntimeException lifetimeFailure){
                                    try{owner.stagePrepared(world.getEntityStore().getStore(),prepared);}
                                    catch(RuntimeException staging){lifetimeFailure.addSuppressed(staging);}
                                    try{prepared.close();}catch(Exception cleanup){lifetimeFailure.addSuppressed(cleanup);}
                                    failed(identity.world(),"LIFETIME",lifetimeFailure);
                                }
                            }));}
                    catch(RuntimeException rejected){
                        try{owner.stagePrepared(world.getEntityStore().getStore(),prepared);}
                        catch(RuntimeException staging){rejected.addSuppressed(staging);}
                        try{prepared.close();}catch(Exception cleanup){rejected.addSuppressed(cleanup);}
                        loading.remove(key);if(whole)groups.remove(groupKey);failed(identity.world(),"PUBLISH",rejected);
                    }
                }));}
        catch(RuntimeException rejected){loading.remove(key);if(whole)groups.remove(groupKey);failed(identity.world(),"ATTACH",rejected);}
    }
    private void onWorld(World world,UUID id,Runnable action){
        var guarded=new Runnable(){@Override public void run(){
            try{action.run();}catch(RuntimeException|Error error){failed(id,"WORLD_CALLBACK",error);}
        }};
        try{if(world.getEntityStore().getStore().isInThread())guarded.run();else world.execute(guarded);}
        catch(RuntimeException closing){
            loading.removeIf(key->key.world().equals(id));
            groups.removeIf(key->key.world().equals(id));
            failed(id,"WORLD_QUEUE",closing);
        }
    }
    private void failed(UUID world,String phase,Throwable error){
        loading.removeIf(key->key.world().equals(world));
        groups.removeIf(key->key.world().equals(world));
        owner.fail(world,"REBIND_"+phase,error);
    }
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){}
}
