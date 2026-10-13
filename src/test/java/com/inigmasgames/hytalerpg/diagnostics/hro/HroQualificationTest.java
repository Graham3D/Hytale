package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline producer qualification, explicitly not a native world-tick or client-FPS measurement. */
class HroQualificationTest {
    @TempDir Path temporary;
    @Test void matchedSyntheticWorkloadAndProducerMeasurement()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        Map<String,Object> facts=Map.of("result","PROJECTED","logicalSlot","skill01","skillId","fire_bolt");
        long[] active=new long[6400],ticks=new long[400];
        var allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();
        try(var recorder=new HroRecorder(temporary,System::nanoTime,()->Map.of("fixture","synthetic"),()->{})) {
            for(int i=0;i<20_000;i++)recorder.observe(owner,"SKILL.PROJECTION","fixture","PROJECTION","op",world,1L,1L,1L,facts);
            long[] disabledSamples=new long[5];long disabledNanos=0;
            for(int sample=0;sample<disabledSamples.length;sample++) {
                long bytesBefore=allocation.getThreadAllocatedBytes(thread),disabledStart=System.nanoTime();
                for(int i=0;i<50_000;i++)recorder.observe(owner,"SKILL.PROJECTION","fixture","PROJECTION","op",world,1L,1L,1L,facts);
                disabledNanos=System.nanoTime()-disabledStart;disabledSamples[sample]=allocation.getThreadAllocatedBytes(thread)-bytesBefore;
            }
            long disabledBytes=disabledSamples[disabledSamples.length-1];
            HroContracts.IDS.forEach(recorder::registered);recorder.start(owner,world);HroRecorderTest.await(()->recorder.active(owner));
            for(int i=0;i<1000;i++){recorder.observe(owner,"SKILL.PROJECTION","fixture","PROJECTION","op",world,1L,1L,1L,facts);if(i%8==0)Thread.sleep(2);}
            assertTrue(recorder.active(owner),recorder.status());
            Random on=new Random(731),off=new Random(731);long onResult=0,offResult=0;int n=0;
            long activeBytesBefore=allocation.getThreadAllocatedBytes(thread);
            for(int tick=0;tick<ticks.length;tick++) {
                long total=0;
                for(int i=0;i<16;i++) {
                    // Same inputs and RNG calls in both workloads. HRO cannot consume the RNG instance.
                    onResult+=on.nextInt(1000);offResult+=off.nextInt(1000);
                    long start=System.nanoTime();recorder.observe(owner,"SKILL.PROJECTION","fixture","PROJECTION","op",world,1L,1L,1L,facts);
                    active[n]=System.nanoTime()-start;total+=active[n++];
                }
                ticks[tick]=total;Thread.sleep(3);
            }
            long allocated=allocation.getThreadAllocatedBytes(thread)-activeBytesBefore;
            assertEquals(offResult,onResult);assertEquals(off.nextLong(),on.nextLong());
            assertTrue(recorder.active(owner),recorder.status());recorder.stop();HroRecorderTest.await(()->recorder.status().startsWith("HRO COMPLETE"));
            Arrays.sort(active);Arrays.sort(ticks);long p99=active[(int)(active.length*.99)],tickP99=ticks[(int)(ticks.length*.99)];
            Map<String,Object> report=new LinkedHashMap<>();report.put("scope","OFFLINE_SYNTHETIC_PRODUCER_ONLY");
            report.put("disabledNanosPerCall",disabledNanos/50_000.0);report.put("disabledAllocatedBytes",disabledBytes);
            report.put("disabledAllocationSamples",disabledSamples);
            report.put("activeP99Nanos",p99);report.put("synthetic16ObservationTickP99Nanos",tickP99);
            report.put("activeP95Nanos",active[(int)(active.length*.95)]);
            report.put("synthetic16ObservationTickMeanNanos",Arrays.stream(ticks).average().orElseThrow());
            report.put("activeAllocatedBytesPerCall",allocated/6400.0);report.put("nativeWorldTickComparison","UNVERIFIED");
            report.put("clientSmoothness","UNVERIFIED");report.put("observerTargetPassed",p99<50_000);
            report.put("syntheticTickTargetPassed",tickP99<=500_000);report.put("retainedHeap","STRUCTURALLY_BOUNDED; NOT_A_LIVE_HEAP_CENSUS");
            Path output=Path.of("build/reports/hro");Files.createDirectories(output);
            Files.writeString(output.resolve("qualification.json"),HroBundle.JSON.toJson(report));
            assertEquals(0,disabledBytes,"Warmed disabled path allocation");
            // Timing results remain evidence, not flaky machine-wide assertions masquerading as correctness tests.
        }
    }
}
