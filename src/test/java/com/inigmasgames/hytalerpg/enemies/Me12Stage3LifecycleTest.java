package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Me12Stage3LifecycleTest {
    @TempDir Path directory;
    @Test void P05P07P10TenOfflineArchiveRootCacheUnloadCyclesPreserveFrozenIdentity()throws Exception{
        var fixture=Me12PersistenceTest.fixture(true);var root=fixture.root();long began=System.nanoTime(),peak=0;
        for(int cycle=0;cycle<10;cycle++){
            var cache=new Me12PlannerCache();var context=fixture.compiled().context();
            try(var store=new FileEncounterStore(directory);var runtime=new PersistentEncounterRuntime(store,(player,reward)->fail("No reward opportunity"));cache){
                cache.replaceWorld(root.world(),cycle);
                var resolved=cache.requestResolved(root.world(),cycle,context,fixture.policies(),root.plan().seed()).toCompletableFuture().get(5,TimeUnit.SECONDS);
                assertEquals(1,cache.metrics().outstanding());assertEquals(context,resolved.compiled().context());
                assertEquals(root.plan(),runtime.reserveEnemyBirthRoot(root,fixture.policies()).toCompletableFuture().get(5,TimeUnit.SECONDS));
                assertEquals(root,runtime.enemyBirthRoot(root.world(),root.encounter()).toCompletableFuture().get(5,TimeUnit.SECONDS).orElseThrow());
                peak=Math.max(peak,cache.metrics().peakBytes());cache.forgetWorld(root.world());assertEquals(0,cache.metrics().outstanding());
                assertThrows(Me12Planner.CompileFailure.class,resolved::compiled);resolved.close();assertFalse(runtime.unavailable());
                for(var actor:root.plan().actors().subList(1,root.plan().actors().size())){
                    var baseline=fixture.policies().balance().rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.INITIAL_PACK_MINION,true,0);
                    assertNull(actor.immutableRewardContext().v2());assertEquals(baseline,actor.immutableRewardContext());
                }
            }
            assertEquals(0,cache.metrics().accountedBytes());assertEquals(0,cache.metrics().outstanding());
        }
        var path=Path.of("build/reports/me12/stage3-lifecycle.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new com.google.gson.Gson().toJson(Map.of("cycles",10,"elapsedMillis",(System.nanoTime()-began)/1_000_000,"peakAccountedBytes",peak,
                "evidence","OFFLINE_AUTHORITATIVE_STORE_AND_CACHE_ONLY","nativeCarrierCleanup","BLOCKED_CONNECTED_EXECUTION",
                "initialMinionXpFactor",root.plan().actors().get(1).immutableRewardContext().xpFactor(),
                "wholePackXpFactorSumAtEqualLevelAndRank",root.plan().actors().stream().mapToDouble(a->a.immutableRewardContext().xpFactor()).sum())));
    }
    @Test void S08S09HeldNativeCallbacksRemainInsideAllSharedBounds()throws Exception{
        var p=Me12Policies.canonical();var c=Me12PlannerTest.context(com.inigmasgames.hytalerpg.difficulty.DifficultyId.NORMAL);
        try(var cache=new Me12PlannerCache()){
            var worlds=new ArrayList<UUID>();var held=new ArrayList<Me12PlannerCache.Resolved>();
            for(int w=0;w<8;w++){UUID world=UUID.randomUUID();worlds.add(world);cache.replaceWorld(world,1);
                for(int i=0;i<32;i++)held.add(cache.requestResolved(world,1,c,p,"dense/"+w+"/"+i).toCompletableFuture().get(5,TimeUnit.SECONDS));}
            assertEquals(256,cache.metrics().outstanding());assertTrue(cache.metrics().accountedBytes()<=Me12PlannerCache.MAX_BYTES);
            assertThrows(Me12Planner.CompileFailure.class,()->cache.requestResolved(worlds.getFirst(),1,c,p,"overflow"));
            for(var world:worlds)cache.forgetWorld(world);assertEquals(0,cache.metrics().outstanding());
            for(var lease:held){assertThrows(Me12Planner.CompileFailure.class,lease::compiled);lease.close();}
        }
    }
}
