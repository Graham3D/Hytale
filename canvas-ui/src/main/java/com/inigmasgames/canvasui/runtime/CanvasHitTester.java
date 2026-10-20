package com.inigmasgames.canvasui.runtime;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.NodeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CanvasHitTester {
    public Hit hit(Canvas canvas, CanvasPoint screenPoint) {
        CanvasPoint point = canvas.viewport().toCanvas(screenPoint);
        List<CanvasNode> reverse = new ArrayList<>(canvas.nodes());
        Collections.reverse(reverse);
        for (CanvasNode node : reverse) {
            NodeDefinition type = canvas.definition().nodeType(node.type());
            for (CanvasPort port : type.ports().values()) {
                CanvasPoint anchor = node.position().add(port.anchorPosition().x(), port.anchorPosition().y());
                if (distance(point, anchor) <= 13) return new Hit(node.nodeId(), port.portId(), true);
            }
            if (point.x() >= node.position().x() && point.y() >= node.position().y()
                    && point.x() <= node.position().x() + type.width()
                    && point.y() <= node.position().y() + type.height()) {
                return new Hit(node.nodeId(), null, false);
            }
        }
        return Hit.BACKGROUND;
    }

    private static double distance(CanvasPoint a, CanvasPoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    public record Hit(String nodeId, String portId, boolean port) {
        public static final Hit BACKGROUND = new Hit(null, null, false);
        public boolean background() { return nodeId == null; }
    }
}
