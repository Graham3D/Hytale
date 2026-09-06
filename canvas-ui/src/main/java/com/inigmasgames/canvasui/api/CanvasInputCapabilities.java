package com.inigmasgames.canvasui.api;

/** Explicit feature declaration so consumers can degrade intentionally. */
public record CanvasInputCapabilities(boolean supportsPointerMove, boolean supportsPointerCapture,
        boolean supportsPrimaryDrag, boolean supportsMiddleDrag, boolean supportsWheel,
        boolean supportsRightClick, boolean supportsModifierKeys, boolean supportsTextInput) { }
