package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;

import java.util.ArrayList;
import java.util.List;

/** Continuous orthogonal edge geometry shared by rendering and deterministic hit testing. */
public final class TreeLinkGeometry {
    public static final double HIT_TOLERANCE = 10.0;

    public List<Segment> route(Canvas canvas, CanvasEdge edge) {
        CanvasPoint a=port(canvas,edge.sourceNodeId(),edge.sourcePortId());
        CanvasPoint b=port(canvas,edge.targetNodeId(),edge.targetPortId());
        double mid=(a.x()+b.x())/2.0;
        List<Segment> result=new ArrayList<>(3);
        add(result,a,CanvasPoint.of(mid,a.y()));
        add(result,CanvasPoint.of(mid,a.y()),CanvasPoint.of(mid,b.y()));
        add(result,CanvasPoint.of(mid,b.y()),b);
        return List.copyOf(result);
    }

    public String hit(Canvas canvas, CanvasPoint pointer) {
        String best=null; double distance=Double.MAX_VALUE;
        for(CanvasEdge edge:canvas.edges()) for(Segment segment:route(canvas,edge)) {
            double candidate=distance(pointer,segment);
            if(candidate<=HIT_TOLERANCE && candidate<distance){distance=candidate;best=edge.edgeId();}
        }
        return best;
    }

    public static CanvasPoint port(Canvas canvas,String nodeId,String portId){
        CanvasNode node=canvas.node(nodeId);
        CanvasPort port=canvas.definition().nodeType(node.type()).port(portId);
        return canvas.viewport().toScreen(node.position().add(port.anchorPosition().x(),port.anchorPosition().y()));
    }
    private static void add(List<Segment> target,CanvasPoint a,CanvasPoint b){if(!a.equals(b))target.add(new Segment(a,b));}
    private static double distance(CanvasPoint p,Segment s){
        double vx=s.end.x()-s.start.x(),vy=s.end.y()-s.start.y();
        double length=vx*vx+vy*vy;
        if(length==0)return Math.hypot(p.x()-s.start.x(),p.y()-s.start.y());
        double t=Math.max(0,Math.min(1,((p.x()-s.start.x())*vx+(p.y()-s.start.y())*vy)/length));
        return Math.hypot(p.x()-(s.start.x()+t*vx),p.y()-(s.start.y()+t*vy));
    }
    public record Segment(CanvasPoint start,CanvasPoint end){
        public double left(){return Math.min(start.x(),end.x());}
        public double top(){return Math.min(start.y(),end.y());}
        public double width(){return Math.abs(end.x()-start.x());}
        public double height(){return Math.abs(end.y()-start.y());}
    }
}
