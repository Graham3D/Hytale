package com.inigmasgames.canvasui.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Backend-neutral graph model. All mutation is validated before it is committed. */
public final class Canvas {
    private final CanvasDefinition definition;
    private final Map<String, CanvasNode> nodes = new LinkedHashMap<>();
    private final Map<String, CanvasEdge> edges = new LinkedHashMap<>();
    private CanvasViewport viewport = CanvasViewport.ORIGIN;
    private String selectedNodeId;

    public Canvas(CanvasDefinition definition) { this.definition = Objects.requireNonNull(definition); }
    public CanvasDefinition definition() { return definition; }
    public synchronized Collection<CanvasNode> nodes() { return List.copyOf(nodes.values()); }
    public synchronized Collection<CanvasEdge> edges() { return List.copyOf(edges.values()); }
    public synchronized CanvasNode node(String id) { return nodes.get(id); }
    public synchronized CanvasEdge edge(String id) { return edges.get(id); }
    public synchronized CanvasViewport viewport() { return viewport; }
    public synchronized String selectedNodeId() { return selectedNodeId; }

    public synchronized CanvasNode createNode(String type, double x, double y) {
        return createNode(UUID.randomUUID().toString(), type, CanvasPoint.of(x, y), Map.of());
    }

    public synchronized CanvasNode createNode(String type, CanvasPoint point, Map<String, String> metadata) {
        return createNode(UUID.randomUUID().toString(), type, point, metadata);
    }

    public synchronized CanvasNode createNode(String nodeId, String type, CanvasPoint point,
                                               Map<String, String> metadata) {
        if (nodes.containsKey(nodeId)) throw new IllegalArgumentException("duplicate node id: " + nodeId);
        if (definition.nodeType(type) == null) throw new IllegalArgumentException("unknown node type: " + type);
        CanvasNode node = new CanvasNode(nodeId, type, point, metadata, true);
        nodes.put(nodeId, node);
        fire(CanvasEventType.NODE_CREATED, nodeId, null, null, node, null);
        changed();
        return node;
    }

    public synchronized CanvasNode moveNode(String nodeId, CanvasPoint position) {
        CanvasNode before = requireNode(nodeId);
        CanvasNode after = before.movedTo(position);
        nodes.put(nodeId, after);
        fire(CanvasEventType.NODE_MOVED, nodeId, null, before, after, null);
        changed();
        return after;
    }

    public synchronized void removeNode(String nodeId) {
        CanvasNode removed = requireNode(nodeId);
        List<String> attached = edges.values().stream()
                .filter(edge -> edge.sourceNodeId().equals(nodeId) || edge.targetNodeId().equals(nodeId))
                .map(CanvasEdge::edgeId).toList();
        attached.forEach(this::removeEdge);
        nodes.remove(nodeId);
        if (Objects.equals(selectedNodeId, nodeId)) selectedNodeId = null;
        fire(CanvasEventType.NODE_REMOVED, nodeId, null, removed, null, null);
        changed();
    }

    public synchronized CanvasNode selectNode(String nodeId) {
        String before = selectedNodeId;
        if (nodeId != null) {
            CanvasNode node = requireNode(nodeId);
            if (!definition.nodeType(node.type()).selectable()) return node;
        }
        selectedNodeId = nodeId;
        if (!Objects.equals(before, nodeId)) {
            fire(CanvasEventType.NODE_SELECTED, nodeId, null, before, nodeId, null);
        }
        return nodeId == null ? null : nodes.get(nodeId);
    }

    public synchronized CanvasViewport pan(double dx, double dy) {
        CanvasViewport before = viewport;
        viewport = viewport.pan(dx, dy);
        fire(CanvasEventType.VIEWPORT_CHANGED, null, null, before, viewport, null);
        changed();
        return viewport;
    }

    public synchronized void setViewport(CanvasViewport value) {
        CanvasViewport before = viewport;
        viewport = Objects.requireNonNull(value);
        fire(CanvasEventType.VIEWPORT_CHANGED, null, null, before, viewport, null);
        changed();
    }

    public synchronized ConnectionResult validateConnection(String sourceNodeId, String sourcePortId,
                                                             String targetNodeId, String targetPortId) {
        CanvasNode source = nodes.get(sourceNodeId);
        CanvasNode target = nodes.get(targetNodeId);
        if (source == null || target == null) return ConnectionResult.reject(ConnectionCode.REJECT_MISSING_NODE, "source or target node does not exist");
        NodeDefinition sourceType = definition.nodeType(source.type());
        NodeDefinition targetType = definition.nodeType(target.type());
        CanvasPort sourcePort = sourceType.port(sourcePortId);
        CanvasPort targetPort = targetType.port(targetPortId);
        if (sourcePort == null || targetPort == null) return ConnectionResult.reject(ConnectionCode.REJECT_MISSING_PORT, "source or target port does not exist");
        if (sourcePort.direction() == PortDirection.INPUT || targetPort.direction() == PortDirection.OUTPUT) {
            return ConnectionResult.reject(ConnectionCode.REJECT_DIRECTION, "connection must flow OUTPUT/BIDIRECTIONAL to INPUT/BIDIRECTIONAL");
        }
        if (connectionCount(sourceNodeId, sourcePortId) >= sourcePort.maxConnections()
                || connectionCount(targetNodeId, targetPortId) >= targetPort.maxConnections()) {
            return ConnectionResult.reject(ConnectionCode.REJECT_MAX_CONNECTIONS, "port connection limit reached");
        }
        boolean duplicate = edges.values().stream().anyMatch(edge ->
                edge.sourceNodeId().equals(sourceNodeId) && edge.sourcePortId().equals(sourcePortId)
                        && edge.targetNodeId().equals(targetNodeId) && edge.targetPortId().equals(targetPortId));
        if (duplicate && !definition.allowDuplicateEdges()) {
            return ConnectionResult.reject(ConnectionCode.REJECT_DUPLICATE, "duplicate edge");
        }
        if (!definition.allowCycles() && createsCycle(sourceNodeId, targetNodeId)) {
            return ConnectionResult.reject(ConnectionCode.REJECT_CYCLE, "edge would create a cycle");
        }
        return definition.connectionPolicy().validate(source, sourcePort, target, targetPort);
    }

    public synchronized CanvasEdge connect(String sourceNodeId, String sourcePortId,
                                           String targetNodeId, String targetPortId) {
        return connect(UUID.randomUUID().toString(), sourceNodeId, sourcePortId,
                targetNodeId, targetPortId, EdgeStyle.standard("default"));
    }

    public synchronized CanvasEdge connect(String edgeId, String sourceNodeId, String sourcePortId,
                                           String targetNodeId, String targetPortId, EdgeStyle style) {
        if (edges.containsKey(edgeId)) throw new IllegalArgumentException("duplicate edge id: " + edgeId);
        ConnectionResult result = validateConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        if (!result.allowed()) {
            fire(CanvasEventType.CONNECTION_REJECTED, targetNodeId, null, null, null, result);
            throw new GraphValidationException(result);
        }
        CanvasEdge edge = new CanvasEdge(edgeId, sourceNodeId, sourcePortId, targetNodeId, targetPortId, style);
        edges.put(edgeId, edge);
        fire(CanvasEventType.CONNECTION_CREATED, null, edgeId, null, edge, result);
        changed();
        return edge;
    }

    public synchronized void removeEdge(String edgeId) {
        CanvasEdge edge = edges.remove(edgeId);
        if (edge == null) throw new IllegalArgumentException("missing edge: " + edgeId);
        fire(CanvasEventType.CONNECTION_REMOVED, null, edgeId, edge, null, null);
        changed();
    }

    public synchronized CanvasSnapshot snapshot() {
        List<CanvasSnapshot.NodeState> nodeStates = nodes.values().stream().map(node ->
                new CanvasSnapshot.NodeState(node.nodeId(), node.type(), node.position().x(),
                        node.position().y(), node.metadata(), node.enabled())).toList();
        List<CanvasSnapshot.EdgeState> edgeStates = edges.values().stream().map(edge ->
                new CanvasSnapshot.EdgeState(edge.edgeId(), edge.sourceNodeId(), edge.sourcePortId(),
                        edge.targetNodeId(), edge.targetPortId(), edge.style())).toList();
        return new CanvasSnapshot(definition.canvasId(), viewport, nodeStates, edgeStates, selectedNodeId);
    }

    public synchronized void restore(CanvasSnapshot snapshot) {
        if (!definition.canvasId().equals(snapshot.canvasId())) throw new IllegalArgumentException("snapshot canvas id mismatch");
        Map<String, CanvasNode> restoredNodes = new LinkedHashMap<>();
        for (CanvasSnapshot.NodeState state : snapshot.nodes()) {
            if (definition.nodeType(state.type()) == null) throw new IllegalArgumentException("snapshot has unknown node type: " + state.type());
            CanvasNode node = new CanvasNode(state.nodeId(), state.type(), CanvasPoint.of(state.x(), state.y()), state.metadata(), state.enabled());
            if (restoredNodes.putIfAbsent(node.nodeId(), node) != null) throw new IllegalArgumentException("snapshot duplicate node id: " + node.nodeId());
        }
        Map<String, CanvasEdge> restoredEdges = new LinkedHashMap<>();
        nodes.clear(); nodes.putAll(restoredNodes);
        edges.clear();
        try {
            for (CanvasSnapshot.EdgeState state : snapshot.edges()) {
                ConnectionResult valid = validateConnection(state.sourceNodeId(), state.sourcePortId(), state.targetNodeId(), state.targetPortId());
                if (!valid.allowed()) throw new GraphValidationException(valid);
                CanvasEdge edge = new CanvasEdge(state.edgeId(), state.sourceNodeId(), state.sourcePortId(), state.targetNodeId(), state.targetPortId(), state.style());
                if (restoredEdges.putIfAbsent(edge.edgeId(), edge) != null) throw new IllegalArgumentException("snapshot duplicate edge id: " + edge.edgeId());
                edges.put(edge.edgeId(), edge);
            }
        } catch (RuntimeException error) {
            nodes.clear(); edges.clear(); viewport = CanvasViewport.ORIGIN; selectedNodeId = null;
            throw error;
        }
        viewport = snapshot.viewport();
        selectedNodeId = snapshot.selectedNodeId();
        if (selectedNodeId != null) requireNode(selectedNodeId);
    }

    public void publish(CanvasEventType type, String nodeId, String edgeId, Object before, Object after,
                        ConnectionResult result) {
        fire(type, nodeId, edgeId, before, after, result);
    }

    private CanvasNode requireNode(String id) {
        CanvasNode node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("missing node: " + id);
        return node;
    }

    private long connectionCount(String nodeId, String portId) {
        return edges.values().stream().filter(edge ->
                (edge.sourceNodeId().equals(nodeId) && edge.sourcePortId().equals(portId))
                        || (edge.targetNodeId().equals(nodeId) && edge.targetPortId().equals(portId))).count();
    }

    private boolean createsCycle(String source, String target) {
        if (source.equals(target)) return true;
        Deque<String> pending = new ArrayDeque<>();
        java.util.HashSet<String> visited = new java.util.HashSet<>();
        pending.push(target);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (!visited.add(current)) continue;
            if (current.equals(source)) return true;
            edges.values().stream().filter(edge -> edge.sourceNodeId().equals(current))
                    .map(CanvasEdge::targetNodeId).forEach(pending::push);
        }
        return false;
    }

    private void changed() { fire(CanvasEventType.CANVAS_CHANGED, null, null, null, snapshot(), null); }
    private void fire(CanvasEventType type, String nodeId, String edgeId, Object before, Object after,
                      ConnectionResult result) {
        CanvasEvent event = new CanvasEvent(type, definition.canvasId(), nodeId, edgeId, before, after, result);
        for (CanvasListener listener : definition.listeners()) listener.onEvent(event);
    }
}
