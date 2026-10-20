package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TabTraceProbeTest {
    @Test void packetMetadataIncludesStructuralFieldsButNoPayload() {
        var pocket = TabTraceProbe.packetDetails("IN", new ClientOpenWindow(WindowType.PocketCrafting));
        assertEquals("IN", pocket.get("direction"));
        assertEquals("PocketCrafting", pocket.get("windowType"));
        assertEquals(ClientOpenWindow.PACKET_ID, pocket.get("packetId"));
        assertFalse(pocket.containsKey("data"));
        var page = TabTraceProbe.packetDetails("OUT", new SetPage(Page.Inventory, false));
        assertEquals("Inventory", page.get("page"));
    }

    @Test void concurrentEventsHaveOneBoundedTotalOrderAndStopIsFinal() throws Exception {
        var session = new TabTraceProbe.Session(UUID.randomUUID(), Path.of("ignored.jsonl"));
        session.start();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int thread = 0; thread < 4; thread++)
                jobs.add(executor.submit(() -> {
                    for (int i = 0; i < 300; i++) session.capture("PACKET", Map.of("packet", "example"));
                }));
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        List<TabTraceProbe.Event> events = session.stop("DONE");
        assertEquals(1202, events.size()); // GO + 1200 packets + STOP
        assertEquals(events.size(), new HashSet<>(events.stream().map(TabTraceProbe.Event::sequence).toList()).size());
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).sequence() > events.get(i - 1).sequence());
            assertTrue(events.get(i).offsetMicros() >= events.get(i - 1).offsetMicros());
        }
        session.capture("LATE", Map.of());
        assertEquals(events.size(), session.stop("DONE_AGAIN").size());
    }

    @Test void eventBudgetDropsExcessWithoutGrowingIndefinitely() {
        var session = new TabTraceProbe.Session(UUID.randomUUID(), Path.of("ignored.jsonl"));
        session.start();
        for (int i = 0; i < 20_100; i++) session.capture("PACKET", Map.of("id", i));
        assertEquals(20_000, session.stop("DONE").size());
        assertTrue(session.dropped() >= 101);
    }
}
