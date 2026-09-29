package com.inigmasgames.hytalerpg.ui.inventory;

/** Vanilla 74px slots with 2px spacing, arranged to fill the workspace. */
final class InventoryGridGeometry {
    static final int COLUMNS = 15;
    static final int ROWS = 5;
    static final int CELLS = COLUMNS * ROWS;
    static final int SLOT = 74;
    static final int SPACING = 2;
    static final int PITCH = SLOT + SPACING;
    static final int WIDTH = COLUMNS * PITCH;
    static final int HEIGHT = ROWS * PITCH;
    private InventoryGridGeometry() { }
}
