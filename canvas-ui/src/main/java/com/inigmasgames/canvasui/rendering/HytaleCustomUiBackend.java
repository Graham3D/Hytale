package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.api.NodeRenderContext;
import com.inigmasgames.canvasui.api.NodeVisual;
import com.inigmasgames.canvasui.api.NodeVisualState;
import com.inigmasgames.canvasui.runtime.CanvasSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Current Hytale CustomUI adapter. No graph rules live here. */
public final class HytaleCustomUiBackend {
    private final CanvasSession session;
    private final CanvasRenderer renderer;
    private final Map<String, String> nodeSelectors = new LinkedHashMap<>();
    private final Map<String, List<String>> edgeSelectors = new LinkedHashMap<>();
    private CanvasPage page;
    private Runnable pageCloser;
    private CanvasPoint previewSource;
    private CanvasPoint previewTarget;
    private boolean previewValid;
    private String hoveredNodeId;
    private String invalidNodeId;

    public HytaleCustomUiBackend(CanvasSession session) {
        this.session = session;
        this.renderer = new CanvasRenderer(session.canvas().definition().edgeRenderer());
    }
    public void attach(CanvasPage page, Runnable pageCloser) { this.page = page; this.pageCloser = pageCloser; }
    public void close(boolean requestPageClose) {
        Runnable closer = pageCloser;
        page = null; pageCloser = null; nodeSelectors.clear(); edgeSelectors.clear(); previewSource = null;
        previewTarget = null; hoveredNodeId = null; invalidNodeId = null;
        if (requestPageClose && closer != null) closer.run();
    }

    public void pointerTarget(String nodeId, boolean invalid) {
        String nextHover = invalid ? null : nodeId;
        String nextInvalid = invalid ? nodeId : null;
        if (java.util.Objects.equals(nextHover, hoveredNodeId) && java.util.Objects.equals(nextInvalid, invalidNodeId)) return;
        hoveredNodeId = nextHover; invalidNodeId = nextInvalid; topologyChanged();
    }

    public void clearPointerTarget() { pointerTarget(null, false); }

    void build(UICommandBuilder commands) {
        commands.append("CanvasUIPage.ui");
        commands.set("#CanvasRevision.TextSpans", Message.raw(CanvasUI.REVISION));
        appendTopology(commands);
        session.recordPageRebuild();
    }

    public void topologyChanged() {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        commands.clear("#CanvasContents");
        appendTopology(commands);
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void updateNodeAndEdges(String nodeId) {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        Canvas canvas = session.canvas();
        CanvasNode node = canvas.node(nodeId);
        String selector = nodeSelectors.get(nodeId);
        if (node != null && selector != null) {
            CanvasPoint screen = renderer.nodeScreenPoint(canvas, node);
            commands.set(selector + ".Anchor", Value.of(anchor(screen, canvas.definition().nodeType(node.type()).width(),
                    canvas.definition().nodeType(node.type()).height())));
        }
        for (CanvasEdge edge : canvas.edges()) {
            if (edge.sourceNodeId().equals(nodeId) || edge.targetNodeId().equals(nodeId)) updateEdge(commands, edge);
        }
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void updateViewport() {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        Canvas canvas = session.canvas();
        for (CanvasNode node : canvas.nodes()) {
            String selector = nodeSelectors.get(node.nodeId());
            if (selector == null) continue;
            NodeDefinition type = canvas.definition().nodeType(node.type());
            commands.set(selector + ".Anchor", Value.of(anchor(renderer.nodeScreenPoint(canvas, node), type.width(), type.height())));
        }
        for (CanvasEdge edge : canvas.edges()) updateEdge(commands, edge);
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void updatePreview(CanvasPoint source, CanvasPoint target, boolean valid) {
        previewSource = source; previewTarget = target; previewValid = valid;
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        List<EdgeSegment> segments = renderer.previewSegments(source, target, valid);
        for (int i = 0; i < 16; i++) {
            Anchor value = i < segments.size() ? segmentAnchor(segments.get(i), 3)
                    : anchor(CanvasPoint.of(-100, -100), 2, 2);
            commands.set("#CanvasPreview" + i + ".Anchor", Value.of(value));
        }
        commands.set("#CanvasStatus.TextSpans", Message.raw(valid ? "Connection target allowed" : "Connection preview — target required/invalid"));
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void clearPreview(String status) {
        previewSource = null; previewTarget = null;
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        for (int i = 0; i < 16; i++) commands.set("#CanvasPreview" + i + ".Anchor", Value.of(anchor(CanvasPoint.of(-100, -100), 2, 2)));
        commands.set("#CanvasStatus.TextSpans", Message.raw(status));
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void status(String value) {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#CanvasStatus.TextSpans", Message.raw(value));
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    private void appendTopology(UICommandBuilder commands) {
        nodeSelectors.clear(); edgeSelectors.clear();
        Canvas canvas = session.canvas();
        int childIndex = 0;
        int edgeIndex = 0;
        for (CanvasEdge edge : canvas.edges()) {
            List<String> selectors = new ArrayList<>();
            List<EdgeSegment> segments = renderer.edgeSegments(canvas, edge);
            for (int i = 0; i < segments.size(); i++) {
                String selector = "#CanvasContents[" + childIndex++ + "]";
                selectors.add(selector);
                commands.append("#CanvasContents", "CanvasEdge.ui");
                commands.set(selector + ".Anchor", Value.of(segmentAnchor(segments.get(i), edge.style().thickness())));
                commands.setObject(selector + ".Background", solid(safeColor(edge.style().color())));
            }
            edgeSelectors.put(edge.edgeId(), selectors);
            edgeIndex++;
        }
        for (CanvasNode node : canvas.nodes()) {
            NodeDefinition type = canvas.definition().nodeType(node.type());
            String selector = "#CanvasContents[" + childIndex++ + "]";
            nodeSelectors.put(node.nodeId(), selector);
            NodeVisualState state = node.nodeId().equals(invalidNodeId) ? NodeVisualState.INVALID_CONNECTION_TARGET
                    : !node.enabled() ? NodeVisualState.DISABLED
                    : node.nodeId().equals(canvas.selectedNodeId()) ? NodeVisualState.SELECTED
                    : node.nodeId().equals(hoveredNodeId) ? NodeVisualState.HOVERED : NodeVisualState.NORMAL;
            NodeVisual visual = type.renderer().render(new NodeRenderContext(node, state));
            CanvasPoint screen = renderer.nodeScreenPoint(canvas, node);
            commands.append("#CanvasContents", "CanvasNode.ui");
            commands.set(selector + ".Anchor", Value.of(anchor(screen, type.width(), type.height())));
            commands.setObject(selector + ".Background", solid(safeColor(visual.backgroundColor())));
            commands.set(selector + " #Title.Anchor", Value.of(titleAnchor(type.width())));
            commands.set(selector + " #Title.TextSpans", Message.raw(text(visual.title())));
            commands.set(selector + " #Subtitle.Anchor", Value.of(subtitleAnchor(type.width())));
            commands.set(selector + " #Subtitle.TextSpans", Message.raw(text(visual.subtitle())));
            int portIndex = 0;
            for (CanvasPort port : type.ports().values()) {
                String color = switch (port.direction()) { case INPUT -> "#6cb9ff"; case OUTPUT -> "#efb65d"; case BIDIRECTIONAL -> "#bf83ff"; };
                String portSelector = selector + " #Ports[" + portIndex++ + "]";
                commands.append(selector + " #Ports", "CanvasPort.ui");
                commands.set(portSelector + ".Anchor", Value.of(anchor(
                        CanvasPoint.of(port.anchorPosition().x() - 7, port.anchorPosition().y() - 7), 14, 14)));
                commands.setObject(portSelector + ".Background", solid(color));
            }
        }
    }

    private void updateEdge(UICommandBuilder commands, CanvasEdge edge) {
        List<String> selectors = edgeSelectors.get(edge.edgeId());
        if (selectors == null) return;
        List<EdgeSegment> segments = renderer.edgeSegments(session.canvas(), edge);
        for (int i = 0; i < Math.min(selectors.size(), segments.size()); i++) {
            commands.set(selectors.get(i) + ".Anchor", Value.of(segmentAnchor(segments.get(i), edge.style().thickness())));
        }
    }

    private static Anchor segmentAnchor(EdgeSegment segment, int thickness) {
        int x1 = (int) Math.round(segment.start().x()); int y1 = (int) Math.round(segment.start().y());
        int x2 = (int) Math.round(segment.end().x()); int y2 = (int) Math.round(segment.end().y());
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) return anchor(CanvasPoint.of(Math.min(x1, x2), y1 - thickness / 2.0), Math.max(2, Math.abs(x2 - x1)), thickness);
        return anchor(CanvasPoint.of(x1 - thickness / 2.0, Math.min(y1, y2)), thickness, Math.max(2, Math.abs(y2 - y1)));
    }

    private static Anchor anchor(CanvasPoint point, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of((int) Math.round(point.x()))); anchor.setTop(Value.of((int) Math.round(point.y())));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }

    private static Anchor titleAnchor(int width) { return anchor(CanvasPoint.of(8, 8), Math.max(4, width - 16), 25); }
    private static Anchor subtitleAnchor(int width) { return anchor(CanvasPoint.of(8, 35), Math.max(4, width - 16), 20); }
    private static PatchStyle solid(String color) { return new PatchStyle().setColor(Value.of(color)); }

    private static String safeColor(String value) { return value != null && value.matches("#[0-9a-fA-F]{6,8}") ? value : "#24354aee"; }
    private static String text(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
    private static long count(UICommandBuilder commands) { return commands.getCommands().length; }
}
