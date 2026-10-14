package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class HroFinalizationTest {
    @TempDir Path root;
    private final UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
    private static final Map<String,Object> BUILD=Map.of("jarSha256","a".repeat(64),"serverSha256","b".repeat(64));
    private HroRecorder recorder(){return new HroRecorder(root,System::nanoTime,()->BUILD,()->{},root.resolve("installation").toString());}
    private Path bundle()throws Exception {try(var paths=Files.list(root)){return paths.filter(Files::isDirectory)
            .filter(path->!path.getFileName().toString().equals("approved-baselines")).findFirst().orElseThrow();}}

    @Test void oneShotArmIsInstallationAndOwnerBoundAndExpires()throws Exception {
        String a=HroArm.installation(root.resolve("installation").toString());
        String b=HroArm.installation(root.resolve("other").toString());
        HroArm.write(root,a,owner,world,1000);
        assertNull(HroArm.consume(root,b,1001));
        assertEquals(owner,HroArm.consume(root,a,1001).owner());
        assertNull(HroArm.consume(root,a,1002));
        HroArm.write(root,a,owner,world,1000);
        assertNull(HroArm.consume(root,a,1000+HroArm.TTL_MILLIS));
        HroArm.write(root,a,owner,world,1000);HroArm.cancel(root);
        assertNull(HroArm.consume(root,a,1001));
    }
    @Test void armedNextBootCapturesAtPluginInstallationAndPreservesInterruptedEvidence()throws Exception {
        try(var first=recorder()){
            assertTrue(first.armNext(owner,world).contains("ARMING"));
            HroRecorderTest.await(()->first.status().contains("ARMED_NEXT_BOOT"));
        }
        try(var next=recorder()){
            HroRecorderTest.await(()->next.active(owner));
            assertTrue(next.status().contains("RECORDING"));
            next.lifecycle(owner,world,1,"READY_QUEUED");
            next.close();HroRecorderTest.await(()->HroRecorderTest.finalized(next.status()));
            assertTrue(next.status().contains("PARTIAL"));
            assertTrue(Files.readString(bundle().resolve("manifest.json")).contains("BOOTSTRAP_UNOBSERVED_BEFORE_PLUGIN_SETUP"));
            HroTool.verifyChecksums(bundle());
        }
    }
    @Test void worldTransitionReconnectAndStaleCallbackKeepOneRunWithExplicitSegments()throws Exception {
        try(var recorder=recorder()){
            HroContracts.IDS.forEach(recorder::registered);recorder.start(owner,world);
            HroRecorderTest.await(()->recorder.active(owner));
            // This test proves segment attribution. Control scheduling so deliberate tryLock loss
            // cannot erase its witnesses; nativeLifecycleAdvancesWithoutWaitingForBusyEvidenceQueue
            // separately exercises actual queue contention and nonblocking lifecycle behavior.
            var sessionField=HroRecorder.class.getDeclaredField("session");sessionField.setAccessible(true);
            Object session=sessionField.get(recorder);var lockField=session.getClass().getDeclaredField("lock");lockField.setAccessible(true);
            var queueLock=(ReentrantLock)lockField.get(session);queueLock.lock();
            try{
            recorder.lifecycle(owner,world,1,"READY_QUEUED");
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","first",world,1L,1L,null,Map.of());
            recorder.disconnect(owner,1);assertFalse(recorder.active(owner));
            UUID later=UUID.randomUUID();recorder.lifecycle(owner,later,2,"READY_QUEUED");
            assertTrue(recorder.active(owner));
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","stale",world,1L,2L,null,Map.of());
            recorder.observe(owner,"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","second",later,2L,2L,null,Map.of());
            recorder.observe(UUID.randomUUID(),"HUD.PUBLICATION","fixture","HUD_LAYOUT_READY","intruder",later,2L,2L,null,Map.of());
            assertTrue(recorder.mark(owner,later,"bad / label : \n !!").contains("bad___label"));
            assertTrue(recorder.mark(owner,later,null).contains("USER_CHECKPOINT"));
            recorder.stop();
            }finally{queueLock.unlock();}
            HroRecorderTest.await(()->HroRecorderTest.finalized(recorder.status()));
            List<HroEvent> events=new ArrayList<>();HroTool.read(bundle(),(event,locator)->events.add(event));
            assertTrue(events.stream().anyMatch(e->e.operationId().equals("first")));
            assertTrue(events.stream().anyMatch(e->e.operationId().equals("second")));
            assertFalse(events.stream().anyMatch(e->e.operationId().equals("stale")||e.operationId().equals("intruder")));
            assertEquals(2,events.stream().filter(e->e.contract().equals("HUD.PUBLICATION"))
                    .map(HroEvent::worldSegmentId).distinct().count());
            assertTrue(Files.readString(bundle().resolve("report.json")).contains("bad___label"));
            assertTrue(Files.readString(bundle().resolve("checkpoints.json")).contains("USER_CHECKPOINT"));
            assertTrue(Files.readString(bundle().resolve("integrity.json")).contains("correlatedReconnects\":1"));
            HroTool.verifyChecksums(bundle());
        }
    }
    @Test void reconnectGraceExpiresWithoutOwnerAndFinalizesPartial()throws Exception {
        AtomicLong clock=new AtomicLong(1);
        try(var recorder=new HroRecorder(root,clock::get,()->BUILD,()->{},root.toString())){
            recorder.start(owner,world);HroRecorderTest.await(()->recorder.active(owner));
            recorder.lifecycle(owner,world,1,"READY_QUEUED");recorder.disconnect(owner,1);
            clock.set(HroRecorder.RECONNECT_GRACE_NANOS+2);
            HroRecorderTest.await(()->HroRecorderTest.finalized(recorder.status()));
            assertTrue(recorder.status().contains("RECONNECT_GRACE_EXPIRED"));
            HroTool.verifyChecksums(bundle());
        }
    }
    @Test void nativeLifecycleAdvancesWithoutWaitingForBusyEvidenceQueue()throws Exception {
        try(var recorder=recorder()){
            recorder.start(owner,world);HroRecorderTest.await(()->recorder.active(owner));
            recorder.lifecycle(owner,world,1,"READY_QUEUED");recorder.disconnect(owner,1);
            var sessionField=HroRecorder.class.getDeclaredField("session");sessionField.setAccessible(true);
            Object session=sessionField.get(recorder);
            var lockField=session.getClass().getDeclaredField("lock");lockField.setAccessible(true);
            var queueLock=(ReentrantLock)lockField.get(session);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            Thread holder=Thread.ofPlatform().start(()->{queueLock.lock();try{entered.countDown();release.await();}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{queueLock.unlock();}});
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                UUID later=UUID.randomUUID();
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->recorder.lifecycle(owner,later,2,"READY_QUEUED"));
                assertTrue(recorder.active(owner),"reconnect cannot be discarded by queue contention");
            }finally{release.countDown();holder.join(5000);}
            recorder.stop();HroRecorderTest.await(()->HroRecorderTest.finalized(recorder.status()));
            HroTool.verifyChecksums(bundle());
        }
    }
    @Test void automaticApprovedComparisonIsIntegrityCheckedAndNeverApprovesCandidate()throws Exception {
        Path old=HroComparisonTest.bundle(root,"old",true,0,1,true);
        Path approved=Files.createDirectory(root.resolve("approved-baselines"));
        Path recipe=root.resolve("recipe.json");Files.writeString(recipe,HroComparisonTest.recipe("PASS").toString());
        HroComparison.baseline(old,recipe,approved.resolve("accepted.json"));
        Path candidate=HroComparisonTest.bundle(root,"candidate",false,0,1,true);
        String coreIndex=HroBundle.sha256(candidate.resolve("checksums.sha256"));
        HroAutoAnalysis.finish(root,candidate,System::nanoTime);
        assertEquals("REGRESSION",HroOffline.value(HroOffline.object(candidate.resolve("comparisons/accepted.json")),"comparison"));
        assertTrue(Files.readString(candidate.resolve("auto-analysis.json")).contains("CHECKSUMS_VERIFIED"));
        assertFalse(Files.exists(approved.resolve("candidate.json")));
        assertEquals(coreIndex,HroBundle.sha256(candidate.resolve("checksums.sha256")));
        HroAutoAnalysis.verifySidecarChecksums(candidate);
        HroTool.verifyChecksums(candidate);
        Files.writeString(candidate.resolve("auto-analysis.json"),"tampered");
        assertThrows(java.io.IOException.class,()->HroAutoAnalysis.verifySidecarChecksums(candidate));
        HroTool.verifyChecksums(candidate);
    }
    @Test void automaticAnalysisBudgetAndBadBaselinePreserveCompletedCapture()throws Exception {
        Path old=HroComparisonTest.bundle(root,"old",true,0,1,true);
        Path approved=Files.createDirectory(root.resolve("approved-baselines"));
        Path recipe=root.resolve("recipe.json");Files.writeString(recipe,HroComparisonTest.recipe("PASS").toString());
        HroComparison.baseline(old,recipe,approved.resolve("accepted.json"));
        Path pending=HroComparisonTest.bundle(root,"pending",false,0,1,true);
        AtomicLong clock=new AtomicLong();
        HroAutoAnalysis.finish(root,pending,()->clock.addAndGet(HroAutoAnalysis.BUDGET_NANOS/2+1));
        assertTrue(Files.readString(pending.resolve("auto-analysis.json")).contains("PENDING_ANALYSIS_BUDGET"));
        HroAutoAnalysis.verifySidecarChecksums(pending);
        HroTool.verifyChecksums(pending);
        Files.writeString(approved.resolve("broken.json"),"{\"kind\":\"UNAPPROVED\"}");
        Path incomplete=HroComparisonTest.bundle(root,"incomplete",false,0,1,true);
        HroAutoAnalysis.finish(root,incomplete,System::nanoTime);
        assertTrue(Files.readString(incomplete.resolve("auto-analysis.json")).contains("INCOMPLETE_COMPARISON"));
        HroAutoAnalysis.verifySidecarChecksums(incomplete);
        HroTool.verifyChecksums(incomplete);
    }
    @Test void analysisOutputFailureCannotInvalidateCompletedCapture()throws Exception {
        Path old=HroComparisonTest.bundle(root,"old",true,0,1,true);
        Path approved=Files.createDirectory(root.resolve("approved-baselines"));
        Path recipe=root.resolve("recipe.json");Files.writeString(recipe,HroComparisonTest.recipe("PASS").toString());
        HroComparison.baseline(old,recipe,approved.resolve("accepted.json"));
        Path candidate=HroComparisonTest.bundle(root,"candidate",false,0,1,true);
        String coreIndex=HroBundle.sha256(candidate.resolve("checksums.sha256"));
        Files.writeString(candidate.resolve("comparisons"),"blocked output directory");
        assertEquals("INCOMPLETE_COMPARISON",HroAutoAnalysis.finish(root,candidate,System::nanoTime));
        assertEquals(coreIndex,HroBundle.sha256(candidate.resolve("checksums.sha256")));
        HroTool.verifyChecksums(candidate);
        HroAutoAnalysis.verifySidecarChecksums(candidate);
    }
}
