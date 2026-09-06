package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.CanvasInputBackend;
import com.inigmasgames.canvasui.api.CanvasInputCapabilities;

/** Capability declaration for Hytale 0.7.0-pre.1's public server CustomUI surface. */
public final class HytaleCustomUiInputBackend implements CanvasInputBackend {
    public static final HytaleCustomUiInputBackend INSTANCE = new HytaleCustomUiInputBackend();
    private static final CanvasInputCapabilities CAPABILITIES = new CanvasInputCapabilities(
            false, false, false, false, false, true, false, true);
    private HytaleCustomUiInputBackend() { }
    @Override public String id() { return "hytale-customui-0.7.0-pre.1"; }
    @Override public CanvasInputCapabilities capabilities() { return CAPABILITIES; }
}
