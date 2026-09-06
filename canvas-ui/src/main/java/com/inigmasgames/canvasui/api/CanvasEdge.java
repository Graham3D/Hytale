package com.inigmasgames.canvasui.api;

import java.util.Objects;

public record CanvasEdge(String edgeId, String sourceNodeId, String sourcePortId,
                         String targetNodeId, String targetPortId, EdgeStyle style) {
    public CanvasEdge {
        Objects.requireNonNull(edgeId); Objects.requireNonNull(sourceNodeId); Objects.requireNonNull(sourcePortId);
        Objects.requireNonNull(targetNodeId); Objects.requireNonNull(targetPortId); Objects.requireNonNull(style);
    }
}
