package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.DurableActionRootLease;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore.EnemyActionRootBlock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

/** Existing enemy root API over the shared writer-reserved lease primitive. */
public final class EnemyActionRootLease implements AutoCloseable {
    private final DurableActionRootLease<EnemyActionRootBlock> delegate;
    private EnemyActionRootLease(DurableActionRootLease<EnemyActionRootBlock> delegate){this.delegate=delegate;}
    public static CompletionStage<EnemyActionRootLease> open(UUID world,UUID actor,long generation,
            IntFunction<CompletionStage<EnemyActionRootBlock>> reserve){
        Objects.requireNonNull(reserve);
        if(generation<0)throw new IllegalArgumentException("ENEMY_ACTION_ROOT_GENERATION");
        return DurableActionRootLease.open(world,actor,count->reserve.apply(count).thenApply(block->{
            if(block==null||block.generation()!=generation)
                throw new IllegalStateException("ENEMY_ACTION_ROOT_BLOCK_IDENTITY");
            return block;
        })).thenApply(EnemyActionRootLease::new);
    }
    public String issue(){return delegate.issue();}
    @Override public void close(){delegate.close();}
}
