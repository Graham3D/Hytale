package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.diagnostics.hro.HroHeapQualification;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Uses the existing test-only instrumentation agent in an offline JVM, never a game process. */
public final class Me12HeapQualification {
    public static void main(String[] args)throws Exception {
        var policy=Me12Policies.canonical();var c=Me12PlannerTest.context(DifficultyId.HELL);
        var compiled=Me12Planner.compile(c,policy,new Me12Planner.Budget(n->{}));
        long measured=retained(compiled);
        if(measured>compiled.accountedBytes())throw new IllegalStateException("ME12_GRAPH_UNDERACCOUNTED:"+measured+"/"+compiled.accountedBytes());
        var world=UUID.randomUUID();long cacheBytes;Me12PlannerCache.Metrics metrics;
        try(var cache=new Me12PlannerCache()){
            cache.replaceWorld(world,1);cache.request(world,1,c,policy,"heap").toCompletableFuture().get(2,TimeUnit.SECONDS);
            cacheBytes=retained(cache);metrics=cache.metrics();
            if(cacheBytes>metrics.accountedBytes()||cacheBytes>Me12PlannerCache.MAX_BYTES)throw new IllegalStateException("ME12_CACHE_UNDERACCOUNTED:"+cacheBytes+"/"+metrics.accountedBytes());
        }
        var report=Map.of("method","Instrumentation.getObjectSize identity traversal including retained graph, cache and worker ownership",
                "graphRetainedBytes",measured,"graphAccountedBytes",compiled.accountedBytes(),"cacheRetainedBytes",cacheBytes,"cacheMetrics",metrics,"limitBytes",Me12PlannerCache.MAX_BYTES);
        var path=Path.of("build/reports/me12/stage2-heap.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static long retained(Object root)throws Exception {
        var field=HroHeapQualification.class.getDeclaredField("instrumentation");field.setAccessible(true);
        var instrumentation=(java.lang.instrument.Instrumentation)field.get(null);
        if(instrumentation==null)throw new IllegalStateException("ME12_TEST_AGENT_MISSING");
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var pending=new ArrayDeque<Object>();pending.add(root);long bytes=0;
        while(!pending.isEmpty()){
            Object value=pending.removeFirst();if(!seen.add(value))continue;
            // JVM-global roots are not planner ownership. Worker stacks and all queue metadata have a separate 3 MiB reserve.
            if(value instanceof Class<?>||value instanceof Enum<?>||value instanceof ClassLoader||value instanceof Thread
                    ||value.getClass().getName().startsWith("jdk.internal.vm."))continue;
            bytes+=instrumentation.getObjectSize(value);var type=value.getClass();
            if(type.isArray()){if(!type.getComponentType().isPrimitive())for(Object child:(Object[])value)if(child!=null)pending.add(child);continue;}
            for(Class<?> current=type;current!=null;current=current.getSuperclass())for(var f:current.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                f.setAccessible(true);Object child=f.get(value);if(child!=null)pending.add(child);
            }
        }return bytes;
    }
}
