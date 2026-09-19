package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;

import java.util.Objects;

/** Input-controller backend whose complete editor frame is supplied by its owner. */
public final class CursorHudGraphEditorBackend implements CanvasRenderBackend {
    private final Canvas canvas;
    private final Runnable render;
    private final java.util.function.Consumer<String> status;
    private final java.util.function.Consumer<CanvasGraphEditorHud.PreviewVisual> preview;

    public CursorHudGraphEditorBackend(Canvas canvas, Runnable render, java.util.function.Consumer<String> status) {
        this(canvas, render, status, ignored -> render.run());
    }

    public CursorHudGraphEditorBackend(Canvas canvas, Runnable render, java.util.function.Consumer<String> status,
                                       java.util.function.Consumer<CanvasGraphEditorHud.PreviewVisual> preview) {
        this.canvas = Objects.requireNonNull(canvas);
        this.render = Objects.requireNonNull(render);
        this.status = Objects.requireNonNull(status);
        this.preview = Objects.requireNonNull(preview);
    }

    @Override public String id() { return "hytale-cursor-graph-editor-0.7.0-pre.3.1"; }
    @Override public void topologyChanged() { render.run(); }
    @Override public void updateNodeAndEdges(String nodeId) { render.run(); }
    @Override public void updateViewport() { render.run(); }
    @Override public void pointerTarget(String nodeId, boolean invalid) { if (invalid) status.accept("Invalid connection target"); }
    @Override public void clearPointerTarget() { }
    @Override public void updatePreview(CanvasPoint source, CanvasPoint target, boolean valid) {
        status.accept(valid ? "Release to parent these nodes" : "Release over a compatible port");
        preview.accept(new CanvasGraphEditorHud.PreviewVisual(source, target, valid));
    }
    @Override public void clearPreview(String value) { status.accept(value); preview.accept(null); render.run(); }
    @Override public void status(String value) { status.accept(value); render.run(); }
}
