package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TraceArchiveRecoveryTest {
    @TempDir Path directory;

    @Test void eachCrashBoundaryRecoversWithoutLossOrDuplication() throws Exception {
        for (TraceArchiveManager.FaultPoint point : TraceArchiveManager.FaultPoint.values()) {
            Path caseDirectory=directory.resolve(point.name());Files.createDirectories(caseDirectory.resolve("archive"));
            byte[] source=events(25,"same-timestamp");Path pending=pending(caseDirectory,1);Files.write(pending,source);
            var failures=new ArrayList<Throwable>();
            var failed=new TraceArchiveManager(caseDirectory.resolve("skill-trace.jsonl"),"SKILL",
                    TraceArchiveManager.Compression.GZIP,true,failures::add,at->{if(at==point)throw new java.io.IOException("INJECTED_"+point);});
            assertTrue(failed.awaitIdle(5,TimeUnit.SECONDS));failed.close();assertTrue(Files.exists(pending),point.name());
            var recovered=new TraceArchiveManager(caseDirectory.resolve("skill-trace.jsonl"),"SKILL",
                    TraceArchiveManager.Compression.GZIP,true,failures::add);
            assertTrue(recovered.awaitIdle(5,TimeUnit.SECONDS));recovered.close();assertFalse(Files.exists(pending),point.name());
            Path output=caseDirectory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(caseDirectory,"SKILL",output);
            assertArrayEquals(source,Files.readAllBytes(output),point.name());
            assertEquals(25,Files.readAllLines(output).size(),point.name());
        }
    }

    @Test void startupRebuildsAMissingManifestAndRemovesOnlyMatchingRawDuplicate() throws Exception {
        Files.createDirectories(directory.resolve("archive"));byte[] source=events(3,"t");
        Path pending=pending(directory,1);Files.write(pending,source);
        var first=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(first.awaitIdle(5,TimeUnit.SECONDS));first.close();
        Path manifest=only(".manifest.json");Path gzip=only(".jsonl.gz");Files.delete(manifest);Files.write(pending,source);
        var restart=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(restart.awaitIdle(5,TimeUnit.SECONDS));restart.close();
        assertTrue(Files.exists(gzip));assertTrue(Files.exists(manifest));assertFalse(Files.exists(pending));
    }

    @Test void verifiedManifestContainsIntegrityCountsOrderingMetadataAndBuildIdentity() throws Exception {
        Files.createDirectories(directory.resolve("archive"));byte[] source=events(3,"manifest");Files.write(pending(directory,12),source);
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(manager.awaitIdle(5,TimeUnit.SECONDS));manager.close();Path manifest=only(".manifest.json");
        var json=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();assertEquals(1,json.get("schemaVersion").getAsInt());
        assertEquals("SKILL",json.get("traceKind").getAsString());assertEquals(12,json.get("segmentSequence").getAsLong());
        assertEquals(3,json.get("eventCount").getAsLong());assertEquals(source.length,json.get("uncompressedBytes").getAsLong());
        assertEquals("GZIP",json.get("compression").getAsString());assertEquals("VERIFIED",json.get("archiveState").getAsString());
        assertEquals(64,json.get("uncompressedSha256").getAsString().length());assertEquals(64,json.get("compressedSha256").getAsString().length());
        assertTrue(json.has("rpgRevision"));assertTrue(json.has("buildVersion"));assertTrue(json.has("hytaleBuild"));
        assertEquals("2026-09-11T00:00:00Z",json.get("firstEventTimestamp").getAsString());assertEquals("2026-09-11T00:00:00Z",json.get("lastEventTimestamp").getAsString());
    }

    @Test void mismatchedCompressedDuplicateIsQuarantinedAndRawRemainsAuthority() throws Exception {
        Files.createDirectories(directory.resolve("archive"));Path pending=pending(directory,1);Files.write(pending,events(2,"raw"));
        Path wrong=directory.resolve("wrong");Files.write(wrong,events(2,"wrong"));
        Path gzip=directory.resolve("archive/skill-trace-20260911T000000Z-000001.jsonl.gz");
        try(var input=Files.newInputStream(wrong);var output=new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzip))){input.transferTo(output);}
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(manager.awaitIdle(5,TimeUnit.SECONDS));manager.close();
        Path output=directory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(directory,"SKILL",output);
        assertArrayEquals(events(2,"raw"),Files.readAllBytes(output));
        try(var paths=Files.list(directory.resolve("archive"))){assertTrue(paths.anyMatch(path->path.getFileName().toString().contains("hash-mismatch")));}
    }

    @Test void partialFinalLineIsQuarantinedAndEveryCompleteLineSurvives() throws Exception {
        Files.createDirectories(directory.resolve("archive"));byte[] complete=events(4,"complete");
        Path pending=pending(directory,1);Files.write(pending,concat(complete,"{\"eventType\":\"PARTIAL".getBytes(StandardCharsets.UTF_8)));
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(manager.awaitIdle(5,TimeUnit.SECONDS));manager.close();
        Path output=directory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(directory,"SKILL",output);
        assertArrayEquals(complete,Files.readAllBytes(output));
        try(var paths=Files.list(directory.resolve("archive"))){assertTrue(paths.anyMatch(path->path.getFileName().toString().contains(".partial-")));}
    }

    @Test void archiveQueueIsBoundedAndFilesystemBacklogSurvivesASlowCompressor() throws Exception {
        Files.createDirectories(directory.resolve("archive"));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var archiveThread=new AtomicReference<String>();
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{},point->{
            if(point==TraceArchiveManager.FaultPoint.DURING_GZIP_WRITE&&entered.getCount()>0){archiveThread.set(Thread.currentThread().getName());entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("DELAY_TIMEOUT");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}}
        });
        assertTrue(manager.awaitIdle(2,TimeUnit.SECONDS));
        for(int i=1;i<=20;i++){Path pending=pending(directory,i);Files.write(pending,events(2,"segment-"+i));manager.enqueue(pending);}
        assertTrue(entered.await(2,TimeUnit.SECONDS));assertTrue(manager.metrics().queuedArchiveTasks()<=TraceArchiveManager.ARCHIVE_QUEUE_CAPACITY);
        assertEquals(20,count(".jsonl.pending"));release.countDown();assertTrue(manager.awaitIdle(10,TimeUnit.SECONDS));manager.close();
        assertTrue(archiveThread.get().startsWith("rpg-trace-archive-skill"));assertEquals(0,count(".jsonl.pending"));assertEquals(20,count(".jsonl.gz"));
    }

    @Test void slowCompressionNeverBlocksRotationOrTheNextActiveSegment() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var failures=new ArrayList<Throwable>();
        var writer=new TraceSegmentWriter(directory.resolve("skill-trace.jsonl"),65_536,"SKILL",
                TraceArchiveManager.Compression.GZIP,true,failures::add,point->{
            if(point==TraceArchiveManager.FaultPoint.DURING_GZIP_WRITE&&entered.getCount()>0){entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("DELAY_TIMEOUT");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}}
        });
        byte[] event=("{\"eventType\":\"DATA\",\"payload\":\""+"x".repeat(39_000)+"\"}\n").getBytes(StandardCharsets.UTF_8);
        writer.write(event);writer.write(event);assertTrue(entered.await(2,TimeUnit.SECONDS));
        long before=System.nanoTime();writer.write(event);long elapsed=System.nanoTime()-before;
        assertTrue(elapsed<100_000_000L,"active append waited for background gzip: "+elapsed+"ns");
        assertTrue(Files.size(directory.resolve("skill-trace.jsonl"))>0);release.countDown();writer.close();
        assertTrue(failures.isEmpty(),failures.toString());
    }

    @Test void identicalTimestampsAndConcurrentSubmissionsRemainValidAndExactlyOnce() throws Exception {
        Path active=directory.resolve("skill-trace.jsonl");var writer=new BoundedTraceWriter(active,65_536,2,e->{});
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
        for(int i=0;i<200;i++){int id=i;futures.add(pool.submit(()->writer.submit(java.util.Map.of("timestamp","2026-09-11T00:00:00Z","eventType","CONCURRENT","id",id,"payload","x".repeat(1000)))));}
        pool.shutdown();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));for(var future:futures)assertTrue(future.get());writer.close();
        Path output=directory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(directory,"SKILL",output);
        var ids=new java.util.HashSet<Integer>();for(String line:Files.readAllLines(output)){var json=JsonParser.parseString(line).getAsJsonObject();assertEquals("2026-09-11T00:00:00Z",json.get("timestamp").getAsString());assertTrue(ids.add(json.get("id").getAsInt()));}
        assertEquals(200,ids.size());
    }

    @Test void archiveDirectoryFailureDoesNotFailAcceptedActiveTraceWrites() throws Exception {
        Files.writeString(directory.resolve("archive"),"occupied");Path active=directory.resolve("skill-trace.jsonl");
        var failures=new ArrayList<Throwable>();var writer=new BoundedTraceWriter(active,65_536,2,failures::add);
        for(int i=0;i<12;i++)assertTrue(writer.submit(java.util.Map.of("eventType","DATA","i",i,"payload","x".repeat(10_000))));
        writer.close();assertEquals(12,writer.metrics().written());assertEquals(0,writer.metrics().dropped());
        assertEquals(12,Files.readAllLines(active).size());assertFalse(failures.isEmpty());
    }

    @Test void legacyPlainRotationsVerifiedGzipAndCurrentAreReadInLogicalOrder() throws Exception {
        Files.write(directory.resolve("skill-trace.jsonl.2"),events(1,"legacy-oldest"));
        Files.write(directory.resolve("skill-trace.jsonl.1"),events(1,"legacy-newest"));
        Files.createDirectories(directory.resolve("archive"));Files.write(pending(directory,1),events(1,"archive"));
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.GZIP,true,e->{});
        assertTrue(manager.awaitIdle(5,TimeUnit.SECONDS));manager.close();Files.write(directory.resolve("skill-trace.jsonl"),events(1,"current"));
        Path output=directory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(directory,"SKILL",output);
        var lines=Files.readAllLines(output);assertEquals(4,lines.size());
        assertTrue(lines.get(0).contains("legacy-oldest"));assertTrue(lines.get(1).contains("legacy-newest"));
        assertTrue(lines.get(2).contains("archive"));assertTrue(lines.get(3).contains("current"));
    }

    @Test void compressionNoneStillRotatesVerifiesAndExportsWithoutDeletingHistory() throws Exception {
        Files.createDirectories(directory.resolve("archive"));byte[] source=events(7,"plain");Files.write(pending(directory,1),source);
        var manager=new TraceArchiveManager(directory.resolve("skill-trace.jsonl"),"SKILL",TraceArchiveManager.Compression.NONE,true,e->{});
        assertTrue(manager.awaitIdle(5,TimeUnit.SECONDS));manager.close();
        Path output=directory.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(directory,"SKILL",output);
        assertArrayEquals(source,Files.readAllBytes(output));assertEquals(1,count(".jsonl"));assertEquals(1,count(".manifest.json"));
    }

    private Path pending(Path root,int sequence){return root.resolve("archive/skill-trace-20260911T000000Z-"+String.format("%06d",sequence)+".jsonl.pending");}
    private Path only(String suffix)throws Exception{try(var paths=Files.list(directory.resolve("archive"))){return paths.filter(path->path.getFileName().toString().endsWith(suffix)).findFirst().orElseThrow();}}
    private long count(String suffix)throws Exception{try(var paths=Files.list(directory.resolve("archive"))){return paths.filter(path->path.getFileName().toString().endsWith(suffix)).count();}}
    private static byte[] events(int count,String marker){var text=new StringBuilder();for(int i=0;i<count;i++)text.append("{\"timestamp\":\"2026-09-11T00:00:00Z\",\"eventType\":\"EVENT\",\"correlationId\":\"").append(marker).append('-').append(i).append("\",\"details\":{\"value\":").append(i).append("}}\n");return text.toString().getBytes(StandardCharsets.UTF_8);}
    private static byte[] concat(byte[] first,byte[] second){byte[] result=java.util.Arrays.copyOf(first,first.length+second.length);System.arraycopy(second,0,result,first.length,second.length);return result;}
}
