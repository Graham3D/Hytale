package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MonsterSpawnTraceTest {
    @TempDir Path temp;
    @Test void boundedDetailsPreserveAggregateCountsAndNativeJobIdentity() throws Exception {
        var world=UUID.randomUUID();
        try(var trace=new MonsterSpawnTrace(temp)){
            assertFalse(MonsterSpawnTrace.enabled());
            trace.start();
            MonsterSpawnTrace.job(world,3,"Wolf_Black",42,"requestedFlock=3");
            MonsterSpawnTrace.job(world,3,"Wolf_Black",42,"requestedFlock=3");
            for(int i=0;i<MonsterSpawnTrace.MAX_DETAILS+10;i++)
                MonsterSpawnTrace.event("NATIVE_JOB_CREATED",world,3,"Wolf_Black","job="+i);
            var running=trace.status();
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+11,running.events());
            assertEquals(MonsterSpawnTrace.MAX_DETAILS,running.detailedEvents());
            var stopped=trace.stop();
            assertFalse(stopped.active());
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+11,stopped.events());
            assertEquals(MonsterSpawnTrace.MAX_DETAILS,stopped.detailedEvents());
            assertFalse(MonsterSpawnTrace.enabled());
            var lines=Files.readAllLines(Path.of(stopped.lastFile()));
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+1,lines.size());
            var summary=JsonParser.parseString(lines.getFirst()).getAsJsonObject();
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+11,summary.get("events").getAsLong());
            assertEquals(MonsterSpawnTrace.MAX_DETAILS,summary.get("detailedEvents").getAsInt());
            MonsterSpawnTrace.event("AFTER_STOP",world,3,"Wolf_Black","ignored");
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+1,Files.readAllLines(Path.of(stopped.lastFile())).size());
        }
    }
    @Test void rareExtensionAndReservationDecisionsSurviveTheOrdinaryDetailCap() throws Exception {
        var world=UUID.randomUUID();
        try(var trace=new MonsterSpawnTrace(temp)){
            trace.start();
            for(int i=0;i<MonsterSpawnTrace.MAX_DETAILS+20;i++)
                MonsterSpawnTrace.event("NATIVE_JOB_CREATED",world,1,"Wolf_Black","job="+i);
            MonsterSpawnTrace.event("NATIVE_EXTENSION_REJECTED",world,1,"Wolf_Black",
                    "job=7 encounter=abc subreason=CHUNK_HEADROOM");
            MonsterSpawnTrace.event("PACK_RESERVATION",world,1,"Wolf_Black",
                    "encounter=abc reason=WORLD_LIMIT");
            MonsterSpawnTrace.event("PACK_LEASE_SUSPENDED",world,1,"Wolf_Black",
                    "encounter=abc pack=def reason=LAST_MEMBER_UNLOADED occupancyBefore=1 occupancyAfter=0");
            var stopped=trace.stop();
            var lines=Files.readAllLines(Path.of(stopped.lastFile()));
            var summary=JsonParser.parseString(lines.getFirst()).getAsJsonObject();
            assertEquals(2,summary.getAsJsonObject("priorityCounts").get("NATIVE_EXTENSION_REJECTED|CHUNK_HEADROOM").getAsLong()
                    +summary.getAsJsonObject("priorityCounts").get("PACK_RESERVATION|WORLD_LIMIT").getAsLong());
            assertEquals(MonsterSpawnTrace.MAX_DETAILS+3,summary.get("detailedEvents").getAsInt());
            assertTrue(lines.get(lines.size()-3).contains("CHUNK_HEADROOM"));
            assertTrue(lines.get(lines.size()-2).contains("WORLD_LIMIT"));
            assertTrue(lines.getLast().contains("LAST_MEMBER_UNLOADED"));
        }
    }
}
