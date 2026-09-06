package com.inigmasgames.canvasui.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CanvasDefinition {
    private final String canvasId;
    private final boolean pannable;
    private final boolean zoomable;
    private final PanGesture panGesture;
    private final boolean allowCycles;
    private final boolean allowDuplicateEdges;
    private final Map<String, NodeDefinition> nodeTypes;
    private final ConnectionPolicy connectionPolicy;
    private final List<CanvasListener> listeners;
    private final CanvasPersistenceAdapter persistence;
    private final com.inigmasgames.canvasui.rendering.EdgeRenderer edgeRenderer;

    private CanvasDefinition(Builder builder) {
        canvasId = builder.canvasId;
        pannable = builder.pannable;
        zoomable = builder.zoomable;
        panGesture = builder.panGesture;
        allowCycles = builder.allowCycles;
        allowDuplicateEdges = builder.allowDuplicateEdges;
        nodeTypes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.nodeTypes));
        connectionPolicy = builder.connectionPolicy;
        listeners = List.copyOf(builder.listeners);
        persistence = builder.persistence;
        edgeRenderer = builder.edgeRenderer;
    }

    public static Builder builder(String canvasId) { return new Builder(canvasId); }
    public String canvasId() { return canvasId; }
    /** Legacy compatibility accessor. */
    public boolean fixedZoom() { return !zoomable; }
    public boolean zoomable() { return zoomable; }
    public double minimumZoom() { return CanvasViewport.MIN_ZOOM; }
    public double maximumZoom() { return CanvasViewport.MAX_ZOOM; }
    public boolean pannable() { return pannable; }
    public PanGesture panGesture() { return panGesture; }
    public boolean allowCycles() { return allowCycles; }
    public boolean allowDuplicateEdges() { return allowDuplicateEdges; }
    public Map<String, NodeDefinition> nodeTypes() { return nodeTypes; }
    public NodeDefinition nodeType(String type) { return nodeTypes.get(type); }
    public ConnectionPolicy connectionPolicy() { return connectionPolicy; }
    public List<CanvasListener> listeners() { return listeners; }
    public CanvasPersistenceAdapter persistence() { return persistence; }
    public com.inigmasgames.canvasui.rendering.EdgeRenderer edgeRenderer() { return edgeRenderer; }

    public static final class Builder {
        private final String canvasId;
        private boolean pannable = true;
        private boolean zoomable = true;
        private PanGesture panGesture = PanGesture.MIDDLE_BUTTON;
        private boolean allowCycles = true;
        private boolean allowDuplicateEdges;
        private final Map<String, NodeDefinition> nodeTypes = new LinkedHashMap<>();
        private ConnectionPolicy connectionPolicy = ConnectionPolicy.allowAll();
        private final List<CanvasListener> listeners = new ArrayList<>();
        private CanvasPersistenceAdapter persistence = CanvasPersistenceAdapter.none();
        private com.inigmasgames.canvasui.rendering.EdgeRenderer edgeRenderer = new com.inigmasgames.canvasui.rendering.OrthogonalEdgeRenderer();

        private Builder(String canvasId) { this.canvasId = Objects.requireNonNull(canvasId); }
        /** Legacy compatibility setter. Prefer {@link #zoomable(boolean)}. */
        public Builder fixedZoom(boolean value) { zoomable = !value; return this; }
        public Builder zoomable(boolean value) { zoomable = value; return this; }
        public Builder pannable(boolean value) { pannable = value; return this; }
        public Builder panGesture(PanGesture value) { panGesture = Objects.requireNonNull(value); return this; }
        public Builder allowCycles(boolean value) { allowCycles = value; return this; }
        public Builder allowDuplicateEdges(boolean value) { allowDuplicateEdges = value; return this; }
        public Builder registerNodeType(NodeDefinition definition) {
            if (nodeTypes.putIfAbsent(definition.type(), definition) != null) throw new IllegalArgumentException("duplicate node type: " + definition.type());
            return this;
        }
        public Builder connectionPolicy(ConnectionPolicy value) { connectionPolicy = Objects.requireNonNull(value); return this; }
        public Builder listener(CanvasListener value) { listeners.add(Objects.requireNonNull(value)); return this; }
        public Builder persistence(CanvasPersistenceAdapter value) { persistence = Objects.requireNonNull(value); return this; }
        public Builder edgeRenderer(com.inigmasgames.canvasui.rendering.EdgeRenderer value) { edgeRenderer = Objects.requireNonNull(value); return this; }
        public CanvasDefinition build() {
            if (canvasId.isBlank()) throw new IllegalArgumentException("canvasId is blank");
            if (nodeTypes.isEmpty()) throw new IllegalStateException("at least one node type is required");
            return new CanvasDefinition(this);
        }
    }
}
