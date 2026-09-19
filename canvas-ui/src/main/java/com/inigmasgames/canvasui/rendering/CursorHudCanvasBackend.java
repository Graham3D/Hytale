package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;

import java.util.Objects;

/** Passive-HUD renderer for the bounded R042 graph interaction proof. */
public final class CursorHudCanvasBackend implements CanvasRenderBackend {
    private final Canvas canvas;
    private final CanvasCursorProbeHud hud;

    public CursorHudCanvasBackend(Canvas canvas, CanvasCursorProbeHud hud) {
        this.canvas = Objects.requireNonNull(canvas);
        this.hud = Objects.requireNonNull(hud);
    }

    @Override public String id() { return "hytale-cursor-hud-0.7.0-pre.2"; }
    @Override public void topologyChanged() { hud.updateGraph(canvas); }
    @Override public void updateNodeAndEdges(String nodeId) { hud.updateGraph(canvas); }
    @Override public void updateViewport() { hud.updateGraph(canvas); }
    @Override public void pointerTarget(String nodeId, boolean invalid) {
        if (invalid) hud.graphStatus("Invalid connection target");
    }
    @Override public void clearPointerTarget() { }
    @Override public void updatePreview(CanvasPoint source, CanvasPoint target, boolean valid) {
        hud.graphStatus(valid ? "Connection target allowed" : "Release over an input port");
    }
    @Override public void clearPreview(String status) { hud.graphStatus(status); }
    @Override public void status(String value) { hud.graphStatus(value); }
}
