package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Presentation-only smooth interpolant and arc-length table. Never used by gameplay geometry. */
public final class HealingParticlePath {
    public static final int SUBDIVISIONS=8,MAX_BLIPS=128,PULSES=3;
    public static final double SPACING=.2,PULSE_SPEED=6;
    private final List<Vec3> points;
    private final double[] distance;
    public HealingParticlePath(List<Vec3> knots){
        if(knots.size()!=7)throw new IllegalArgumentException("HEAL_PATH_KNOTS");
        var values=new ArrayList<Vec3>(49);values.add(knots.getFirst());
        for(int i=0;i<6;i++){
            var a=knots.get(i);var b=knots.get(i+1);
            var ma=(i==0?b.subtract(a):b.subtract(knots.get(i-1)).multiply(.5));
            var mb=(i==5?b.subtract(a):knots.get(i+2).subtract(a).multiply(.5));
            for(int j=1;j<=SUBDIVISIONS;j++){
                double t=j/(double)SUBDIVISIONS,t2=t*t,t3=t2*t;
                values.add(j==SUBDIVISIONS?b:a.multiply(2*t3-3*t2+1).add(ma.multiply(t3-2*t2+t)).add(b.multiply(-2*t3+3*t2)).add(mb.multiply(t3-t2)));
            }
        }
        points=List.copyOf(values);distance=new double[points.size()];
        for(int i=1;i<points.size();i++){distance[i]=distance[i-1]+points.get(i).subtract(points.get(i-1)).length();if(!Double.isFinite(distance[i]))throw new IllegalArgumentException("HEAL_PATH_EXTENT");}
    }
    public double length(){return distance[distance.length-1];}
    public int blips(){return length()<1e-6?0:Math.min(MAX_BLIPS,Math.max(2,(int)Math.ceil(length()/SPACING-1e-9)+1));}
    public Vec3 at(double arc){
        if(!Double.isFinite(arc))throw new IllegalArgumentException("HEAL_PATH_DISTANCE");
        if(arc<=0)return points.getFirst();if(arc>=length())return points.getLast();
        int index=Arrays.binarySearch(distance,arc);if(index>=0)return points.get(index);
        index=-index-1;double width=distance[index]-distance[index-1];
        return width<1e-9?points.get(index):ElasticBeamTether.linear(points.get(index-1),points.get(index),(arc-distance[index-1])/width);
    }
}
