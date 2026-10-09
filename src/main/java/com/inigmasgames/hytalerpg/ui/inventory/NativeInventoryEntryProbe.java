package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.io.adapter.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;
import com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/** PocketCrafting observation entry for the unified Inventory page; not a pre-open key hook. */
public final class NativeInventoryEntryProbe implements AutoCloseable {
    public enum Mode { OBSERVE, REDIRECT }
    private static final class Session {
        final Mode mode; final AtomicInteger budget = new AtomicInteger(256);
        final AtomicBoolean opening = new AtomicBoolean();
        Session(Mode mode) { this.mode = mode; }
    }
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final RpgUiProjectionService projection;
    private final RpgUiTraceService trace;
    private final CommandInventoryEntryAdapter entry;
    private final InventoryEntryGate gate = new InventoryEntryGate();
    private final boolean automaticRedirect;
    private final Map<UUID, AtomicInteger> entryBudgets = new ConcurrentHashMap<>();
    private final Set<UUID> postOpenQueued = ConcurrentHashMap.newKeySet();
    private final Set<UUID> nativeTraceBypass = ConcurrentHashMap.newKeySet();
    private final PacketFilter inbound, outbound;
    private volatile boolean closed;

    public static boolean enabled(Path configurationDirectory) {
        return Files.isRegularFile(configurationDirectory.resolve("inventory-ui-probe-enabled"));
    }
    public NativeInventoryEntryProbe(RpgUiProjectionService projection, RpgUiTraceService trace,
                                     Path configurationDirectory, CommandInventoryEntryAdapter entry) {
        this.projection = projection; this.trace = trace; this.entry = entry;
        InventoryProbePage.preload();
        // The connected R118 Tab test produced no PocketCrafting packet. Keep the
        // candidate route opt-in until a supported, connected entry signal exists.
        automaticRedirect = Files.isRegularFile(configurationDirectory.resolve("inventory-ui-redirect-enabled"));
        entry.onDismiss(player -> {
            if (gate.state(player) != InventoryEntryGate.State.CLOSED)
                entryTrace(player, "INVENTORY_CUSTOM_CLOSED", 0, Map.of());
            gate.detach(player);
        });
        inbound = PacketAdapters.registerInbound((PlayerPacketFilter)this::filter);
        outbound = PacketAdapters.registerOutbound((PlayerPacketWatcher)(player, packet) -> {
            if (packet instanceof SetPage page) record(player, "OUTBOUND_PAGE", Map.of("nativePage", String.valueOf(page.page)));
            var diagnostic = UiInteractionTrace.current();
            if (diagnostic != null && UiInteractionTrace.active(player.getUuid())) {
                if (packet instanceof SetPage page) diagnostic.event(player.getUuid(), "NATIVE_PAGE", "",
                        Map.of("page", String.valueOf(page.page), "direction", "OUTBOUND"));
                if (packet instanceof UpdateWindow update)
                    diagnostic.event(player.getUuid(), "WINDOW_UPDATE", "",
                            Map.of("windowId", update.id, "direction", "OUTBOUND"));
            }
            // In singleplayer the inbound adapter is bypassed by the stream transport.
            // An UpdateWindow for slot zero is emitted after the server creates the
            // client-requested window; verify its actual type on the world thread.
            if (packet instanceof UpdateWindow update && update.id == 0) postOpen(player);
        });
    }
    public void arm(PlayerRef player, Mode mode) {
        if (closed) throw new IllegalStateException("Probe closed");
        sessions.put(player.getUuid(), new Session(mode));
        record(player, "ARMED", Map.of("mode", mode.name(), "connectedAcceptance", false));
    }
    /** Temporarily let an unmodified native Inventory open for passive drop tracing. */
    public void nativeTraceBypass(UUID player, boolean enabled) {
        if (enabled) nativeTraceBypass.add(player);
        else nativeTraceBypass.remove(player);
    }
    public void detach(UUID player) { sessions.remove(player); gate.detach(player); entryBudgets.remove(player); postOpenQueued.remove(player); nativeTraceBypass.remove(player); }

    private void postOpen(PlayerRef player) {
        if (closed || !postOpenQueued.add(player.getUuid())) return;
        var ref = player.getReference();
        if (ref == null || !ref.isValid()) { postOpenQueued.remove(player.getUuid()); return; }
        var store = ref.getStore();
        try {
            store.getExternalData().getWorld().execute(() -> {
                try {
                    if (closed || nativeTraceBypass.contains(player.getUuid())
                            || !ref.isValid() || player.getReference() != ref) return;
                    var entity = store.getComponent(ref, Player.getComponentType());
                    if (entity == null || entity.getPageManager().getCustomPage() != null) return;
                    var windows = entity.getWindowManager();
                    Window nativeWindow = windows.getWindow(0);
                    if (!(nativeWindow instanceof FieldCraftingWindow) || nativeWindow.getType() != WindowType.PocketCrafting) return;
                    var diagnostic = UiInteractionTrace.current();
                    if (diagnostic != null) diagnostic.event(player.getUuid(), "FIELD_CRAFTING_WINDOW", "",
                            Map.of("windowId", 0, "windowType", "PocketCrafting", "observation", "OPEN"));
                    if (entry.open(player, ref, store)) {
                        // Close only the exact window we observed. Never touch other
                        // crafting stations or a replacement opened meanwhile.
                        if (windows.getWindow(0) == nativeWindow) windows.closeWindow(ref, 0, store);
                        entryTrace(player.getUuid(), "INVENTORY_POST_OPEN_BRIDGED", 0,
                                Map.of("source", "UpdateWindow", "windowType", "PocketCrafting"));
                    }
                } catch (RuntimeException error) {
                    entryTrace(player.getUuid(), "INVENTORY_POST_OPEN_ABORTED", 0,
                            Map.of("reason", error.getClass().getSimpleName()));
                } finally {
                    postOpenQueued.remove(player.getUuid());
                }
            });
        } catch (RuntimeException error) {
            postOpenQueued.remove(player.getUuid());
            entryTrace(player.getUuid(), "INVENTORY_POST_OPEN_ABORTED", 0,
                    Map.of("reason", "QUEUE_" + error.getClass().getSimpleName()));
        }
    }
    public boolean aliasActive(PlayerRef player) { return !closed && sessions.containsKey(player.getUuid()); }
    public void record(PlayerRef player, String event, Map<String, ?> details) {
        var session = sessions.get(player.getUuid());
        if (session != null && session.budget.getAndDecrement() > 0)
            trace.trace(player.getUuid(), "INVENTORY_PROBE_" + event, "inventory-probe", details);
    }
    private boolean filter(PlayerRef player, Packet packet) {
        var session = sessions.get(player.getUuid());
        if (closed) return false;
        var diagnostic = UiInteractionTrace.current();
        if (packet instanceof ClientOpenWindow window && diagnostic != null)
            diagnostic.event(player.getUuid(), "WINDOW_REQUEST", "",
                    Map.of("windowType", String.valueOf(window.type), "direction", "INBOUND"));
        if (session != null && packet.getClass().getPackageName().matches(".*\\.(inventory|window|interface_)"))
            record(player, "INBOUND", Map.of("packet", packet.getClass().getSimpleName(),
                    "windowType", packet instanceof ClientOpenWindow w ? String.valueOf(w.type) : ""));
        if (nativeTraceBypass.contains(player.getUuid())) return false;
        if (!automaticRedirect && (session == null || session.mode != Mode.REDIRECT)) return false;
        var request = gate.request(player.getUuid(), packet);
        if (!request.consumed()) return false;
        if (!request.schedule()) return true;
        long observed = System.nanoTime();
        var ref = player.getReference();
        if (ref == null || !ref.isValid()) { gate.dismiss(player.getUuid(), request.generation()); return true; }
        var store = ref.getStore();
        try {
            store.getExternalData().getWorld().execute(() -> {
                long queued = System.nanoTime();
                if (closed || !gate.pending(player.getUuid(), request.generation()) || !ref.isValid()
                        || player.getReference() != ref) {
                    gate.dismiss(player.getUuid(), request.generation());
                    entryTrace(player.getUuid(), "INVENTORY_ENTRY_ABORTED", request.generation(), Map.of("reason", "STALE"));
                    return;
                }
                try {
                    boolean opened = entry.open(player, ref, store,
                            () -> gate.dismiss(player.getUuid(), request.generation()));
                    long openNanos = System.nanoTime();
                    if (opened && gate.opened(player.getUuid(), request.generation()))
                        entryTrace(player.getUuid(), "INVENTORY_CUSTOM_OPENED", request.generation(), Map.of(
                                "windowType", "PocketCrafting",
                                "redirectQueueLatencyMs", TimeUnit.NANOSECONDS.toMillis(queued - observed),
                                "redirectOpenLatencyMs", TimeUnit.NANOSECONDS.toMillis(openNanos - observed)));
                    else gate.dismiss(player.getUuid(), request.generation());
                } catch (RuntimeException error) {
                    gate.dismiss(player.getUuid(), request.generation());
                    entryTrace(player.getUuid(), "INVENTORY_ENTRY_ABORTED", request.generation(),
                            Map.of("reason", error.getClass().getSimpleName()));
                }
            });
        } catch (RuntimeException error) {
            gate.dismiss(player.getUuid(), request.generation());
            entryTrace(player.getUuid(), "INVENTORY_ENTRY_ABORTED", request.generation(),
                    Map.of("reason", "QUEUE_" + error.getClass().getSimpleName()));
        }
        return true; // Suppress only PocketCrafting; all other native windows pass through.
    }
    private void entryTrace(UUID player, String event, long generation, Map<String, ?> details) {
        if (entryBudgets.computeIfAbsent(player, ignored -> new AtomicInteger(48)).getAndDecrement() > 0)
            trace.trace(player, event, "inventory-entry-" + generation, details);
    }
    public void open(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store, String source) {
        if (!aliasActive(player)) return;
        var entity = store.getComponent(ref, Player.getComponentType());
        if (entity == null || entity.getPageManager().getCustomPage() instanceof InventoryProbePage) return;
        record(player, "OPEN", Map.of("source", source, "nativeEntryProven", false));
        entity.getPageManager().openCustomPage(ref, store, new InventoryProbePage(player, projection, this));
    }
    /** Death closes only our page and invalidates any queued inventory-entry work. */
    public static final class DeathCleanupSystem extends DeathSystems.OnDeathSystem {
        private final NativeInventoryEntryProbe owner;
        public DeathCleanupSystem(NativeInventoryEntryProbe owner) { this.owner = owner; }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death,
                                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            owner.detach(player.getUuid());
            Player entity = store.getComponent(ref, Player.getComponentType());
            if (entity != null && entity.getPageManager().getCustomPage() instanceof InventoryProbePage)
                entity.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
        }
    }
    @Override public void close() {
        closed = true; sessions.clear(); gate.clear(); entryBudgets.clear(); postOpenQueued.clear();
        PacketAdapters.deregisterInbound(inbound); PacketAdapters.deregisterOutbound(outbound);
    }
}
