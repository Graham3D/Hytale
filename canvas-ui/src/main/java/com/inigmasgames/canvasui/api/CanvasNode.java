package com.inigmasgames.canvasui.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CanvasNode(String nodeId, String type, CanvasPoint position,
                         Map<String, String> metadata, boolean enabled) {
    public CanvasNode {
        Objects.requireNonNull(nodeId); Objects.requireNonNull(type); Objects.requireNonNull(position);
        metadata = Map.copyOf(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
        if (nodeId.isBlank() || type.isBlank()) throw new IllegalArgumentException("node id/type is blank");
    }
    public CanvasNode movedTo(CanvasPoint point) { return new CanvasNode(nodeId, type, point, metadata, enabled); }
    public CanvasNode enabled(boolean value) { return new CanvasNode(nodeId, type, position, metadata, value); }
}
