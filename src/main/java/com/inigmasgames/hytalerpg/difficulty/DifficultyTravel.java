package com.inigmasgames.hytalerpg.difficulty;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** One transfer per character, durable intent before native handoff, no duplicate player data. */
public final class DifficultyTravel {
    public record Destination(UUID world,String name,DifficultyId mode,double x,double y,double z,float yaw){
        public Destination{Objects.requireNonNull(world);Objects.requireNonNull(mode);if(name==null||name.isBlank())throw new IllegalArgumentException("WORLD_NAME");
            for(double n:new double[]{x,y,z,yaw})if(!Double.isFinite(n))throw new IllegalArgumentException("INVALID_SPAWN");}
    }
    public record Pending(UUID operation,UUID player,UUID source,Destination destination,boolean forced){
        public Pending{Objects.requireNonNull(operation);Objects.requireNonNull(player);Objects.requireNonNull(source);Objects.requireNonNull(destination);}
    }
    public record Journal(List<Pending> pending){public Journal{pending=List.copyOf(pending);if(pending.size()>1024||pending.stream().map(Pending::player).distinct().count()!=pending.size())throw new IllegalArgumentException("TRANSFER_JOURNAL_BOUNDS");}}
    public interface Port {
        CompletionStage<UUID> admit(UUID player,DifficultyId mode,boolean forced);
        CompletionStage<Destination> prepare(UUID player,DifficultyId mode);
        /** Must revalidate source/admission, clean existing owners, await persistence, then native handoff exactly once. */
        CompletionStage<Void> handoff(Pending pending);
        CompletionStage<UUID> location(UUID player);
    }
    private final DifficultyStorage<Journal> storage;private final Port port;private final Executor io;
    private volatile Map<UUID,Pending> pending;
    private final ConcurrentMap<UUID,CompletableFuture<Void>> active=new ConcurrentHashMap<>();
    public DifficultyTravel(Path path,Port port,Executor io){this.storage=new DifficultyStorage<>(path,Journal.class);this.port=port;this.io=io;
        var initial=new HashMap<UUID,Pending>();storage.read().orElse(new Journal(List.of())).pending().forEach(p->initial.put(p.player(),p));pending=Map.copyOf(initial);}
    public boolean busy(UUID player){return active.containsKey(player)||pending.containsKey(player);}
    public Optional<Pending> pending(UUID player){return Optional.ofNullable(pending.get(player));}
    public CompletionStage<Void> request(UUID player,DifficultyId mode,boolean forced){
        var result=new CompletableFuture<Void>();var existing=active.putIfAbsent(player,result);if(existing!=null)return existing.minimalCompletionStage();
        if(pending.containsKey(player)){active.remove(player,result);result.completeExceptionally(new IllegalStateException("TRANSFER_RECOVERY_REQUIRED"));return result;}
        try{port.admit(player,mode,forced).thenCompose(source->port.prepare(player,mode).thenCompose(destination->{
            if(source.equals(destination.world()))return CompletableFuture.failedFuture(new IllegalStateException("ALREADY_IN_DIFFICULTY"));
            var intent=new Pending(UUID.randomUUID(),player,source,destination,forced);
            return CompletableFuture.runAsync(()->save(intent),io).thenCompose(ignored->port.handoff(intent))
                    .thenRunAsync(()->clear(player,intent.operation()),io);
        })).whenComplete((v,e)->finish(player,result,e));}catch(Throwable e){finish(player,result,e);}return result.minimalCompletionStage();
    }
    /** Reconnect observes the native location: arrived means acknowledge; otherwise return safely to Normal.
     * No uncertain cross-world operation is ever replayed in the same connection. */
    public CompletionStage<Void> recover(UUID player){
        var result=new CompletableFuture<Void>();var existing=active.putIfAbsent(player,result);if(existing!=null)return existing.minimalCompletionStage();
        var intent=pending.get(player);if(intent==null){finish(player,result,null);return result;}
        try{port.location(player).thenCompose(location->{
            if(location.equals(intent.destination().world()))return CompletableFuture.completedFuture(null);
            return port.prepare(player,DifficultyId.NORMAL).thenCompose(destination->{
                if(location.equals(destination.world()))return CompletableFuture.completedFuture(null);
                return port.handoff(new Pending(intent.operation(),player,location,destination,true));
            });
        }).thenRunAsync(()->clear(player,intent.operation()),io).whenComplete((v,e)->finish(player,result,e));}catch(Throwable e){finish(player,result,e);}return result.minimalCompletionStage();
    }
    private void finish(UUID player,CompletableFuture<Void> result,Throwable error){active.remove(player,result);if(error==null)result.complete(null);else result.completeExceptionally(error);}
    private synchronized void save(Pending intent){var next=new HashMap<>(pending);if(next.putIfAbsent(intent.player(),intent)!=null)throw new IllegalStateException("TRANSFER_ALREADY_PENDING");
        var journal=new Journal(List.copyOf(next.values()));pending=Map.copyOf(next);storage.write(journal);}
    private synchronized void clear(UUID player,UUID operation){var old=pending.get(player);if(old==null||!old.operation().equals(operation))throw new IllegalStateException("TRANSFER_OPERATION_CHANGED");
        var next=new HashMap<>(pending);next.remove(player);storage.write(new Journal(List.copyOf(next.values())));pending=Map.copyOf(next);}
}
