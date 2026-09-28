package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeSpatialAliasTest {
    @Test void everyCoveredCellResolvesToOneRectangleAndKeepsItsHorizontalGrabOffset() {
        for (int column : new int[]{0, 7, 16}) {
            int count = 0;
            for (int visual = 0; visual < NativeSpatialAlias.CELLS; visual++) {
                int offset = NativeSpatialAlias.sourceColumn(visual, column);
                if (offset < 0) continue;
                assertTrue(NativeSpatialAlias.covered(visual, column));
                assertEquals(visual % 18 - column, offset);
                assertEquals(9 - offset, NativeSpatialAlias.destinationColumn(9 + visual / 18 * 18, offset));
                count++;
            }
            assertEquals(8, count);
        }
    }

    @Test void targetSnapsInsideBagAndRejectsInvalidCellOrGrab() {
        assertEquals(16, NativeSpatialAlias.destinationColumn(17, 0));
        assertEquals(0, NativeSpatialAlias.destinationColumn(0, 1));
        assertEquals(-1, NativeSpatialAlias.destinationColumn(-1, 0));
        assertEquals(-1, NativeSpatialAlias.destinationColumn(72, 0));
        assertEquals(-1, NativeSpatialAlias.destinationColumn(0, 2));
        assertFalse(NativeSpatialAlias.covered(72, 0));
    }
}
