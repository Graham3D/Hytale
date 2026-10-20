package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.progress.Stage13EncounterGroupCommitTest.*;

class Stage13V2SparseLoadTest {
    @TempDir Path directory;
    @Test void sparseProductionReceiptsIncludeCollectionAndForceWithNoDiscardedSamples()throws Exception {
        try(var store=FileEncounterStore.durableV2(directory)){
            var runtime=runtime(store);store.resetTimings();double[] samples=new double[60];
            for(int i=0;i<samples.length;i++){long start=System.nanoTime();assertTrue(done(hit(runtime,101+i).durable()));samples[i]=(System.nanoTime()-start)/1e6;}
            assertEquals(60,forceCount(store));var sorted=samples.clone();Arrays.sort(sorted);
            var result=new LinkedHashMap<String,Object>();result.put("scope","SPARSE_PRODUCTION_V2_NOT_NATIVE_PROOF");result.put("orderedSamplesMs",samples);result.put("p50Ms",sorted[29]);result.put("p95Ms",sorted[56]);result.put("p99Ms",sorted[59]);result.put("collectionWindowNanos",0);result.put("discardedSamples",0);result.put("timings",store.timings().snapshot());result.put("barriers",store.barrierMetrics());
            Path out=Path.of("build/stage13-hardening/v2-sparse-load.json");Files.createDirectories(out.getParent());Files.writeString(out,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        }
    }
}
