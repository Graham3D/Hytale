package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.NodeDefinition;

import java.util.ArrayList;
import java.util.List;

/** Immutable presentation projection shared by passive HUD and native Search Mode. */
public record SkillTreeViewModel(String title, String subtitle, CursorCanvasEditor.LibraryKind libraryKind,
                                 String query, List<CursorCanvasEditor.LibraryEntry> entries,
                                 int scrollOffset, int maximumScrollOffset, int totalMatches,
                                 List<Node> nodes, List<Link> links, String status, boolean searchMode) {
    public SkillTreeViewModel {
        title = title == null || title.isBlank() ? "SKILL TREE" : title;
        subtitle = subtitle == null ? "SHAPE YOUR JOURNEY" : subtitle;
        query = query == null ? "" : query;
        entries = List.copyOf(entries);
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
        status = status == null ? "" : status;
    }

    public static SkillTreeViewModel project(String title, Canvas canvas,
                                             CursorCanvasEditor.LibraryKind kind, String query,
                                             LibraryBrowser.Window window, String selectedLinkId,
                                             String status, boolean searchMode) {
        List<Node> nodes = new ArrayList<>();
        for (CanvasNode node : canvas.nodes()) {
            NodeDefinition definition = canvas.definition().nodeType(node.type());
            CanvasPoint point = canvas.viewport().toScreen(node.position());
            List<Port> ports = new ArrayList<>();
            for (CanvasPort port : definition.ports().values())
                ports.add(new Port(port.portId(), point.add(port.anchorPosition().x(), port.anchorPosition().y())));
            nodes.add(new Node(node.nodeId(), node.type(), point, definition.width(), definition.height(),
                    node.metadata().getOrDefault("label", node.nodeId()),
                    node.metadata().getOrDefault("subtitle", node.type()),
                    node.metadata().getOrDefault("icon", ""),
                    Boolean.parseBoolean(node.metadata().getOrDefault("occupied", "false")),
                    node.nodeId().equals(canvas.selectedNodeId()), ports));
        }
        TreeLinkGeometry geometry = new TreeLinkGeometry();
        List<Link> links = new ArrayList<>();
        for (CanvasEdge edge : canvas.edges())
            links.add(new Link(edge.edgeId(), geometry.route(canvas, edge), edge.edgeId().equals(selectedLinkId)));
        return new SkillTreeViewModel(title, "SHAPE YOUR JOURNEY", kind, query, window.entries(),
                window.offset(), window.maximumOffset(), window.totalMatches(), nodes, links, status, searchMode);
    }

    public record Node(String nodeId, String type, CanvasPoint point, int width, int height,
                       String label, String subtitle, String icon, boolean occupied,
                       boolean selected, List<Port> ports) {
        public Node { ports = List.copyOf(ports); }
    }
    public record Port(String portId, CanvasPoint point) { }
    public record Link(String edgeId, List<TreeLinkGeometry.Segment> segments, boolean selected) {
        public Link { segments = List.copyOf(segments); }
    }
}
