package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.CanvasPoint;

/** Explicit, bounded interaction state for one library-origin icon drag. */
public final class TreeDragController {
    public enum State { IDLE, ARMED, DRAGGING, SNAPPING_TO_TARGET, RETURNING_TO_ORIGIN, CANCELLED }
    public static final double DRAG_THRESHOLD = 6.0;
    public static final long ANIMATION_MILLIS = 160L;

    private State state = State.IDLE;
    private CursorCanvasEditor.LibraryEntry entry;
    private CanvasPoint press;
    private CanvasPoint origin;
    private CanvasPoint pointer;
    private CanvasPoint animationStart;
    private CanvasPoint animationEnd;
    private long animationStarted;
    private long generation;

    public void arm(CursorCanvasEditor.LibraryEntry value, CanvasPoint press, CanvasPoint sourceCenter) {
        cancel();
        entry = value; this.press=press; pointer = press; origin = sourceCenter; state = State.ARMED; generation++;
    }

    public boolean move(CanvasPoint value) {
        pointer = value;
        if (state == State.ARMED && distance(press, value) >= DRAG_THRESHOLD) state = State.DRAGGING;
        return state == State.DRAGGING;
    }

    public void animateTo(CanvasPoint destination, boolean accepted, long nowMillis) {
        if (state != State.DRAGGING) return;
        animationStart = pointer;
        animationEnd = destination;
        animationStarted = nowMillis;
        state = accepted ? State.SNAPPING_TO_TARGET : State.RETURNING_TO_ORIGIN;
    }

    public CanvasPoint visualPoint(long nowMillis) {
        if (!animating()) return pointer;
        double raw = Math.min(1.0, Math.max(0.0, (nowMillis - animationStarted) / (double) ANIMATION_MILLIS));
        double eased = 1.0 - Math.pow(1.0 - raw, 3.0);
        return CanvasPoint.of(animationStart.x() + (animationEnd.x() - animationStart.x()) * eased,
                animationStart.y() + (animationEnd.y() - animationStart.y()) * eased);
    }

    public boolean completeIfDue(long nowMillis) {
        if (!animating() || nowMillis - animationStarted < ANIMATION_MILLIS) return false;
        clear();
        return true;
    }

    public void cancel() { if (state != State.IDLE) state = State.CANCELLED; clear(); }
    public State state() { return state; }
    public CursorCanvasEditor.LibraryEntry entry() { return entry; }
    public CanvasPoint origin() { return origin; }
    public CanvasPoint pointer() { return pointer; }
    public long generation() { return generation; }
    public boolean active() { return state != State.IDLE && state != State.CANCELLED; }
    public boolean dragging() { return state == State.DRAGGING; }
    public boolean animating() { return state == State.SNAPPING_TO_TARGET || state == State.RETURNING_TO_ORIGIN; }

    private void clear() {
        state = State.IDLE; entry = null; press=null; origin = null; pointer = null;
        animationStart = null; animationEnd = null; animationStarted = 0L;
    }
    private static double distance(CanvasPoint a, CanvasPoint b) { return Math.hypot(a.x()-b.x(), a.y()-b.y()); }
}
