package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.NodeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Presentation-only movable port anchors stored with each node's persisted layout metadata. */
public final class PortAnchorResolver {
    public static final String PREFIX = "canvas.port.";
    private PortAnchorResolver() { }

    public static CanvasPoint relative(Canvas canvas, CanvasNode node, String portId) {
        CanvasPort port = canvas.definition().nodeType(node.type()).port(portId);
        if (port == null) throw new IllegalArgumentException("missing port: " + portId);
        // Skill inputs and triangular-joint ports are authored anchors. Only Passive ports orbit.
        if ("skill".equals(node.type()) || "joint".equals(node.type())) return port.anchorPosition();
        String key = PREFIX + portId;
        try {
            String x = node.metadata().get(key + ".x");
            String y = node.metadata().get(key + ".y");
            if (x != null && y != null) return CanvasPoint.of(Double.parseDouble(x), Double.parseDouble(y));
        } catch (RuntimeException ignored) { }
        return port.anchorPosition();
    }

    public static CanvasPoint screen(Canvas canvas, String nodeId, String portId) {
        CanvasNode node = canvas.node(nodeId);
        CanvasPoint anchor = relative(canvas, node, portId);
        return canvas.viewport().toScreen(node.position().add(anchor.x(), anchor.y()));
    }

    /** Moves only the actively dragged node's connected ports toward their opposite endpoints. */
    public static boolean orbitConnected(Canvas canvas, String nodeId) {
        CanvasNode node = canvas.node(nodeId);
        if (node == null) return false;
        if (!"passive".equals(node.type())) return false;
        NodeDefinition type = canvas.definition().nodeType(node.type());
        Map<String, List<CanvasPoint>> destinations = new java.util.LinkedHashMap<>();
        for (CanvasEdge edge : canvas.edges()) {
            if (edge.sourceNodeId().equals(nodeId)) destinations.computeIfAbsent(edge.sourcePortId(), ignored -> new ArrayList<>())
                    .add(center(canvas, edge.targetNodeId()));
            if (edge.targetNodeId().equals(nodeId)) destinations.computeIfAbsent(edge.targetPortId(), ignored -> new ArrayList<>())
                    .add(center(canvas, edge.sourceNodeId()));
        }
        if (destinations.isEmpty()) return false;
        Map<String, String> metadata = new java.util.LinkedHashMap<>(node.metadata());
        boolean changed = false;
        CanvasPoint center = localCenter(node.type(), type);
        for (var entry : destinations.entrySet()) {
            CanvasPoint average = average(entry.getValue());
            CanvasPoint localDestination = average.subtract(node.position());
            CanvasPoint anchor = perimeter(node.type(), type, center, localDestination);
            String key = PREFIX + entry.getKey();
            String x = Double.toString(anchor.x()), y = Double.toString(anchor.y());
            changed |= !x.equals(metadata.put(key + ".x", x));
            changed |= !y.equals(metadata.put(key + ".y", y));
        }
        if (changed) canvas.updateNodeMetadata(nodeId, metadata);
        return changed;
    }

    public static String orientation(CanvasPoint anchor, CanvasNode node, NodeDefinition type) {
        CanvasPoint center = localCenter(node.type(), type);
        double angle = Math.atan2(anchor.y() - center.y(), anchor.x() - center.x());
        int sector = Math.floorMod((int)Math.round(angle / (Math.PI / 4.0)), 8);
        return switch (sector) {
            case 0 -> "Right";
            case 1 -> "DownRight";
            case 2 -> "Down";
            case 3 -> "DownLeft";
            case 4 -> "Left";
            case 5 -> "UpLeft";
            case 6 -> "Up";
            default -> "UpRight";
        };
    }

    private static CanvasPoint center(Canvas canvas, String nodeId) {
        CanvasNode node = canvas.node(nodeId);
        NodeDefinition type = canvas.definition().nodeType(node.type());
        CanvasPoint local = localCenter(node.type(), type);
        return node.position().add(local.x(), local.y());
    }

    private static CanvasPoint localCenter(String nodeType, NodeDefinition type) {
        if ("skill".equals(nodeType)) return CanvasPoint.of(66, 31);
        if ("passive".equals(nodeType)) return CanvasPoint.of(62, 27);
        return CanvasPoint.of(type.width() / 2.0, type.height() / 2.0);
    }

    private static CanvasPoint perimeter(String nodeType, NodeDefinition type, CanvasPoint center, CanvasPoint target) {
        double dx = target.x() - center.x(), dy = target.y() - center.y();
        double length = Math.hypot(dx, dy);
        if (length < 0.0001) return center;
        dx /= length; dy /= length;
        if ("passive".equals(nodeType)) return center.add(dx * 35.0, dy * 35.0);
        double halfWidth = "skill".equals(nodeType) ? 36.0 : type.width() / 2.0;
        double halfHeight = "skill".equals(nodeType) ? 36.0 : type.height() / 2.0;
        double scale = Math.min(Math.abs(dx) < 0.0001 ? Double.MAX_VALUE : halfWidth / Math.abs(dx),
                Math.abs(dy) < 0.0001 ? Double.MAX_VALUE : halfHeight / Math.abs(dy));
        return center.add(dx * scale, dy * scale);
    }

    private static CanvasPoint average(List<CanvasPoint> points) {
        double x = 0, y = 0;
        for (CanvasPoint point : points) { x += point.x(); y += point.y(); }
        return CanvasPoint.of(x / points.size(), y / points.size());
    }
}
