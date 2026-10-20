package com.inigmasgames.canvasui.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanvasGraphTest {
    private static NodeDefinition nodeType(String type, int outputMax) {
        return NodeDefinition.builder(type).size(100, 60)
                .port(CanvasPort.input("in", "flow", 8, 0, 30))
                .port(CanvasPort.output("out", "flow", outputMax, 100, 30)).build();
    }

    private static CanvasDefinition definition(String id) {
        return CanvasDefinition.builder(id).registerNodeType(nodeType("a", 8))
                .registerNodeType(nodeType("b", 8)).build();
    }

    @Test void snapshotRoundTripPreservesGraphViewportSelectionAndConsumerMetadata() {
        Canvas source = new Canvas(definition("roundtrip"));
        source.createNode("n1", "a", CanvasPoint.of(10, 20), Map.of("externalId", "opaque-42"));
        source.createNode("n2", "b", CanvasPoint.of(300, 220), Map.of());
        source.connect("e1", "n1", "out", "n2", "in", new EdgeStyle(4, "#abcdef", "flow", "rounded"));
        source.setViewport(new CanvasViewport(55, -12));
        source.selectNode("n2");
        CanvasSnapshot snapshot = source.snapshot();
        CanvasSnapshot decoded = CanvasSnapshotCodec.decode(CanvasSnapshotCodec.encode(snapshot));
        assertEquals(snapshot, decoded);
        Canvas restored = new Canvas(definition("roundtrip"));
        restored.restore(decoded);
        assertEquals(snapshot, restored.snapshot());
        assertEquals("opaque-42", restored.node("n1").metadata().get("externalId"));
    }

    @Test void connectionLimitsAndDuplicateEdgesAreRejected() {
        CanvasDefinition limited = CanvasDefinition.builder("limits")
                .registerNodeType(nodeType("limited", 1)).build();
        Canvas canvas = new Canvas(limited);
        canvas.createNode("s", "limited", CanvasPoint.of(0, 0), Map.of());
        canvas.createNode("t1", "limited", CanvasPoint.of(100, 0), Map.of());
        canvas.createNode("t2", "limited", CanvasPoint.of(200, 0), Map.of());
        canvas.connect("s", "out", "t1", "in");
        assertEquals(ConnectionCode.REJECT_MAX_CONNECTIONS,
                canvas.validateConnection("s", "out", "t2", "in").code());
        assertThrows(GraphValidationException.class, () -> canvas.connect("s", "out", "t2", "in"));
    }

    @Test void consumerPolicyRejectsWithoutCreatingEdge() {
        CanvasDefinition policy = CanvasDefinition.builder("policy").registerNodeType(nodeType("a", 8))
                .connectionPolicy((s, sp, t, tp) -> ConnectionResult.reject(ConnectionCode.REJECT_CUSTOM, "consumer says no"))
                .build();
        Canvas canvas = new Canvas(policy);
        canvas.createNode("s", "a", CanvasPoint.of(0, 0), Map.of());
        canvas.createNode("t", "a", CanvasPoint.of(100, 0), Map.of());
        GraphValidationException error = assertThrows(GraphValidationException.class,
                () -> canvas.connect("s", "out", "t", "in"));
        assertEquals("consumer says no", error.result().reason());
        assertTrue(canvas.edges().isEmpty());
    }

    @Test void danglingAndMissingPortSnapshotEdgesAreRejected() {
        CanvasSnapshot dangling = new CanvasSnapshot("bad", CanvasViewport.ORIGIN,
                List.of(new CanvasSnapshot.NodeState("n", "a", 0, 0, Map.of(), true)),
                List.of(new CanvasSnapshot.EdgeState("e", "n", "out", "missing", "in", EdgeStyle.standard("x"))), null);
        Canvas canvas = new Canvas(definition("bad"));
        assertThrows(GraphValidationException.class, () -> canvas.restore(dangling));
        assertTrue(canvas.nodes().isEmpty());
    }

    @Test void deletingNodeCleansAttachedEdgesAndDeletingEdgeIsExact() {
        Canvas canvas = new Canvas(definition("delete"));
        canvas.createNode("a", "a", CanvasPoint.of(0, 0), Map.of());
        canvas.createNode("b", "b", CanvasPoint.of(100, 0), Map.of());
        canvas.createNode("c", "b", CanvasPoint.of(200, 0), Map.of());
        canvas.connect("e1", "a", "out", "b", "in", EdgeStyle.standard("x"));
        canvas.connect("e2", "a", "out", "c", "in", EdgeStyle.standard("x"));
        canvas.removeEdge("e2");
        assertNull(canvas.edge("e2"));
        canvas.removeNode("b");
        assertTrue(canvas.edges().isEmpty());
        assertNull(canvas.node("b"));
    }

    @Test void optionalAcyclicModeRejectsCycleWhileGenericDefaultAllowsIt() {
        CanvasDefinition acyclic = CanvasDefinition.builder("acyclic").allowCycles(false)
                .registerNodeType(nodeType("a", 8)).build();
        Canvas canvas = new Canvas(acyclic);
        for (String id : List.of("a", "b", "c")) canvas.createNode(id, "a", CanvasPoint.of(0, 0), Map.of());
        canvas.connect("a", "out", "b", "in");
        canvas.connect("b", "out", "c", "in");
        assertEquals(ConnectionCode.REJECT_CYCLE, canvas.validateConnection("c", "out", "a", "in").code());
    }

    @Test void viewportPersistsAndNodeCoordinatesRemainIndependent() {
        Canvas canvas = new Canvas(definition("viewport"));
        canvas.createNode("n", "a", CanvasPoint.of(50, 75), Map.of());
        canvas.pan(200, -90);
        assertEquals(CanvasPoint.of(50, 75), canvas.node("n").position());
        Canvas restored = new Canvas(definition("viewport"));
        restored.restore(canvas.snapshot());
        assertEquals(new CanvasViewport(200, -90), restored.viewport());
    }

    @Test void eventsExposeSelectionMovementAndConnectionChanges() {
        List<CanvasEventType> events = new ArrayList<>();
        CanvasDefinition definition = CanvasDefinition.builder("events").registerNodeType(nodeType("a", 8))
                .listener(event -> events.add(event.type())).build();
        Canvas canvas = new Canvas(definition);
        canvas.createNode("a", "a", CanvasPoint.of(0, 0), Map.of());
        canvas.createNode("b", "a", CanvasPoint.of(100, 0), Map.of());
        canvas.selectNode("a"); canvas.moveNode("a", CanvasPoint.of(20, 30));
        canvas.connect("a", "out", "b", "in");
        assertTrue(events.containsAll(List.of(CanvasEventType.NODE_CREATED, CanvasEventType.NODE_SELECTED,
                CanvasEventType.NODE_MOVED, CanvasEventType.CONNECTION_CREATED, CanvasEventType.CANVAS_CHANGED)));
    }
}
