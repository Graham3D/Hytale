package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** World-load barrier over the existing durable birth replay and pack-capacity owner. */
public final class EnemyWorldAdmission {
    private final Function<UUID,CompletionStage<FileEncounterStore.EnemyWorldInventory>> recovery;
    private final EnemyPackCapacity capacity;
    private final boolean requireNativeReview;
    private final java.util.function.Supplier<String> configRevision;
    private final Map<UUID,CompletionStage<FileEncounterStore.EnemyWorldInventory>> loading=new HashMap<>();
    private final Map<UUID,FileEncounterStore.EnemyWorldInventory> recovered=new HashMap<>();
    private record Known(EnemyBirthPlan birth,EnemyPackRecord pack){}
    private final Map<UUID,Map<UUID,Known>> catalog=new HashMap<>();
    private final Set<UUID> rebound=new HashSet<>();
    private final Set<UUID> failed=new HashSet<>();

    public EnemyWorldAdmission(Function<UUID,CompletionStage<FileEncounterStore.EnemyWorldInventory>> recovery,
                               EnemyPackCapacity capacity){
        this(recovery,capacity,false,()->"unknown");
    }
    /** Production native worlds inspect staged LOAD actors even when no durable pack exists. */
    public EnemyWorldAdmission(Function<UUID,CompletionStage<FileEncounterStore.EnemyWorldInventory>> recovery,
                               EnemyPackCapacity capacity,boolean requireNativeReview){
        this(recovery,capacity,requireNativeReview,()->"unknown");
    }
    public EnemyWorldAdmission(Function<UUID,CompletionStage<FileEncounterStore.EnemyWorldInventory>> recovery,
                               EnemyPackCapacity capacity,boolean requireNativeReview,
                               java.util.function.Supplier<String> configRevision){
        this.recovery=Objects.requireNonNull(recovery);this.capacity=Objects.requireNonNull(capacity);
        this.requireNativeReview=requireNativeReview;this.configRevision=Objects.requireNonNull(configRevision);
    }

    /** Duplicate AddWorldEvent/boot observations share one replay. A failed replay never opens admission. */
    public synchronized CompletionStage<FileEncounterStore.EnemyWorldInventory> begin(UUID world){
        Objects.requireNonNull(world);
        var existing=loading.get(world);if(existing!=null)return existing;
        var result=new CompletableFuture<FileEncounterStore.EnemyWorldInventory>();
        CompletionStage<FileEncounterStore.EnemyWorldInventory> stage=result.minimalCompletionStage();
        loading.put(world,stage);
        try{recovery.apply(world).whenComplete((inventory,error)->{
            synchronized(this){
              if(loading.get(world)!=stage)return; // World was removed before the read completed.
              if(error!=null){result.completeExceptionally(error);return;}
              try{
                validate(world,inventory);
                // Validate all durable records; loaded capacity comes only from verified native actors.
                capacity.restore(world,productionCapacityPacks(inventory));
                var births=new HashMap<UUID,EnemyBirthPlan>();
                for(var birth:inventory.births())births.put(birth.encounter(),birth);
                var known=new HashMap<UUID,Known>();
                for(var pack:inventory.packs())known.put(pack.packId(),new Known(births.get(pack.encounterId()),pack));
                catalog.put(world,known);recovered.put(world,inventory);
                if(!requireNativeReview&&requiredRebind(inventory).isEmpty())rebound.add(world);
                result.complete(inventory);
              }catch(RuntimeException rejected){result.completeExceptionally(rejected);}
            }
        });}catch(RuntimeException rejected){result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }

    /** The native owner must reconcile every live birth and occupied anchor before fresh admission. */
    public synchronized void rebindComplete(UUID world,Collection<UUID> validatedEncounters){
        if(failed.contains(world))throw new IllegalStateException("ENEMY_WORLD_WRITER_UNCERTAIN");
        var inventory=recovered.get(world);
        if(inventory==null)throw new IllegalStateException("ENEMY_WORLD_RECOVERY_INCOMPLETE");
        var expected=requiredRebind(inventory);
        if(validatedEncounters==null||validatedEncounters.size()!=expected.size()||!expected.equals(new HashSet<>(validatedEncounters)))
            throw new IllegalStateException("ENEMY_WORLD_REBIND_INCOMPLETE");
        rebound.add(world);
    }

    public synchronized boolean admits(UUID world){return !failed.contains(world)&&rebound.contains(world)&&recovered.containsKey(world);}
    /** Read-only diagnostics: only loaded production packs and pending natural births. */
    public synchronized int activePackReservations(UUID world){return capacity.count(world);}
    public record Status(EnemyPackCapacity.Snapshot leases,int dormantNonterminalProductionPacks,int qaExcludedRecords){}
    public synchronized Status status(UUID world){
        int dormant=0,qa=0;
        for(var known:catalog.getOrDefault(world,Map.of()).values()){
            if(qa(known.birth())){qa++;continue;}
            var state=known.pack().state();
            if(state!=EnemyPackRecord.State.DEFEATED&&state!=EnemyPackRecord.State.ABORTED
                    &&!capacity.contains(EnemyPackCapacity.Reservation.of(known.pack())))dormant++;
        }
        return new Status(capacity.metrics(world),dormant,qa);
    }
    public synchronized boolean failed(UUID world){return failed.contains(world);}
    /** An uncertain writer result cannot be retried or treated as an ordinary spawn in this process. */
    public synchronized void failClosed(UUID world){Objects.requireNonNull(world);failed.add(world);rebound.remove(world);}
    public synchronized Optional<FileEncounterStore.EnemyWorldInventory> inventory(UUID world){return Optional.ofNullable(recovered.get(world));}
    /** The pack decision reserves through the existing capacity index only after world recovery/rebind. */
    public synchronized EnemyPackCapacity.Admission reserve(EnemyPackCapacity.Reservation reservation){
        if(!admits(reservation.world()))throw new IllegalStateException("ENEMY_WORLD_NOT_ADMITTED");
        int before=capacity.count(reservation.world());
        var result=capacity.reserve(reservation);
        trace(result.accepted()?"PACK_LEASE_ACQUIRED":"PACK_NEW_BIRTH_DENIED",reservation,null,
                "gate="+result.gate(),before);
        return result;
    }
    public synchronized boolean pending(EnemyPackCapacity.Reservation reservation){
        return admits(reservation.world())&&capacity.pending(reservation);
    }
    /** Verified world-thread publication/rebind: same immutable birth, original pack and native UUIDs. */
    public synchronized void activatePublished(EnemyBirthPlan birth,EnemyPackRecord pack,
            Collection<UUID> loadedActors){
        if(qa(birth))return;
        if(!recovered.containsKey(birth.world())||failed.contains(birth.world())
                ||birth.pack()==null||!birth.pack().packId().equals(pack.packId())
                ||!birth.encounter().equals(pack.encounterId())||birth.generation()!=pack.generation()
                ||pack.state()==EnemyPackRecord.State.DEFEATED||pack.state()==EnemyPackRecord.State.ABORTED
                ||!birth.activeActors(pack).stream().map(EnemyDescriptor::entityId).toList().containsAll(loadedActors)
                ||new HashSet<>(loadedActors).size()!=loadedActors.size())
            throw new IllegalStateException("ENEMY_PACK_LEASE_PUBLICATION_IDENTITY");
        var known=catalog.computeIfAbsent(birth.world(),ignored->new HashMap<>());
        var prior=known.get(pack.packId());
        if(prior!=null&&!prior.birth().equals(birth))throw new IllegalStateException("ENEMY_PACK_LEASE_REBIND_IDENTITY");
        known.put(pack.packId(),new Known(birth,pack));
        var reservation=EnemyPackCapacity.Reservation.of(pack);
        int before=capacity.count(pack.worldId());boolean pending=capacity.pending(reservation);
        int overBefore=capacity.metrics(pack.worldId()).grandfatheredOverCapPacks();
        boolean first=capacity.activate(reservation,loadedActors);
        if(first){
            trace(pending?"PACK_LEASE_ACTIVATED":"PACK_REACTIVATED",reservation,
                    loadedActors.iterator().next(),"members="+loadedActors.size(),before);
            if(!pending&&capacity.metrics(pack.worldId()).grandfatheredOverCapPacks()>overBefore)
                trace("PACK_GRANDFATHERED_OVER_CAP",reservation,loadedActors.iterator().next(),
                        "reason=INCUMBENT_REBIND",before);
        }else if(loadedActors.isEmpty()&&before!=capacity.count(pack.worldId()))
            trace("PACK_LEASE_SUSPENDED",reservation,null,"reason=UNLOADED_BEFORE_PUBLICATION",before);
    }
    public synchronized void beginSuspension(EnemyBirthPlan birth){
        if(!qa(birth))capacity.beginTransition(EnemyPackCapacity.Reservation.of(birth.pack()));
    }
    public synchronized void finishSuspension(EnemyBirthPlan birth,EnemyPackRecord persisted){
        if(qa(birth))return;
        var reservation=EnemyPackCapacity.Reservation.of(birth.pack());
        var known=catalog.getOrDefault(birth.world(),Map.of()).get(reservation.pack());
        if(known==null||!known.birth().equals(birth)||!persisted.packId().equals(reservation.pack())
                ||persisted.state()!=EnemyPackRecord.State.SUSPENDED)
            throw new IllegalStateException("ENEMY_PACK_LEASE_SUSPEND_IDENTITY");
        catalog.get(birth.world()).put(reservation.pack(),new Known(birth,persisted));
        int before=capacity.count(birth.world());
        if(capacity.endTransition(reservation))trace("PACK_LEASE_SUSPENDED",reservation,null,"reason=LAST_MEMBER_UNLOADED",before);
    }
    public synchronized void memberRemoved(EnemyBirthPlan birth,UUID actor,String reason){
        if(qa(birth))return;
        var reservation=EnemyPackCapacity.Reservation.of(birth.pack());
        if(birth.actors().stream().noneMatch(value->value.entityId().equals(actor)))
            throw new IllegalStateException("ENEMY_PACK_LEASE_FOREIGN_ACTOR");
        int before=capacity.count(birth.world());
        if(capacity.removeActor(reservation,actor))trace("PACK_LEASE_SUSPENDED",reservation,actor,
                "reason=LAST_MEMBER_"+reason,before);
    }
    /** Only a pre-seal fallback may use this; no durable special birth may be rolled back here. */
    public synchronized boolean releaseUnsealed(EnemyPackCapacity.Reservation reservation){
        int before=capacity.count(reservation.world());boolean released=capacity.release(reservation);
        if(released)trace("PACK_LEASE_RELEASED",reservation,null,"reason=UNSEALED_FALLBACK",before);
        MonsterSpawnTrace.event("PACK_RESERVATION",reservation.world(),-1,"all",
                "encounter="+reservation.encounter()+" reason=UNSEALED_RELEASE released="+released);
        return released;
    }
    /** Terminal durable pack state is the authority for releasing a published reservation. */
    public synchronized boolean releaseTerminal(EnemyPackRecord pack){
        if(pack.state()!=EnemyPackRecord.State.ABORTED&&pack.state()!=EnemyPackRecord.State.DEFEATED)
            throw new IllegalStateException("ENEMY_CAPACITY_RELEASE_NONTERMINAL");
        var reservation=EnemyPackCapacity.Reservation.of(pack);int before=capacity.count(pack.worldId());
        boolean released=capacity.release(reservation);
        var known=catalog.getOrDefault(pack.worldId(),Map.of()).get(pack.packId());
        if(known!=null)catalog.get(pack.worldId()).put(pack.packId(),new Known(known.birth(),pack));
        if(released)trace("PACK_LEASE_RELEASED",reservation,null,"reason="+pack.state(),before);
        MonsterSpawnTrace.event("PACK_RESERVATION",pack.worldId(),-1,"all",
                "encounter="+pack.encounterId()+" reason="+pack.state()+" released="+released);
        return released;
    }
    public synchronized void worldUnload(UUID world){
        capacity.unload(world);catalog.remove(world);recovered.remove(world);rebound.remove(world);failed.remove(world);
        loading.remove(world);
    }
    private void trace(String stage,EnemyPackCapacity.Reservation reservation,UUID actor,String detail,int before){
        MonsterSpawnTrace.event(stage,reservation.world(),-1,"all", "encounter="+reservation.encounter()
                +" pack="+reservation.pack()+" actor="+(actor==null?"none":actor)
                +" cell="+capacity.cellLabel(reservation.anchor())+" "+(detail.startsWith("reason=")?detail:"reason="+detail)
                +" occupancyBefore="+before+" occupancyAfter="+capacity.count(reservation.world())
                +" configRevision="+configRevision.get());
    }
    private static boolean qa(EnemyBirthPlan birth){return birth.actors().stream()
            .allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA);}

    private static void validate(UUID world,FileEncounterStore.EnemyWorldInventory inventory){
        Objects.requireNonNull(inventory);
        var births=new HashMap<UUID,EnemyBirthPlan>();var packs=new HashMap<UUID,EnemyBirthPlan>();
        for(var birth:inventory.births()){
            if(!world.equals(birth.world())||births.putIfAbsent(birth.encounter(),birth)!=null)throw new IllegalStateException("ENEMY_WORLD_RECOVERY_BIRTH_IDENTITY");
            if(birth.pack()!=null&&packs.putIfAbsent(birth.pack().packId(),birth)!=null)
                throw new IllegalStateException("ENEMY_WORLD_RECOVERY_DUPLICATE_PACK");
        }
        if(packs.size()!=inventory.packs().size())throw new IllegalStateException("ENEMY_WORLD_RECOVERY_PACK_COUNT");
        var currentPacks=new HashMap<UUID,EnemyPackRecord>();
        var packsByEncounter=new HashMap<UUID,EnemyPackRecord>();
        for(var pack:inventory.packs()){
            var birth=packs.get(pack.packId());
            if(!world.equals(pack.worldId())||birth==null||!birth.encounter().equals(pack.encounterId())
                    ||birth.generation()!=pack.generation())throw new IllegalStateException("ENEMY_WORLD_RECOVERY_PACK_IDENTITY");
            if(currentPacks.putIfAbsent(pack.packId(),pack)!=null
                    ||packsByEncounter.putIfAbsent(pack.encounterId(),pack)!=null)
                throw new IllegalStateException("ENEMY_WORLD_RECOVERY_DUPLICATE_PACK");
        }
        var compensated=new HashSet<UUID>();
        for(var decision:inventory.compensations()){
            var birth=births.get(decision.encounter());
            var pack=birth==null||birth.pack()==null?null:currentPacks.get(birth.pack().packId());
            if(!world.equals(decision.world())||!compensated.add(decision.encounter())||birth==null
                    ||birth.generation()!=decision.generation()||!birth.seed().equals(decision.seed())
                    ||pack==null||pack.state()!=EnemyPackRecord.State.ABORTED
                    ||!"BIRTH_COMPENSATED".equals(pack.abortReason()))
                throw new IllegalStateException("ENEMY_WORLD_RECOVERY_COMPENSATION_IDENTITY");
        }
        var anchors=new HashSet<UUID>();
        for(var anchor:inventory.anchors()){
            if(!world.equals(anchor.worldId())||!anchors.add(anchor.anchorId()))
                throw new IllegalStateException("ENEMY_WORLD_RECOVERY_ANCHOR_IDENTITY");
            if(anchor.state()==SuperUniqueAnchor.State.LIVE){
                var pack=packsByEncounter.get(anchor.encounterId());
                if(pack==null||!pack.encounterId().equals(anchor.encounterId())||pack.generation()!=anchor.cycle()
                        ||!pack.anchor().equals(anchor.position()))
                    throw new IllegalStateException("ENEMY_WORLD_RECOVERY_LIVE_ANCHOR_PACK");
            }
        }
    }
    private static Set<UUID> requiredRebind(FileEncounterStore.EnemyWorldInventory inventory){
        var required=new HashSet<UUID>();
        for(var pack:inventory.packs())if(pack.state()!=EnemyPackRecord.State.ABORTED
                &&pack.state()!=EnemyPackRecord.State.DEFEATED)required.add(pack.encounterId());
        for(var compensation:inventory.compensations())required.add(compensation.encounter());
        for(var anchor:inventory.anchors())if(anchor.state()==SuperUniqueAnchor.State.RESERVED
                ||anchor.state()==SuperUniqueAnchor.State.LIVE)required.add(anchor.encounterId());
        return required;
    }

    private static List<EnemyPackRecord> productionCapacityPacks(FileEncounterStore.EnemyWorldInventory inventory){
        var births=new HashMap<UUID,EnemyBirthPlan>();
        for(var birth:inventory.births())births.put(birth.encounter(),birth);
        return inventory.packs().stream().filter(pack->{
            var birth=births.get(pack.encounterId());
            return birth==null||birth.actors().stream()
                    .anyMatch(actor->actor.spawnOrigin()!=EnemyRewardContext.Origin.QA);
        }).toList();
    }
}
