package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpatialLayoutTest {
    private static final SpatialLayout.Size BOW = new SpatialLayout.Size(2, 4);
    private static final SpatialLayout.Size SMALL = new SpatialLayout.Size(1, 1);

    @Test void everyCoveredCellSelectsOneEntryWithGrabOffset() {
        var bag = new SpatialLayout(18, 4);
        assertTrue(bag.add("bow", BOW, new SpatialLayout.Position(2, 0)));
        for (int y = 0; y < 4; y++) for (int x = 2; x < 4; x++) {
            var grab = bag.grab(x, y).orElseThrow();
            assertEquals("bow", grab.id()); assertEquals(x-2, grab.offsetX()); assertEquals(y, grab.offsetY());
        }
        assertTrue(bag.move(bag.grab(3, 3).orElseThrow(), 11, 3));
        assertEquals(new SpatialLayout.Position(10, 0), bag.at(10, 0).orElseThrow().position());
    }
    @Test void eightScatteredFreeCellsCannotReceiveBow() {
        var bag = new SpatialLayout(4, 4);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            if ((x+y)%2 == 0) assertTrue(bag.add(x+":"+y, SMALL, new SpatialLayout.Position(x,y)));
        assertEquals(8, bag.entries().size());
        var before = bag.entries();
        assertTrue(bag.firstFit(BOW).isEmpty()); assertEquals(before, bag.entries());
    }
    @Test void cancelNeverRemovesSourceAndRejectedMoveIsUnchanged() {
        var bag = new SpatialLayout(18, 4);
        bag.add("bow", BOW, new SpatialLayout.Position(0,0));
        bag.add("potion", SMALL, new SpatialLayout.Position(3,0));
        var before = bag.entries(); long revision = bag.revision();
        bag.grab(1,3); // Cancel consists only of discarding this intent.
        assertEquals(before, bag.entries()); assertEquals(revision, bag.revision());
        assertFalse(bag.move(bag.grab(0,0).orElseThrow(),3,0));
        assertEquals(before, bag.entries()); assertEquals(revision, bag.revision());
    }
    @Test void staleAndReplayedMovesFail() {
        var bag = new SpatialLayout(18,4);
        bag.add("bow", BOW, new SpatialLayout.Position(0,0));
        var grab = bag.grab(0,0).orElseThrow();
        assertTrue(bag.move(grab,4,0)); assertFalse(bag.move(grab,8,0));
        var stale = bag.grab(4,0).orElseThrow();
        bag.add("potion", SMALL, new SpatialLayout.Position(0,0));
        assertFalse(bag.move(stale,8,0));
    }
    @Test void fixedOrientationBoundsAndDuplicateIdentity() {
        var bag = new SpatialLayout(18,4);
        assertFalse(bag.add("bow", BOW, new SpatialLayout.Position(17,0)));
        assertFalse(bag.add("bow", BOW, new SpatialLayout.Position(0,1)));
        assertFalse(bag.add("bow", BOW, new SpatialLayout.Position(Integer.MAX_VALUE,0)));
        assertTrue(bag.add("bow", BOW, new SpatialLayout.Position(16,0)));
        assertFalse(bag.add("bow", SMALL, new SpatialLayout.Position(0,0)));
        assertFalse(bag.move(new SpatialLayout.Grab("bow",-1,0,bag.revision()),0,0));
        assertThrows(IllegalArgumentException.class, () -> new SpatialLayout.Size(0,4));
    }
    @Test void deterministicPackingIsTopToBottomLeftToRight() {
        var bag = new SpatialLayout(18,4);
        bag.add("bow", BOW, bag.firstFit(BOW).orElseThrow());
        assertEquals(new SpatialLayout.Position(2,0), bag.firstFit(SMALL).orElseThrow());
        assertEquals(new SpatialLayout.Position(2,0), bag.firstFit(BOW).orElseThrow());
    }
    @Test void randomMovesPreserveEntryCountSizesAndNonoverlap() {
        var bag = new SpatialLayout(18,4); var random = new Random(917);
        for (int i=0;i<18;i++) bag.add("entry"+i, SMALL, bag.firstFit(SMALL).orElseThrow());
        for (int i=0;i<2000;i++) {
            var entry=bag.entries().get(random.nextInt(18));
            var grab=bag.grab(entry.position().x(),entry.position().y()).orElseThrow();
            bag.move(grab,random.nextInt(24)-3,random.nextInt(8)-2);
            Set<String> occupied=new HashSet<>();
            for(var e:bag.entries()) {
                assertEquals(SMALL,e.size());
                assertTrue(e.position().x()>=0 && e.position().x()<18 && e.position().y()>=0 && e.position().y()<4);
                assertTrue(occupied.add(e.position().toString()));
            }
            assertEquals(18,occupied.size());
        }
    }
    @Test void swapCommitsBothRectanglesOnceAndRejectsReplay() {
        var bag = new SpatialLayout(4, 4);
        assertTrue(bag.add("left", BOW, new SpatialLayout.Position(0, 0)));
        assertTrue(bag.add("right", BOW, new SpatialLayout.Position(2, 0)));
        var intent = bag.grab(1, 3).orElseThrow();
        var result = bag.moveOrSwap(intent, 3, 3);
        assertEquals(SpatialLayout.PlacementOutcome.SWAPPED, result.outcome());
        assertEquals(2, result.changed().size());
        assertEquals("left", bag.at(2, 0).orElseThrow().id());
        assertEquals("right", bag.at(0, 0).orElseThrow().id());
        assertEquals(SpatialLayout.PlacementOutcome.STALE, bag.moveOrSwap(intent, 3, 3).outcome());
    }
    @Test void snappedPlacementAcceptsEveryCoveredBowCellAndClampsToGrid() {
        for (int offsetY = 0; offsetY < 4; offsetY++) for (int offsetX = 0; offsetX < 2; offsetX++) {
            var bag = new SpatialLayout(18, 4);
            assertTrue(bag.add("bow", BOW, new SpatialLayout.Position(0, 0)));
            var intent = bag.grab(offsetX, offsetY).orElseThrow();
            // Release on the top row: a four-row item must snap vertically to row zero.
            var result = bag.moveOrSwapSnapped(intent, 10, 0);
            assertEquals(SpatialLayout.PlacementOutcome.MOVED, result.outcome());
            assertEquals(new SpatialLayout.Position(10 - offsetX, 0),
                    bag.entries().getFirst().position());
        }
        var edge = new SpatialLayout(18, 4);
        edge.add("bow", BOW, new SpatialLayout.Position(0, 0));
        assertEquals(SpatialLayout.PlacementOutcome.MOVED,
                edge.moveOrSwapSnapped(edge.grab(0, 0).orElseThrow(), 17, 3).outcome());
        assertEquals(new SpatialLayout.Position(16, 0), edge.entries().getFirst().position());
    }
    @Test void snappedPlacementSkipsBlockedCellAndPreservesSourceOnFailure() {
        var bag = new SpatialLayout(5, 4);
        bag.add("bow", BOW, new SpatialLayout.Position(0, 0));
        bag.add("rockA", SMALL, new SpatialLayout.Position(2, 0));
        bag.add("rockB", SMALL, new SpatialLayout.Position(2, 1));
        var result = bag.moveOrSwapSnapped(bag.grab(1, 3).orElseThrow(), 3, 0);
        assertEquals(SpatialLayout.PlacementOutcome.MOVED, result.outcome());
        assertEquals(new SpatialLayout.Position(3, 0), bag.entries().getFirst().position());

        var full = new SpatialLayout(2, 4);
        full.add("bow", BOW, new SpatialLayout.Position(0, 0));
        var before = full.entries(); long revision = full.revision();
        assertEquals(SpatialLayout.PlacementOutcome.NO_FIT,
                full.moveOrSwapSnapped(full.grab(1, 3).orElseThrow(), 1, 0).outcome());
        assertEquals(before, full.entries()); assertEquals(revision, full.revision());
    }
    @Test void rejectedSwapCannotPartiallyMoveEitherItem() {
        var bag = new SpatialLayout(5, 4);
        bag.add("large", new SpatialLayout.Size(2, 2), new SpatialLayout.Position(0, 0));
        bag.add("smallA", SMALL, new SpatialLayout.Position(3, 0));
        bag.add("smallB", SMALL, new SpatialLayout.Position(4, 0));
        var before = bag.entries(); long revision = bag.revision();
        assertEquals(SpatialLayout.PlacementOutcome.AMBIGUOUS,
                bag.moveOrSwap(bag.grab(0, 0).orElseThrow(), 3, 0).outcome());
        assertEquals(before, bag.entries()); assertEquals(revision, bag.revision());
        var edge = new SpatialLayout(4, 4);
        edge.add("potion", SMALL, new SpatialLayout.Position(0, 3));
        edge.add("bow", BOW, new SpatialLayout.Position(2, 0));
        var original = edge.entries(); long originalRevision = edge.revision();
        assertEquals(SpatialLayout.PlacementOutcome.NO_FIT,
                edge.moveOrSwap(edge.grab(0, 3).orElseThrow(), 2, 0).outcome());
        assertEquals(original, edge.entries()); assertEquals(originalRevision, edge.revision());
        assertEquals(SpatialLayout.PlacementOutcome.OUT_OF_BOUNDS,
                edge.moveOrSwap(edge.grab(2, 0).orElseThrow(), 1, 2).outcome());
        assertEquals(original, edge.entries()); assertEquals(originalRevision, edge.revision());
    }
    @Test void randomizedMoveAndSwapPreserveEveryRectangleAndRevision() {
        var bag = new SpatialLayout(18, 4); var random = new Random(1172);
        bag.add("bowA", BOW, new SpatialLayout.Position(0, 0));
        bag.add("bowB", BOW, new SpatialLayout.Position(2, 0));
        for (int i = 0; i < 24; i++) bag.add("small" + i, SMALL, bag.firstFit(SMALL).orElseThrow());
        for (int step = 0; step < 2000; step++) {
            var entriesBefore = bag.entries(); long revisionBefore = bag.revision();
            var chosen = entriesBefore.get(random.nextInt(entriesBefore.size()));
            int offsetX = random.nextInt(chosen.size().width());
            int offsetY = random.nextInt(chosen.size().height());
            var grab = bag.grab(chosen.position().x() + offsetX, chosen.position().y() + offsetY).orElseThrow();
            var result = bag.moveOrSwap(grab, random.nextInt(23) - 2, random.nextInt(9) - 2);
            assertEquals(revisionBefore + (result.accepted() ? 1 : 0), bag.revision());
            if (!result.accepted()) assertEquals(entriesBefore, bag.entries());
            assertEquals(entriesBefore.size(), bag.entries().size());
            var occupied = new HashSet<String>();
            for (var e : bag.entries()) {
                for (int y = e.position().y(); y < e.position().y() + e.size().height(); y++)
                    for (int x = e.position().x(); x < e.position().x() + e.size().width(); x++) {
                        assertTrue(x >= 0 && x < 18 && y >= 0 && y < 4);
                        assertTrue(occupied.add(x + "," + y));
                    }
            }
        }
    }
    @Test void literalCaseInsensitiveVisibleNameSearchNormalizesUnicode() {
        assertTrue(VisibleItemSearch.matches("Witherstring","sTrI"));
        assertTrue(VisibleItemSearch.matches("\u00a76Witherstring","wither"));
        assertTrue(VisibleItemSearch.matches("Épée", "e\u0301pe\u0301e"));
        assertFalse(VisibleItemSearch.matches("Unidentified bow", "Witherstring"));
        assertFalse(VisibleItemSearch.matches("Iron sword", ".*"));
        assertTrue(VisibleItemSearch.matches("Iron sword", ""));
    }
}
