package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;

/** Low-angle vacuum solution for the authored gravity/speed, with bounded swept-path validation. */
public final class ProjectileBallistics {
    private ProjectileBallistics(){}
    public record Solution(Vec3 origin,Vec3 velocity,double gravity,double flightSeconds) {
        public Vec3 at(double t){return origin.add(velocity.multiply(t)).add(new Vec3(0,-.5*gravity*t*t,0));}
        public double pathLength(){
            double distance=0;Vec3 previous=origin;
            for(int i=1;i<=64;i++){Vec3 next=at(flightSeconds*i/64);distance+=Math.sqrt(next.distanceSquared(previous));previous=next;}
            return distance;
        }
        public boolean clear(java.util.function.BiPredicate<Vec3,Vec3> sweep) {
            int steps=(int)Math.ceil((velocity.length()+gravity*flightSeconds)*flightSeconds/.20);
            if(steps<1||steps>2048)throw new IllegalStateException("BALLISTIC_SWEEP_BUDGET");
            Vec3 last=origin;
            for(int i=1;i<=steps;i++){Vec3 next=at(flightSeconds*i/steps);if(!sweep.test(last,next))return false;last=next;}
            return true;
        }
    }
    /** Deterministic authored-time solution used by heavy arcing projectiles such as Fireball. */
    public static Optional<Solution> timed(Vec3 origin,Vec3 target,double horizontalSpeed,double gravity,
                                           double reach,double minimumSeconds,double maximumSeconds) {
        for(double n:new double[]{horizontalSpeed,gravity,reach,minimumSeconds,maximumSeconds})
            if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("INVALID_TIMED_BALLISTIC_PARAMETERS");
        if(maximumSeconds<minimumSeconds)throw new IllegalArgumentException("INVALID_TIMED_BALLISTIC_WINDOW");
        Vec3 delta=target.subtract(origin);double horizontal=delta.horizontalLength();
        if(horizontal<1e-6||horizontal>reach+1e-9)return Optional.empty();
        double seconds=Math.clamp(horizontal/horizontalSpeed,minimumSeconds,maximumSeconds);
        Vec3 horizontalVelocity=delta.horizontalNormalized().multiply(horizontal/seconds);
        double vertical=(delta.y()+.5*gravity*seconds*seconds)/seconds;
        Vec3 velocity=horizontalVelocity.add(new Vec3(0,vertical,0));
        if(!Double.isFinite(velocity.lengthSquared())||velocity.lengthSquared()<1e-12)return Optional.empty();
        return Optional.of(new Solution(origin,velocity,gravity,seconds));
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
