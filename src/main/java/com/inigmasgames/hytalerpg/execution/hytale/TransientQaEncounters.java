package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/** Session-only QA pack and action-root storage. Affix owners consume HytaleDifficultyCombat as usual. */
public final class TransientQaEncounters {
    private record ActorKey(UUID world,UUID actor) {}
    private static final class Group {
        final EnemyBirthPlan birth;
        final Set<UUID> remaining=new HashSet<>();
        final Map<UUID,Long> actionHighWater=new HashMap<>();
        EnemyPackRecord pack;
        NativeEnemyBirthAttachment.Prepared prepared;
        Group(EnemyBirthPlan birth){this.birth=birth;this.pack=birth.pack();
            birth.actors().forEach(actor->remaining.add(actor.entityId()));}
    }
    private final Map<UUID,Group> encounters=new HashMap<>();
    private final Map<ActorKey,UUID> actors=new HashMap<>();
    private final BiConsumer<UUID,UUID> forget;
    private final BiConsumer<Store<EntityStore>,EnemyPackRecord> refreshPack;
    public TransientQaEncounters(HytaleDifficultyCombat combat){
        this(Objects.requireNonNull(combat)::forget,combat::refreshEnemyPack);
    }
    TransientQaEncounters(BiConsumer<UUID,UUID> forget,
            BiConsumer<Store<EntityStore>,EnemyPackRecord> refreshPack){
        this.forget=Objects.requireNonNull(forget);this.refreshPack=Objects.requireNonNull(refreshPack);
    }

    public synchronized void reserve(EnemyBirthPlan birth) {
        if(birth.pack()==null||birth.actors().isEmpty()||birth.actors().stream().anyMatch(actor->
                actor.spawnOrigin()!=EnemyRewardContext.Origin.QA||!actor.worldId().equals(birth.world())
                        ||!actor.encounterId().equals(birth.encounter())))
            throw new IllegalArgumentException("QA_TRANSIENT_BIRTH_PROVENANCE");
        if(encounters.size()>=4096||encounters.containsKey(birth.encounter()))
            throw new IllegalStateException("QA_TRANSIENT_CAPACITY");
        for(var actor:birth.actors())if(actors.containsKey(new ActorKey(birth.world(),actor.entityId())))
            throw new IllegalStateException("QA_TRANSIENT_DUPLICATE_ACTOR");
        var group=new Group(birth);encounters.put(birth.encounter(),group);
        for(var actor:birth.actors())actors.put(new ActorKey(birth.world(),actor.entityId()),birth.encounter());
    }
    public synchronized CompletionStage<FileEncounterStore.EnemyActionRootBlock> reserveActionRoots(
            EnemyDescriptor actor,int count) {
        var group=encounters.get(actor.encounterId());
        if(group==null||!group.birth.actors().contains(actor)||!group.remaining.contains(actor.entityId())
                ||count<1||count>256)throw new IllegalStateException("QA_ACTION_ROOT_OWNER");
        long first=Math.addExact(group.actionHighWater.getOrDefault(actor.logicalActorId(),0L),1);
        long last=Math.addExact(first,count-1);
        group.actionHighWater.put(actor.logicalActorId(),last);
        return CompletableFuture.completedStage(new FileEncounterStore.EnemyActionRootBlock(
                actor.worldId(),actor.logicalActorId(),actor.encounterGeneration(),first,last));
    }
    public synchronized void publish(EnemyBirthPlan birth,NativeEnemyBirthAttachment.Prepared prepared) {
        var group=encounters.get(birth.encounter());
        if(group==null||group.birth!=birth||group.prepared!=null||prepared==null||prepared.birth()!=birth)
            throw new IllegalStateException("QA_TRANSIENT_PUBLICATION_BINDING");
        group.prepared=prepared;group.pack=birth.pack().staged().publish();
    }
    public synchronized boolean contains(UUID world,UUID actor){return actors.containsKey(new ActorKey(world,actor));}
    public synchronized int size(){return encounters.size();}
    public synchronized EnemyPackRecord pack(UUID world,UUID actor){
        var id=actors.get(new ActorKey(world,actor));var group=id==null?null:encounters.get(id);
        return group==null?null:group.pack;
    }
    public synchronized void defeat(Store<EntityStore> store,UUID actor){
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        defeat(store,world,actor);
    }
    synchronized void defeat(Store<EntityStore> store,UUID world,UUID actor){
        var group=encounters.get(actors.get(new ActorKey(world,actor)));
        if(group==null||group.prepared==null)return;
        var descriptor=group.birth.actors().stream().filter(a->a.entityId().equals(actor)).findFirst().orElseThrow();
        var next=group.pack.terminalDefeat(descriptor.logicalActorId(),"enemy-death/"+world+"/"+actor);
        if(next!=group.pack){group.pack=next;refreshPack.accept(store,next);}
    }
    public synchronized List<UUID> loadedActors(UUID world){
        return actors.keySet().stream().filter(key->key.world().equals(world)).map(ActorKey::actor).toList();
    }
    /** Called by the native removal owner. Death receipts stay in the pack until remaining guards retire. */
    public synchronized void retire(Store<EntityStore> store,UUID actor) throws Exception {
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        retire(world,actor);
    }
    synchronized void retire(UUID world,UUID actor) throws Exception {
        var key=new ActorKey(world,actor);var encounter=actors.remove(key);
        if(encounter==null)return;
        var group=encounters.get(encounter);if(group==null)return;
        group.remaining.remove(actor);
        forget.accept(world,actor);
        if(group.prepared!=null)group.prepared.retireActor(actor);
        if(group.remaining.isEmpty()){
            encounters.remove(encounter);
            if(group.prepared!=null)group.prepared.close();
        }
    }
    public synchronized void discard(EnemyBirthPlan birth){
        var group=encounters.remove(birth.encounter());
        if(group!=null)for(var actor:birth.actors())actors.remove(new ActorKey(birth.world(),actor.entityId()));
    }
    /** Plugin shutdown drops session-only identities; native actor/action owners close on world teardown. */
    public synchronized void clear(){encounters.clear();actors.clear();}
}
