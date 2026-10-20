package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.CanvasPoint;

/** Axis-aligned current-backend segment. A future backend can replace this renderer. */
public record EdgeSegment(CanvasPoint start, CanvasPoint end) { }
