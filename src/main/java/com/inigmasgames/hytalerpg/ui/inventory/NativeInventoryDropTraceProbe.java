package com.inigmasgames.hytalerpg.ui.inventory;

import com.google.gson.Gson;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.InventoryAction;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.window.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Passive, one-shot native Inventory drop capture. Never filters or rewrites packets/events. */
public final class NativeInventoryDropTraceProbe implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(NativeInventoryDropTraceProbe.class.getName());
    private static final Gson JSON = new Gson();
    private static final int MAX_EVENTS = 3_000;
    private static final long ARM_DELAY_MS = 2_000, CAPTURE_MS = 10_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);
    private final Path directory;
    private final NativeInventoryEntryProbe entry;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "native-inventory-drop-trace");
        thread.setDaemon(true);
        return thread;
    });
    private final PacketFilter inbound, outbound;
    private volatile boolean closed;

    public NativeInventoryDropTraceProbe(Path directory, NativeInventoryEntryProbe entry) {
        this.directory = directory;
        this.entry = entry;
        inbound = PacketAdapters.registerInbound((PlayerPacketWatcher) (player, packet) -> observe(player, "IN", packet));
        outbound = PacketAdapters.registerOutbound((PlayerPacketWatcher) (player, packet) -> observe(player, "OUT", packet));
    }

    public String arm(PlayerRef player, World world) {
        if (closed) return "Native drop trace is unavailable during shutdown.";
        UUID id = player.getUuid();
        Path path = directory.resolve("native-drop-" + STAMP.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".jsonl");
        Session session = new Session(id, path);
        if (sessions.putIfAbsent(id, session) != null) return "A native drop trace is already armed.";
        // Bypass only Hywind's page substitution for this player. Hytale's packets and
        // inventory handlers remain untouched, so the observation uses vanilla Inventory.
        if (entry != null) entry.nativeTraceBypass(id, true);
        session.note("ARMED", Map.of("delayMs", ARM_DELAY_MS, "captureMs", CAPTURE_MS,
                "maxEvents", MAX_EVENTS, "page", "native Inventory"));
        scheduler.schedule(() -> onWorld(world, session, () -> {
            if (sessions.get(id) != session || player.getReference() == null
                    || !player.getReference().isValid()) {
                finish(session, "PLAYER_UNAVAILABLE");
                return;
            }
            session.start();
            player.sendMessage(Message.raw("NATIVE DROP TRACE GO: open vanilla Inventory, drag one native item outside its frame, and release."));
        }), ARM_DELAY_MS, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> onWorld(world, session, () -> {
            finish(session, "TEN_SECOND_WINDOW_COMPLETE");
            if (player.getReference() != null && player.getReference().isValid())
                player.sendMessage(Message.raw("Native drop trace saved: " + path.getFileName()));
        }), ARM_DELAY_MS + CAPTURE_MS, TimeUnit.MILLISECONDS);
        return "Native drop trace armed. Close chat; after GO use vanilla Inventory within 10 seconds. File: " + path;
    }

    private void onWorld(World world, Session session, Runnable task) {
        try { world.execute(task); }
        catch (RuntimeException unavailable) { finish(session, "WORLD_UNAVAILABLE"); }
    }

    private void observe(PlayerRef player, String direction, Packet packet) {
        Session session = sessions.get(player.getUuid());
        if (session == null || !session.active() || !relevant(packet)) return;
        session.capture("PACKET", packetDetails(direction, packet));
    }

    private static boolean relevant(Packet packet) {
        String domain = packet.getClass().getPackageName();
        return domain.endsWith(".inventory") || domain.endsWith(".window") || packet instanceof SetPage;
    }

    static Map<String, Object> packetDetails(String direction, Packet packet) {
        var details = new LinkedHashMap<String, Object>();
        details.put("direction", direction);
        details.put("packet", packet.getClass().getSimpleName());
        details.put("packetId", packet.getId());
        if (packet instanceof DropItemStack drop) {
            details.put("inventorySectionId", drop.inventorySectionId);
            details.put("slotId", drop.slotId);
            details.put("quantity", drop.quantity);
        } else if (packet instanceof MoveItemStack move) {
            details.put("fromSectionId", move.fromSectionId);
            details.put("fromSlotId", move.fromSlotId);
            details.put("toSectionId", move.toSectionId);
            details.put("toSlotId", move.toSlotId);
            details.put("quantity", move.quantity);
        } else if (packet instanceof InventoryAction action) {
            details.put("inventorySectionId", action.inventorySectionId);
            details.put("actionType", String.valueOf(action.inventoryActionType));
        } else if (packet instanceof ClientOpenWindow open) {
            details.put("windowType", String.valueOf(open.type));
        } else if (packet instanceof OpenWindow open) {
            details.put("windowId", open.id);
            details.put("windowType", String.valueOf(open.windowType));
        } else if (packet instanceof UpdateWindow update) {
            details.put("windowId", update.id);
        } else if (packet instanceof CloseWindow close) {
            details.put("windowId", close.id);
        } else if (packet instanceof SendWindowAction action) {
            details.put("windowId", action.id);
            details.put("actionType", action.action == null ? "null" : action.action.getClass().getSimpleName());
        } else if (packet instanceof SetPage page) {
            details.put("page", String.valueOf(page.page));
        }
        return details;
    }

    public final class PlayerRequestObserver extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {
        public PlayerRequestObserver() { super(DropItemEvent.PlayerRequest.class); }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, DropItemEvent.PlayerRequest event) {
            var player = chunk.getComponent(index, PlayerRef.getComponentType());
            var session = sessions.get(player.getUuid());
            if (session != null) session.capture("DROP_PLAYER_REQUEST", Map.of(
                    "inventorySectionId", event.getInventorySectionId(),
                    "slotId", event.getSlotId(), "cancelledAtObservation", event.isCancelled()));
        }
    }

    public final class DropObserver extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {
        public DropObserver() { super(DropItemEvent.Drop.class); }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, DropItemEvent.Drop event) {
            var player = chunk.getComponent(index, PlayerRef.getComponentType());
            var session = sessions.get(player.getUuid());
            if (session != null) session.capture("DROP_WORLD_EVENT", Map.of(
                    "itemId", event.getItemStack() == null ? "" : String.valueOf(event.getItemStack().getItemId()),
                    "quantity", event.getItemStack() == null ? 0 : event.getItemStack().getQuantity(),
                    "cancelledAtObservation", event.isCancelled()));
        }
    }

    public void detach(UUID player) {
        Session session = sessions.get(player);
        if (session != null) finish(session, "PLAYER_DISCONNECTED");
    }

    private void finish(Session session, String reason) {
        if (!sessions.remove(session.player, session)) return;
        if (entry != null) entry.nativeTraceBypass(session.player, false);
        List<Event> events = session.stop(reason);
        try {
            Files.createDirectories(directory);
            try (BufferedWriter writer = Files.newBufferedWriter(session.path, StandardCharsets.UTF_8)) {
                for (Event event : events) { writer.write(JSON.toJson(event)); writer.newLine(); }
                writer.write(JSON.toJson(Map.of("kind", "SUMMARY", "utc", Instant.now().toString(),
                        "reason", reason, "capturedEvents", events.size(), "droppedEvents", session.dropped(),
                        "inboundPacketsObserved", events.stream().filter(e -> "IN".equals(e.details.get("direction"))).count(),
                        "sawInboundDropPacket", events.stream().anyMatch(e -> "IN".equals(e.details.get("direction"))
                                && "DropItemStack".equals(e.details.get("packet"))),
                        "sawPlayerRequest", events.stream().anyMatch(e -> e.kind.equals("DROP_PLAYER_REQUEST")))));
                writer.newLine();
            }
            LOG.info("NATIVE_DROP_TRACE_SAVED path=" + session.path + " events=" + events.size());
        } catch (IOException error) { LOG.log(Level.WARNING, "NATIVE_DROP_TRACE_SAVE_FAILED path=" + session.path, error); }
    }

    @Override public void close() {
        closed = true;
        for (Session session : List.copyOf(sessions.values())) finish(session, "PLUGIN_SHUTDOWN");
        scheduler.shutdownNow();
        PacketAdapters.deregisterInbound(inbound);
        PacketAdapters.deregisterOutbound(outbound);
    }

    record Event(long sequence, String utc, long offsetMicros, String kind, Map<String, ?> details) { }
    private static final class Session {
        final UUID player;
        final Path path;
        final List<Event> events = new ArrayList<>();
        long startNanos, sequence, dropped;
        boolean active, finished;
        Session(UUID player, Path path) { this.player = player; this.path = path; }
        synchronized void note(String kind, Map<String, ?> details) { append(kind, details); }
        synchronized void start() { startNanos = System.nanoTime(); active = true; append("GO", Map.of()); }
        synchronized boolean active() {
            return active && !finished && System.nanoTime() - startNanos < TimeUnit.MILLISECONDS.toNanos(CAPTURE_MS);
        }
        synchronized long dropped() { return dropped; }
        synchronized void capture(String kind, Map<String, ?> details) { if (active()) append(kind, details); }
        private void append(String kind, Map<String, ?> details) {
            if (events.size() >= MAX_EVENTS) { dropped++; return; }
            long now = System.nanoTime();
            events.add(new Event(++sequence, Instant.now().toString(), startNanos == 0 ? 0 :
                    TimeUnit.NANOSECONDS.toMicros(now - startNanos), kind, Map.copyOf(details)));
        }
        synchronized List<Event> stop(String reason) {
            if (finished) return List.copyOf(events);
            append("STOP", Map.of("reason", reason));
            active = false; finished = true;
            return List.copyOf(events);
        }
    }
}
