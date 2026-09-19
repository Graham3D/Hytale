package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.CanvasInputBackend;
import com.inigmasgames.canvasui.api.CanvasInputCapabilities;

/**
 * Candidate passive-HUD input backend. R041 connected evidence proves pointer
 * move/button delivery. Primary graph drag remains conservatively unclaimed
 * until the R042 connected gate passes.
 */
public final class HytaleCursorHudInputBackend implements CanvasInputBackend {
    public static final HytaleCursorHudInputBackend INSTANCE = new HytaleCursorHudInputBackend();
    private static final CanvasInputCapabilities CAPABILITIES = new CanvasInputCapabilities(
            true, false, false, false, false, false, false, false);
    private HytaleCursorHudInputBackend() { }
    @Override public String id() { return "hytale-cursor-hud-0.7.0-pre.2-candidate"; }
    @Override public CanvasInputCapabilities capabilities() { return CAPABILITIES; }
    public String captureSemantics() { return "SESSION_LOGICAL_CAPTURE"; }
}
