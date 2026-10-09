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
                MonsterSpawnTrace.event("ORIGINAL_GROUP_RESTORED",world,3,"Wolf_Black","alive=3 stillStaged=0");
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
}
