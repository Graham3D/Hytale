package com.inigmasgames.canvasui.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NodeDefinition {
    private final String type;
    private final int width;
    private final int height;
    private final boolean draggable;
    private final boolean selectable;
    private final Map<String, CanvasPort> ports;
    private final NodeRenderer renderer;

    private NodeDefinition(Builder builder) {
        type = builder.type;
        width = builder.width;
        height = builder.height;
        draggable = builder.draggable;
        selectable = builder.selectable;
        ports = Collections.unmodifiableMap(new LinkedHashMap<>(builder.ports));
        renderer = builder.renderer;
    }

    public static Builder builder(String type) { return new Builder(type); }
    public String type() { return type; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean draggable() { return draggable; }
    public boolean selectable() { return selectable; }
    public Map<String, CanvasPort> ports() { return ports; }
    public CanvasPort port(String id) { return ports.get(id); }
    public NodeRenderer renderer() { return renderer; }

    public static final class Builder {
        private final String type;
        private int width = 140;
        private int height = 72;
        private boolean draggable = true;
        private boolean selectable = true;
        private final Map<String, CanvasPort> ports = new LinkedHashMap<>();
        private NodeRenderer renderer;

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type");
            this.renderer = context -> NodeVisual.simple(context.node().nodeId(), "#24354aee");
        }

        public Builder size(int width, int height) {
            if (width < 20 || height < 20) throw new IllegalArgumentException("node size is too small");
            this.width = width; this.height = height; return this;
        }
        public Builder draggable(boolean value) { draggable = value; return this; }
        public Builder selectable(boolean value) { selectable = value; return this; }
        public Builder port(CanvasPort port) {
            if (ports.putIfAbsent(port.portId(), port) != null) throw new IllegalArgumentException("duplicate port: " + port.portId());
            return this;
        }
        public Builder renderer(NodeRenderer renderer) { this.renderer = Objects.requireNonNull(renderer); return this; }
        public NodeDefinition build() {
            if (type.isBlank()) throw new IllegalArgumentException("node type is blank");
            return new NodeDefinition(this);
        }
    }
}
