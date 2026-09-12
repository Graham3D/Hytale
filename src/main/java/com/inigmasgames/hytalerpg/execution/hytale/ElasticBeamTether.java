package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Arrays;
import java.util.List;

/** Bounded critically-damped visual tether. It never participates in gameplay geometry. */
public final class ElasticBeamTether {
    public static final int PIECES=6;
    private static final double OMEGA=16;
    private static final double MAX_ELAPSED=.25;
    private static final double MAX_SPEED=12;
    private final Vec3[] points=new Vec3[PIECES+1];
    private final Vec3[] velocities=new Vec3[PIECES+1];
    private double updatedAt=Double.NaN;

    public List<Vec3> update(Vec3 start,Vec3 end,double now){
        if(start==null||end==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid elastic tether frame");
        double length=end.subtract(start).length();
        if(!Double.isFinite(length))throw new IllegalArgumentException("Invalid tether extent");
        if(Double.isNaN(updatedAt)||now<updatedAt||now-updatedAt>MAX_ELAPSED||length<1e-6
                ||start.distanceSquared(points[0])>16||end.distanceSquared(points[PIECES])>16){
            for(int i=0;i<=PIECES;i++){points[i]=linear(start,end,i/(double)PIECES);velocities[i]=Vec3.ZERO;}
            updatedAt=now;return List.copyOf(Arrays.asList(points.clone()));
        }
        double dt=Math.max(0,now-updatedAt);
        // Closed-form critically damped response to each frame's fresh line: bounded work, no catch-up loop.
        for(int i=1;i<PIECES;i++){
            double t=i/(double)PIECES;var desired=linear(start,end,t);var point=points[i];var velocity=velocities[i];
            var displacement=point.subtract(desired);var response=velocity.add(displacement.multiply(OMEGA));
            double decay=Math.exp(-OMEGA*dt);
            point=desired.add(displacement.add(response.multiply(dt)).multiply(decay));
            velocity=clamp(velocity.subtract(response.multiply(OMEGA*dt)).multiply(decay),MAX_SPEED);
            var offset=point.subtract(desired);double lag=offset.length();
            double envelope=Math.min(.5,.08*length)*Math.sin(Math.PI*t);
            if(lag>envelope){point=desired.add(offset.multiply(envelope/lag));velocity=Vec3.ZERO;}
            if(point.distanceSquared(desired)<1e-6&&velocity.lengthSquared()<4e-4){point=desired;velocity=Vec3.ZERO;}
            points[i]=point;velocities[i]=velocity;
        }
        points[0]=start;points[PIECES]=end;velocities[0]=Vec3.ZERO;velocities[PIECES]=Vec3.ZERO;updatedAt=now;
        return List.copyOf(Arrays.asList(points.clone()));
    }

    public static Vec3 linear(Vec3 start,Vec3 end,double t){return start.multiply(1-t).add(end.multiply(t));}
    private static Vec3 clamp(Vec3 value,double maximum){double length=value.length();return length>maximum?value.multiply(maximum/length):value;}
}
