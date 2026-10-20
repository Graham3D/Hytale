package com.inigmasgames.canvasui.runtime;

import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasViewport;

public final class CanvasDragController {
    private String nodeId;
    private CanvasPoint grabOffset;
    private CanvasPoint origin;
    private boolean thresholdPassed;

    public void begin(CanvasNode node, CanvasPoint pointerScreen, CanvasViewport viewport) {
        nodeId = node.nodeId();
        CanvasPoint pointerCanvas = viewport.toCanvas(pointerScreen);
        grabOffset = pointerCanvas.subtract(node.position());
        origin = pointerScreen;
        thresholdPassed = false;
    }

    public CanvasPoint update(CanvasPoint pointerScreen, CanvasViewport viewport) {
        if (!active()) throw new IllegalStateException("drag is not active");
        if (!thresholdPassed && Math.hypot(pointerScreen.x() - origin.x(), pointerScreen.y() - origin.y()) >= 4.0) {
            thresholdPassed = true;
        }
        CanvasPoint canvasPointer = viewport.toCanvas(pointerScreen);
        return canvasPointer.subtract(grabOffset);
    }

    public String nodeId() { return nodeId; }
    public CanvasPoint grabOffset() { return grabOffset; }
    public boolean active() { return nodeId != null; }
    public boolean thresholdPassed() { return thresholdPassed; }
    public void end() { nodeId = null; grabOffset = null; origin = null; thresholdPassed = false; }
}
