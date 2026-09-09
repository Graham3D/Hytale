package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;

/** One serialized authority for native encounter callbacks. Storage uncertainty freezes awards, never combat. */
public final class PersistentEncounterRuntime implements AutoCloseable {
    private final EncounterContributions ledger=new EncounterContributions();
    private final FileEncounterStore store;
    private final FileEncounterStore.AwardDelivery awards;
    private final Set<Key> loaded=new HashSet<>();
    private record Key(UUID world,UUID enemy){}
    private volatile boolean unavailable;
    private final DurableEncounterEffects contextLoads=new DurableEncounterEffects();
    private final Map<Key,Attachment> attachments=new HashMap<>();
    private final Map<Key,CompletionStage<Void>> exclusionFrontiers=new HashMap<>();
    // Derived ceiling: two lifecycle transitions/context, plus one mutation, one death and
    // up to 64 captured mastery predicates per each of the 256 already admitted effect jobs.
    private static final int MAX_TRANSITIONS=EncounterContributions.MAX_ENCOUNTERS*2+DurableEncounterEffects.MAX_PENDING*(EncounterContributions.MAX_SUPPORT_ENCOUNTERS+2);
    private int pendingTransitions;private long transitionRejections;private boolean stopping;
    private final ExecutorService transitions=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(MAX_TRANSITIONS),
            r->Thread.ofPlatform().daemon().name("RPG-encounter-ledger-transitions").unstarted(r));
    private static final class Attachment {
        CompletableFuture<Void> tail=new CompletableFuture<>();
        final Set<UUID> contributors=new LinkedHashSet<>();
        boolean detached,closing;
    }
    /** Startup/load IO is independent of the short ledger monitor. No native handle is queued. */
    public synchronized CompletionStage<Boolean> attachNative(UUID world,UUID enemy,String role,Optional<EnemyRewardRegistry.Spawn> candidate){
        if(stopping)throw new IllegalStateException("ENCOUNTER_SHUTDOWN_ADMISSION_CLOSED");
        if(exclusionFrontiers.containsKey(new Key(world,enemy)))throw new IllegalStateException("ENCOUNTER_PERMANENTLY_DISQUALIFIED");
        var key=new Key(world,enemy);if(attachments.containsKey(key)||finalizing.containsKey(key))throw new IllegalStateException("ENCOUNTER_CONTEXT_GENERATION_BUSY");
        if(attachments.size()>=EncounterContributions.MAX_ENCOUNTERS)throw new FileEncounterStore.CapacityRejected();
        try(var lease=contextLoads.reserve()){
            var predecessor=store.loadFrontier();
            var attachment=new Attachment();var receipt=new CompletableFuture<Boolean>();attachments.put(key,attachment);
            attachment.tail=receipt.thenApply(ignored->null);
            var task=lease.submit(CompletableFuture.completedStage(null),ignored->{
                try{receipt.complete(attachPrepared(world,enemy,role,candidate,predecessor));}
                catch(Throwable error){unavailable=true;receipt.completeExceptionally(error);throw error;}
            });
            task.whenComplete((ignored,error)->{if(error!=null){unavailable=true;receipt.completeExceptionally(error);}});
            return receipt.minimalCompletionStage();
        }
    }
    public synchronized boolean observing(UUID world,UUID enemy){var key=new Key(world,enemy);var a=attachments.get(key);return !unavailable&&(a!=null?!a.closing:loaded.contains(key));}
    public synchronized boolean attaching(UUID world,UUID enemy){var a=attachments.get(new Key(world,enemy));return a!=null&&!a.tail.isDone();}
    /** Takes finite provisional predecessors BEFORE publishing this transition's tail. */
    private <T> CompletableFuture<T> transition(List<Key> keys,Supplier<T> operation){
        if(pendingTransitions>=MAX_TRANSITIONS){transitionRejections++;throw new FileEncounterStore.CapacityRejected();}
        pendingTransitions++;
        var predecessors=keys.stream().map(attachments::get).filter(Objects::nonNull).map(v->v.tail).toArray(CompletableFuture[]::new);
        var future=CompletableFuture.allOf(predecessors).thenApplyAsync(ignored->{synchronized(this){return guarded(operation);}},transitions);
        var tail=future.thenApply(ignored->(Void)null);
        for(var key:keys){var attached=attachments.get(key);if(attached!=null)attached.tail=tail;}
        future.whenComplete((ignored,error)->{synchronized(this){pendingTransitions--;}if(error!=null)unavailable=true;});return future;
    }
    public synchronized CompletionStage<Boolean> captureMasteryEligibility(UUID world,UUID enemy,UUID actor,int level,long now){
        var key=new Key(world,enemy);var a=attachments.get(key);
        if(a==null||a.tail.isDone()){if(a!=null)a.tail.getNow(null);return CompletableFuture.completedStage(provisionalMasteryEligible(world,enemy,actor,level,now));}
        return transition(List.of(key),()->provisionalMasteryEligible(world,enemy,actor,level,now)).minimalCompletionStage();
    }
    /** Provisional result is admission/calculation only. Only durable() certifies persisted success. */
    public record Submission<T>(T provisional,CompletionStage<T> durable){}
    public synchronized Submission<Boolean> submitDamage(UUID world,UUID enemy,UUID actor,double before,double after,double max,boolean hostile,long now){return submitContribution(world,enemy,actor,()->ledger.damage(world,enemy,actor,before,after,max,hostile,now));}
    public synchronized Submission<Boolean> submitAbsorb(UUID world,UUID enemy,UUID actor,double amount,boolean hostile,long now){return submitContribution(world,enemy,actor,()->ledger.absorb(world,enemy,actor,amount,hostile,now));}
    public synchronized Submission<Boolean> submitControl(UUID world,UUID enemy,UUID actor,boolean changed,boolean taunt,boolean hostile,long now){return submitContribution(world,enemy,actor,()->ledger.control(world,enemy,actor,changed,taunt,hostile,now));}
    private Submission<Boolean> submitContribution(UUID world,UUID enemy,UUID actor,BooleanSupplier operation){return guarded(()->{
        if(stopping)throw new IllegalStateException("ENCOUNTER_SHUTDOWN_ADMISSION_CLOSED");
        var key=new Key(world,enemy);var a=attachments.get(key);
        if(a!=null){
            if(a.closing)return new Submission<>(false,CompletableFuture.completedStage(false));
            if(a.contributors.size()>=EncounterContributions.MAX_CONTRIBUTORS&&!a.contributors.contains(actor))throw new FileEncounterStore.CapacityRejected();
            if(!a.tail.isDone()){
                var reservation=store.reserveSubmission(world,List.of(enemy));
                a.contributors.add(actor);
                var calculated=transition(List.of(key),()->{
                    boolean accepted=loaded.contains(key)&&operation.getAsBoolean();
                    try(reservation){return receipt(accepted,reservation.submit(accepted?List.of(ledger.snapshot(world,enemy)):List.of()));}
                });
                calculated.whenComplete((ignored,error)->{if(error!=null)reservation.close();});
                return new Submission<>(true,calculated.thenCompose(Submission::durable).minimalCompletionStage());
            }
            a.tail.getNow(null);
        }
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
    public final class HealingAdmission implements AutoCloseable {
        private final UUID world;private final List<UUID> enemies;private final FileEncounterStore.SubmissionReservation reservation;private boolean submitted;
        private HealingAdmission(UUID world,List<UUID> enemies){this.world=world;this.enemies=List.copyOf(enemies);reservation=store.reserveSubmission(world,enemies);}
        @Override public void close(){if(!submitted)reservation.close();}
    }
    public synchronized HealingAdmission reserveHealing(UUID world,UUID beneficiary,long now){
        if(stopping||unavailable)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNAVAILABLE");
        var enemies=new LinkedHashSet<>(ledger.healingEncounters(world,beneficiary,now));
        attachments.forEach((key,a)->{if(key.world().equals(world)&&!a.closing&&!a.tail.isDone())enemies.add(key.enemy());});
        enemies.removeIf(enemy->{var a=attachments.get(new Key(world,enemy));return a!=null&&a.closing;});
        if(enemies.size()>EncounterContributions.MAX_SUPPORT_ENCOUNTERS)throw new FileEncounterStore.CapacityRejected();
        return new HealingAdmission(world,List.copyOf(enemies));
    }
    public synchronized Submission<Integer> submitHeal(HealingAdmission admission,UUID healer,UUID beneficiary,double amount,boolean ally,long observed){
        if(admission.submitted)throw new IllegalStateException("HEAL_ADMISSION_ALREADY_USED");admission.submitted=true;
        var keys=admission.enemies.stream().map(enemy->new Key(admission.world,enemy)).toList();
        var selected=Set.copyOf(admission.enemies);
        Supplier<Submission<Integer>> calculate=()->{
            int count=ledger.healSelected(admission.world,healer,beneficiary,amount,ally,observed,selected);
            try(var reserved=admission.reservation){return receipt(count,reserved.submit(count>0?admission.enemies.stream()
                    .filter(enemy->loaded.contains(new Key(admission.world,enemy))).map(enemy->ledger.snapshot(admission.world,enemy)).toList():List.of()));}
        };
        if(keys.stream().map(attachments::get).filter(Objects::nonNull).anyMatch(a->!a.tail.isDone())){
            var ready=transition(keys,calculate);ready.whenComplete((ignored,error)->{if(error!=null)admission.reservation.close();});
            return new Submission<>(admission.enemies.size(),ready.thenCompose(Submission::durable).minimalCompletionStage());
        }
        keys.stream().map(attachments::get).filter(Objects::nonNull).forEach(a->a.tail.getNow(null));
        return guarded(calculate);
    }
    private <T> Submission<T> receipt(T value,CompletionStage<Void> persisted){
        var durable=persisted.thenApply(ignored->value);durable.whenComplete((ignored,error)->{if(error!=null)unavailable=true;});return new Submission<>(value,durable);
    }
    public void awaitDurable(){guarded(()->{store.awaitSubmissions();return null;});}
    /** For capturing a deferred reward predicate only; never use as proof of durable contribution. */
    public synchronized boolean provisionalMasteryEligible(UUID world,UUID enemy,UUID actor,int level,long now){return contains(world,enemy)&&ledger.masteryEligible(world,enemy,actor,level,now);}
    public PersistentEncounterRuntime(FileEncounterStore store,BiConsumer<UUID,EarnedReward> awards){this(store,(player,reward,learning)->{
        if(learning!=null)throw new IllegalStateException("LEARNING_DELIVERY_ADAPTER_REQUIRED");awards.accept(player,reward);
    });}
    private int freeDeathTickets;
    public PersistentEncounterRuntime(FileEncounterStore store,FileEncounterStore.AwardDelivery awards){this.store=Objects.requireNonNull(store);this.awards=Objects.requireNonNull(awards);freeDeathTickets=FileEncounterStore.MAX_PENDING-store.pendingCount();}
    /** Only new native spawns may supply a classifier; loads must pass an empty candidate. */
    public boolean attach(UUID world,UUID enemy,String currentRole,Optional<EnemyRewardRegistry.Spawn> newSpawn){return attachPrepared(world,enemy,currentRole,newSpawn,null);}
    private boolean attachPrepared(UUID world,UUID enemy,String currentRole,Optional<EnemyRewardRegistry.Spawn> newSpawn,CompletionStage<Void> predecessor){return guarded(()->{
        if(predecessor!=null)EncounterGroupCommit.await(predecessor.toCompletableFuture());
        var key=new Key(world,enemy);
        synchronized(this){if(loaded.contains(key))return true;if(loaded.size()>=EncounterContributions.MAX_ENCOUNTERS||finalizing.containsKey(key))return false;}
        if(store.death(world,enemy).isPresent())return false;
        var saved=predecessor==null?store.load(world,enemy):store.loadPrepared(world,enemy);
        if(saved.isEmpty()){
            if(newSpawn.isEmpty())return false;
            var candidate=newSpawn.get();if(!candidate.world().equals(world)||!candidate.enemy().equals(enemy))throw new IllegalArgumentException("ENCOUNTER_ID_MISMATCH");
            saved=Optional.of(store.create(candidate));
        }
        if(!saved.get().spawn().roleId().equals(currentRole)){
            if(predecessor==null)store.disqualify(world,enemy);else store.disqualifyPrepared(world,enemy);
            return false;
        }
        synchronized(this){
            if(saved.get().disqualified()||finalizing.containsKey(key)||!ledger.restore(saved.get()))return false;
            loaded.add(key);return true;
        }
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
    public int drain(int budget){return guarded(()->{store.awaitSubmissions();return store.drainLearning(budget,awards);});}
    /** Immutable worker input; no native object and no shared mutable ledger crosses the boundary. */
    public record PreparedDeath(EncounterContributions.Snapshot snapshot,Vec3 position,long observedAt,
                                CompletionStage<Void> contributions){}
    private final Map<Key,PreparedDeath> finalizing=new HashMap<>();
    public synchronized List<UUID> provisionalContributors(UUID world,UUID enemy){
        var ids=new LinkedHashSet<UUID>();if(contains(world,enemy))ledger.snapshot(world,enemy).credits().forEach(c->ids.add(c.player()));
        var a=attachments.get(new Key(world,enemy));if(a!=null)ids.addAll(a.contributors);return List.copyOf(ids);
    }
    public synchronized CompletionStage<Optional<PreparedDeath>> prepareDeathNative(UUID world,UUID enemy,Vec3 position,long observedAt){
        var key=new Key(world,enemy);var a=attachments.get(key);
        if(finalizing.containsKey(key)||a!=null&&a.closing||a==null&&!loaded.contains(key))return CompletableFuture.completedStage(Optional.empty());
        if(freeDeathTickets<=0)throw new FileEncounterStore.CapacityRejected();freeDeathTickets--;
        if(a==null)return CompletableFuture.completedStage(prepareDeath(world,enemy,position,observedAt));
        a.closing=true;
        return transition(List.of(key),()->{var result=prepareDeath(world,enemy,position,observedAt);if(result.isEmpty())freeDeathTickets++;return result;}).minimalCompletionStage();
    }
    /** Caller reserves a bounded death/effect ticket before this transition. */
    public synchronized Optional<PreparedDeath> prepareDeath(UUID world,UUID enemy,Vec3 position,long observedAt){
        var key=new Key(world,enemy);var existing=finalizing.get(key);if(existing!=null)return Optional.of(existing);
        if(!contains(world,enemy))return Optional.empty();
        var prepared=new PreparedDeath(ledger.snapshot(world,enemy),position,observedAt,store.submissionFrontier());
        finalizing.put(key,prepared);ledger.remove(world,enemy);loaded.remove(key);return Optional.of(prepared);
    }
    /** Effects worker only. Progression-dependent participant facts are resolved by its caller. */
    public EncounterContributions.DeathPlan finishDeath(PreparedDeath prepared,List<EncounterContributions.Participant> participants){return guarded(()->{
        EncounterGroupCommit.await(prepared.contributions().toCompletableFuture());
        var isolated=new EncounterContributions();isolated.restore(prepared.snapshot());var spawn=prepared.snapshot().spawn();
        var plan=isolated.death(spawn.world(),spawn.enemy(),prepared.position(),prepared.observedAt(),participants);
        store.freezePrepared(plan);
        synchronized(this){finalizing.remove(new Key(spawn.world(),spawn.enemy()),prepared);}
        return plan;
    });}
    public int drainReadyPlans(int budget){return guarded(()->{
        int before=store.pendingCount(),attempts=store.drainPrepared(budget,awards),after=store.pendingCount();
        synchronized(this){freeDeathTickets+=Math.max(0,before-after);}return attempts;
    });}
    public synchronized CompletionStage<Void> excludeNow(UUID world,UUID enemy){
        var key=new Key(world,enemy);
        if(exclusionFrontiers.containsKey(key))return exclusionFrontiers.get(key);
        if(exclusionFrontiers.size()>=EncounterContributions.MAX_ENCOUNTERS)throw new FileEncounterStore.CapacityRejected();
        if(finalizing.containsKey(key))throw new IllegalStateException("ENCOUNTER_DEATH_FINALIZING");
        var a=attachments.get(key);
        if(a!=null){
            if(a.closing)throw new IllegalStateException("ENCOUNTER_CONTEXT_FINALIZING");
            a.closing=true;
            var frontier=transition(List.of(key),()->{ledger.remove(world,enemy);loaded.remove(key);return store.submissionFrontier();})
                    .thenCompose(Function.identity()).minimalCompletionStage();
            exclusionFrontiers.put(key,frontier);return frontier;
        }
        var frontier=store.submissionFrontier();ledger.remove(world,enemy);loaded.remove(key);exclusionFrontiers.put(key,frontier);return frontier;
    }
    public void persistExclusion(UUID world,UUID enemy,CompletionStage<Void> frontier){guarded(()->{
        EncounterGroupCommit.await(frontier.toCompletableFuture());store.disqualifyPrepared(world,enemy);return null;
    });}
    public synchronized void detachNative(UUID world,UUID enemy){
        var key=new Key(world,enemy);var attached=attachments.get(key);
        if(attached!=null){
            if(attached.detached)return;attached.detached=true;attached.closing=true;
            transition(List.of(key),()->{ledger.remove(world,enemy);loaded.remove(key);attachments.remove(key,attached);return null;});
        }else{ledger.remove(world,enemy);loaded.remove(key);}
        // Immutable finalization and already submitted WAL work outlive the native handle.
    }
    @Override public void close(){
        synchronized(this){stopping=true;}
        try{contextLoads.close();}finally{
            transitions.shutdown();
            try{if(!transitions.awaitTermination(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS)){transitions.shutdownNow();throw new IllegalStateException("ENCOUNTER_TRANSITION_SHUTDOWN_TIMEOUT");}}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
        }
        awaitDurable();
    }
    public synchronized void detach(UUID world,UUID enemy){awaitDurable();ledger.remove(world,enemy);loaded.remove(new Key(world,enemy));}
    public synchronized void unload(UUID world){awaitDurable();ledger.unload(world);loaded.removeIf(k->k.world().equals(world));}
    public synchronized boolean unavailable(){return unavailable;}
    public void rejectIncompleteNativeObservation(){unavailable=true;}
    public synchronized Map<String,Object> handoffMetrics(){return Map.of("contexts",attachments.size(),"pendingDeaths",FileEncounterStore.MAX_PENDING-freeDeathTickets,
            "pendingTransitions",pendingTransitions,"maxTransitions",MAX_TRANSITIONS,"transitionRejections",transitionRejections,"loads",contextLoads.metrics(),"wal",store.submissionMetrics(),"uncertain",unavailable);}
    private <T> T guarded(Supplier<T> operation){
        if(unavailable)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED");
        try{return operation.get();}catch(FileEncounterStore.CapacityRejected rejection){throw rejection;}catch(RuntimeException failure){unavailable=true;throw failure;}
    }
}
