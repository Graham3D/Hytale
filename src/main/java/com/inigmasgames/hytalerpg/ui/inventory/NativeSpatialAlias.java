package com.inigmasgames.hytalerpg.ui.inventory;

/** One visual rectangle resolving to one source slot; no inventory ownership lives here. */
public final class NativeSpatialAlias {
    public static final int COLUMNS = 18;
    public static final int ROWS = 4;
    public static final int CELLS = COLUMNS * ROWS;
    public static final int BOW_WIDTH = 2;
    public static final int BOW_HEIGHT = 4;

    private NativeSpatialAlias() { }

    public static boolean covered(int visualSlot, int column) {
        if (visualSlot < 0 || visualSlot >= CELLS || column < 0 || column > COLUMNS - BOW_WIDTH)
            return false;
        int x = visualSlot % COLUMNS;
        return x >= column && x < column + BOW_WIDTH;
    }

    public static int sourceColumn(int visualSlot, int column) {
        return covered(visualSlot, column) ? visualSlot % COLUMNS - column : -1;
    }

    public static int destinationColumn(int targetVisualSlot, int grabColumn) {
        if (targetVisualSlot < 0 || targetVisualSlot >= CELLS || grabColumn < 0 || grabColumn >= BOW_WIDTH)
            return -1;
        return Math.max(0, Math.min(COLUMNS - BOW_WIDTH, targetVisualSlot % COLUMNS - grabColumn));
    }
}
