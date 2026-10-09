package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.*;

/** Single completion owner for the existing birth stages; no gameplay or persistence policy. */
public final class NativeBirthContinuation<S,P> {
    public enum Outcome { DECLINED, COMPENSATED, PUBLISHED, AWAITING_LOAD }
    public interface Steps<S,P> {
        CompletionStage<P> prepare(S sealed);
        void activate(P prepared);
        CompletionStage<Void> publish(S sealed,P prepared);
        void finish(S sealed,P prepared);
        CompletionStage<Void> compensate(S sealed,P prepared,Throwable cause);
        void uncertain(S sealed,P prepared,String phase,Throwable cause);
        default void awaitingLoad(S sealed,P prepared,Throwable cause){throw new IllegalStateException("ENEMY_BIRTH_LOAD_OWNER_MISSING",cause);}
    }
    private final Executor world;
    private final BooleanSupplier current;
    private final Steps<S,P> steps;
    private final CompletableFuture<Outcome> result;
    private S sealed;private P prepared;
    private String phase="RESERVE";
    public NativeBirthContinuation(Executor world,BooleanSupplier current,Steps<S,P> steps,CompletableFuture<Outcome> result){
        this.world=world;this.current=current;this.steps=steps;this.result=result;
    }
    public CompletionStage<Outcome> start(CompletionStage<Optional<S>> start){
        observe(start,(value,error)->{
            if(error!=null){uncertain(error);return;}
            if(value.isEmpty()){result.complete(Outcome.DECLINED);return;}
            sealed=value.get();phase="ATTACH";
            try{observe(steps.prepare(sealed),(attached,attachError)->{
                if(attachError!=null){compensate(attachError);return;}
                prepared=attached;phase="ACTIVATE";
                try{steps.activate(prepared);}catch(Throwable rejected){compensate(rejected);return;}
                phase="PUBLISH";
                try{observe(steps.publish(sealed,prepared),(ignored,publishError)->{
                    if(publishError!=null){uncertain(publishError);return;}
                    phase="LIFETIME";
                    try{steps.finish(sealed,prepared);result.complete(Outcome.PUBLISHED);}
                    catch(Throwable failure){uncertain(failure);}
                });}catch(Throwable failure){uncertain(failure);}
            });}catch(Throwable rejected){compensate(rejected);}
        });
        return result.minimalCompletionStage();
    }
    private void compensate(Throwable cause){
        Throwable root=cause;while(root.getCause()!=null)root=root.getCause();
        if(root instanceof NativeBirthAwaitingLoad){
            phase="AWAITING_LOAD";
            try{steps.awaitingLoad(sealed,prepared,cause);result.complete(Outcome.AWAITING_LOAD);}
            catch(Throwable failure){uncertain(failure);}
            return;
        }
        phase="COMPENSATE";
        try{observe(steps.compensate(sealed,prepared,cause),(ignored,error)->{
            if(error!=null){if(error!=cause)error.addSuppressed(cause);uncertain(error);}
            else result.complete(Outcome.COMPENSATED);
        });}catch(Throwable error){if(error!=cause)error.addSuppressed(cause);uncertain(error);}
    }
    private <T> void observe(CompletionStage<T> stage,BiConsumer<T,Throwable> callback){
        stage.whenComplete((value,error)->{
            if(result.isDone())return;
            try{world.execute(()->{
                if(result.isDone())return;
                if(!current.getAsBoolean()){
                    result.completeExceptionally(new IllegalStateException("ENEMY_BIRTH_OBSOLETE_WORLD"));return;
                }
                try{callback.accept(value,error);}catch(Throwable failure){uncertain(failure);}
            });}catch(Throwable rejected){
                // No world mutation on the writer thread. The quarantine owner schedules its own cleanup.
                uncertain(rejected);
            }
        });
    }
    private void uncertain(Throwable cause){
        if(result.isDone())return;
        try{if(current.getAsBoolean())steps.uncertain(sealed,prepared,phase,cause);}
        catch(Throwable cleanup){if(cleanup!=cause)cause.addSuppressed(cleanup);}
        finally{result.completeExceptionally(cause);}
    }
}
