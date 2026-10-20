package com.inigmasgames.canvasui.rendering;

import java.util.List;

@FunctionalInterface
public interface EdgeRenderer {
    List<EdgeSegment> render(EdgeRenderContext context);
}
