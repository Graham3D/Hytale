package com.inigmasgames.canvasui.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasEventType;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.ConnectionResult;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.canvasui.rendering.HytaleCustomUiBackend;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CanvasSession implements AutoCloseable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long UI_INTERVAL_NANOS = 100_000_000L;
    private final CanvasService owner;
    private final UUID playerId;
    private final Canvas canvas;
    private final CanvasInputController input;
    private final HytaleCustomUiBackend backend;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long openedNanos = System.nanoTime();
    private long lastUiNanos;
    private long pointerEvents;
    private long uiUpdates;
    private long pageRebuilds;
    private long commandsEmitted;
    private long latencySamples;
    private long processingLatencyNanos;
    private long peakLatencyNanos;
    private String searchQuery = "";

    CanvasSession(CanvasService owner, Player player, PlayerRef playerRef, CanvasDefinition definition,
                  java.util.function.Consumer<Canvas> initializer) {
        this.owner = owner;
        this.playerId = playerRef.getUuid();
        this.canvas = new Canvas(definition);
        definition.persistence().load(definition.canvasId()).ifPresent(canvas::restore);
        initializer.accept(canvas);
        this.backend = new HytaleCustomUiBackend(this);
        this.input = new CanvasInputController(this, backend);
        owner.openPage(player, playerRef, this, backend);
        canvas.publish(CanvasEventType.CANVAS_OPENED, null, null, null, canvas.snapshot(), null);
        LOGGER.atInfo().log("CANVASUI_OPEN revision=%s player=%s canvas=%s", CanvasUI.REVISION, playerId, definition.canvasId());
    }

    public UUID playerId() { return playerId; }
    public Canvas canvas() { return canvas; }
    public boolean isClosed() { return closed.get(); }

    public CanvasNode createNode(String type, double x, double y) {
        ensureOpen(); CanvasNode node = canvas.createNode(type, x, y); backend.topologyChanged(); persist(); return node;
    }
    public CanvasNode createNode(String id, String type, CanvasPoint point, Map<String, String> metadata) {
        ensureOpen(); CanvasNode node = canvas.createNode(id, type, point, metadata); backend.topologyChanged(); persist(); return node;
    }
    public void removeNode(String id) { ensureOpen(); canvas.removeNode(id); backend.topologyChanged(); persist(); }
    public CanvasNode moveNode(String id, double x, double y) {
        ensureOpen(); CanvasNode node = canvas.moveNode(id, CanvasPoint.of(x, y)); backend.updateNodeAndEdges(id); persist(); return node;
    }
    public CanvasNode selectNode(String id) { ensureOpen(); CanvasNode node = canvas.selectNode(id); backend.topologyChanged(); return node; }
    public CanvasEdge connect(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        ensureOpen(); CanvasEdge edge = canvas.connect(sourceNode, sourcePort, targetNode, targetPort); backend.topologyChanged(); persist(); return edge;
    }
    public CanvasEdge connect(String edgeId, String sourceNode, String sourcePort, String targetNode,
                              String targetPort, EdgeStyle style) {
        ensureOpen(); CanvasEdge edge = canvas.connect(edgeId, sourceNode, sourcePort, targetNode, targetPort, style);
        backend.topologyChanged(); persist(); return edge;
    }
    public void removeEdge(String id) { ensureOpen(); canvas.removeEdge(id); backend.topologyChanged(); persist(); }
    public void pan(double dx, double dy) { ensureOpen(); canvas.pan(dx, dy); backend.updateViewport(); persist(); }
    public void zoom(double zoom, CanvasPoint cursorScreenPoint) {
        ensureOpen(); canvas.zoom(zoom, cursorScreenPoint); backend.updateViewport(); persist();
    }
    public String searchQuery() { return searchQuery; }
    public void search(String query) { ensureOpen(); searchQuery = query == null ? "" : query; backend.topologyChanged(); }
    public ConnectionResult validateConnection(String sn, String sp, String tn, String tp) {
        return canvas.validateConnection(sn, sp, tn, tp);
    }

    void pointerButton(com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent event) {
        if (!isClosed()) input.button(event);
    }
    void pointerMotion(com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent event) {
        if (!isClosed()) input.motion(event);
    }
    boolean renderDue(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastUiNanos < UI_INTERVAL_NANOS) return false;
        lastUiNanos = now; return true;
    }
    void persist() { canvas.definition().persistence().save(canvas.definition().canvasId(), canvas.snapshot()); }
    void recordPointer(long latencyNanos) {
        pointerEvents++; latencySamples++; processingLatencyNanos += latencyNanos; peakLatencyNanos = Math.max(peakLatencyNanos, latencyNanos);
    }
    public void recordUiUpdate(long commands) { uiUpdates++; commandsEmitted += commands; }
    public void recordPageRebuild() { pageRebuilds++; }

    public CanvasMetrics metrics() {
        double seconds = Math.max(0.001, (System.nanoTime() - openedNanos) / 1_000_000_000.0);
        return new CanvasMetrics(pointerEvents, uiUpdates, pageRebuilds, commandsEmitted,
                pointerEvents / seconds, uiUpdates / seconds,
                latencySamples == 0 ? 0 : processingLatencyNanos / 1_000_000.0 / latencySamples,
                peakLatencyNanos / 1_000_000.0);
    }

    @Override public void close() { close("CONSUMER_CLOSE"); }
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        input.clear();
        persist();
        CanvasMetrics metrics = metrics();
        canvas.publish(CanvasEventType.CANVAS_CLOSED, null, null, canvas.snapshot(), reason, null);
        boolean pageAlreadyClosing = reason.equals("PAGE_DISMISS") || reason.equals("PLAYER_DISCONNECT")
                || reason.equals("WORLD_TRANSITION") || reason.equals("SERVICE_CLOSE");
        backend.close(!pageAlreadyClosing);
        owner.sessionClosed(this);
        LOGGER.atInfo().log("CANVASUI_CLOSE revision=%s player=%s canvas=%s reason=%s pointerEvents=%d uiUpdates=%d rebuilds=%d commands=%d pointerHz=%.2f uiHz=%.2f avgProcessingMs=%.3f peakProcessingMs=%.3f",
                CanvasUI.REVISION, playerId, canvas.definition().canvasId(), reason, metrics.pointerEvents(),
                metrics.uiUpdates(), metrics.pageRebuilds(), metrics.commandsEmitted(), metrics.pointerEventsPerSecond(),
                metrics.uiUpdatesPerSecond(), metrics.averageProcessingLatencyMillis(), metrics.peakProcessingLatencyMillis());
    }

    private void ensureOpen() { if (isClosed()) throw new IllegalStateException("canvas session is closed"); }
}
