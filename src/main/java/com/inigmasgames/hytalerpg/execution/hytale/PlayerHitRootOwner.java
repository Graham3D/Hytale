package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.progress.DurableActionRootLease;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore.PlayerHitRootBlock;
import java.util.*;
import java.util.concurrent.*;

/** Connection-scoped cache of encounter-writer reservations, with no world-thread IO. */
public final class PlayerHitRootOwner implements AutoCloseable {
    @FunctionalInterface interface Reservation {
        CompletionStage<PlayerHitRootBlock> reserve(UUID world,UUID player,int count);
    }
    private static final class Entry {
        final UUID world;
        final Object connection;
        final CompletionStage<DurableActionRootLease<PlayerHitRootBlock>> opening;
        boolean closed;
        Entry(UUID world,Object connection,CompletionStage<DurableActionRootLease<PlayerHitRootBlock>> opening){
            this.world=world;this.connection=connection;this.opening=opening;
        }
    }
    private final Reservation reserve;
    private final Map<UUID,Entry> players=new HashMap<>();
    private boolean closed;
    public PlayerHitRootOwner(HytaleEncounterRewards rewards){this(Objects.requireNonNull(rewards)::reservePlayerHitRoots);}
    PlayerHitRootOwner(Reservation reserve){this.reserve=Objects.requireNonNull(reserve);}
    public synchronized CompletionStage<Void> open(UUID world,UUID player,Object connection){
        Objects.requireNonNull(world);Objects.requireNonNull(player);Objects.requireNonNull(connection);
        if(closed)return CompletableFuture.failedStage(new IllegalStateException("PLAYER_HIT_ROOT_OWNER_CLOSED"));
        var old=players.get(player);
        if(old!=null&&old.world.equals(world)&&old.connection==connection&&!old.closed)
            return old.opening.thenApply(ignored->null);
        if(old!=null)release(old);
        var opening=DurableActionRootLease.open(world,player,count->reserve.reserve(world,player,count));
        var entry=new Entry(world,connection,opening);
        players.put(player,entry);
        opening.whenComplete((lease,error)->{if(lease!=null){synchronized(this){if(entry.closed||closed)lease.close();}}});
        return opening.thenApply(ignored->null);
    }
    /** Null means this route has no proven durable identity; the ordinary hit still proceeds. */
    public synchronized String issue(UUID world,UUID player){
        var entry=players.get(player);
        if(entry==null||entry.closed||!entry.world.equals(world))return null;
        var future=entry.opening.toCompletableFuture();
        if(!future.isDone()||future.isCompletedExceptionally())return null;
        return future.getNow(null).issue();
    }
    public synchronized void detach(UUID player,Object connection){
        var entry=players.get(player);
        if(entry!=null&&entry.connection==connection&&players.remove(player,entry))release(entry);
    }
    private void release(Entry entry){
        entry.closed=true;
        var future=entry.opening.toCompletableFuture();
        if(future.isDone()&&!future.isCompletedExceptionally())future.getNow(null).close();
    }
    @Override public synchronized void close(){closed=true;for(var entry:players.values())release(entry);players.clear();}
}
