package com.inigmasgames.hytalerpg.progress;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

/** Issues only writer-reserved IDs; unused reservations are discarded on close or crash. */
public final class DurableActionRootLease<B extends DurableActionRootLease.Block> implements AutoCloseable {
    public interface Block {
        UUID world();
        UUID actor();
        long first();
        long last();
        String id(long sequence);
    }
    private static final int BLOCK_SIZE=64,REFILL_AT=16;
    private final UUID world,actor;
    private final IntFunction<CompletionStage<B>> reserve;
    private final ArrayDeque<B> blocks=new ArrayDeque<>();
    private long next;
    private boolean reserving,closed;
    private Throwable failed;

    private DurableActionRootLease(UUID world,UUID actor,IntFunction<CompletionStage<B>> reserve){
        this.world=Objects.requireNonNull(world);this.actor=Objects.requireNonNull(actor);
        this.reserve=Objects.requireNonNull(reserve);
    }
    public static <B extends Block> CompletionStage<DurableActionRootLease<B>> open(
            UUID world,UUID actor,IntFunction<CompletionStage<B>> reserve){
        var lease=new DurableActionRootLease<>(world,actor,reserve);
        try{return reserve.apply(BLOCK_SIZE).thenApply(block->{
            synchronized(lease){lease.accept(block);}
            return lease;
        });}catch(RuntimeException rejected){return CompletableFuture.failedStage(rejected);}
    }
    /** World-thread issuance never waits for disk. */
    public synchronized String issue(){
        if(closed||failed!=null)throw new IllegalStateException("ACTION_ROOT_LEASE_UNAVAILABLE",failed);
        var block=blocks.peekFirst();
        if(block==null)throw new IllegalStateException("ACTION_ROOT_BLOCK_NOT_READY");
        long value=next;String id=block.id(value);next=value+1;
        if(next>block.last()){blocks.removeFirst();next=blocks.isEmpty()?0:blocks.peekFirst().first();}
        int available=0;
        for(var pending:blocks)available+=pending.last()-(pending==blocks.peekFirst()?next:pending.first())+1;
        if(available<=REFILL_AT&&!reserving)refill();
        return id;
    }
    private void refill(){
        reserving=true;
        try{reserve.apply(BLOCK_SIZE).whenComplete((block,error)->{
            synchronized(this){
                reserving=false;
                if(error!=null){failed=error;return;}
                try{accept(block);}catch(RuntimeException invalid){failed=invalid;}
            }
        });}catch(RuntimeException rejection){reserving=false;failed=rejection;}
    }
    private void accept(B block){
        if(block==null||!world.equals(block.world())||!actor.equals(block.actor())
                ||block.first()<1||block.last()<block.first()||block.last()-block.first()>=256)
            throw new IllegalStateException("ACTION_ROOT_BLOCK_IDENTITY");
        var tail=blocks.peekLast();
        if(tail!=null&&block.first()<=tail.last())throw new IllegalStateException("ACTION_ROOT_BLOCK_OVERLAP");
        if(closed)return;
        if(blocks.isEmpty())next=block.first();
        blocks.addLast(block);
    }
    @Override public synchronized void close(){closed=true;blocks.clear();}
}
