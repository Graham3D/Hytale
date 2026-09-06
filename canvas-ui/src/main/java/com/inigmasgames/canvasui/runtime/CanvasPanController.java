package com.inigmasgames.canvasui.runtime;

import com.inigmasgames.canvasui.api.CanvasViewport;

public final class CanvasPanController {
    private boolean active;
    public void begin() { active = true; }
    public CanvasViewport update(CanvasViewport viewport, double dx, double dy) {
        if (!active) throw new IllegalStateException("pan is not active");
        return viewport.pan(dx, dy);
    }
    public boolean active() { return active; }
    public void end() { active = false; }
}
