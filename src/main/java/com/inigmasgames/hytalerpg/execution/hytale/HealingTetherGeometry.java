package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Endpoint-controlled presentation geometry. No targeting, distance validation or healing decisions. */
public final class HealingTetherGeometry {
    public static final double FLOW_SPEED=4; // Reference stock sparks use +4 forward velocity.
    public static final int MAX_SPANS=30; // Six core spans plus two wrapped highlights, each at most twelve spans.
    public record Span(Vec3 from,Vec3 to,boolean highlight){}
    public static List<Span> frame(List<Vec3> points,double elapsed){
        if(points.size()!=ElasticBeamTether.PIECES+1||!Double.isFinite(elapsed)||elapsed<0)throw new IllegalArgumentException("Invalid tether geometry frame");
        double[] distance=new double[points.size()];var result=new ArrayList<Span>();
        for(int i=1;i<points.size();i++){
            double length=points.get(i).subtract(points.get(i-1)).length();
            if(!Double.isFinite(length))throw new IllegalArgumentException("Invalid tether span");
            distance[i]=distance[i-1]+length;
            if(length>1e-8)result.add(new Span(points.get(i-1),points.get(i),false));
        }
        double length=distance[distance.length-1];if(length<1e-6)return List.copyOf(result);
        double width=Math.min(.65,length*.15);
        for(int pulse=0;pulse<2;pulse++){
            double head=((elapsed%(length/FLOW_SPEED))*FLOW_SPEED+pulse*length*.5)%length;
            addInterval(result,points,distance,Math.max(0,head-width),head);
            if(head<width)addInterval(result,points,distance,length+head-width,length);
        }
        if(result.size()>MAX_SPANS)throw new IllegalStateException("Tether span capacity");
        return List.copyOf(result);
    }
    private static void addInterval(List<Span> result,List<Vec3> points,double[] distance,double from,double to){
        for(int i=1;i<points.size();i++){
            double start=Math.max(from,distance[i-1]),end=Math.min(to,distance[i]),length=distance[i]-distance[i-1];
            if(length<1e-8||end-start<1e-8)continue;
            result.add(new Span(ElasticBeamTether.linear(points.get(i-1),points.get(i),(start-distance[i-1])/length),
                ElasticBeamTether.linear(points.get(i-1),points.get(i),(end-distance[i-1])/length),true));
        }
    }
    private HealingTetherGeometry(){}
}
