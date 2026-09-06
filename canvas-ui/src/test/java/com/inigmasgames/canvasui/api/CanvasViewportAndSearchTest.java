package com.inigmasgames.canvasui.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanvasViewportAndSearchTest {
    @Test void transformRoundTripsAtEverySupportedReferenceZoom() {
        CanvasPoint point = CanvasPoint.of(127.25, -31.75);
        for (double zoom : new double[]{0.35, 0.5, 0.75, 1.0, 1.5, 2.0}) {
            CanvasViewport viewport = new CanvasViewport(83, -19, zoom);
            CanvasPoint roundTrip = viewport.toCanvas(viewport.toScreen(point));
            assertEquals(point.x(), roundTrip.x(), 1e-9, "x at " + zoom);
            assertEquals(point.y(), roundTrip.y(), 1e-9, "y at " + zoom);
        }
    }

    @Test void cursorCenteredZoomPreservesPointUnderCursorAndClampsBounds() {
        CanvasViewport viewport = new CanvasViewport(40, 80, 0.75);
        CanvasPoint cursor = CanvasPoint.of(641, 377);
        CanvasPoint before = viewport.toCanvas(cursor);
        CanvasViewport changed = viewport.zoomAround(cursor, 1.5);
        assertEquals(before.x(), changed.toCanvas(cursor).x(), 1e-9);
        assertEquals(before.y(), changed.toCanvas(cursor).y(), 1e-9);
        assertEquals(2.0, changed.zoomAround(cursor, 99).zoom());
        assertEquals(0.35, changed.zoomAround(cursor, -4).zoom());
    }

    @Test void snapshotPersistsZoomAndLegacyFormatDefaultsToOne() {
        CanvasSnapshot snapshot = new CanvasSnapshot("zoom", new CanvasViewport(12, 34, 1.5), List.of(), List.of(), null);
        String encoded = CanvasSnapshotCodec.encode(snapshot);
        assertEquals(snapshot, CanvasSnapshotCodec.decode(encoded));
        String legacy = encoded.replace("format=2", "format=1").replaceAll("(?m)^vz=.*\\R", "");
        assertEquals(1.0, CanvasSnapshotCodec.decode(legacy).viewport().zoom());
    }

    @Test void searchCoversNameDescriptionTagsCasePartialEmptyAndMultipleMatches() {
        NodeVisual wolf = NodeVisual.simple("Wolf", "#000000")
                .withSearchMetadata("Summon Wolf", "Calls a loyal companion", List.of("Nature", "Pet", "summons"));
        NodeVisual passive = NodeVisual.simple("Pack Bond", "#000000")
                .withSearchMetadata("Pack Bond", "Strengthens all summons nearby", List.of("Passive"));
        NodeVisual tagged = NodeVisual.simple("Spirit Mark", "#000000")
                .withSearchMetadata("Spirit Mark", "Marks a target", List.of("SUMMONS", "curse"));
        assertTrue(CanvasSearch.matches("summons", wolf));
        assertTrue(CanvasSearch.matches("SUMMON", wolf));
        assertTrue(CanvasSearch.matches("summons", passive));
        assertTrue(CanvasSearch.matches("summons", tagged));
        assertTrue(CanvasSearch.matches("loyal comp", wolf));
        assertTrue(CanvasSearch.matches("", wolf));
        assertFalse(CanvasSearch.matches("fireball", wolf));

        NodeDefinition type = NodeDefinition.builder("skill").renderer(ctx -> switch (ctx.node().nodeId()) {
            case "wolf" -> wolf; case "passive" -> passive; default -> tagged;
        }).build();
        CanvasDefinition definition = CanvasDefinition.builder("search").registerNodeType(type).build();
        Canvas canvas = new Canvas(definition);
        for (String id : List.of("wolf", "passive", "tagged"))
            canvas.createNode(id, "skill", CanvasPoint.of(0, 0), Map.of());
        assertEquals(3, CanvasSearch.matchingNodeIds("summons", canvas.nodes(), definition).size());
    }
}
