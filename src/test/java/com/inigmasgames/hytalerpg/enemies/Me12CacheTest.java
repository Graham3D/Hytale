package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12CacheTest {
    @Test void S08S09SingleFlightWorldAndServerLimitsCancellationAndNoWorldCompilation() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var builds=new AtomicInteger();String caller=Thread.currentThread().getName();
        try(var cache=new Me12PlannerCache((c,p,b)->{
            assertNotEquals(caller,Thread.currentThread().getName());builds.incrementAndGet();entered.countDown();
            try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new Me12Planner.CompileFailure("CANCELLED");}
            throw new Me12Planner.CompileFailure("INJECTED_FAILURE");
        })) {
            var c=Me12PlannerTest.context(DifficultyId.NORMAL);var p=Me12Policies.canonical();var futures=new ArrayList<CompletionStage<Me12Planner.Plan>>();
            for(int w=0;w<8;w++){
                var world=UUID.nameUUIDFromBytes(new byte[]{(byte)w});cache.replaceWorld(world,1);
                assertTrue(cache.warm(world,1,c.key(p),"miss").isEmpty());
                for(int i=0;i<32;i++)futures.add(cache.request(world,1,c,p,"queue/"+i));
                assertThrows(Me12Planner.CompileFailure.class,()->cache.request(world,1,c,p,"33rd"));
            }
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertEquals(1,builds.get());assertEquals(256,cache.metrics().outstanding());
            var ninth=UUID.randomUUID();cache.replaceWorld(ninth,1);assertThrows(Me12Planner.CompileFailure.class,()->cache.request(ninth,1,c,p,"257th"));
            // Canceling a subscriber's copy cannot bypass queue accounting or cancel another world's flight.
            futures.getFirst().toCompletableFuture().cancel(true);assertEquals(256,cache.metrics().outstanding());
            release.countDown();for(var f:futures)assertThrows(CompletionException.class,()->f.toCompletableFuture().join());
            assertEquals(0,cache.metrics().outstanding());assertEquals(Me12PlannerCache.BASE_BYTES+9*512,cache.metrics().accountedBytes());
        }finally{release.countDown();}
    }
    @Test void S08S09MemoryStateDeadlineAndStaleGenerationFailuresAreExplicit() throws Exception {
        var context=Me12PlannerTest.context(DifficultyId.NORMAL);var policy=Me12Policies.canonical();var world=UUID.randomUUID();
        try(var cache=new Me12PlannerCache((c,p,b)->{b.charge(Me12PlannerCache.MAX_BYTES);throw new AssertionError("unreachable");})){
            cache.replaceWorld(world,1);var error=assertThrows(ExecutionException.class,()->cache.request(world,1,context,policy,"memory").toCompletableFuture().get(2,TimeUnit.SECONDS));
            assertTrue(error.getCause().getMessage().contains("SHARED_MEMORY"));assertTrue(cache.metrics().peakBytes()<=Me12PlannerCache.MAX_BYTES);
        }
        var budget=new Me12Planner.Budget(Long.MAX_VALUE,n->{});for(int i=0;i<100000;i++)budget.state();assertThrows(Me12Planner.CompileFailure.class,budget::state);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var cache=new Me12PlannerCache((c,p,b)->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return Me12Planner.compile(c,p,b);})){cache.replaceWorld(world,1);
            var pending=cache.request(world,1,context,policy,"stale");assertTrue(entered.await(1,TimeUnit.SECONDS));cache.replaceWorld(world,2);release.countDown();
            assertTrue(assertThrows(ExecutionException.class,()->pending.toCompletableFuture().get(2,TimeUnit.SECONDS)).getCause().getMessage().contains("STALE_WORLD"));
            assertThrows(Me12Planner.CompileFailure.class,()->cache.warm(world,1,context.key(policy),"stale"));
        }finally{release.countDown();}
    }
    @Test void S07AllFrozenRestrictionsParticipateInKeysAndWarmLookupDoesNoCompile() throws Exception {
        var c=Me12PlannerTest.context(DifficultyId.HELL);var p=Me12Policies.canonical();var key=c.key(p);
        var weights=new TreeMap<>(c.effectiveWeights());weights.put("ME-001",weights.get("ME-001")+1);
        assertNotEquals(key,Me12DistributionTest.withWeights(c,weights).key(p));
        var member=c.leader();var changed=new Me12Planner.Member(member.role(),member.source(),member.binding(),Set.of("WATER"),false);
        assertNotEquals(key,new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),2,c.difficulty(),c.evidence(),changed,c.minions(),List.of(),c.effectiveWeights()).key(p));
        var builds=new AtomicInteger();try(var cache=new Me12PlannerCache((ctx,policy,budget)->{builds.incrementAndGet();return Me12Planner.compile(ctx,policy,budget);})){
            var world=UUID.randomUUID();cache.replaceWorld(world,1);var expected=cache.request(world,1,c,p,"warm").toCompletableFuture().get(2,TimeUnit.SECONDS);
            long[] times=new long[10000];for(int i=0;i<times.length;i++){long start=System.nanoTime();assertEquals(expected,cache.warm(world,1,key,"warm").orElseThrow());times[i]=System.nanoTime()-start;}
            Arrays.sort(times);assertTrue(times[9499]<=500000);assertTrue(times[9899]<=1000000);assertEquals(1,builds.get());
            System.out.println("ME12_CACHE warmP95Ns="+times[9499]+" warmP99Ns="+times[9899]+" metrics="+cache.metrics());
        }
    }
    @Test void P07ShadowCannotPublishOrConsumeLegacyRngAndDrainDoesNotCompile() throws Exception {
        var context=Me12PlannerTest.context(DifficultyId.NORMAL);var policies=Me12Policies.canonical();
        try(var cache=new Me12PlannerCache()){
            var world=UUID.randomUUID();cache.replaceWorld(world,1);
            assertTrue(cache.shadow(Me12FeatureState.DISABLED,world,1,context,policies,"birth").toCompletableFuture().join().isEmpty());
            assertTrue(cache.shadow(Me12FeatureState.DRAIN,world,1,context,policies,"birth").toCompletableFuture().join().isEmpty());
            assertEquals(0,cache.metrics().contexts());
            var result=cache.shadow(Me12FeatureState.SHADOW,world,1,context,policies,"birth").toCompletableFuture().get(2,TimeUnit.SECONDS).orElseThrow();
            assertEquals(result,cache.shadow(Me12FeatureState.SHADOW,world,1,context,policies,"birth").toCompletableFuture().get(2,TimeUnit.SECONDS).orElseThrow());
            assertThrows(IllegalArgumentException.class,()->cache.shadow(Me12FeatureState.ENABLED,world,1,context,policies,"birth"));
            cache.forgetWorld(world);assertThrows(Me12Planner.CompileFailure.class,()->cache.warm(world,1,context.key(policies),"birth"));
        }
    }
    @Test void S08TwoBuilderMaximumAndCooperativeShutdown() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var active=new AtomicInteger();var maximum=new AtomicInteger();
        var cache=new Me12PlannerCache((c,p,b)->{int count=active.incrementAndGet();maximum.accumulateAndGet(count,Math::max);entered.countDown();
            try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{active.decrementAndGet();}
            b.check();throw new Me12Planner.CompileFailure("fixture-finished");});
        try{
            var world=UUID.randomUUID();cache.replaceWorld(world,1);var c=Me12PlannerTest.context(DifficultyId.NORMAL);var p=Me12Policies.canonical();
            var futures=new ArrayList<CompletionStage<Me12Planner.Plan>>();
            for(int i=0;i<3;i++){var unique=new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),i,c.difficulty(),c.evidence(),c.leader(),c.minions(),c.hordeSlots(),c.effectiveWeights());futures.add(cache.request(world,1,unique,p,"builders"));}
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertEquals(2,maximum.get());assertEquals(3,cache.metrics().inFlight());
            cache.close();for(var future:futures)assertThrows(CompletionException.class,()->future.toCompletableFuture().join());
        }finally{release.countDown();cache.close();}
    }
    @Test void S08WarmLatencyWhileBothBuildersCompile() throws Exception {
        var p=Me12Policies.canonical();var c=Me12PlannerTest.context(DifficultyId.NORMAL);var world=UUID.randomUUID();
        try(var cache=new Me12PlannerCache()){
            cache.replaceWorld(world,1);cache.request(world,1,c,p,"warm").toCompletableFuture().get(2,TimeUnit.SECONDS);String key=c.key(p);
            var nightmare=cache.request(world,1,Me12PlannerTest.context(DifficultyId.NIGHTMARE),p,"cold");
            var hell=cache.request(world,1,Me12PlannerTest.context(DifficultyId.HELL),p,"cold");
            long[] times=new long[10000];for(int i=0;i<times.length;i++){long start=System.nanoTime();assertTrue(cache.warm(world,1,key,"contended/"+i).isPresent());times[i]=System.nanoTime()-start;}
            nightmare.toCompletableFuture().get(2,TimeUnit.SECONDS);hell.toCompletableFuture().get(2,TimeUnit.SECONDS);Arrays.sort(times);
            assertTrue(times[9499]<=500000,"contended p95 "+times[9499]);assertTrue(times[9899]<=1000000,"contended p99 "+times[9899]);
            assertTrue(cache.metrics().peakBytes()<=Me12PlannerCache.MAX_BYTES);
            var output=java.nio.file.Path.of("build/reports/me12/stage2-cache.json");java.nio.file.Files.createDirectories(output.getParent());
            java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("warmSamples",times.length,"contendedP95Nanos",times[9499],"contendedP99Nanos",times[9899],"metrics",cache.metrics())));
        }
    }
}
