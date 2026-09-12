package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import java.util.ArrayList;

/** Connected AA QA falsified +Z velocity: the visible native beam flows along -Z.
 * Geometry/target ownership is unchanged; only the presentation basis is reversed. */
public final class NativeBeamTransform {
    private NativeBeamTransform() { }
    public static Rotation3f rotation(Vec3 from, Vec3 to) {
        var d=to.subtract(from).normalized().multiply(-1);
        return new Rotation3f((float)-Math.asin(Math.clamp(d.y(),-1,1)),
                (float)Math.atan2(d.x(),d.z()),0);
    }
    public record Sample(Vec3 position,Rotation3f rotation){}
    public static Vec3 curve(Vec3 from,Vec3 to,double t){
        double sag=Math.min(1.2,to.subtract(from).length()*.12);
        var control=from.add(to).multiply(.5).add(new Vec3(0,-2*sag,0));
        return from.multiply((1-t)*(1-t)).add(control.multiply(2*t*(1-t))).add(to.multiply(t*t));
    }
    /** Bounded tangent-oriented quadratic stream, not a new gameplay/LOS spline. */
    public static List<Sample> stream(Vec3 from,Vec3 to){
        double length=to.subtract(from).length();if(length<1e-5)return List.of();
        int count=Math.clamp((int)Math.ceil(length/.75),2,24);
        var values=new ArrayList<Sample>(count);
        for(int i=0;i<count;i++){
            double t=(double)i/count;var p=curve(from,to,t);
            var next=curve(from,to,Math.min(1,t+.001));
            values.add(new Sample(p,rotation(p,next)));
        }
        return List.copyOf(values);
    }
    public static List<Vec3> samples(Vec3 from, Vec3 to) {
        var delta=to.subtract(from);
        if(delta.lengthSquared()<1e-10)return List.of();
        int count=Math.clamp((int)Math.ceil(delta.length()/2),1,9);
        var result=new ArrayList<Vec3>(count);
        // Do not emit past the target anchor: the native emitter itself extends forward.
        for(int i=0;i<count;i++)result.add(from.add(delta.multiply((double)i/count)));
        return List.copyOf(result);
    }
}
