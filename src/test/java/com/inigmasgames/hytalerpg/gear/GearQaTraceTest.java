package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearQaTraceTest {
    @TempDir Path root;
    @Test void routesSummonReceiptsToOwnerAndRecordsBaselineAndUnequip() throws Exception {
        var owner=UUID.randomUUID();var sentinel=UUID.randomUUID();var other=UUID.randomUUID();
        var item=new GearAffixQaSuite(GearCatalog.load()).create("qa159-05",owner);
        try(var trace=new GearQaTrace(root)){
            GearQaTrace.install(trace);trace.actorOwner(actor->actor.equals(sentinel)?owner:actor);
            var path=Path.of(trace.on(owner).split("; ",2)[1]);
            var equipped=new GearEffectSnapshot(List.of(item));
            GearQaTrace.equipment(owner,equipped,List.of(item),Set.of(item.identity()));
            GearQaTrace.equipment(owner,equipped,List.of(item),Set.of(item.identity()));
            GearQaTrace.runtime(com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord.create(sentinel,
                    com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.DAMAGE_APPLIED,"hit-1",Map.of("healthBefore",50,"healthAfter",30)));
            GearQaTrace.runtime(com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord.create(other,
                    com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.DAMAGE_APPLIED,"other-hit",Map.of()));
            GearQaTrace.equipment(owner,GearEffectSnapshot.EMPTY,List.of(),Set.of());trace.off(owner);
            var lines=Files.readAllLines(path);
            assertEquals(5,lines.size());assertTrue(lines.get(1).contains("qa159-05"));
            assertTrue(lines.get(2).contains("RUNTIME_DAMAGE_APPLIED"));assertTrue(lines.get(2).contains("healthAfter"));
            assertTrue(lines.get(3).contains("VALID_EQUIPMENT_SNAPSHOT"));
            assertFalse(String.join("",lines).contains("other-hit"));
        }
    }
    @Test void sentinelTraceIsIndependentOptInAndBounded() throws Exception {
        var owner=UUID.randomUUID();
        var backpressure=new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        try(var gear=new GearQaTrace(root.resolve("gear"));var sentinel=new GearQaTrace(root.resolve("sentinel"),"Sentinel",backpressure::add)){
            GearQaTrace.install(gear);gear.on(owner);
            assertTrue(sentinel.snapshot(owner,Map.of()).contains("off"));
            var path=Path.of(sentinel.on(owner).split("; ",2)[1]);
            for(int i=0;i<3100;i++)sentinel.snapshot(owner,Map.of("index",i));
            assertTrue(sentinel.snapshot(owner,Map.of("late",true)).contains("off"));
            sentinel.off(owner);assertTrue(GearQaTrace.active(owner));
            var lines=Files.readAllLines(path);assertFalse(lines.isEmpty());
            assertTrue(lines.stream().filter(line->line.contains("SENTINEL_SNAPSHOT")).count()<=2999);
            assertFalse(String.join("",lines).contains("\"late\""));
            assertTrue(backpressure.stream().allMatch(error->error instanceof java.util.concurrent.RejectedExecutionException));
            if(!backpressure.isEmpty())assertTrue(String.join("",lines).contains("TRACE_GAP"));
            assertTrue(Files.size(path)<=4L*1024*1024);
        }
    }
    @Test void recordsOrderedPlayerScopedEvidenceAndCloses() throws Exception {
        var owner=UUID.randomUUID();var other=UUID.randomUUID();
        try(var trace=new GearQaTrace(root)){
            GearQaTrace.install(trace);
            var line=trace.on(owner);
            assertTrue(line.contains("gear-trace-"));
            GearQaTrace.record(other,"OTHER",Map.of("value",1));
            trace.mark(owner,"before-drop");
            GearQaTrace.record(owner,"ENEMY_GEAR_DECISION",Map.of("result","NO_DROP"));
            trace.off(owner);
            assertFalse(GearQaTrace.active(owner));
            var path=Path.of(line.substring(line.indexOf(';')+2));
            var json=Files.readAllLines(path);
            assertEquals(4,json.size());
            assertTrue(json.get(0).contains("SESSION_START"));
            assertTrue(json.get(1).contains("before-drop"));
            assertTrue(json.get(2).contains("NO_DROP"));
            assertTrue(json.get(3).contains("SESSION_END"));
            assertFalse(String.join("",json).contains("OTHER"));
        }
    }
}
