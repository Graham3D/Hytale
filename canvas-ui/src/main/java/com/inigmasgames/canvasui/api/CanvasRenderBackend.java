package com.inigmasgames.canvasui.api;

/** UI-system-neutral rendering boundary for current CustomUI and future Noesis backends. */
public interface CanvasRenderBackend {
    String id();
    void topologyChanged();
    void updateNodeAndEdges(String nodeId);
    void updateViewport();

    /** Optional interaction-presentation hooks shared by page and cursor-HUD backends. */
    default void pointerTarget(String nodeId, boolean invalid) { }
    default void clearPointerTarget() { }
    default void updatePreview(CanvasPoint source, CanvasPoint target, boolean valid) { }
    default void clearPreview(String status) { }
    default void status(String value) { }
}
