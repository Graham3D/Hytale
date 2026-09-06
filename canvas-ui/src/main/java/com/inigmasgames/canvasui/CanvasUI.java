package com.inigmasgames.canvasui;

import com.inigmasgames.canvasui.runtime.CanvasService;

/** Stable entry point used by consuming mods after the CanvasUI plugin is enabled. */
public final class CanvasUI {
    public static final String REVISION = "R007";
    private static volatile CanvasService service;

    private CanvasUI() { }

    public static CanvasService service() {
        CanvasService current = service;
        if (current == null) throw new IllegalStateException("CanvasUI plugin is not enabled");
        return current;
    }

    static void install(CanvasService value) { service = value; }
    static void uninstall(CanvasService value) { if (service == value) service = null; }
}
