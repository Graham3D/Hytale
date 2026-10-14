package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import java.util.concurrent.*;

/** Server-owned off-thread compiler. Futures return values, never an ECS publication callback.
 * Cache and in-flight memory share one account; all waiters occupy bounded admission slots. */
public final class Me12PlannerCache implements AutoCloseable {
    @FunctionalInterface interface Compiler {Me12Planner.Compiled compile(Me12Planner.Context context,Me12Policies policies,Me12Planner.Budget budget);}
    private final Compiler compiler;
    public Me12PlannerCache(){this(Me12Planner::compile);}
    Me12PlannerCache(Compiler compiler){this.compiler=Objects.requireNonNull(compiler);}
    public static final long MAX_BYTES=64L*1024*1024;
    public static final long BASE_BYTES=3L*1024*1024; // two worker stacks plus bounded request/future/queue metadata
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"Hywind-ME12-compiler");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private record Cached(Me12Planner.Compiled compiled,long bytes,boolean publishing){}
    private final Map<String,Cached> cache=new LinkedHashMap<>(16,.75f,true);
    private final Map<String,Flight> flights=new HashMap<>();
    private final Map<UUID,Integer> outstanding=new HashMap<>();
    private final Map<UUID,Long> generations=new HashMap<>();
    private final Set<Resolved> leases=new HashSet<>();
    private final Map<String,Integer> pins=new HashMap<>();
    private int requests;private long bytes=BASE_BYTES,peak=BASE_BYTES,hits,misses;private boolean closed;
    private static final class Flight {
        final CompletableFuture<Me12Planner.Compiled> completion=new CompletableFuture<>();
        final Me12Planner.Context context;final Me12Policies policies;long bytes;Future<?> task;boolean started;
        Flight(Me12Planner.Context context,Me12Policies policies){this.context=context;this.policies=policies;}
    }
    public record Metrics(long accountedBytes,long peakBytes,int contexts,int inFlight,int outstanding,int builders,long hits,long misses) {}
    public final class Resolved implements AutoCloseable {
        private Me12Planner.Compiled compiled;private final Me12Planner.Plan plan;
        private final UUID world;private final long generation;private final String key;
        private Resolved(UUID world,long generation,Me12Planner.Compiled compiled,String seed){
            this.world=world;this.generation=generation;this.compiled=compiled;this.key=compiled.key();this.plan=compiled.sample(seed);
            leases.add(this);pins.merge(key,1,Integer::sum);
        }
        /** Graph may be consumed only while this lease is live; callers must not retain it. */
        public Me12Planner.Compiled compiled(){synchronized(Me12PlannerCache.this){current(world,generation);if(compiled==null)throw new Me12Planner.CompileFailure("LEASE_CLOSED");return compiled;}}
        public Me12Planner.Plan plan(){return plan;}
        @Override public void close(){synchronized(Me12PlannerCache.this){if(compiled==null)return;compiled=null;leases.remove(this);pins.compute(key,(k,n)->n==null||n<=1?null:n-1);releaseRequest(world);}}
    }
    private void releaseRequest(UUID world){requests--;outstanding.compute(world,(k,n)->n==null||n<=1?null:n-1);}
    public synchronized Metrics metrics(){return new Metrics(bytes,peak,cache.size(),flights.size(),requests,workers.getActiveCount(),hits,misses);}
    public synchronized void replaceWorld(UUID world,long generation) {
        Objects.requireNonNull(world);if(closed)throw new Me12Planner.CompileFailure("CACHE_CLOSED");
        if(generation<0||generation<=generations.getOrDefault(world,-1L))throw new IllegalArgumentException("ME12_WORLD_GENERATION_ORDER");
        if(!generations.containsKey(world))account(null,512);
        for(var lease:List.copyOf(leases))if(lease.world.equals(world))lease.close();
        generations.put(world,generation);
    }
    public synchronized void forgetWorld(UUID world){for(var lease:List.copyOf(leases))if(lease.world.equals(world))lease.close();if(generations.remove(world)!=null)bytes-=512;}
    private void current(UUID world,long generation){if(closed)throw new Me12Planner.CompileFailure("CACHE_CLOSED");if(!Objects.equals(generations.get(world),generation))throw new Me12Planner.CompileFailure("STALE_WORLD");}
    /** Diagnostic-only result: no persistence/native/reward callback is accepted by this API. */
    public CompletionStage<Optional<Me12Planner.Plan>> shadow(Me12FeatureState state,UUID world,long generation,Me12Planner.Context context,Me12Policies policies,String birthSeed){
        state.validateStage2();if(!state.evaluatesShadow())return CompletableFuture.completedFuture(Optional.empty());
        return request(world,generation,context,policies,"shadow/"+com.inigmasgames.hytalerpg.progress.RewardIntent.digest(birthSeed)).thenApply(Optional::of);
    }
    /** Returns empty on a miss. It never compiles or waits on the world thread. */
    public synchronized Optional<Me12Planner.Plan> warm(UUID world,long generation,String key,String seed) {
        current(world,generation);var compiled=cache.get(key);return compiled==null?Optional.empty():Optional.of(compiled.compiled().sample(seed));
    }
    public CompletionStage<Me12Planner.Plan> request(UUID world,long generation,Me12Planner.Context context,Me12Policies policies,String seed) {
        return requestResolved(world,generation,context,policies,seed).thenApply(value->{try(value){return value.plan();}});
    }
    /** Existing bounded cache also returns the compiler needed by the native birth finalizer. */
    public CompletionStage<Resolved> requestResolved(UUID world,long generation,Me12Planner.Context context,Me12Policies policies,String seed) {
        synchronized(this) {
            current(world,generation);
            if(requests>=256||outstanding.getOrDefault(world,0)>=32)throw new Me12Planner.CompileFailure("REQUEST_LIMIT");
            String key=context.key(policies);var ready=cache.get(key);
            if(ready!=null){
                hits++;
                if(!ready.compiled().context().equals(context)||!ready.compiled().policies().equals(policies))throw new Me12Planner.CompileFailure("CACHE_KEY_COLLISION");
                requests++;outstanding.merge(world,1,Integer::sum);
                try{return CompletableFuture.completedFuture(new Resolved(world,generation,ready.compiled(),seed)).minimalCompletionStage();}
                catch(RuntimeException failure){releaseRequest(world);throw failure;}
            }
            var flight=flights.get(key);
            misses++;
            if(flight!=null&&(!flight.context.equals(context)||!flight.policies.equals(policies)))throw new Me12Planner.CompileFailure("CACHE_KEY_COLLISION");
            requests++;outstanding.merge(world,1,Integer::sum);
            if(flight==null) {
                flight=new Flight(context,policies);
                try {account(flight,65536L+policies.documents().stream().mapToLong(d->4L*d.utf8().length()).sum());}
                catch(RuntimeException failure){requests--;outstanding.compute(world,(k,n)->n<=1?null:n-1);throw failure;}
                flights.put(key,flight);var work=flight;
                try {flight.task=workers.submit(()->build(key,context,policies,work));}
                catch(RejectedExecutionException e){flights.remove(key);release(flight);flight.completion.completeExceptionally(new Me12Planner.CompileFailure("BUILDER_QUEUE"));}
            }
            // Register while holding the publication lock, so no unaccounted completed graph can escape eviction.
            var result=flight.completion.thenApply(compiled->{synchronized(this){current(world,generation);return new Resolved(world,generation,compiled,seed);}});
            result.whenComplete((value,failure)->{if(failure!=null)synchronized(this){releaseRequest(world);}});
            return result.minimalCompletionStage();
        }
    }
    private void build(String key,Me12Planner.Context context,Me12Policies policies,Flight flight) {
        try {
            synchronized(this){flight.started=true;}
            var budget=new Me12Planner.Budget(n->account(flight,n));
            var compiled=compiler.compile(context,policies,budget);
            synchronized(this){if(closed)throw new Me12Planner.CompileFailure("CACHE_CLOSED");cache.put(key,new Cached(compiled,flight.bytes,true));}
            flight.completion.complete(compiled);
            synchronized(this){flights.remove(key);if(closed)release(flight);else cache.put(key,new Cached(compiled,flight.bytes,false));}
        }catch(RuntimeException failure){synchronized(this){flights.remove(key);cache.remove(key);release(flight);}flight.completion.completeExceptionally(failure);}
    }
    private void release(Flight flight){bytes-=flight.bytes;flight.bytes=0;}
    private synchronized void account(Flight flight,long size) {
        if(closed)throw new Me12Planner.CompileFailure("CANCELLED");
        while(bytes+size>MAX_BYTES){
            var oldest=cache.entrySet().stream().filter(e->!e.getValue().publishing()&&!pins.containsKey(e.getKey())).map(Map.Entry::getKey).findFirst().orElse(null);
            if(oldest==null)break;bytes-=cache.remove(oldest).bytes();
        }
        if(bytes+size>MAX_BYTES)throw new Me12Planner.CompileFailure("SHARED_MEMORY");
        bytes+=size;if(flight!=null)flight.bytes+=size;peak=Math.max(peak,bytes);
    }
    @Override public synchronized void close(){if(closed)return;closed=true;for(var lease:List.copyOf(leases))lease.close();workers.shutdownNow();cache.clear();generations.clear();
        for(var flight:List.copyOf(flights.values())){if(flight.task!=null)flight.task.cancel(true);flight.completion.completeExceptionally(new Me12Planner.CompileFailure("CANCELLED"));if(!flight.started){flights.values().remove(flight);release(flight);}}
        // In-flight allocations are released by their worker; queued builds have no allocation.
        bytes=flights.values().stream().mapToLong(f->f.bytes).sum();}
}
