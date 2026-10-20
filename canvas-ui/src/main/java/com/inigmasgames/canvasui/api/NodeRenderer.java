package com.inigmasgames.canvasui.api;

@FunctionalInterface
public interface NodeRenderer {
    NodeVisual render(NodeRenderContext context);
}
