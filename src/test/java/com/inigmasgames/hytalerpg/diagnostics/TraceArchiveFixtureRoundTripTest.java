package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only round-trip against the supplied connected traces when present on the reference machine. */
class TraceArchiveFixtureRoundTripTest {
    @TempDir Path temporary;

    @Test void suppliedSkillAndUiFixturesRoundTripByteForByteWithSubstantialCompression() throws Exception {
        Path sourceDirectory=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale","data","pre-release",
                "Saves","RPG","mods","InigmasGames_HytaleRPGPhase00Audit","logs","rpg");
        Path skill=sourceDirectory.resolve("skill-trace.jsonl"),ui=sourceDirectory.resolve("ui-trace.jsonl");
        Assumptions.assumeTrue(Files.isRegularFile(skill)&&Files.isRegularFile(ui),"supplied connected trace fixtures unavailable");
        FixtureResult skillResult=verify(skill,"SKILL",1),uiResult=verify(ui,"UI",2);
        Path report=Path.of("build","reports","trace-archive-fixtures.json");Files.createDirectories(report.getParent());
        Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("skill",skillResult,"ui",uiResult))+System.lineSeparator());
    }

    private FixtureResult verify(Path source,String kind,int fixture) throws Exception {
        Path root=temporary.resolve(kind.toLowerCase());Path archive=root.resolve("archive");Files.createDirectories(archive);
        String base=kind.toLowerCase()+"-trace-20260911T000000Z-"+String.format("%06d",fixture);
        Path pending=archive.resolve(base+".jsonl.pending");Files.copy(source,pending);
        var failures=new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        var manager=new TraceArchiveManager(root.resolve(kind.toLowerCase()+"-trace.jsonl"),kind,
                TraceArchiveManager.Compression.GZIP,true,failures::add);
        assertTrue(manager.awaitIdle(20,TimeUnit.SECONDS));manager.close();assertTrue(failures.isEmpty(),failures.toString());
        Path expanded=root.resolve("expanded.jsonl");TraceArchiveReader.exportLegacyJsonl(root,kind,expanded);
        byte[] original=Files.readAllBytes(source),reconstructed=Files.readAllBytes(expanded);
        assertArrayEquals(original,reconstructed,"SHA-256-protected bytes must reconstruct exactly");
        List<String> originalLines=Files.readAllLines(source),expandedLines=Files.readAllLines(expanded);
        assertEquals(originalLines.size(),expandedLines.size());
        for(int index=0;index<originalLines.size();index++){
            JsonElement before=JsonParser.parseString(originalLines.get(index));JsonElement after=JsonParser.parseString(expandedLines.get(index));
            assertEquals(before,after,"event mismatch at index "+index);
        }
        assertEquals(histogram(originalLines),histogram(expandedLines));
        Map<String,Long> histogram=histogram(originalLines);
        for(String event:List.of("PERSISTENCE_HANDOFF_METRICS","CHANNEL_UPKEEP","NATIVE_RPG_TICK_SUMMARY"))
            if(histogram.containsKey(event))assertEquals(histogram.get(event),histogram(expandedLines).get(event));
        Path gzip;
        try(var paths=Files.list(archive)){gzip=paths.filter(path->path.getFileName().toString().endsWith(".jsonl.gz")).findFirst().orElseThrow();}
        assertTrue(Files.size(gzip)<=original.length*0.15,"fixture must compress to no more than 15% of source bytes");
        String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
        return new FixtureResult(original.length,Files.size(gzip),originalLines.size(),expandedLines.size(),sha,true,
                1.0-(double)Files.size(gzip)/original.length);
    }

    private static Map<String,Long> histogram(List<String> lines){
        var result=new LinkedHashMap<String,Long>();
        for(String line:lines){String event=JsonParser.parseString(line).getAsJsonObject().get("eventType").getAsString();result.merge(event,1L,Long::sum);}
        return result;
    }
    private record FixtureResult(long originalBytes,long compressedBytes,long sourceEventCount,long expandedEventCount,
                                 String uncompressedSha256,boolean sha256RoundTrip,double diskReductionFraction){}
}
