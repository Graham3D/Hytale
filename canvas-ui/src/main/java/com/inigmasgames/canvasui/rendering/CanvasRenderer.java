package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;

/** Backend-neutral geometry calculations shared by current and future UI backends. */
public final class CanvasRenderer {
    private final EdgeRenderer edgeRenderer;

    public CanvasRenderer(EdgeRenderer edgeRenderer) { this.edgeRenderer = edgeRenderer; }

    public CanvasPoint nodeScreenPoint(Canvas canvas, CanvasNode node) {
        return canvas.viewport().toScreen(node.position());
    }

    public CanvasPoint portScreenPoint(Canvas canvas, String nodeId, String portId) {
        CanvasNode node = canvas.node(nodeId);
        if (node == null) throw new IllegalArgumentException("missing node: " + nodeId);
        CanvasPort port = canvas.definition().nodeType(node.type()).port(portId);
        if (port == null) throw new IllegalArgumentException("missing port: " + portId);
        return canvas.viewport().toScreen(node.position().add(port.anchorPosition().x(), port.anchorPosition().y()));
    }

    public java.util.List<EdgeSegment> edgeSegments(Canvas canvas, CanvasEdge edge) {
        return edgeRenderer.render(new EdgeRenderContext(
                portScreenPoint(canvas, edge.sourceNodeId(), edge.sourcePortId()),
                portScreenPoint(canvas, edge.targetNodeId(), edge.targetPortId()), edge.style(), false, true));
    }

    public java.util.List<EdgeSegment> previewSegments(CanvasPoint source, CanvasPoint target, boolean valid) {
        return edgeRenderer.render(new EdgeRenderContext(source, target,
                com.inigmasgames.canvasui.api.EdgeStyle.standard("preview"), true, valid));
    }
}
