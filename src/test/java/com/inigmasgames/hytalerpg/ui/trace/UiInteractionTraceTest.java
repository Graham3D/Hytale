package com.inigmasgames.hytalerpg.ui.trace;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UiInteractionTraceTest {
    @TempDir Path temporary;

    @Test void optInRecordsCorrelatedStagesMarkerAndBoundedPerPlayerFile() throws Exception {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        try (var trace = new UiInteractionTrace(temporary)) {
            UiInteractionTrace.install(trace);
            assertFalse(UiInteractionTrace.active(player));
            var disabled = UiInteractionTrace.beginIfActive(player, "inventory", "inventory.sort", "sort", Map::of);
            assertFalse(disabled.active());
            assertEquals(0, Files.list(temporary).count());
            assertTrue(trace.on(player).contains("UI trace ON"));
            assertFalse(UiInteractionTrace.active(other));
            assertTrue(UiInteractionTrace.active(player));
            var action = UiInteractionTrace.beginIfActive(player, "inventory", "inventory.grid.cell.4.2",
                    "Dropped", () -> Map.of("itemId", "Weapon_Shortbow_Iron", "clickedCellX", 4));
            assertTrue(action.active());
            action.stage("HIT_TEST", Map.of("entryId", "bow"));
            action.stage("DOMAIN_OPERATION", Map.of("operation", "MOVE", "result", "NO_FIT"));
            action.stage("UI_PATCH", Map.of("affectedElements", "#Status.Text"));
            action.complete("NO_FIT", Map.of("bagRevisionAfter", 4));
            assertTrue(trace.mark(player, "before-unequip").contains("saved"));
            assertTrue(trace.off(player).contains("UI trace OFF"));
            assertFalse(UiInteractionTrace.active(player));
            var files = Files.list(temporary).filter(path -> path.toString().endsWith(".jsonl")).toList();
            assertEquals(1, files.size());
            var rows = Files.readAllLines(files.getFirst()).stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
            assertEquals("SESSION_START", rows.getFirst().get("event").getAsString());
            assertEquals("SESSION_END", rows.getLast().get("event").getAsString());
            assertTrue(rows.stream().anyMatch(row -> "MARK".equals(row.get("event").getAsString())
                    && "before-unequip".equals(row.get("label").getAsString())));
            var input = rows.stream().filter(row -> "INPUT_RECEIVED".equals(row.get("event").getAsString()))
                    .findFirst().orElseThrow();
            var exit = rows.stream().filter(row -> "HANDLER_EXIT".equals(row.get("event").getAsString()))
                    .findFirst().orElseThrow();
            assertEquals(input.get("correlationId"), exit.get("correlationId"));
            assertEquals("NO_FIT", exit.get("result").getAsString());
            assertTrue(input.get("timestamp").getAsString().matches(".*\\.\\d{3}Z"));
        } finally { UiInteractionTrace.install(null); }
    }

    @Test void disconnectEndsOnlyItsOwnSession() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        try (var trace = new UiInteractionTrace(temporary)) {
            UiInteractionTrace.install(trace);
            trace.on(first); trace.on(second);
            trace.disconnect(first);
            assertFalse(UiInteractionTrace.active(first));
            assertTrue(UiInteractionTrace.active(second));
            trace.off(second);
            assertEquals(2, Files.list(temporary).filter(path -> path.toString().endsWith(".jsonl")).count());
        } finally { UiInteractionTrace.install(null); }
    }
}
