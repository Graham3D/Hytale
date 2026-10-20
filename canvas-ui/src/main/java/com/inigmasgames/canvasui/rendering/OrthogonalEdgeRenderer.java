package com.inigmasgames.canvasui.rendering;

import com.inigmasgames.canvasui.api.CanvasPoint;

import java.util.List;

/** Efficient spline-like fallback made from two horizontal and one vertical segment. */
public final class OrthogonalEdgeRenderer implements EdgeRenderer {
    @Override
    public List<EdgeSegment> render(EdgeRenderContext context) {
        CanvasPoint source = context.sourceScreenPoint();
        CanvasPoint target = context.targetScreenPoint();
        double elbowX = (source.x() + target.x()) / 2.0;
        return List.of(
                new EdgeSegment(source, CanvasPoint.of(elbowX, source.y())),
                new EdgeSegment(CanvasPoint.of(elbowX, source.y()), CanvasPoint.of(elbowX, target.y())),
                new EdgeSegment(CanvasPoint.of(elbowX, target.y()), target));
    }
}
