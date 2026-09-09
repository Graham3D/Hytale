package com.inigmasgames.hytalerpg.progress;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Ordered, bounded post-durability work. Callers must capture values, never native ECS objects. */
public final class DurableEncounterEffects implements AutoCloseable {
    public static final int MAX_PENDING=256;
    /** Maximum retained value envelope per admitted job; identities/participants are bounded at capture. */
    public static final int MAX_JOB_BYTES=256*1024;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->Thread.ofPlatform().daemon().name("RPG-encounter-durable-effects").unstarted(r));
    private CompletableFuture<Void> tail=CompletableFuture.completedFuture(null);
    private int pending;
    private boolean closed;
    private volatile Throwable failure;
    private long rejections,failures,completed;
    private final java.util.Set<Reservation> reservations=new java.util.HashSet<>();
    public final class Reservation implements AutoCloseable {
        private boolean submitted,released;
        private final long admitted=System.nanoTime();
        public <T> CompletionStage<Void> submit(CompletionStage<T> durable,Consumer<T> effect){synchronized(DurableEncounterEffects.this){
            if(submitted||released||closed||failure!=null)throw new IllegalStateException("ENCOUNTER_EFFECTS_UNAVAILABLE",failure);
            submitted=true;
            tail=CompletableFuture.runAsync(()->{
                try{
                    if(failure!=null)throw new IllegalStateException("ENCOUNTER_EFFECTS_UNAVAILABLE",failure);
                    T result=durable.toCompletableFuture().get(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS);
                    effect.accept(result);
                }catch(Throwable error){failure=error;if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new CompletionException(error);}
                finally{release();}
            },worker);
            // Timeout is uncertainty, NOT cancellation: the physical job still owns its ticket
            // until it actually exits. Late durable writes remain recoverable, never retried.
            tail.orTimeout(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS).whenComplete((ignored,error)->{
                if(error!=null){failure=error;synchronized(DurableEncounterEffects.this){failures++;}}
            });
            return tail.minimalCompletionStage();
        }}
        private void release(){synchronized(DurableEncounterEffects.this){if(!released){released=true;pending--;reservations.remove(this);if(submitted)completed++;}}}
        @Override public void close(){synchronized(DurableEncounterEffects.this){if(!submitted)release();}}
    }
    public synchronized Reservation reserve(){
        if(closed||failure!=null)throw new IllegalStateException("ENCOUNTER_EFFECTS_UNAVAILABLE",failure);
        if(pending>=MAX_PENDING){rejections++;throw new FileEncounterStore.CapacityRejected();}
        pending++;var lease=new Reservation();reservations.add(lease);return lease;
    }
    /** Finite predecessor snapshot. Capture BEFORE submitting dependent work on this executor. */
    public synchronized CompletionStage<Void> frontier(){return tail.minimalCompletionStage();}
    public <T> CompletionStage<T> submit(java.util.function.Supplier<T> valuesOnlyWork){
        var result=new CompletableFuture<T>();
        try(var lease=reserve()){
            var completion=lease.submit(CompletableFuture.completedStage(null),ignored->{
                try{result.complete(valuesOnlyWork.get());}catch(Throwable error){result.completeExceptionally(error);throw error;}
            });
            completion.whenComplete((ignored,error)->{if(error!=null)result.completeExceptionally(error);});
        }
        return result.minimalCompletionStage();
    }
    public synchronized int pending(){return pending;}
    public Throwable failure(){return failure;}
    public synchronized java.util.Map<String,Object> metrics(){
        long now=System.nanoTime(),oldest=reservations.stream().mapToLong(r->now-r.admitted).max().orElse(0);
        return java.util.Map.of("pendingCount",pending,"reservedBytes",(long)pending*MAX_JOB_BYTES,
                "maxPending",MAX_PENDING,"maxReservedBytes",(long)MAX_PENDING*MAX_JOB_BYTES,"oldestReceiptMs",oldest/1e6,
                "rejections",rejections,"failures",failures,"completed",completed,"uncertain",failure!=null);
    }
    public void await(){CompletableFuture<Void> current;synchronized(this){current=tail;}EncounterGroupCommit.await(current);
        if(failure!=null)throw new IllegalStateException("ENCOUNTER_EFFECTS_UNAVAILABLE",failure);
    }
    @Override public void close(){
        synchronized(this){closed=true;worker.shutdown();}
        try{if(!worker.awaitTermination(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS)){worker.shutdownNow();throw new IllegalStateException("ENCOUNTER_EFFECTS_SHUTDOWN_TIMEOUT");}}
        catch(InterruptedException error){worker.shutdownNow();Thread.currentThread().interrupt();throw new IllegalStateException("ENCOUNTER_EFFECTS_SHUTDOWN_INTERRUPTED",error);}
        await();
    }
}
