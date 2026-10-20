package com.inigmasgames.canvasui.api;

import java.util.Objects;

/** A typed connection point whose anchor is relative to its node's top-left. */
public record CanvasPort(String portId, PortDirection direction, String semanticType,
                         int maxConnections, CanvasPoint anchorPosition) {
    public CanvasPort {
        Objects.requireNonNull(portId, "portId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(semanticType, "semanticType");
        Objects.requireNonNull(anchorPosition, "anchorPosition");
        if (portId.isBlank()) throw new IllegalArgumentException("portId is blank");
        if (maxConnections < 1) throw new IllegalArgumentException("maxConnections must be positive");
    }

    public static CanvasPort input(String id, String type, int max, double x, double y) {
        return new CanvasPort(id, PortDirection.INPUT, type, max, CanvasPoint.of(x, y));
    }

    public static CanvasPort output(String id, String type, int max, double x, double y) {
        return new CanvasPort(id, PortDirection.OUTPUT, type, max, CanvasPoint.of(x, y));
    }

    public static CanvasPort bidirectional(String id, String type, int max, double x, double y) {
        return new CanvasPort(id, PortDirection.BIDIRECTIONAL, type, max, CanvasPoint.of(x, y));
    }
}
