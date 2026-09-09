package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;

/** Low-angle vacuum solution for the authored gravity/speed, with bounded swept-path validation. */
public final class ProjectileBallistics {
    private ProjectileBallistics(){}
    public record Solution(Vec3 origin,Vec3 velocity,double gravity,double flightSeconds) {
        public Vec3 at(double t){return origin.add(velocity.multiply(t)).add(new Vec3(0,-.5*gravity*t*t,0));}
        public boolean clear(java.util.function.BiPredicate<Vec3,Vec3> sweep) {
            int steps=(int)Math.ceil((velocity.length()+gravity*flightSeconds)*flightSeconds/.20);
            if(steps<1||steps>2048)throw new IllegalStateException("BALLISTIC_SWEEP_BUDGET");
            Vec3 last=origin;
            for(int i=1;i<=steps;i++){Vec3 next=at(flightSeconds*i/steps);if(!sweep.test(last,next))return false;last=next;}
            return true;
        }
    }
    public static Optional<Solution> low(Vec3 origin,Vec3 target,double speed,double gravity,double reach,double maximumSeconds) {
        for(double n:new double[]{speed,gravity,reach,maximumSeconds})if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("INVALID_BALLISTIC_PARAMETERS");
        var delta=target.subtract(origin);double d=delta.horizontalLength(),v2=speed*speed;
        if(d<1e-6||d>reach+1e-9)return Optional.empty();
        double discriminant=v2*v2-gravity*(gravity*d*d+2*delta.y()*v2);
        if(!Double.isFinite(discriminant)||discriminant<0)return Optional.empty();
        double tangent=(v2-Math.sqrt(discriminant))/(gravity*d),cos=1/Math.sqrt(1+tangent*tangent);
        double time=d/(speed*cos);
        if(!Double.isFinite(time)||time>maximumSeconds+1e-9)return Optional.empty();
        Vec3 velocity=delta.horizontalNormalized().multiply(speed*cos).add(new Vec3(0,speed*cos*tangent,0));
        return Optional.of(new Solution(origin,velocity,gravity,time));
    }
}
