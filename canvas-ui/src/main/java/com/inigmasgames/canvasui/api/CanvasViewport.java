package com.inigmasgames.canvasui.api;

/** Immutable canvas-to-screen transform: screen = canvas * zoom + offset. */
public record CanvasViewport(double offsetX, double offsetY, double zoom) {
    public static final double MIN_ZOOM = 0.35;
    public static final double MAX_ZOOM = 2.0;
    public static final CanvasViewport ORIGIN = new CanvasViewport(0, 0, 1.0);

    public CanvasViewport(double offsetX, double offsetY) { this(offsetX, offsetY, 1.0); }

    public CanvasViewport {
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(zoom)) {
            throw new IllegalArgumentException("viewport values must be finite");
        }
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("zoom must be between " + MIN_ZOOM + " and " + MAX_ZOOM);
        }
    }

    public CanvasViewport pan(double dx, double dy) { return new CanvasViewport(offsetX + dx, offsetY + dy, zoom); }
    public CanvasPoint toScreen(CanvasPoint canvasPoint) {
        return CanvasPoint.of(canvasPoint.x() * zoom + offsetX, canvasPoint.y() * zoom + offsetY);
    }
    public CanvasPoint toCanvas(CanvasPoint screenPoint) {
        return CanvasPoint.of((screenPoint.x() - offsetX) / zoom, (screenPoint.y() - offsetY) / zoom);
    }
    public CanvasViewport zoomAround(CanvasPoint screenPoint, double requestedZoom) {
        double nextZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
        CanvasPoint fixedCanvasPoint = toCanvas(screenPoint);
        return new CanvasViewport(screenPoint.x() - fixedCanvasPoint.x() * nextZoom,
                screenPoint.y() - fixedCanvasPoint.y() * nextZoom, nextZoom);
    }
}
