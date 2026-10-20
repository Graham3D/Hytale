package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.logger.HytaleLogger;
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
import com.inigmasgames.canvasui.api.CanvasSearch;
import com.inigmasgames.canvasui.api.CanvasRenderBackend;
import com.inigmasgames.canvasui.runtime.CanvasSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Current Hytale CustomUI adapter. No graph rules live here. */
public final class HytaleCustomUiBackend implements CanvasRenderBackend {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final CanvasSession session;
    private final CanvasRenderer renderer;
    private final Map<String, String> nodeSelectors = new LinkedHashMap<>();
    private final Map<String, List<EdgePart>> edgeSelectors = new LinkedHashMap<>();
    private CanvasPage page;
    private Runnable pageCloser;
    private CanvasPoint previewSource;
    private CanvasPoint previewTarget;
    private boolean previewValid;
    private String hoveredNodeId;
    private String invalidNodeId;
    private String pendingDisconnectEdgeId;

    public HytaleCustomUiBackend(CanvasSession session) {
        this.session = session;
        this.renderer = new CanvasRenderer(session.canvas().definition().edgeRenderer());
    }
    @Override public String id() { return "hytale-customui-renderer"; }
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

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.append("CanvasUIPage.ui");
        commands.set("#CanvasRevision.TextSpans", Message.raw(CanvasUI.REVISION));
        appendTopology(commands, events);
        bind(events, CustomUIEventBindingType.ValueChanged, "#SearchInput", "search", "", "#SearchInput.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ZoomSlider",
                new EventData().append("Event", "ValueChanged").append("TargetKind", "zoom-slider")
                        .append("TargetId", "").append("@ZoomValue", "#ZoomSlider.Value"), false);
        bind(events, CustomUIEventBindingType.Activating, "#DisconnectYes", "dialog", "yes", "");
        bind(events, CustomUIEventBindingType.Activating, "#DisconnectNo", "dialog", "no", "");
        session.recordPageRebuild();
    }

    @Override
    public void topologyChanged() {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.clear("#CanvasContents");
        appendTopology(commands, events);
        page.flush(commands, events);
        session.recordUiUpdate(count(commands));
    }

    @Override
    public void updateNodeAndEdges(String nodeId) {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        Canvas canvas = session.canvas();
        CanvasNode node = canvas.node(nodeId);
        String selector = nodeSelectors.get(nodeId);
        if (node != null && selector != null) {
            CanvasPoint screen = renderer.nodeScreenPoint(canvas, node);
            double zoom = canvas.viewport().zoom();
            commands.setObject(selector + ".Anchor", anchor(screen, scaled(canvas.definition().nodeType(node.type()).width(), zoom),
                    scaled(canvas.definition().nodeType(node.type()).height(), zoom)));
        }
        for (CanvasEdge edge : canvas.edges()) {
            if (edge.sourceNodeId().equals(nodeId) || edge.targetNodeId().equals(nodeId)) updateEdge(commands, edge);
        }
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    @Override public void updateViewport() {
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        Canvas canvas = session.canvas();
        for (CanvasNode node : canvas.nodes()) {
            String selector = nodeSelectors.get(node.nodeId());
            if (selector == null) continue;
            NodeDefinition type = canvas.definition().nodeType(node.type());
            double zoom = canvas.viewport().zoom();
            commands.setObject(selector + ".Anchor", anchor(renderer.nodeScreenPoint(canvas, node), scaled(type.width(), zoom), scaled(type.height(), zoom)));
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
            commands.setObject("#CanvasPreview" + i + ".Anchor", value);
        }
        commands.set("#CanvasStatus.TextSpans", Message.raw(valid ? "Connection target allowed" : "Connection preview — target required/invalid"));
        page.flush(commands);
        session.recordUiUpdate(count(commands));
    }

    public void clearPreview(String status) {
        previewSource = null; previewTarget = null;
        if (page == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        for (int i = 0; i < 16; i++) commands.setObject("#CanvasPreview" + i + ".Anchor", anchor(CanvasPoint.of(-100, -100), 2, 2));
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

    private void appendTopology(UICommandBuilder commands, UIEventBuilder events) {
        nodeSelectors.clear(); edgeSelectors.clear();
        Canvas canvas = session.canvas();
        String query = session.searchQuery();
        int matches = 0;
        int childIndex = 0;
        int edgeIndex = 0;
        for (CanvasEdge edge : canvas.edges()) {
            List<EdgePart> selectors = new ArrayList<>();
            List<EdgeSegment> segments = renderer.edgeSegments(canvas, edge);
            for (int i = 0; i < segments.size(); i++) {
                String lineSelector = "#CanvasContents[" + childIndex++ + "]";
                commands.append("#CanvasContents", "CanvasEdge.ui");
                String hitSelector = "#CanvasContents[" + childIndex++ + "]";
                commands.append("#CanvasContents", "CanvasEdgeHit.ui");
                selectors.add(new EdgePart(lineSelector, hitSelector));
                setEdgeGeometry(commands, lineSelector, hitSelector, segments.get(i), edge.style().thickness());
                commands.setObject(lineSelector + ".Background", solid(safeColor(edge.style().color())));
                bind(events, CustomUIEventBindingType.RightClicking, hitSelector, "edge", edge.edgeId(), "");
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
            boolean match = CanvasSearch.matches(query, visual);
            if (match) matches++;
            CanvasPoint screen = renderer.nodeScreenPoint(canvas, node);
            commands.append("#CanvasContents", "CanvasNode.ui");
            double zoom = canvas.viewport().zoom();
            int width = scaled(type.width(), zoom); int height = scaled(type.height(), zoom);
            commands.setObject(selector + ".Anchor", anchor(screen, width, height));
            String background = query.isBlank() ? safeColor(visual.backgroundColor())
                    : match ? "#2f8f6bff" : "#18212a88";
            commands.setObject(selector + ".Background", solid(background));
            commands.setObject(selector + " #Title.Anchor", titleAnchor(width));
            commands.set(selector + " #Title.TextSpans", Message.raw(text(visual.title())));
            commands.setObject(selector + " #Subtitle.Anchor", subtitleAnchor(width));
            commands.set(selector + " #Subtitle.TextSpans", Message.raw(text(visual.subtitle())));
            int portIndex = 0;
            for (CanvasPort port : type.ports().values()) {
                String color = switch (port.direction()) { case INPUT -> "#6cb9ff"; case OUTPUT -> "#efb65d"; case BIDIRECTIONAL -> "#bf83ff"; };
                String portSelector = selector + " #Ports[" + portIndex++ + "]";
                commands.append(selector + " #Ports", "CanvasPort.ui");
                commands.setObject(portSelector + ".Anchor", anchor(
                        CanvasPoint.of(port.anchorPosition().x() * zoom - 7, port.anchorPosition().y() * zoom - 7), 14, 14));
                commands.setObject(portSelector + ".Background", solid(color));
            }
        }
        commands.set("#ZoomValue.TextSpans", Message.raw(String.format(java.util.Locale.ROOT, "ZOOM %.2f", canvas.viewport().zoom())));
        commands.set("#ZoomSlider.Value", canvas.viewport().zoom());
        commands.set("#SearchCount.TextSpans", Message.raw(query.isBlank() ? canvas.nodes().size() + " nodes"
                : matches + " / " + canvas.nodes().size() + " matches"));
    }

    private void updateEdge(UICommandBuilder commands, CanvasEdge edge) {
        List<EdgePart> selectors = edgeSelectors.get(edge.edgeId());
        if (selectors == null) return;
        List<EdgeSegment> segments = renderer.edgeSegments(session.canvas(), edge);
        for (int i = 0; i < Math.min(selectors.size(), segments.size()); i++) {
            EdgePart part = selectors.get(i);
            setEdgeGeometry(commands, part.lineSelector(), part.hitSelector(), segments.get(i), edge.style().thickness());
        }
    }

    public void handleEvent(String event, String targetKind, String targetId, String value) {
        LOGGER.atInfo().log("CANVASUI_CUSTOM_EVENT revision=%s canvas=%s event=%s targetKind=%s targetId=%s value=%s",
                CanvasUI.REVISION, session.canvas().definition().canvasId(), event, targetKind, targetId, value);
        if ("ValueChanged".equals(event) && "search".equals(targetKind)) {
            session.search(value); return;
        }
        if ("ValueChanged".equals(event) && "zoom-slider".equals(targetKind)) {
            try { session.zoom(Double.parseDouble(value), CanvasPoint.of(960, 540)); }
            catch (NumberFormatException error) { status("Invalid zoom value from client: " + value); }
            return;
        }
        if ("RightClicking".equals(event) && "edge".equals(targetKind) && session.canvas().edge(targetId) != null) {
            pendingDisconnectEdgeId = targetId;
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#DisconnectModal.Visible", true);
            commands.set("#DisconnectEdge.TextSpans", Message.raw(targetId));
            page.flush(commands); session.recordUiUpdate(count(commands)); return;
        }
        if ("Activating".equals(event) && "dialog".equals(targetKind)) {
            if ("yes".equals(targetId) && pendingDisconnectEdgeId != null
                    && session.canvas().edge(pendingDisconnectEdgeId) != null) {
                String removed = pendingDisconnectEdgeId; pendingDisconnectEdgeId = null;
                session.removeEdge(removed);
                UICommandBuilder commands = new UICommandBuilder();
                commands.set("#DisconnectModal.Visible", false);
                commands.set("#CanvasStatus.TextSpans", Message.raw("Disconnected " + removed));
                page.flush(commands); session.recordUiUpdate(count(commands)); return;
            }
            pendingDisconnectEdgeId = null;
            UICommandBuilder commands = new UICommandBuilder(); commands.set("#DisconnectModal.Visible", false);
            page.flush(commands); session.recordUiUpdate(count(commands));
        }
    }

    public void recordRawEvent(String rawData) {
        LOGGER.atInfo().log("CANVASUI_CUSTOM_EVENT_RAW revision=%s canvas=%s payload=%s",
                CanvasUI.REVISION, session.canvas().definition().canvasId(), rawData);
    }

    private static void bind(UIEventBuilder events, CustomUIEventBindingType type, String selector,
                             String kind, String id, String dynamicValue) {
        EventData data = new EventData().append("Event", type.name()).append("TargetKind", kind)
                .append("TargetId", id);
        if (!dynamicValue.isBlank()) data.append("@Value", dynamicValue);
        events.addEventBinding(type, selector, data, false);
    }

    private static void setEdgeGeometry(UICommandBuilder commands, String lineSelector, String hitSelector,
                                        EdgeSegment segment, int thickness) {
        int hit = Math.max(14, thickness + 10);
        int x1 = (int) Math.round(segment.start().x()); int y1 = (int) Math.round(segment.start().y());
        int x2 = (int) Math.round(segment.end().x()); int y2 = (int) Math.round(segment.end().y());
        boolean horizontal = Math.abs(x2 - x1) >= Math.abs(y2 - y1);
        if (horizontal) {
            int width = Math.max(2, Math.abs(x2 - x1));
            commands.setObject(lineSelector + ".Anchor", anchor(CanvasPoint.of(Math.min(x1, x2), y1 - thickness / 2.0), width, thickness));
            commands.setObject(hitSelector + ".Anchor", anchor(CanvasPoint.of(Math.min(x1, x2), y1 - hit / 2.0), width, hit));
        } else {
            int height = Math.max(2, Math.abs(y2 - y1));
            commands.setObject(lineSelector + ".Anchor", anchor(CanvasPoint.of(x1 - thickness / 2.0, Math.min(y1, y2)), thickness, height));
            commands.setObject(hitSelector + ".Anchor", anchor(CanvasPoint.of(x1 - hit / 2.0, Math.min(y1, y2)), hit, height));
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
    private static int scaled(int value, double zoom) { return Math.max(1, (int) Math.round(value * zoom)); }
    private static long count(UICommandBuilder commands) { return commands.getCommands().length; }
    private record EdgePart(String lineSelector, String hitSelector) { }
}
