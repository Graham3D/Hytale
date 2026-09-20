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
import com.inigmasgames.canvasui.api.CanvasRenderBackend;
import com.inigmasgames.canvasui.api.editor.PortAnchorResolver;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class CanvasInputController {
    public enum LinkDragState { IDLE, PORT_ARMED, LINK_DRAGGING, COMMITTING, CANCELLED }
    private static final double LINK_DRAG_THRESHOLD = 4.0;
    private final Canvas canvas;
    private final CanvasRenderBackend backend;
    private final Runnable persist;
    private final BooleanSupplier renderDue;
    private final LongConsumer recordPointer;
    private final BiFunction<CanvasNode, CanvasPoint, CanvasPoint> moveConstraint;
    private final Consumer<String> nodeMoved;
    private final CanvasHitTester hitTester = new CanvasHitTester();
    private final CanvasDragController drag = new CanvasDragController();
    private final CanvasPanController pan = new CanvasPanController();
    private CanvasPoint pointer = CanvasPoint.of(0, 0);
    private String connectionNode;
    private String connectionPort;
    private CanvasPoint connectionPress;
    private LinkDragState linkState = LinkDragState.IDLE;
    private CanvasHitTester.Hit candidate = CanvasHitTester.Hit.BACKGROUND;
    private ConnectionResult candidateResult = ConnectionResult.reject(ConnectionCode.REJECT_CUSTOM, "no target");

    CanvasInputController(CanvasSession session, CanvasRenderBackend backend) {
        this(session.canvas(), backend, session::persist, () -> session.renderDue(false), session::recordPointer);
    }

    /** Production input core shared by the CustomUI page and passive cursor-HUD renderers. */
    public CanvasInputController(Canvas canvas, CanvasRenderBackend backend, Runnable persist,
                                 BooleanSupplier renderDue, LongConsumer recordPointer) {
        this(canvas, backend, persist, renderDue, recordPointer, (node, point) -> point, ignored -> { });
    }

    /** Production input core with an optional presentation-space node movement constraint. */
    public CanvasInputController(Canvas canvas, CanvasRenderBackend backend, Runnable persist,
                                 BooleanSupplier renderDue, LongConsumer recordPointer,
                                 BiFunction<CanvasNode, CanvasPoint, CanvasPoint> moveConstraint) {
        this(canvas,backend,persist,renderDue,recordPointer,moveConstraint,ignored -> { });
    }

    public CanvasInputController(Canvas canvas, CanvasRenderBackend backend, Runnable persist,
                                 BooleanSupplier renderDue, LongConsumer recordPointer,
                                 BiFunction<CanvasNode, CanvasPoint, CanvasPoint> moveConstraint,
                                 Consumer<String> nodeMoved) {
        this.canvas = Objects.requireNonNull(canvas);
        this.backend = Objects.requireNonNull(backend);
        this.persist = Objects.requireNonNull(persist);
        this.renderDue = Objects.requireNonNull(renderDue);
        this.recordPointer = Objects.requireNonNull(recordPointer);
        this.moveConstraint = Objects.requireNonNull(moveConstraint);
        this.nodeMoved = Objects.requireNonNull(nodeMoved);
    }

    void button(PlayerMouseButtonEvent event) {
        long started = System.nanoTime();
        try {
            updatePointer(event.getScreenPoint());
            MouseButtonEvent button = event.getMouseButton();
            if (button == null) return;
            if (button.state == MouseButtonState.Pressed) press(button.mouseButtonType);
            else release(button.mouseButtonType);
        } finally { recordPointer.accept(System.nanoTime() - started); }
    }

    void motion(PlayerMouseMotionEvent event) {
        long started = System.nanoTime();
        try {
            MouseMotionEvent motion = event.getMouseMotion();
            Vector2i delta = motion == null ? null : motion.relativeMotion;
            if (event.getScreenPoint() != null) updatePointer(event.getScreenPoint());
            else if (delta != null) pointer = pointer.add(delta.x, delta.y);
            if (drag.active()) {
                CanvasPoint next = moveConstraint.apply(canvas.node(drag.nodeId()),
                        drag.update(pointer, canvas.viewport()));
                if (drag.thresholdPassed()) {
                    canvas.moveNode(drag.nodeId(), next);
                    nodeMoved.accept(drag.nodeId());
                    if (renderDue.getAsBoolean()) backend.updateNodeAndEdges(drag.nodeId());
                }
            } else if (pan.active() && delta != null) {
                canvas.setViewport(pan.update(canvas.viewport(), delta.x, delta.y));
                if (renderDue.getAsBoolean()) backend.updateViewport();
            } else if (connectionNode != null) {
                updateConnectionPreview();
            } else {
                CanvasHitTester.Hit hover = hitTester.hit(canvas, pointer);
                backend.pointerTarget(hover.nodeId(), false);
            }
        } finally { recordPointer.accept(System.nanoTime() - started); }
    }

    /** Feeds an already transformed canvas-screen position into the shared graph controller. */
    public void button(CanvasPoint screenPoint, MouseButtonType button, MouseButtonState state) {
        long started = System.nanoTime();
        try {
            if (screenPoint != null) pointer = screenPoint;
            if (button == null || state == null) return;
            if (state == MouseButtonState.Pressed) press(button);
            else release(button);
        } finally { recordPointer.accept(System.nanoTime() - started); }
    }

    /** Feeds transformed/coalesced pointer motion into the shared graph controller. */
    public void motion(CanvasPoint screenPoint, Integer deltaX, Integer deltaY) {
        long started = System.nanoTime();
        try {
            if (screenPoint != null) pointer = screenPoint;
            else if (deltaX != null && deltaY != null) pointer = pointer.add(deltaX, deltaY);
            if (drag.active()) {
                CanvasPoint next = moveConstraint.apply(canvas.node(drag.nodeId()),
                        drag.update(pointer, canvas.viewport()));
                if (drag.thresholdPassed()) {
                    canvas.moveNode(drag.nodeId(), next);
                    nodeMoved.accept(drag.nodeId());
                    if (renderDue.getAsBoolean()) backend.updateNodeAndEdges(drag.nodeId());
                }
            } else if (pan.active() && deltaX != null && deltaY != null) {
                canvas.setViewport(pan.update(canvas.viewport(), deltaX, deltaY));
                if (renderDue.getAsBoolean()) backend.updateViewport();
            } else if (connectionNode != null) {
                updateConnectionPreview();
            } else {
                CanvasHitTester.Hit hover = hitTester.hit(canvas, pointer);
                backend.pointerTarget(hover.nodeId(), false);
            }
        } finally { recordPointer.accept(System.nanoTime() - started); }
    }

    private void press(MouseButtonType button) {
        CanvasHitTester.Hit hit = hitTester.hit(canvas, pointer);
        if (button == MouseButtonType.Left && hit.port()) {
            CanvasNode node = canvas.node(hit.nodeId());
            CanvasPort port = canvas.definition().nodeType(node.type()).port(hit.portId());
            if (port.direction() != PortDirection.INPUT) {
                connectionNode = hit.nodeId(); connectionPort = hit.portId();
                connectionPress = pointer;
                linkState = LinkDragState.PORT_ARMED;
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
            drag.end(); backend.updateNodeAndEdges(nodeId); persist.run();
            canvas.publish(CanvasEventType.DRAG_ENDED, nodeId, null, moved, canvas.node(nodeId).position(), null);
        }
        if (pan.active() && (button == MouseButtonType.Middle || button == MouseButtonType.Left)) {
            pan.end(); backend.updateViewport(); persist.run();
        }
        if (connectionNode != null && button == MouseButtonType.Left) {
            linkState = LinkDragState.COMMITTING;
            candidate = hitTester.hit(canvas, pointer);
            candidateResult = validateCandidate(candidate);
            if (candidateResult.allowed()) {
                try {
                    canvas.connect(connectionNode, connectionPort, candidate.nodeId(), candidate.portId());
                    backend.topologyChanged(); persist.run(); backend.clearPreview("Connection created");
                } catch (GraphValidationException rejected) {
                    backend.clearPreview("REJECTED: " + rejected.result().reason());
                }
            } else {
                canvas.publish(CanvasEventType.CONNECTION_REJECTED, candidate.nodeId(), null,
                        connectionNode + ":" + connectionPort, candidate, candidateResult);
                backend.clearPreview("REJECTED: " + candidateResult.reason());
            }
            connectionNode = null; connectionPort = null; connectionPress = null;
            candidate = CanvasHitTester.Hit.BACKGROUND; linkState = LinkDragState.IDLE;
            backend.clearPointerTarget();
        }
    }

    private ConnectionResult validateCandidate(CanvasHitTester.Hit hit) {
        if (!hit.port()) return ConnectionResult.reject(ConnectionCode.REJECT_MISSING_PORT, "release over a target port");
        return canvas.validateConnection(connectionNode, connectionPort, hit.nodeId(), hit.portId());
    }

    private CanvasPoint sourceScreenPoint() {
        return PortAnchorResolver.screen(canvas, connectionNode, connectionPort);
    }

    private void updateConnectionPreview() {
        if (linkState == LinkDragState.PORT_ARMED && distance(connectionPress, pointer) >= LINK_DRAG_THRESHOLD)
            linkState = LinkDragState.LINK_DRAGGING;
        candidate = hitTester.hit(canvas, pointer);
        candidateResult = validateCandidate(candidate);
        backend.pointerTarget(candidate.nodeId(), !candidateResult.allowed());
        CanvasPoint endpoint = candidateResult.allowed()
                ? portScreenPoint(candidate.nodeId(), candidate.portId()) : pointer;
        if (renderDue.getAsBoolean()) backend.updatePreview(sourceScreenPoint(), endpoint, candidateResult.allowed());
    }

    private CanvasPoint portScreenPoint(String nodeId, String portId) {
        return PortAnchorResolver.screen(canvas, nodeId, portId);
    }

    private static double distance(CanvasPoint a, CanvasPoint b) {
        return a == null || b == null ? 0.0 : Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    private void updatePointer(org.joml.Vector2fc value) {
        if (value != null) pointer = CanvasPoint.of(value.x(), value.y());
    }

    public boolean dragging() { return drag.active(); }
    public String draggingNodeId() { return drag.active() ? drag.nodeId() : null; }
    public LinkDragState linkState() { return linkState; }
    public void clear() {
        drag.end(); pan.end(); connectionNode = null; connectionPort = null; connectionPress = null;
        candidate = CanvasHitTester.Hit.BACKGROUND; linkState = LinkDragState.IDLE;
        backend.clearPreview("Connection cancelled"); backend.clearPointerTarget();
    }
}
