package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.diagnostics.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Whole diagnostic process workload, including writer drain. Not a native-world or client benchmark. */
class HroReleaseQualificationTest {
    @TempDir Path root;
    @Test void repeatedMatchedWorkloadsIncludeInitializationWorkerDrainAndConcurrentManualRecorder()throws Exception {
        var process=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        var trials=new ArrayList<Map<String,Object>>();UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        for(int round=0;round<4;round++) {
            long[] results=new long[2];
            for(int mode=0;mode<2;mode++) {
                boolean enabled=mode==1;var rng=new Random(931);long beforeCpu=process.getProcessCpuTime(),beforeWall=System.nanoTime(),initialization=0;
                long beforeGc=ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum();
                var firstWrite=new java.util.concurrent.atomic.AtomicLong();long captureRequested=System.nanoTime();
                long[] callbacks=new long[1024];int index=0;Path directory=Files.createDirectory(root.resolve("trial-"+round+"-"+mode));
                try(var recorder=new HroRecorder(directory,System::nanoTime,()->Map.of("qualification","SYNTHETIC"),()->firstWrite.compareAndSet(0,System.nanoTime()));
                    var manual=new BoundedTraceWriter(directory.resolve("manual.jsonl"),1024*1024,1,error->{throw new AssertionError(error);})) {
                    HroContracts.IDS.forEach(recorder::registered);
                    if(enabled){long start=System.nanoTime();recorder.start(owner,world);HroRecorderTest.await(()->recorder.active(owner));initialization=System.nanoTime()-start;}
                    for(int batch=0;batch<64;batch++) {
                        for(int i=0;i<16;i++) {
                            int value=rng.nextInt(10000);results[mode]+=value;
                            var facts=Map.<String,Object>of("cause","Physical","healthBefore",100,"healthAfter",75,"accepted",25,"postAbsorption",25,"reconciliation","MATCH","itemId","item-"+index);
                            String operation="op-"+index;long start=System.nanoTime();
                            recorder.observe(owner,"COMBAT.RECONCILE","fixture","NATIVE_RESULT",operation,world,1L,(long)batch,1L,facts);
                            callbacks[index++]=System.nanoTime()-start;
                        }
                        manual.submit(Map.of("event","qualification","role","role-"+batch,"value",results[mode]));
                        assertTrue(SharedTraceBudget.used()<=SharedTraceBudget.LIMIT);Thread.sleep(3);
                    }
                    if(enabled){assertTrue(recorder.active(owner));recorder.stop();HroRecorderTest.await(()->recorder.status().startsWith("HRO COMPLETE"));}
                }
                long elapsed=System.nanoTime()-beforeWall,cpu=process.getProcessCpuTime()-beforeCpu;Arrays.sort(callbacks);
                long gc=ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum()-beforeGc;
                trials.add(Map.of("round",round,"enabled",enabled,"elapsedIncludingPacingAndDrainNanos",elapsed,"wholeProcessCpuNanos",cpu,
                        "admissionReadyNanos",initialization,"workerFirstWriteNanos",enabled?firstWrite.get()-captureRequested:0,"gcCollectionMillis",gc,
                        "producerP95Nanos",callbacks[972],"producerP99Nanos",callbacks[1013],"authoritativeResult",results[mode]));
            }assertEquals(results[0],results[1]);
        }
        HroRecorderTest.await(()->SharedTraceBudget.used()==0);
        Path report=Path.of("build/reports/hro");Files.createDirectories(report);
        Files.writeString(report.resolve("release-workloads.json"),HroBundle.JSON.toJson(Map.of("scope","OFFLINE_WHOLE_DIAGNOSTIC_PROCESS_WITH_CONCURRENT_LEGACY_WRITER",
                "trials",trials,"nativeTickAndClient","UNVERIFIED","faultCoverage","HroRecorderTest: slow disk, disk-full injection, expiry, interruption, async stop; HroQualificationTest: burst overload")));
    }
}
