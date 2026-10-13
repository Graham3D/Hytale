package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.diagnostics.SharedTraceBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

class HroRecorderTest {
    @TempDir Path root;
    private final UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
    private static final Map<String,Object> IDENTITY=Map.of("jarSha256","fixture","rpg.sourceCommit","original-capture-commit");
    private HroRecorder recorder(HroRecorder.Fault fault){return new HroRecorder(root,System::nanoTime,()->IDENTITY,fault);}
    private void begin(HroRecorder recorder)throws Exception {
        HroContracts.IDS.forEach(recorder::registered);assertTrue(recorder.start(owner,world).contains("STARTING"));
        await(()->recorder.active(owner));
    }
    private void observe(HroRecorder recorder){recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","hud",world,1L,1L,1L,Map.of());}
    static void await(java.util.function.BooleanSupplier condition)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(5);
        assertTrue(condition.getAsBoolean(),"diagnostic worker did not finish");
    }
    static boolean finalized(String status){return status.startsWith("HRO COMPLETE")||status.startsWith("HRO PARTIAL");}
    private Path bundle()throws Exception {try(var files=Files.list(root)){return files.filter(Files::isDirectory).findFirst().orElseThrow();}}
    @Test void producesNavigablePackageAndPreservesImmutableScalarSnapshot()throws Exception {
        try(var recorder=recorder(()->{})) {
            begin(recorder);var facts=new HashMap<String,Object>();facts.put("reason","original");facts.put("password","do-not-record");
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","hud",world,1L,1L,1L,facts);
            facts.put("reason","mutated");recorder.stop();await(()->recorder.status().startsWith("HRO COMPLETE"));
            Path directory=bundle();
            for(String file:List.of("manifest.json","summary.md","findings.json","coverage.json","capabilities.json","performance.json","integrity.json","checksums.sha256","indexes/operations.jsonl"))
                assertTrue(Files.isRegularFile(directory.resolve(file)),file);
            try(var in=new GZIPInputStream(Files.newInputStream(directory.resolve("events/segment-0001.jsonl.gz")))) {
                String contents=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(contents.contains("original"));assertFalse(contents.contains("mutated"));assertFalse(contents.contains("do-not-record"));
            }
            assertTrue(Files.readString(directory.resolve("manifest.json")).contains("original-capture-commit"));
            HroTool.analyze(directory);assertTrue(Files.isRegularFile(directory.resolve("analysis.json")));
            Files.writeString(directory.resolve("indexes/operations.jsonl"),"tampered");
            assertThrows(java.io.IOException.class,()->HroTool.analyze(directory));
            Files.writeString(directory.resolve("checksums.sha256"), "");
            assertThrows(java.io.IOException.class,()->HroTool.analyze(directory));
        }
    }
    @Test void slowDiskCannotBlockOwnerOrStopAndQueuePressureIsVisible()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var recorder=recorder(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}})) {
            begin(recorder);observe(recorder);assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{for(int i=0;i<2000;i++)observe(recorder);recorder.stop();});
            assertTrue(recorder.status().contains("FINALIZING"));assertTrue(SharedTraceBudget.used()<=SharedTraceBudget.LIMIT);
            release.countDown();await(()->finalized(recorder.status()));
            assertTrue(JsonParser.parseString(Files.readString(bundle().resolve("integrity.json"))).getAsJsonObject().get("dropped").getAsLong()>0);
        }finally{release.countDown();}
        await(()->SharedTraceBudget.used()==0);
    }
    @Test void scopedLossSurvivesChecksumsAndOfflineReplay()throws Exception {
        try(var recorder=recorder(()->{})) {
            begin(recorder);
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","complete-hud",world,1L,1L,1L,Map.of());
            recorder.loss(owner,"GEAR.EQUIPPED","missing-item","QUEUE_LOCK_CONTENTION");
            recorder.stop();await(()->finalized(recorder.status()));
            Path directory=bundle();
            JsonObject integrity=JsonParser.parseString(Files.readString(directory.resolve("integrity.json"))).getAsJsonObject();
            assertEquals(1,integrity.get("dropped").getAsLong());
            assertEquals("GEAR.EQUIPPED",integrity.getAsJsonArray("lossScopes").get(0).getAsJsonObject().get("contract").getAsString());
            JsonObject coverage=JsonParser.parseString(Files.readString(directory.resolve("coverage.json"))).getAsJsonObject();
            assertEquals("PASS",coverage.getAsJsonObject("HUD.PUBLICATION").get("outcome").getAsString());
            assertEquals("INCONCLUSIVE",coverage.getAsJsonObject("GEAR.EQUIPPED").get("outcome").getAsString());
            HroTool.analyze(directory);
            JsonObject offline=JsonParser.parseString(Files.readString(directory.resolve("analysis.json"))).getAsJsonObject()
                    .getAsJsonObject("coverage");
            assertEquals("PASS",offline.getAsJsonObject("HUD.PUBLICATION").get("outcome").getAsString());
            assertEquals("INCONCLUSIVE",offline.getAsJsonObject("GEAR.EQUIPPED").get("outcome").getAsString());
            HroTool.verifyChecksums(directory);
        }
    }
    @Test void lossDetailOverflowStaysBoundedToItsContract()throws Exception {
        try(var recorder=recorder(()->{})) {
            begin(recorder);observe(recorder);
            for(int i=0;i<160;i++)recorder.loss(owner,"GEAR.EQUIPPED","item-"+i,"FACT_LIMIT");
            recorder.stop();await(()->finalized(recorder.status()));
            JsonObject integrity=JsonParser.parseString(Files.readString(bundle().resolve("integrity.json"))).getAsJsonObject();
            assertEquals(160,integrity.get("dropped").getAsLong());
            JsonArray scopes=integrity.getAsJsonArray("lossScopes");assertTrue(scopes.size()<=129);
            assertEquals("LOSS_DETAIL_CAP",scopes.get(scopes.size()-1).getAsJsonObject().get("reason").getAsString());
            assertEquals(32,scopes.get(scopes.size()-1).getAsJsonObject().get("count").getAsInt());
            HroTool.analyze(bundle());
            JsonObject coverage=HroOffline.object(bundle().resolve("analysis.json")).getAsJsonObject("coverage");
            assertEquals("PASS",coverage.getAsJsonObject("HUD.PUBLICATION").get("outcome").getAsString());
            assertEquals("INCONCLUSIVE",coverage.getAsJsonObject("GEAR.EQUIPPED").get("outcome").getAsString());
        }
    }
    @Test void diskFailureDoesNotEscapeCallbackAndRecoveryRetainsCaptureBuild()throws Exception {
        try(var recorder=recorder(()->{throw new java.io.IOException("disk full");})) {
            recorder.start(owner,world);assertDoesNotThrow(()->observe(recorder));await(()->recorder.status().contains("PARTIAL"));
            Path directory=bundle();HroBundle.recover(directory);
            JsonObject manifest=JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
            assertEquals("original-capture-commit",manifest.getAsJsonObject("captureBuild").get("rpg.sourceCommit").getAsString());
            assertEquals("RECOVERED_PARTIAL",manifest.get("state").getAsString());
        }
    }
    @Test void expiryIsAutomaticWithoutMoreGameplayCallbacks()throws Exception {
        var clock=new AtomicLong(1);
        try(var recorder=new HroRecorder(root,clock::get,()->IDENTITY,()->{})) {
            begin(recorder);clock.set(HroRecorder.DURATION_NANOS+2);await(()->recorder.status().startsWith("HRO COMPLETE"));
            assertTrue(recorder.status().contains("EXPIRED"));
        }
    }
    @Test void scopeAndDisabledCaptureDoNotReadPayload()throws Exception {
        try(var recorder=recorder(()->{})) {
            Map<String,Object> explosive=new AbstractMap<>() {
                @Override public Set<Entry<String,Object>> entrySet(){throw new AssertionError("payload inspected while disabled");}
            };
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","op",world,null,null,null,explosive);
            begin(recorder);
            recorder.observe(UUID.randomUUID(),"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","op",world,null,null,null,explosive);
            recorder.stop();await(()->recorder.status().startsWith("HRO COMPLETE"));
        }
    }
    @Test void eventWorldHasAliasWhenCaptureStartedWithoutWorld()throws Exception {
        try(var recorder=recorder(()->{})) {
            HroContracts.IDS.forEach(recorder::registered);
            assertTrue(recorder.start(owner,null).contains("STARTING"));
            await(()->recorder.active(owner));
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","hud",world,1L,1L,1L,Map.of());
            recorder.stop();await(()->finalized(recorder.status()));
            try(var in=new GZIPInputStream(Files.newInputStream(bundle().resolve("events/segment-0001.jsonl.gz")))) {
                String event=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(event.contains("\"worldSegmentId\":\"null:segment-1\""));
                assertTrue(event.contains("\"worldSegmentId\":\""));
                assertTrue(event.contains(":segment-1"));
            }
        }
    }
    @Test void saveDirectoryIsRejected(){assertThrows(IllegalArgumentException.class,()->new HroRecorder(root.resolve("Saves/Hywind/diagnostics")));}
    @Test void originalOriginRequiredForRecovery(){assertThrows(java.io.IOException.class,()->HroBundle.recover(root));}
    @Test void sharedQueueBudgetRejectsInsteadOfBorrowingUnboundedMemory() {
        long available=SharedTraceBudget.LIMIT-SharedTraceBudget.used();
        assertTrue(SharedTraceBudget.acquire(available));
        try{assertFalse(SharedTraceBudget.acquire(1));}finally{SharedTraceBudget.release(available);}
    }
    @Test void producerThresholdCountResolvesLogBucketAmbiguity() {
        assertFalse(HroRecorder.producerP99WithinBudget(0,0));
        assertTrue(HroRecorder.producerP99WithinBudget(1000,10));
        assertFalse(HroRecorder.producerP99WithinBudget(1000,11));
        assertTrue(HroRecorder.producerP99WithinBudget(16325,163));
        assertFalse(HroRecorder.producerP99WithinBudget(16325,164));
    }
    @Test void performanceReportAttributesExactThresholdCrossings()throws Exception {
        AtomicLong clock=new AtomicLong();
        try(var recorder=new HroRecorder(root,()->clock.addAndGet(40_000),()->IDENTITY,()->{})) {
            begin(recorder);observe(recorder);recorder.stop();await(()->finalized(recorder.status()));
            JsonObject performance=JsonParser.parseString(Files.readString(bundle().resolve("performance.json"))).getAsJsonObject();
            assertTrue(performance.get("producerAtOrAboveBudgetSamples").getAsLong()>0);
            assertTrue(performance.getAsJsonObject("producerAtOrAboveBudgetByContract").get("HUD.PUBLICATION").getAsLong()>0);
            assertEquals("OBSERVED_BUDGET_EXCEEDED",performance.get("producerQualification").getAsString());
        }
    }
}
