package com.inigmasgames.canvasui.api;

/** Generic event. before/after contain the relevant immutable model value when applicable. */
public record CanvasEvent(CanvasEventType type, String canvasId, String nodeId, String edgeId,
                          Object before, Object after, ConnectionResult connectionResult) { }
