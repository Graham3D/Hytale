package com.inigmasgames.canvasui.runtime;

import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasViewport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateAndDragTest {
    private static com.inigmasgames.canvasui.api.Canvas canvas() {
        var type = com.inigmasgames.canvasui.api.NodeDefinition.builder("type").size(100, 60)
                .port(com.inigmasgames.canvasui.api.CanvasPort.input("in", "x", 2, 0, 30))
                .port(com.inigmasgames.canvasui.api.CanvasPort.output("out", "x", 2, 100, 30)).build();
        var canvas = new com.inigmasgames.canvasui.api.Canvas(
                com.inigmasgames.canvasui.api.CanvasDefinition.builder("test").registerNodeType(type).build());
        canvas.createNode("a", "type", CanvasPoint.of(100, 100), Map.of());
        canvas.createNode("b", "type", CanvasPoint.of(400, 100), Map.of());
        canvas.connect("a", "out", "b", "in");
        return canvas;
    }

    @Test void screenCanvasTransformsAreExactInversesAtFixedZoom() {
        CanvasCoordinateTransform transform = new CanvasCoordinateTransform();
        CanvasViewport viewport = new CanvasViewport(45.5, -17.25);
        CanvasPoint canvas = CanvasPoint.of(420, 260);
        CanvasPoint screen = transform.canvasToScreen(canvas, viewport);
        assertEquals(CanvasPoint.of(465.5, 242.75), screen);
        assertEquals(canvas, transform.screenToCanvas(screen, viewport));
    }

    @Test void panningChangesOnlyViewport() {
        CanvasPanController pan = new CanvasPanController();
        CanvasPoint node = CanvasPoint.of(300, 200);
        pan.begin();
        CanvasViewport moved = pan.update(CanvasViewport.ORIGIN, 80, -35);
        assertEquals(node, CanvasPoint.of(300, 200));
        assertEquals(new CanvasViewport(80, -35), moved);
        pan.end();
        assertFalse(pan.active());
    }

    @Test void dragPreservesGrabOffsetAndUsesFourUnitThreshold() {
        CanvasNode node = new CanvasNode("n", "type", CanvasPoint.of(100, 100), Map.of(), true);
        CanvasViewport viewport = new CanvasViewport(30, 40);
        CanvasDragController drag = new CanvasDragController();
        drag.begin(node, CanvasPoint.of(140, 160), viewport);
        assertEquals(CanvasPoint.of(10, 20), drag.grabOffset());
        assertEquals(CanvasPoint.of(102, 101), drag.update(CanvasPoint.of(142, 161), viewport));
        assertFalse(drag.thresholdPassed());
        assertEquals(CanvasPoint.of(160, 160), drag.update(CanvasPoint.of(200, 220), viewport));
        assertTrue(drag.thresholdPassed());
        drag.end();
        assertFalse(drag.active());
    }

    @Test void hitTestingUsesCanvasCoordinatesAndPrioritizesPorts() {
        var canvas = canvas(); canvas.setViewport(new CanvasViewport(50, 25));
        CanvasHitTester.Hit port = new CanvasHitTester().hit(canvas, CanvasPoint.of(250, 155));
        assertTrue(port.port()); assertEquals("a", port.nodeId()); assertEquals("out", port.portId());
        CanvasHitTester.Hit node = new CanvasHitTester().hit(canvas, CanvasPoint.of(175, 145));
        assertFalse(node.port()); assertEquals("a", node.nodeId());
    }

    @Test void viewportMovesBothEdgeEndpointsWithoutChangingGraphCoordinates() {
        var canvas = canvas();
        var renderer = new com.inigmasgames.canvasui.rendering.CanvasRenderer(
                new com.inigmasgames.canvasui.rendering.OrthogonalEdgeRenderer());
        var before = renderer.edgeSegments(canvas, canvas.edges().iterator().next());
        canvas.pan(70, -20);
        var after = renderer.edgeSegments(canvas, canvas.edges().iterator().next());
        assertEquals(before.get(0).start().add(70, -20), after.get(0).start());
        assertEquals(CanvasPoint.of(100, 100), canvas.node("a").position());
        assertEquals(CanvasPoint.of(400, 100), canvas.node("b").position());
    }

    @Test void hitTestingDraggingAndEdgeAnchorsRemainCorrectAtNonUnitZoom() {
        var canvas = canvas();
        canvas.setViewport(new CanvasViewport(25, 40, 0.5));
        CanvasPoint nodeInterior = canvas.viewport().toScreen(CanvasPoint.of(150, 130));
        CanvasHitTester.Hit hit = new CanvasHitTester().hit(canvas, nodeInterior);
        assertEquals("a", hit.nodeId()); assertFalse(hit.port());

        CanvasDragController drag = new CanvasDragController();
        drag.begin(canvas.node("a"), nodeInterior, canvas.viewport());
        CanvasPoint moved = drag.update(nodeInterior.add(50, 25), canvas.viewport());
        assertEquals(CanvasPoint.of(200, 150), moved);

        var renderer = new com.inigmasgames.canvasui.rendering.CanvasRenderer(
                new com.inigmasgames.canvasui.rendering.OrthogonalEdgeRenderer());
        assertEquals(canvas.viewport().toScreen(CanvasPoint.of(200, 130)),
                renderer.portScreenPoint(canvas, "a", "out"));
    }
}
