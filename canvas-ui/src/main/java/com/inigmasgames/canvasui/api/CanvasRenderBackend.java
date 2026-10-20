package com.inigmasgames.canvasui.api;

/** UI-system-neutral rendering boundary for current CustomUI and future Noesis backends. */
public interface CanvasRenderBackend {
    String id();
    void topologyChanged();
    void updateNodeAndEdges(String nodeId);
    void updateViewport();
}
