package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static java.nio.file.StandardOpenOption.*;

class Stage13BarrierPriorityTest {
    @TempDir Path directory;
    @Test void queuedForegroundBlocksMaintenanceEvenBeforeItBeginsEncoding()throws Exception {
        var timing=new EncounterPersistenceTimings();var arbiter=new EncounterBarrierArbiter(timing);
        try(var channel=FileChannel.open(directory.resolve("data"),CREATE_NEW,WRITE)){
            arbiter.queued();var waiting=new CountDownLatch(1);
            var low=CompletableFuture.runAsync(()->{waiting.countDown();try{arbiter.force(channel,EncounterBarrierArbiter.Kind.BUNDLE);}catch(Exception e){throw new CompletionException(e);}});
            assertTrue(waiting.await(1,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->low.get(50,TimeUnit.MILLISECONDS));
            arbiter.force(channel,EncounterBarrierArbiter.Kind.FOREGROUND);arbiter.completed();low.get(5,TimeUnit.SECONDS);
            var events=(List<?>)arbiter.snapshot().get("forceEvents");assertEquals("FOREGROUND",((Map<?,?>)events.get(0)).get("kind"));assertEquals("BUNDLE",((Map<?,?>)events.get(1)).get("kind"));
        }
    }
    @Test void boundedWriterBackpressureAllowsItsMaintenancePrerequisiteToProgress()throws Exception {
        var arbiter=new EncounterBarrierArbiter(new EncounterPersistenceTimings());
        try(var channel=FileChannel.open(directory.resolve("data"),CREATE_NEW,WRITE)){
            for(int i=0;i<256;i++)arbiter.queued();arbiter.suspend();arbiter.force(channel,EncounterBarrierArbiter.Kind.PREPARATION);arbiter.resume();
            for(int i=0;i<256;i++)arbiter.completed();arbiter.force(channel,EncounterBarrierArbiter.Kind.BUNDLE);
            assertEquals(2,((List<?>)arbiter.snapshot().get("forceEvents")).size());
        }
    }
    @Test void resetIsDiagnosticOnlyAndDoesNotDropQueueOwnership()throws Exception {
        var arbiter=new EncounterBarrierArbiter(new EncounterPersistenceTimings());arbiter.queued();arbiter.reset();arbiter.completed();assertThrows(IllegalStateException.class,arbiter::completed);
    }
}
