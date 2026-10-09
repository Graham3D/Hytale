package com.inigmasgames.canvasui.api;

import java.util.UUID;

/** Optional diagnostics seam; the CanvasUI runtime never owns trace files. */
public interface CanvasInteractionObserver {
    CanvasInteractionObserver NONE = new CanvasInteractionObserver() {};
    default boolean enabled(UUID player) { return false; }
    default Action begin(UUID player, String canvasId, String elementId, String controlType,
                         String event, double x, double y) { return result -> {}; }
    interface Action { void finish(String result); }
}
