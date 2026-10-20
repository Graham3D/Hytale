package com.inigmasgames.canvasui.runtime;

import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasViewport;

public final class CanvasCoordinateTransform {
    public CanvasPoint screenToCanvas(CanvasPoint screenPoint, CanvasViewport viewport) {
        return viewport.toCanvas(screenPoint);
    }
    public CanvasPoint canvasToScreen(CanvasPoint canvasPoint, CanvasViewport viewport) {
        return viewport.toScreen(canvasPoint);
    }
}
