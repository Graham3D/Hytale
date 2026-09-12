package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Arrays;
import java.util.List;

/** Bounded critically-damped visual tether. It never participates in gameplay geometry. */
public final class ElasticBeamTether {
    public static final int PIECES=6;
    private static final double SPRING=64;
    private static final double DAMPING=16;
    private static final double MAX_STEP=1d/60;
    private static final double MAX_ELAPSED=.10;
    private static final double MAX_LAG=1.35;
    private static final double MAX_SPEED=12;
    private final Vec3[] points=new Vec3[PIECES+1];
    private final Vec3[] velocities=new Vec3[PIECES+1];
    private double updatedAt=Double.NaN;

    public List<Vec3> update(Vec3 start,Vec3 end,double now){
        if(start==null||end==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid elastic tether frame");
        if(Double.isNaN(updatedAt)||now<updatedAt){
            for(int i=0;i<=PIECES;i++){points[i]=linear(start,end,i/(double)PIECES);velocities[i]=Vec3.ZERO;}
            updatedAt=now;return List.copyOf(Arrays.asList(points.clone()));
        }
        double elapsed=Math.min(MAX_ELAPSED,Math.max(0,now-updatedAt));
        int steps=Math.max(1,(int)Math.ceil(elapsed/MAX_STEP));double dt=elapsed/steps;
        for(int step=0;step<steps&&dt>0;step++)for(int i=1;i<PIECES;i++){
            double t=i/(double)PIECES;var desired=linear(start,end,t);var point=points[i];var velocity=velocities[i];
            var acceleration=desired.subtract(point).multiply(SPRING).subtract(velocity.multiply(DAMPING));
            velocity=clamp(velocity.add(acceleration.multiply(dt)),MAX_SPEED);
            point=point.add(velocity.multiply(dt));
            var offset=point.subtract(desired);double lag=offset.length();
            if(lag>MAX_LAG)point=desired.add(offset.multiply(MAX_LAG/lag));
            if(point.distanceSquared(desired)<1e-6&&velocity.lengthSquared()<4e-4){point=desired;velocity=Vec3.ZERO;}
            points[i]=point;velocities[i]=velocity;
        }
        points[0]=start;points[PIECES]=end;velocities[0]=Vec3.ZERO;velocities[PIECES]=Vec3.ZERO;updatedAt=now;
        return List.copyOf(Arrays.asList(points.clone()));
    }

    public static Vec3 linear(Vec3 start,Vec3 end,double t){return start.multiply(1-t).add(end.multiply(t));}
    private static Vec3 clamp(Vec3 value,double maximum){double length=value.length();return length>maximum?value.multiply(maximum/length):value;}
}
