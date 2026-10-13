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
    @Test void overloadPreservesCriticalLaneAndReportsTimingAndEvidenceLoss()throws Exception {
        UUID player=UUID.randomUUID();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        long[] timing=new long[2000];
        try(var recorder=new HroRecorder(temporary,System::nanoTime,()->Map.of("fixture","overload"),()->{
            entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        })) {
            HroContracts.IDS.forEach(recorder::registered);recorder.start(player,player);
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var facts=Map.of("before",100d,"after",75d,"equipmentRevision","r1");
            for(int i=0;i<timing.length;i++){long begin=System.nanoTime();recorder.observe(player,"COMBAT.RECONCILE","fixture","STAGE","op",player,1L,null,null,facts);timing[i]=System.nanoTime()-begin;}
            recorder.observe(player,"COMBAT.RECONCILE","fixture","NATIVE_RESULT","op",player,1L,null,null,
                    Map.of("healthBefore",100,"healthAfter",75,"accepted",25,"postAbsorption",25,"reconciliation","MATCH"));
            assertTrue(com.inigmasgames.hytalerpg.diagnostics.SharedTraceBudget.used()<=16L*1024*1024);
            recorder.stop();release.countDown();HroRecorderTest.await(()->recorder.status().startsWith("HRO COMPLETE"));
            Path bundle;try(var files=Files.list(temporary)){bundle=files.findFirst().orElseThrow();}
            var events=new ArrayList<HroEvent>();HroTool.read(bundle,(event,locator)->events.add(event));
            assertEquals(1,events.stream().filter(e->e.boundary().equals("NATIVE_RESULT")).count());
            assertTrue(events.size()<=512);assertTrue(Files.readString(bundle.resolve("integrity.json")).contains("\"evidenceComplete\":false"));
            HroTool.analyze(bundle);assertFalse(Files.readString(bundle.resolve("findings.json")).contains("\"outcome\":\"PASS\""));
            Arrays.sort(timing);Path report=Path.of("build/reports/hro");Files.createDirectories(report);
            Files.writeString(report.resolve("overload.json"),HroBundle.JSON.toJson(Map.of("scope","OFFLINE_BLOCKED_WRITER",
                    "producerP99Nanos",timing[1980],"criticalOutcomeRetained",true,"routineLossVisible",true,"gameplayMutation","NONE")));
        }finally{release.countDown();}
    }
    @Test void matchedSyntheticWorkloadAndProducerMeasurement()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        Map<String,Object> facts=Map.of("generation","generation","receiptId","receipt","before",100d,"remaining",75d,
                "requested",50d,"absorbed",25d,"damageRemaining",25d,"durable",true,"duplicate",false,"equipmentRevision","revision");
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
                    long start=System.nanoTime();recorder.observe(owner,"CYCLONE.ACCOUNTING","fixture","DEBIT","op",world,1L,1L,1L,facts);
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
            report.put("syntheticTickTargetPassed",tickP99<=500_000&&Arrays.stream(ticks).average().orElseThrow()<=100_000);report.put("retainedHeap","STRUCTURALLY_BOUNDED; NOT_A_LIVE_HEAP_CENSUS");
            Path output=Path.of("build/reports/hro");Files.createDirectories(output);
            Files.writeString(output.resolve("qualification.json"),HroBundle.JSON.toJson(report));
            assertEquals(0,disabledBytes,"Warmed disabled path allocation");
            // Timing results remain evidence, not flaky machine-wide assertions masquerading as correctness tests.
        }
    }
}
