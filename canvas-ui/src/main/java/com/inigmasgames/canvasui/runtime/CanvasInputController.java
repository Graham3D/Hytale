package com.inigmasgames.canvasui.runtime;

import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseMotionEvent;
import com.hypixel.hytale.protocol.Vector2i;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEventType;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.ConnectionCode;
import com.inigmasgames.canvasui.api.ConnectionResult;
import com.inigmasgames.canvasui.api.GraphValidationException;
import com.inigmasgames.canvasui.api.PanGesture;
import com.inigmasgames.canvasui.api.PortDirection;
import com.inigmasgames.canvasui.rendering.HytaleCustomUiBackend;

final class CanvasInputController {
    private final CanvasSession session;
    private final Canvas canvas;
    private final HytaleCustomUiBackend backend;
    private final CanvasHitTester hitTester = new CanvasHitTester();
    private final CanvasDragController drag = new CanvasDragController();
    private final CanvasPanController pan = new CanvasPanController();
    private CanvasPoint pointer = CanvasPoint.of(0, 0);
    private String connectionNode;
    private String connectionPort;
    private CanvasHitTester.Hit candidate = CanvasHitTester.Hit.BACKGROUND;
    private ConnectionResult candidateResult = ConnectionResult.reject(ConnectionCode.REJECT_CUSTOM, "no target");

    CanvasInputController(CanvasSession session, HytaleCustomUiBackend backend) {
        this.session = session; this.canvas = session.canvas(); this.backend = backend;
    }

    void button(PlayerMouseButtonEvent event) {
        long started = System.nanoTime();
        try {
            updatePointer(event.getScreenPoint());
            MouseButtonEvent button = event.getMouseButton();
            if (button == null) return;
            if (button.state == MouseButtonState.Pressed) press(button.mouseButtonType);
            else release(button.mouseButtonType);
        } finally { session.recordPointer(System.nanoTime() - started); }
    }

    void motion(PlayerMouseMotionEvent event) {
        long started = System.nanoTime();
        try {
            MouseMotionEvent motion = event.getMouseMotion();
            Vector2i delta = motion == null ? null : motion.relativeMotion;
            if (event.getScreenPoint() != null) updatePointer(event.getScreenPoint());
            else if (delta != null) pointer = pointer.add(delta.x, delta.y);
            if (drag.active()) {
                CanvasPoint next = drag.update(pointer, canvas.viewport());
                if (drag.thresholdPassed()) {
                    canvas.moveNode(drag.nodeId(), next);
                    if (session.renderDue(false)) backend.updateNodeAndEdges(drag.nodeId());
                }
            } else if (pan.active() && delta != null) {
                canvas.setViewport(pan.update(canvas.viewport(), delta.x, delta.y));
                if (session.renderDue(false)) backend.updateViewport();
            } else if (connectionNode != null) {
                candidate = hitTester.hit(canvas, pointer);
                candidateResult = validateCandidate(candidate);
                backend.pointerTarget(candidate.nodeId(), !candidateResult.allowed());
                if (session.renderDue(false)) backend.updatePreview(sourceScreenPoint(), pointer, candidateResult.allowed());
            } else {
                CanvasHitTester.Hit hover = hitTester.hit(canvas, pointer);
                backend.pointerTarget(hover.nodeId(), false);
            }
        } finally { session.recordPointer(System.nanoTime() - started); }
    }

    private void press(MouseButtonType button) {
        CanvasHitTester.Hit hit = hitTester.hit(canvas, pointer);
        if (button == MouseButtonType.Left && hit.port()) {
            CanvasNode node = canvas.node(hit.nodeId());
            CanvasPort port = canvas.definition().nodeType(node.type()).port(hit.portId());
            if (port.direction() != PortDirection.INPUT) {
                connectionNode = hit.nodeId(); connectionPort = hit.portId();
                canvas.publish(CanvasEventType.CONNECTION_PREVIEW_STARTED, connectionNode, null, null, connectionPort, null);
                backend.updatePreview(sourceScreenPoint(), pointer, false);
                return;
            }
        }
        if (button == MouseButtonType.Left && !hit.background()) {
            CanvasNode node = canvas.node(hit.nodeId());
            canvas.selectNode(node.nodeId()); backend.topologyChanged();
            if (node.enabled() && canvas.definition().nodeType(node.type()).draggable()) {
                drag.begin(node, pointer, canvas.viewport());
                canvas.publish(CanvasEventType.DRAG_STARTED, node.nodeId(), null, node.position(), null, null);
            }
            return;
        }
        if (hit.background()) {
            canvas.selectNode(null); backend.topologyChanged();
            boolean desired = canvas.definition().panGesture() == PanGesture.LEFT_BACKGROUND
                    ? button == MouseButtonType.Left : button == MouseButtonType.Middle;
            if (canvas.definition().pannable() && desired) pan.begin();
        }
    }

    private void release(MouseButtonType button) {
        if (drag.active() && button == MouseButtonType.Left) {
            String nodeId = drag.nodeId(); boolean moved = drag.thresholdPassed();
            drag.end(); backend.updateNodeAndEdges(nodeId); session.persist();
            canvas.publish(CanvasEventType.DRAG_ENDED, nodeId, null, moved, canvas.node(nodeId).position(), null);
        }
        if (pan.active() && (button == MouseButtonType.Middle || button == MouseButtonType.Left)) {
            pan.end(); backend.updateViewport(); session.persist();
        }
        if (connectionNode != null && button == MouseButtonType.Left) {
            candidate = hitTester.hit(canvas, pointer);
            candidateResult = validateCandidate(candidate);
            if (candidateResult.allowed()) {
                try {
                    canvas.connect(connectionNode, connectionPort, candidate.nodeId(), candidate.portId());
                    backend.topologyChanged(); session.persist(); backend.clearPreview("Connection created");
                } catch (GraphValidationException rejected) {
                    backend.clearPreview("REJECTED: " + rejected.result().reason());
                }
            } else {
                canvas.publish(CanvasEventType.CONNECTION_REJECTED, candidate.nodeId(), null,
                        connectionNode + ":" + connectionPort, candidate, candidateResult);
                backend.clearPreview("REJECTED: " + candidateResult.reason());
            }
            connectionNode = null; connectionPort = null; candidate = CanvasHitTester.Hit.BACKGROUND;
            backend.clearPointerTarget();
        }
    }

    private ConnectionResult validateCandidate(CanvasHitTester.Hit hit) {
        if (!hit.port()) return ConnectionResult.reject(ConnectionCode.REJECT_MISSING_PORT, "release over a target port");
        return canvas.validateConnection(connectionNode, connectionPort, hit.nodeId(), hit.portId());
    }

    private CanvasPoint sourceScreenPoint() {
        CanvasNode node = canvas.node(connectionNode);
        CanvasPort port = canvas.definition().nodeType(node.type()).port(connectionPort);
        return canvas.viewport().toScreen(node.position().add(port.anchorPosition().x(), port.anchorPosition().y()));
    }

    private void updatePointer(org.joml.Vector2fc value) {
        if (value != null) pointer = CanvasPoint.of(value.x(), value.y());
    }

    void clear() { drag.end(); pan.end(); connectionNode = null; connectionPort = null; candidate = CanvasHitTester.Hit.BACKGROUND; backend.clearPointerTarget(); }
}
