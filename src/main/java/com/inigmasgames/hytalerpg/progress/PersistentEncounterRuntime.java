package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;

/** One serialized authority for native encounter callbacks. Storage uncertainty freezes awards, never combat. */
public final class PersistentEncounterRuntime {
    private final EncounterContributions ledger=new EncounterContributions();
    private final FileEncounterStore store;
    private final FileEncounterStore.AwardDelivery awards;
    private final Set<Key> loaded=new HashSet<>();
    private record Key(UUID world,UUID enemy){}
    private volatile boolean unavailable;
    /** Provisional result is admission/calculation only. Only durable() certifies persisted success. */
    public record Submission<T>(T provisional,CompletionStage<T> durable){}
    public synchronized Submission<Boolean> submitDamage(UUID world,UUID enemy,UUID actor,double before,double after,double max,boolean hostile,long now){return submitContribution(world,enemy,()->ledger.damage(world,enemy,actor,before,after,max,hostile,now));}
    public synchronized Submission<Boolean> submitAbsorb(UUID world,UUID enemy,UUID actor,double amount,boolean hostile,long now){return submitContribution(world,enemy,()->ledger.absorb(world,enemy,actor,amount,hostile,now));}
    public synchronized Submission<Boolean> submitControl(UUID world,UUID enemy,UUID actor,boolean changed,boolean taunt,boolean hostile,long now){return submitContribution(world,enemy,()->ledger.control(world,enemy,actor,changed,taunt,hostile,now));}
    private Submission<Boolean> submitContribution(UUID world,UUID enemy,BooleanSupplier operation){return guarded(()->{
        if(!loaded.contains(new Key(world,enemy)))return new Submission<>(false,CompletableFuture.completedStage(false));
        try(var reservation=store.reserveSubmission(world,List.of(enemy))){
            boolean result=operation.getAsBoolean();return receipt(result,reservation.submit(result?List.of(ledger.snapshot(world,enemy)):List.of()));
        }
    });}
    public synchronized Submission<Integer> submitHeal(UUID world,UUID healer,UUID beneficiary,double actualEligibleHealing,boolean allyAllowed,long now){return guarded(()->{
        var enemies=new ArrayList<>(ledger.healingEncounters(world,beneficiary,now));
        try(var reservation=store.reserveSubmission(world,enemies)){
            int count=ledger.heal(world,healer,beneficiary,actualEligibleHealing,allyAllowed,now);
            return receipt(count,reservation.submit(count>0?enemies.stream().map(enemy->ledger.snapshot(world,enemy)).toList():List.of()));
        }
    });}
    private <T> Submission<T> receipt(T value,CompletionStage<Void> persisted){
        var durable=persisted.thenApply(ignored->value);durable.whenComplete((ignored,error)->{if(error!=null)unavailable=true;});return new Submission<>(value,durable);
    }
    public synchronized void awaitDurable(){guarded(()->{store.awaitSubmissions();return null;});}
    /** For capturing a deferred reward predicate only; never use as proof of durable contribution. */
    public synchronized boolean provisionalMasteryEligible(UUID world,UUID enemy,UUID actor,int level,long now){return contains(world,enemy)&&ledger.masteryEligible(world,enemy,actor,level,now);}
    public PersistentEncounterRuntime(FileEncounterStore store,BiConsumer<UUID,EarnedReward> awards){this(store,(player,reward,learning)->{
        if(learning!=null)throw new IllegalStateException("LEARNING_DELIVERY_ADAPTER_REQUIRED");awards.accept(player,reward);
    });}
    public PersistentEncounterRuntime(FileEncounterStore store,FileEncounterStore.AwardDelivery awards){this.store=Objects.requireNonNull(store);this.awards=Objects.requireNonNull(awards);}
    /** Only new native spawns may supply a classifier; loads must pass an empty candidate. */
    public synchronized boolean attach(UUID world,UUID enemy,String currentRole,Optional<EnemyRewardRegistry.Spawn> newSpawn){return guarded(()->{
        var key=new Key(world,enemy);if(loaded.contains(key))return true;
        if(loaded.size()>=EncounterContributions.MAX_ENCOUNTERS||store.death(world,enemy).isPresent())return false;
        var saved=store.load(world,enemy);
        if(saved.isEmpty()){
            if(newSpawn.isEmpty())return false;
            var candidate=newSpawn.get();if(!candidate.world().equals(world)||!candidate.enemy().equals(enemy))throw new IllegalArgumentException("ENCOUNTER_ID_MISMATCH");
            saved=Optional.of(store.create(candidate));
        }
        if(!saved.get().spawn().roleId().equals(currentRole)){store.disqualify(world,enemy);return false;}
        if(saved.get().disqualified()||!ledger.restore(saved.get()))return false;
        loaded.add(key);return true;
    });}
    public synchronized boolean contains(UUID world,UUID enemy){return !unavailable&&loaded.contains(new Key(world,enemy));}
    public synchronized List<UUID> contributors(UUID world,UUID enemy){awaitDurable();return contains(world,enemy)?ledger.snapshot(world,enemy).credits().stream().map(EncounterContributions.Credit::player).toList():List.of();}
    public synchronized Optional<EnemyRewardRegistry.Spawn> spawn(UUID world,UUID enemy){return contains(world,enemy)?Optional.of(ledger.snapshot(world,enemy).spawn()):Optional.empty();}
    public synchronized void disqualify(UUID world,UUID enemy){guarded(()->{store.awaitSubmissions();store.disqualify(world,enemy);ledger.disqualify(world,enemy);detach(world,enemy);return null;});}
    public synchronized boolean damage(UUID world,UUID enemy,UUID actor,double before,double after,double max,boolean hostile,long now){
        return contribute(world,enemy,()->ledger.damage(world,enemy,actor,before,after,max,hostile,now));
    }
    public synchronized boolean absorb(UUID world,UUID enemy,UUID actor,double amount,boolean hostile,long now){return contribute(world,enemy,()->ledger.absorb(world,enemy,actor,amount,hostile,now));}
    public synchronized boolean control(UUID world,UUID enemy,UUID actor,boolean changed,boolean taunt,boolean hostile,long now){return contribute(world,enemy,()->ledger.control(world,enemy,actor,changed,taunt,hostile,now));}
    public synchronized int heal(UUID world,UUID healer,UUID beneficiary,double actualEligibleHealing,boolean allyAllowed,long now){return guarded(()->{
        store.awaitSubmissions();
        var encounters=ledger.healingEncounters(world,beneficiary,now);
        try(var reservation=store.reserve(encounters.size())){
            int count=ledger.heal(world,healer,beneficiary,actualEligibleHealing,allyAllowed,now);
            if(count>0)for(var enemy:encounters)store.save(ledger.snapshot(world,enemy),reservation);return count;
        }
    });}
    private boolean contribute(UUID world,UUID enemy,BooleanSupplier operation){return guarded(()->{
        store.awaitSubmissions();
        if(!loaded.contains(new Key(world,enemy)))return false;
        try(var reservation=store.reserve(1)){
            if(!operation.getAsBoolean())return false;
            store.save(ledger.snapshot(world,enemy),reservation);return true;
        }
    });}
    public synchronized boolean masteryEligible(UUID world,UUID enemy,UUID actor,int level,long now){awaitDurable();return contains(world,enemy)&&ledger.masteryEligible(world,enemy,actor,level,now);}
    public synchronized Optional<EncounterContributions.DeathPlan> death(UUID world,UUID enemy,Vec3 position,long now,List<EncounterContributions.Participant> participants){return guarded(()->{
        store.awaitSubmissions();
        var previous=store.death(world,enemy);if(previous.isPresent())return previous;
        if(!loaded.contains(new Key(world,enemy)))return Optional.empty();
        return store.withDeathCapacity(()->{var plan=ledger.death(world,enemy,position,now,participants);store.freeze(plan);detach(world,enemy);return Optional.of(plan);});
    });}
    public synchronized int drain(int budget){return guarded(()->{store.awaitSubmissions();return store.drainLearning(budget,awards);});}
    public synchronized void detach(UUID world,UUID enemy){awaitDurable();ledger.remove(world,enemy);loaded.remove(new Key(world,enemy));}
    public synchronized void unload(UUID world){awaitDurable();ledger.unload(world);loaded.removeIf(k->k.world().equals(world));}
    public synchronized boolean unavailable(){return unavailable;}
    private <T> T guarded(Supplier<T> operation){
        if(unavailable)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED");
        try{return operation.get();}catch(FileEncounterStore.CapacityRejected rejection){throw rejection;}catch(RuntimeException failure){unavailable=true;throw failure;}
    }
}
