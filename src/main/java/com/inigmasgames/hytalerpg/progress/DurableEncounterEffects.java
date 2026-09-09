package com.inigmasgames.hytalerpg.progress;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Ordered, bounded post-durability work. Callers must capture values, never native ECS objects. */
public final class DurableEncounterEffects implements AutoCloseable {
    public static final int MAX_PENDING=256;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->Thread.ofPlatform().daemon().name("RPG-encounter-durable-effects").unstarted(r));
    private CompletableFuture<Void> tail=CompletableFuture.completedFuture(null);
    private int pending;
    private boolean closed;
    private volatile Throwable failure;
    public final class Reservation implements AutoCloseable {
        private boolean submitted,released;
        public <T> void submit(CompletionStage<T> durable,Consumer<T> effect){synchronized(DurableEncounterEffects.this){
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
        }}
        private void release(){synchronized(DurableEncounterEffects.this){if(!released){released=true;pending--;}}}
        @Override public void close(){synchronized(DurableEncounterEffects.this){if(!submitted)release();}}
    }
    public synchronized Reservation reserve(){
        if(closed||failure!=null)throw new IllegalStateException("ENCOUNTER_EFFECTS_UNAVAILABLE",failure);
        if(pending>=MAX_PENDING)throw new FileEncounterStore.CapacityRejected();
        pending++;return new Reservation();
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
