package com.inigmasgames.hytalerpg;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real durable storage cost, not a connected/native tick benchmark. */
class Stage13DurabilityLoadTest {
    @TempDir Path temp;
    @Test void fourActorSixteenVictimDurableContributionLoadIsMeasuredAndRestores()throws Exception {
        var registry=EnemyRewardRegistry.load();var store=new FileEncounterStore(temp.resolve("encounters"));
        var runtime=new PersistentEncounterRuntime(store,(player,reward)->fail("No death was observed"));
        UUID world=new UUID(1,1);var actors=new ArrayList<UUID>();var enemies=new ArrayList<UUID>();
        for(int i=0;i<4;i++)actors.add(new UUID(2,i+1));
        for(int i=0;i<16;i++){
            var enemy=new UUID(3,i+1);enemies.add(enemy);
            var spawn=registry.classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,0).orElseThrow();
            assertTrue(runtime.attach(world,enemy,"Wolf_Black",Optional.of(spawn)));
        }
        double[] samples=new double[60];
        for(int tick=0;tick<samples.length;tick++){
            long start=System.nanoTime();
            for(var actor:actors)for(var enemy:enemies)assertTrue(runtime.damage(world,enemy,actor,100,99,100,true,1000+tick*100));
            samples[tick]=(System.nanoTime()-start)/1e6;
        }
        runtime.unload(world);
        for(var enemy:enemies){assertTrue(runtime.attach(world,enemy,"Wolf_Black",Optional.empty()));assertEquals(4,runtime.contributors(world,enemy).size());}
        Arrays.sort(samples);var result=new LinkedHashMap<String,Object>();
        result.put("scope","REAL_FILE_ENCOUNTER_CONTRIBUTIONS_ONLY_NOT_NATIVE_TICK_PROOF");result.put("actors",4);result.put("victims",16);
        result.put("updatesPerSample",64);result.put("samples",samples.length);result.put("p50Ms",samples[29]);result.put("p95Ms",samples[56]);result.put("p99Ms",samples[59]);
        result.put("targetP95Ms",4);result.put("targetP99Ms",8);result.put("withinNominalRpgTickBudget",samples[56]<=4&&samples[59]<=8);
        result.put("connectedProof",false);result.put("restoredContributorCounts",true);result.put("nativePhysicsAiNetworkRenderingMeasured",false);
        Path out=Path.of("build/stage13-hardening/durable-load.json");Files.createDirectories(out.getParent());Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("STAGE13_DURABILITY_LOAD "+new com.google.gson.Gson().toJson(result));
    }
}
