package com.inigmasgames.canvasui.runtime.cursor;

/** Immutable, bounded copy of one client-supplied pointer observation. */
record CursorProbeSample(long sequence, Source source, Kind kind, boolean validPosition,
                         double x, double y, Integer deltaX, Integer deltaY,
                         String button, String state, int clicks, String heldButtons,
                         boolean targetBlock, boolean targetEntity, boolean itemInHand) {
    enum Source { PACKET, EVENT }
    enum Kind { BUTTON, MOTION }

    boolean transition() { return kind == Kind.BUTTON; }
}
