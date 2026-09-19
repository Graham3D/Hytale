package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.BiPredicate;

/** Shared Fireball ballistic solution and bounded preview sampling. */
public final class FireballTrajectory {
    public static final int PREVIEW_SAMPLES=16;
    private FireballTrajectory(){}
    public static Optional<ProjectileBallistics.Solution> solve(Vec3 origin,Vec3 target,ProjectileMotion motion,double reach,double speedFactor){
        if(motion==null||!motion.timedBallistic())throw new IllegalArgumentException("FIREBALL_TIMED_MOTION_REQUIRED");
        return ProjectileBallistics.timed(origin,target,motion.horizontalSpeed()*speedFactor,motion.gravity(),reach,
                motion.minimumTravelSeconds()/speedFactor,motion.maximumTravelSeconds()/speedFactor);
    }
    public static Preview preview(ProjectileBallistics.Solution solution,double radius,BiPredicate<Vec3,Vec3> clear){
        Objects.requireNonNull(solution);Objects.requireNonNull(clear);
        if(!Double.isFinite(radius)||radius<=0)throw new IllegalArgumentException("INVALID_FIREBALL_PREVIEW_RADIUS");
        var points=new ArrayList<Vec3>(PREVIEW_SAMPLES+1);points.add(solution.origin());
        Vec3 prior=solution.origin();boolean impact=false;
        for(int i=1;i<=PREVIEW_SAMPLES;i++){
            Vec3 next=solution.at(solution.flightSeconds()*i/PREVIEW_SAMPLES);
            if(!clear.test(prior,next)){
                Vec3 low=prior,high=next;
                for(int n=0;n<8;n++){Vec3 mid=low.add(high).multiply(.5);if(clear.test(prior,mid))low=mid;else high=mid;}
                points.add(low);impact=true;break;
            }
            points.add(next);prior=next;
        }
        return new Preview(List.copyOf(points),points.getLast(),impact);
    }
    public record Preview(List<Vec3> points,Vec3 impact,boolean blocked){
        public Preview{points=List.copyOf(points);if(points.size()<2||points.size()>PREVIEW_SAMPLES+1||impact==null)throw new IllegalArgumentException("INVALID_FIREBALL_PREVIEW");}
    }
}
