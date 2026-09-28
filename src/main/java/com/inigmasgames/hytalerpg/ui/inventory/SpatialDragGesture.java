package com.inigmasgames.hytalerpg.ui.inventory;

/** Pointer gesture for a future cursor-HUD inventory host. It owns no item payloads. */
public final class SpatialDragGesture {
    public record Ghost(String id, double left, double top, int width, int height) { }
    private final SpatialLayout layout;
    private final int left, top, pitch, columns, rows;
    private SpatialLayout.Grab grabbed;
    private double pressX, pressY, lastX, lastY;
    private int itemLeft, itemTop, itemWidth, itemHeight;
    private boolean moved;

    public SpatialDragGesture(SpatialLayout layout, int left, int top, int pitch, int columns, int rows) {
        if (layout == null || pitch <= 0 || columns <= 0 || rows <= 0)
            throw new IllegalArgumentException("Invalid drag surface");
        this.layout = layout;
        this.left = left; this.top = top; this.pitch = pitch;
        this.columns = columns; this.rows = rows;
    }

    /** A press in any occupied footprint cell starts one bounded gesture. */
    public boolean press(double x, double y) {
        cancel();
        if (!inside(x, y)) return false;
        grabbed = layout.grab(cellX(x), cellY(y)).orElse(null);
        if (grabbed == null) return false;
        var entry = layout.at(cellX(x), cellY(y)).orElseThrow();
        itemLeft = left + entry.position().x() * pitch;
        itemTop = top + entry.position().y() * pitch;
        itemWidth = entry.size().width() * pitch;
        itemHeight = entry.size().height() * pitch;
        pressX = lastX = x; pressY = lastY = y;
        return true;
    }

    public void motion(double x, double y) {
        if (grabbed == null || !Double.isFinite(x) || !Double.isFinite(y)) return;
        lastX = x; lastY = y;
        moved |= Math.hypot(x - pressX, y - pressY) >= 4;
    }

    public Ghost ghost() {
        if (grabbed == null || !moved) return null;
        return new Ghost(grabbed.id(), itemLeft + lastX - pressX, itemTop + lastY - pressY,
                itemWidth, itemHeight);
    }

    /** Release commits only placement metadata; the caller must check native payload freshness. */
    public SpatialLayout.PlacementResult release(double x, double y) {
        if (grabbed == null) return null;
        motion(x, y);
        var intent = grabbed;
        boolean valid = moved && inside(x, y);
        cancel();
        if (!valid) return null;
        return layout.moveOrSwapSnapped(intent, cellX(x), cellY(y));
    }

    public void cancel() { grabbed = null; moved = false; }
    public boolean active() { return grabbed != null; }

    private boolean inside(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y)
                && x >= left && y >= top && x < left + (double) columns * pitch
                && y < top + (double) rows * pitch;
    }
    private int cellX(double x) { return (int) ((x - left) / pitch); }
    private int cellY(double y) { return (int) ((y - top) / pitch); }
}
