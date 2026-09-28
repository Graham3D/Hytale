package com.inigmasgames.hytalerpg.ui.inventory;

import com.google.gson.Gson;
import com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.inventory.InventoryAction;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.CloseWindow;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.protocol.packets.window.SendWindowAction;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Two-second, passive packet and manager-state capture for the native Inventory action. */
public final class TabTraceProbe implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TabTraceProbe.class.getName());
    private static final Gson JSON = new Gson();
    private static final int MAX_EVENTS = 20_000;
    private static final long ARM_DELAY_MS = 2_000;
    private static final long CAPTURE_MS = 2_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final Path directory;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "hywind-tab-trace"); thread.setDaemon(true); return thread;
    });
    private final PacketFilter inbound;
    private final PacketFilter outbound;
    private volatile boolean closed;

    public TabTraceProbe(Path directory) {
        this.directory = directory;
        inbound = PacketAdapters.registerInbound((PlayerPacketWatcher)(player, packet) -> observe(player, "IN", packet));
        outbound = PacketAdapters.registerOutbound((PlayerPacketWatcher)(player, packet) -> observe(player, "OUT", packet));
    }

    /** Called from the player's world thread. Nothing is installed on the client. */
    public String arm(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        if (closed) return "Tab trace is unavailable during shutdown.";
        UUID id = player.getUuid();
        if (sessions.containsKey(id)) return "A Tab trace is already armed for this player.";
        String filename = "tab-trace-" + STAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jsonl";
        Session session = new Session(id, directory.resolve(filename));
        if (sessions.putIfAbsent(id, session) != null) return "A Tab trace is already armed for this player.";
        session.note("ARMED", Map.of("delayMs", ARM_DELAY_MS, "captureMs", CAPTURE_MS,
                "instructions", "Close chat. On GO, press Tab then Escape within two seconds."));
        scheduler.schedule(() -> onWorld(world, () -> {
            if (sessions.get(id) != session || !ref.isValid() || player.getReference() != ref) {
                finish(session, "PLAYER_UNAVAILABLE"); return;
            }
            session.start();
            sample(session, store.getComponent(ref, Player.getComponentType()));
            player.sendMessage(Message.raw("TAB TRACE GO: press Tab, then Escape now (2 seconds)."));
        }, session), ARM_DELAY_MS, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> onWorld(world, () -> {
            if (sessions.get(id) == session && ref.isValid())
                sample(session, store.getComponent(ref, Player.getComponentType()));
            finish(session, "TWO_SECOND_WINDOW_COMPLETE");
            if (ref.isValid()) player.sendMessage(Message.raw("Tab trace saved: " + session.path.getFileName()));
        }, session), ARM_DELAY_MS + CAPTURE_MS, TimeUnit.MILLISECONDS);
        return "Tab trace armed. Close chat; in 2 seconds watch for GO, then press Tab and Escape. File: " + session.path;
    }

    private void onWorld(World world, Runnable task, Session session) {
        try { world.execute(task); }
        catch (RuntimeException error) { finish(session, "WORLD_UNAVAILABLE_" + error.getClass().getSimpleName()); }
    }

    private void observe(PlayerRef player, String direction, Packet packet) {
        Session session = sessions.get(player.getUuid());
        if (session == null || !session.active()) return;
        session.capture("PACKET", packetDetails(direction, packet));
    }

    static Map<String, Object> packetDetails(String direction, Packet packet) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("direction", direction);
        details.put("packet", packet.getClass().getName());
        details.put("packetId", packet.getId());
        details.put("channel", String.valueOf(packet.getChannel()));
        // Only structural UI fields. Never serialize inventory, chat, coordinates or packet bodies.
        if (packet instanceof ClientOpenWindow value) details.put("windowType", String.valueOf(value.type));
        else if (packet instanceof OpenWindow value) { details.put("windowId", value.id); details.put("windowType", String.valueOf(value.windowType)); }
        else if (packet instanceof UpdateWindow value) details.put("windowId", value.id);
        else if (packet instanceof CloseWindow value) details.put("windowId", value.id);
        else if (packet instanceof SendWindowAction value) {
            details.put("windowId", value.id);
            details.put("actionType", value.action == null ? "null" : value.action.getClass().getSimpleName());
        } else if (packet instanceof SetPage value) details.put("page", String.valueOf(value.page));
        else if (packet instanceof CustomPageEvent value) details.put("eventType", String.valueOf(value.type));
        else if (packet instanceof InventoryAction value) details.put("actionType", String.valueOf(value.inventoryActionType));
        return details;
    }

    /** World-thread state sample; detects transitions without changing either manager. */
    private void sample(Session session, Player entity) {
        if (entity == null || !session.active()) return;
        try { sampleManagers(session, entity); }
        catch (RuntimeException error) {
            session.capture("MANAGER_SAMPLE_ERROR", Map.of("type", error.getClass().getSimpleName()));
        }
    }

    private void sampleManagers(Session session, Player entity) {
        String page = entity.getPageManager().getCustomPage() == null ? "none" :
                entity.getPageManager().getCustomPage().getClass().getName();
        List<Window> windows = new ArrayList<>(entity.getWindowManager().getWindows());
        windows.sort(Comparator.comparingInt(Window::getId));
        List<String> current = new ArrayList<>(windows.size());
        Set<String> field = new HashSet<>();
        for (Window window : windows) {
            String descriptor = window.getId() + ":" + window.getType() + ":" + window.getClass().getName();
            current.add(descriptor);
            if (window instanceof FieldCraftingWindow) {
                field.add(descriptor);
                session.watchClose(window, descriptor);
            }
        }
        session.managerSample(page, current, field);
    }

    public final class Tick extends EntityTickingSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() {
            return Query.and(PlayerRef.getComponentType(), Player.getComponentType());
        }
        @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                                   Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) return;
            Session session = sessions.get(player.getUuid());
            if (session != null && session.active()) sample(session, chunk.getComponent(index, Player.getComponentType()));
        }
    }

    public void detach(UUID player) {
        Session session = sessions.get(player);
        if (session != null) finish(session, "PLAYER_DISCONNECTED");
    }

    private void finish(Session session, String reason) {
        if (!sessions.remove(session.player, session)) return;
        List<Event> events = session.stop(reason);
        try {
            Files.createDirectories(directory);
            try (BufferedWriter writer = Files.newBufferedWriter(session.path, StandardCharsets.UTF_8)) {
                for (Event event : events) { writer.write(JSON.toJson(event)); writer.newLine(); }
                writer.write(JSON.toJson(Map.of("kind", "SUMMARY", "utc", Instant.now().toString(),
                        "reason", reason, "capturedEvents", events.size(), "droppedEvents", session.dropped(),
                        "captureMs", CAPTURE_MS, "player", session.player.toString())));
                writer.newLine();
            }
            LOG.info("TAB_TRACE_SAVED path=" + session.path + " events=" + events.size() + " dropped=" + session.dropped());
        } catch (IOException error) { LOG.log(Level.WARNING, "TAB_TRACE_SAVE_FAILED path=" + session.path, error); }
    }

    @Override public void close() {
        closed = true;
        for (Session session : List.copyOf(sessions.values())) finish(session, "PLUGIN_SHUTDOWN");
        scheduler.shutdownNow();
        PacketAdapters.deregisterInbound(inbound);
        PacketAdapters.deregisterOutbound(outbound);
    }

    record Event(long sequence, String utc, long offsetMicros, String kind, Map<String, ?> details) { }
    static final class Session {
        private final UUID player;
        private final Path path;
        private final List<Event> events = new ArrayList<>();
        private final Map<Integer, Registration> closeListeners = new HashMap<>();
        private long epochNanos, sequence, dropped;
        private boolean active, finished;
        private String previousPage = "";
        private List<String> previousWindows = List.of();
        private Set<String> previousField = Set.of();
        Session(UUID player, Path path) { this.player = player; this.path = path; }
        synchronized boolean active() {
            return active && !finished && System.nanoTime() - epochNanos < TimeUnit.MILLISECONDS.toNanos(CAPTURE_MS);
        }
        synchronized long dropped() { return dropped; }
        synchronized void start() { epochNanos = System.nanoTime(); active = true; append("GO", Map.of("captureMs", CAPTURE_MS)); }
        synchronized void note(String kind, Map<String, ?> details) { append(kind, details); }
        synchronized void capture(String kind, Map<String, ?> details) { if (active()) append(kind, details); }
        private void append(String kind, Map<String, ?> details) {
            if (events.size() >= MAX_EVENTS) { dropped++; return; }
            long now = System.nanoTime();
            events.add(new Event(++sequence, Instant.now().toString(), epochNanos == 0 ? 0 :
                    TimeUnit.NANOSECONDS.toMicros(now - epochNanos), kind, Map.copyOf(details)));
        }
        synchronized void managerSample(String page, List<String> windows, Set<String> field) {
            if (!active()) return;
            boolean changed = !page.equals(previousPage) || !windows.equals(previousWindows);
            capture(changed ? "MANAGER_TRANSITION" : "MANAGER_SAMPLE", Map.of(
                    "customPage", page, "windowManager", List.copyOf(windows),
                    "previousCustomPage", previousPage, "previousWindows", previousWindows));
            for (String id : field) if (!previousField.contains(id)) capture("FIELD_CRAFTING_APPEARED", Map.of("window", id));
            for (String id : previousField) if (!field.contains(id)) capture("FIELD_CRAFTING_DISAPPEARED", Map.of("window", id));
            previousPage = page;
            previousWindows = List.copyOf(windows);
            previousField = Set.copyOf(field);
        }
        synchronized void watchClose(Window window, String descriptor) {
            if (!closeListeners.containsKey(window.getId()))
                closeListeners.put(window.getId(), window.registerCloseEvent(event ->
                        capture("FIELD_CRAFTING_CLOSE_EVENT", Map.of("window", descriptor))));
        }
        synchronized List<Event> stop(String reason) {
            if (finished) return List.copyOf(events);
            if (active) {
                if (events.size() == MAX_EVENTS) { events.remove(events.size() - 1); dropped++; }
                append("STOP", Map.of("reason", reason));
            }
            active = false; finished = true;
            for (Registration registration : closeListeners.values()) registration.unregister();
            closeListeners.clear();
            return List.copyOf(events);
        }
    }
}
