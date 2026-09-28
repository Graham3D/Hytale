package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialDragGestureTest {
    private static final SpatialLayout.Size BOW = new SpatialLayout.Size(2, 4);

    @Test void coveredCellDragPreservesPixelOffsetAndSnapsOnRelease() {
        for (int y = 0; y < 4; y++) for (int x = 0; x < 2; x++) {
            var layout = new SpatialLayout(18, 4);
            assertTrue(layout.add("bow", BOW, new SpatialLayout.Position(0, 0)));
            var drag = new SpatialDragGesture(layout, 200, 300, 64, 18, 4);
            double pressX = 200 + x * 64 + 21, pressY = 300 + y * 64 + 27;
            assertTrue(drag.press(pressX, pressY));
            drag.motion(pressX + 3, pressY);
            assertNull(drag.ghost());
            double releaseX = 200 + 9 * 64 + 21, releaseY = 300 + 2 * 64 + 27;
            drag.motion(releaseX, releaseY);
            var ghost = drag.ghost();
            assertNotNull(ghost);
            assertEquals(200 + releaseX - pressX, ghost.left());
            assertEquals(300 + releaseY - pressY, ghost.top());
            assertEquals(128, ghost.width()); assertEquals(256, ghost.height());
            var result = drag.release(releaseX, releaseY);
            assertNotNull(result); assertTrue(result.accepted());
            assertEquals(new SpatialLayout.Position(9 - x, 0), layout.entries().getFirst().position());
            assertFalse(drag.active()); assertNull(drag.ghost());
        }
    }

    @Test void clickOutsideReleaseAndCancellationNeverMoveTheItem() {
        var layout = new SpatialLayout(18, 4);
        layout.add("bow", BOW, new SpatialLayout.Position(0, 0));
        var drag = new SpatialDragGesture(layout, 200, 300, 64, 18, 4);
        assertFalse(drag.press(199, 301));
        assertTrue(drag.press(221, 327));
        assertNull(drag.release(221, 327));
        assertEquals(new SpatialLayout.Position(0, 0), layout.entries().getFirst().position());
        assertTrue(drag.press(221, 327));
        drag.motion(400, 500);
        assertNull(drag.release(1800, 500));
        assertEquals(new SpatialLayout.Position(0, 0), layout.entries().getFirst().position());
        assertTrue(drag.press(221, 327));
        drag.cancel();
        assertNull(drag.release(600, 327));
        assertEquals(new SpatialLayout.Position(0, 0), layout.entries().getFirst().position());
    }
}
