package com.inigmasgames.canvasui.api;

/** A point in the stable, fixed-zoom canvas coordinate space. */
public record CanvasPoint(double x, double y) {
    public static CanvasPoint of(double x, double y) { return new CanvasPoint(x, y); }
    public CanvasPoint add(double dx, double dy) { return new CanvasPoint(x + dx, y + dy); }
    public CanvasPoint subtract(CanvasPoint other) { return new CanvasPoint(x - other.x, y - other.y); }
}
