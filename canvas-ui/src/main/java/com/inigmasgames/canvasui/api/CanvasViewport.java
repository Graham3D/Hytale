package com.inigmasgames.canvasui.api;

/** Viewport translation only. CanvasUI intentionally has no zoom state. */
public record CanvasViewport(double offsetX, double offsetY) {
    public static final CanvasViewport ORIGIN = new CanvasViewport(0, 0);
    public CanvasViewport pan(double dx, double dy) { return new CanvasViewport(offsetX + dx, offsetY + dy); }
    public CanvasPoint toScreen(CanvasPoint canvasPoint) { return canvasPoint.add(offsetX, offsetY); }
    public CanvasPoint toCanvas(CanvasPoint screenPoint) { return screenPoint.add(-offsetX, -offsetY); }
}
