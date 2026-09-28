package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;

import java.util.Objects;

/** Passive-HUD renderer for the bounded R042 graph interaction proof. */
public final class CursorHudCanvasBackend implements CanvasRenderBackend {
    private final Canvas canvas;
    private final CanvasCursorProbeHud hud;
    private final boolean inventoryProof;

    public CursorHudCanvasBackend(Canvas canvas, CanvasCursorProbeHud hud) {
        this(canvas, hud, false);
    }

    public CursorHudCanvasBackend(Canvas canvas, CanvasCursorProbeHud hud, boolean inventoryProof) {
        this.canvas = Objects.requireNonNull(canvas);
        this.hud = Objects.requireNonNull(hud);
        this.inventoryProof = inventoryProof;
    }

    @Override public String id() { return "hytale-cursor-hud-0.7.0-pre.2"; }
    @Override public void topologyChanged() { render(); }
    @Override public void updateNodeAndEdges(String nodeId) { render(); }
    @Override public void updateViewport() { render(); }
    private void render() { if (inventoryProof) hud.updateInventoryGraph(canvas); else hud.updateGraph(canvas); }
    @Override public void pointerTarget(String nodeId, boolean invalid) {
        if (invalid) hud.graphStatus("Invalid connection target");
    }
    @Override public void clearPointerTarget() { }
    @Override public void updatePreview(CanvasPoint source, CanvasPoint target, boolean valid) {
        hud.graphStatus(valid ? "Connection target allowed" : "Release over an input port");
    }
    @Override public void clearPreview(String status) { hud.graphStatus(status); }
    @Override public void status(String value) {
        if (inventoryProof) hud.inventoryStatus(value); else hud.graphStatus(value);
    }
}
