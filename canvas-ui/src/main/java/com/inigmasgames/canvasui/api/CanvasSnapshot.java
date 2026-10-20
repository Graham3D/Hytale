package com.inigmasgames.canvasui.api;

import java.util.List;
import java.util.Map;

/** Backend-neutral serialization DTO; consumers choose the storage/codec. */
public record CanvasSnapshot(String canvasId, CanvasViewport viewport,
                             List<NodeState> nodes, List<EdgeState> edges, String selectedNodeId) {
    public CanvasSnapshot {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
    }

    public record NodeState(String nodeId, String type, double x, double y,
                            Map<String, String> metadata, boolean enabled) {
        public NodeState { metadata = Map.copyOf(metadata == null ? Map.of() : metadata); }
    }

    public record EdgeState(String edgeId, String sourceNodeId, String sourcePortId,
                            String targetNodeId, String targetPortId, EdgeStyle style) { }
}
