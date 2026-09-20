package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.NodeDefinition;

/** Resolves compatible content nodes from live layout geometry. Authority decides replacement semantics. */
public final class TreeDropResolver {
    public static final double SNAP_MARGIN = 18.0;

    public Result resolve(Canvas canvas, CursorCanvasEditor.LibraryKind kind, CanvasPoint screenPoint) {
        CanvasPoint point = canvas.viewport().toCanvas(screenPoint);
        Candidate best = null;
        for (CanvasNode node : canvas.nodes()) {
            String required = kind == CursorCanvasEditor.LibraryKind.SKILL ? "skill" : "passive";
            if (!required.equals(node.type())) continue;
            NodeDefinition type = canvas.definition().nodeType(node.type());
            double left=node.position().x(), top=node.position().y(), right=left+type.width(), bottom=top+type.height();
            double dx=Math.max(left-point.x(), Math.max(0, point.x()-right));
            double dy=Math.max(top-point.y(), Math.max(0, point.y()-bottom));
            double boundsDistance=Math.hypot(dx,dy);
            if (boundsDistance > SNAP_MARGIN) continue;
            CanvasPoint center=canvas.viewport().toScreen(CanvasPoint.of(left+type.width()/2.0,top+type.height()/2.0));
            double centerDistance=Math.hypot(screenPoint.x()-center.x(),screenPoint.y()-center.y());
            Candidate candidate=new Candidate(node,center,boundsDistance,centerDistance);
            if (best==null || candidate.compareTo(best)<0) best=candidate;
        }
        if (best==null) return Result.rejected("No compatible node near release point");
        return new Result(true,best.node.nodeId(),best.center,"PASS");
    }

    private record Candidate(CanvasNode node, CanvasPoint center, double boundsDistance, double centerDistance)
            implements Comparable<Candidate> {
        @Override public int compareTo(Candidate other) {
            int under=Double.compare(boundsDistance==0?0:1,other.boundsDistance==0?0:1);
            return under!=0?under:Double.compare(centerDistance,other.centerDistance);
        }
    }
    public record Result(boolean accepted,String nodeId,CanvasPoint center,String reason) {
        static Result rejected(String reason){return new Result(false,null,null,reason);}
    }
}
