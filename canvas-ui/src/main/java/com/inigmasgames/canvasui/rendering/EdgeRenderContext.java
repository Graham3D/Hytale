package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.EdgeStyle;

public record EdgeRenderContext(CanvasPoint sourceScreenPoint, CanvasPoint targetScreenPoint,
                                EdgeStyle style, boolean preview, boolean valid) { }
