package com.inigmasgames.hytalerpg.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SkillCaptureSessionsTest {
    @TempDir Path directory;

    @Test void personalCaptureHasRealStartStopScopeAndIndependentLevels() throws Exception {
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),world=UUID.randomUUID();
        try(var trace=new RpgSkillTraceService(directory.resolve("background.jsonl"),
                new SkillTraceConfiguration(true,"NORMAL",1,1,true))){
            assertTrue(trace.status(owner).contains("personal capture off"));
            assertTrue(trace.start(owner,world,SkillTraceLevel.DETAILED).contains("started"));
            assertTrue(trace.wantsCompileStages());
            assertTrue(trace.start(owner,world,SkillTraceLevel.NORMAL).contains("already active"));
            trace.trace(RpgTraceRecord.create(owner,RpgTraceEventType.CAST_REQUEST,"owned",Map.of("skill","teleport")));
            trace.trace(RpgTraceRecord.create(other,RpgTraceEventType.CAST_REQUEST,"foreign",Map.of("skill","blizzard")));
            assertTrue(trace.stop(owner).contains("completed=OPERATOR_STOP"));
            assertFalse(trace.wantsCompileStages());
            assertTrue(trace.stop(owner).contains("completed=OPERATOR_STOP"));
        }
        var personal=Files.list(directory).filter(path->path.getFileName().toString().startsWith("skill-trace-")).findFirst().orElseThrow();
        String json=Files.readString(personal);
        assertTrue(json.contains("owned"));
        assertFalse(json.contains("foreign"));
        assertTrue(json.contains("\"traceType\":\"SKILL\""));
    }

    @Test void performanceCaptureOnlyReceivesItsWorldsRawNativeTicks() throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),elsewhere=UUID.randomUUID();
        try(var trace=new RpgSkillTraceService(directory.resolve("background.jsonl"),
                new SkillTraceConfiguration(true,"NORMAL",1,1,true))){
            trace.start(owner,world,SkillTraceLevel.PERFORMANCE);
            trace.trace(RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE,"matching",
                    Map.of("world",world.toString(),"rpgWallMs",2.0)));
            trace.trace(RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE,"elsewhere",
                    Map.of("world",elsewhere.toString(),"rpgWallMs",3.0)));
            trace.stop(owner);
        }
        var personal=Files.list(directory).filter(path->path.getFileName().toString().startsWith("skill-trace-")).findFirst().orElseThrow();
        String json=Files.readString(personal);
        assertTrue(json.contains("matching"));
        assertFalse(json.contains("elsewhere"));
    }
}
