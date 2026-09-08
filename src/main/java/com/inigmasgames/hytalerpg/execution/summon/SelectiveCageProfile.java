package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Authoritative design geometry, NOT a registered native collider. Disabled by the master's explicit safety gate. */
public record SelectiveCageProfile(double range,double radius,int segments,double height,double thickness,
                                   double duration,double coefficient,double rootSeconds){
    public static final String BLOCKED_BOUNDARY="BONE_CAGE_NATIVE_ENEMY_ONLY_COLLISION_UNVERIFIED";
    public SelectiveCageProfile{
        if(segments!=12)throw new IllegalArgumentException("Bone Cage requires twelve boundary segments");
        for(double n:new double[]{range,radius,height,thickness,duration,coefficient,rootSeconds})
            if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Invalid cage profile");
    }
    public record Segment(Vec3 start,Vec3 end,double height,double thickness){}
    public List<Segment> boundary(Vec3 center){
        Objects.requireNonNull(center);var edges=new ArrayList<Segment>();
        for(int i=0;i<segments;i++){
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments;
            edges.add(new Segment(center.add(new Vec3(Math.cos(a)*radius,0,Math.sin(a)*radius)),
                    center.add(new Vec3(Math.cos(b)*radius,0,Math.sin(b)*radius)),height,thickness));
        }
        return List.copyOf(edges);
    }
}
