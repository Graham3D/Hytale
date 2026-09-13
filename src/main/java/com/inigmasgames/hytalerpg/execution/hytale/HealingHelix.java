package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** One rotation-minimizing strand. Carries its source frame across time and transports it along arc. */
public final class HealingHelix {
    public static final double RADIUS=.175,PITCH=1.35,TURNS_PER_SECOND=.875,FLOW_SPEED=2;
    private Vec3 previousTangent,previousNormal;
    private double elapsed,flow;
    public void sample(HealingParticlePath path,int count,double dt,Vec3[] output){
        elapsed=(elapsed+dt)%8; // .875 turns/sec repeats in eight seconds; phase never grows without bound.
        double length=path.length(),step=length/(count-1);
        flow=(flow+FLOW_SPEED*dt)%step;
        Vec3 tangent=tangent(path,0),normal=previousNormal==null?seed(tangent):transport(previousNormal,previousTangent,tangent);
        previousTangent=tangent;previousNormal=normal;
        // Pin the authoritative endpoints. Only interior samples flow; keep exactly the existing count.
        output[0]=path.at(0);output[count-1]=path.at(length);
        for(int i=1;i<count-1;i++){
            double arc=(i*step+flow)%length;
            var next=tangent(path,arc);normal=transport(normal,tangent,next);tangent=next;
            var binormal=cross(tangent,normal).normalized();
            double theta=arc/PITCH*2*Math.PI-elapsed*TURNS_PER_SECOND*2*Math.PI;
            // Taper the first/last half metre so the strand terminates precisely at the anchors.
            double radius=RADIUS*Math.min(1,Math.min(arc,length-arc)/.5);
            output[i]=path.at(arc).add(normal.multiply(Math.cos(theta)*radius)).add(binormal.multiply(Math.sin(theta)*radius));
        }
    }
    public static Vec3 tangent(HealingParticlePath path,double arc){
        double h=Math.min(.025,path.length()/100);
        return path.at(Math.min(path.length(),arc+h)).subtract(path.at(Math.max(0,arc-h))).normalized();
    }
    private static Vec3 seed(Vec3 t){
        Vec3 axis=Math.abs(t.x())<.8?new Vec3(1,0,0):new Vec3(0,1,0);
        return axis.subtract(t.multiply(dot(axis,t))).normalized();
    }
    public static Vec3 transport(Vec3 normal,Vec3 oldTangent,Vec3 tangent){
        double cosine=Math.max(-1,Math.min(1,dot(oldTangent,tangent)));
        var axis=cross(oldTangent,tangent);double sine=axis.length();
        Vec3 rotated=normal;
        if(sine>1e-8){
            axis=axis.multiply(1/sine);
            rotated=normal.multiply(cosine).add(cross(axis,normal).multiply(sine)).add(axis.multiply(dot(axis,normal)*(1-cosine)));
        }
        // At exact reversal rotate around the previous normal: it remains valid, without a world-up reset.
        var projected=rotated.subtract(tangent.multiply(dot(rotated,tangent)));
        return projected.lengthSquared()<1e-12?seed(tangent):projected.normalized();
    }
    private static double dot(Vec3 a,Vec3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Vec3 cross(Vec3 a,Vec3 b){return new Vec3(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}
