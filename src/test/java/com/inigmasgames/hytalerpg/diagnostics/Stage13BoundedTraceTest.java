package com.inigmasgames.hytalerpg.diagnostics;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Stage13BoundedTraceTest {
    @TempDir Path directory;
    @Test void stalledDiskCannotCreateAnUnboundedQueueOrRunIoOnTheProducer()throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var lines=new CopyOnWriteArrayList<String>();
        var producer=Thread.currentThread();var wrongThread=new AtomicBoolean();
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,2,e->{},line->{
            wrongThread.compareAndSet(false,Thread.currentThread()==producer);started.countDown();
            if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("fixture release timeout");lines.add(new String(line,StandardCharsets.UTF_8));
        });
        try{
            assertTrue(writer.submit(Map.of("eventType","FIRST")));assertTrue(started.await(1,TimeUnit.SECONDS));
            for(int i=0;i<1000;i++)writer.submit(Map.of("eventType","DATA","sequence",i));
            assertTrue(writer.metrics().queuedRecords()<=BoundedTraceWriter.QUEUE_RECORDS);assertTrue(writer.metrics().retainedBytes()<=BoundedTraceWriter.QUEUE_BYTES);
            assertTrue(writer.metrics().dropped()>0);
        }finally{release.countDown();writer.close();}
        assertFalse(wrongThread.get());assertEquals(0,writer.metrics().retainedBytes());assertEquals(writer.metrics().accepted(),writer.metrics().written());
        assertTrue(lines.stream().anyMatch(line->line.contains("TRACE_GAP")&&line.contains("\"evidenceComplete\":false")));
    }
    @Test void oversizedRecordIsRejectedAndTheNextRecordCarriesAnExplicitGap()throws Exception{
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,2,e->{});
        assertFalse(writer.submit(Map.of("oversized","x".repeat(100_000))));assertTrue(writer.submit(Map.of("eventType","NORMAL")));writer.close();
        var text=Files.readString(directory.resolve("trace"));assertTrue(text.contains("TRACE_GAP"));assertTrue(text.contains("NORMAL"));
        assertFalse(text.contains("oversized"));assertEquals(1,writer.metrics().dropped());assertEquals(1,writer.metrics().written());
    }
    @Test void fileFailureIsCountedAndCannotThrowIntoGameplay()throws Exception{
        var occupied=directory.resolve("occupied");Files.writeString(occupied,"file");
        var writer=new BoundedTraceWriter(occupied.resolve("trace"),65_536,2,e->{});
        assertDoesNotThrow(()->writer.submit(Map.of("eventType","TEST")));assertDoesNotThrow(writer::close);
        assertTrue(writer.metrics().failed()>0);assertEquals(0,writer.metrics().written());assertEquals(0,writer.metrics().retainedBytes());
    }
    @Test void orderedAcceptedRecordsRetainAllCorrelationFields()throws Exception{
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,2,e->{});
        for(int i=0;i<20;i++)assertTrue(writer.submit(Map.of("eventType","DAMAGE_INSPECTED","rootCastId","root","skillInstanceId","instance","correlationId","corr","sequence",i)));
        writer.close();var lines=Files.readAllLines(directory.resolve("trace"));assertEquals(20,lines.size());
        for(int i=0;i<20;i++){var record=com.google.gson.JsonParser.parseString(lines.get(i)).getAsJsonObject();assertEquals(i,record.get("sequence").getAsInt());assertEquals("root",record.get("rootCastId").getAsString());assertEquals("instance",record.get("skillInstanceId").getAsString());assertEquals("corr",record.get("correlationId").getAsString());}
        assertEquals(0,writer.metrics().dropped());assertEquals(0,writer.metrics().failed());
    }
    @Test void closedWriterRejectsInsteadOfSilentlyAcceptingUndrainableTasks(){
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,2,e->{});writer.close();assertFalse(writer.submit(Map.of("eventType","AFTER_CLOSE")));assertEquals(1,writer.metrics().dropped());
    }
    @Test void invalidRetentionCannotCreateUnboundedDiskBudget(){
        assertThrows(IllegalArgumentException.class,()->new SkillTraceConfiguration(true,"NORMAL",Integer.MAX_VALUE,4,false));
        assertThrows(IllegalArgumentException.class,()->new SkillTraceConfiguration(true,"NORMAL",8,Integer.MAX_VALUE,false));
    }
    @Test void multibyteTextCannotExceedTheConfiguredFileByteBudget(){
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,2,e->{});
        assertFalse(writer.submit(Map.of("text","\u4e00".repeat(30_000))));writer.close();assertEquals(1,writer.metrics().dropped());
    }
    @Test void rotationPreservesWholeJsonLinesAndExactRetentionBudget()throws Exception{
        var writer=new BoundedTraceWriter(directory.resolve("trace"),65_536,3,e->{});
        for(int i=0;i<40;i++)assertTrue(writer.submit(Map.of("eventType","DATA","index",i,"payload","x".repeat(10_000))));
        writer.close();assertEquals(0,writer.metrics().dropped());assertEquals(0,writer.metrics().failed());
        try(var files=Files.list(directory)){
            var paths=files.toList();assertEquals(3,paths.size());for(var file:paths){assertTrue(Files.size(file)<=65_536);for(var line:Files.readAllLines(file))assertDoesNotThrow(()->com.google.gson.JsonParser.parseString(line));}
        }
    }
}
