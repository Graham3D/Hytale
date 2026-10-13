package com.inigmasgames.canvasui.api;

import java.util.UUID;

/** Optional diagnostics seam; the CanvasUI runtime never owns trace files. */
public interface CanvasInteractionObserver {
    CanvasInteractionObserver NONE = new CanvasInteractionObserver() {};
    default boolean enabled(UUID player) { return false; }
    default boolean lifecycleEnabled(UUID player) { return enabled(player); }
    /** Owner-thread lifecycle witness; no diagnostic provider may change ownership decisions. */
    default void lifecycle(UUID player,String document,String boundary,boolean closedOnce,boolean hudRestored) { }
    default Action begin(UUID player, String canvasId, String elementId, String controlType,
                         String event, double x, double y) { return result -> {}; }
    interface Action { void finish(String result); }
}
