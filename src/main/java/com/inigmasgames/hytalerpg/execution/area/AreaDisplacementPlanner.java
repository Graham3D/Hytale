package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

/** Bounded grounded pull/push using short swept, supported steps; never a velocity guessed to mean metres. */
public final class AreaDisplacementPlanner {
    private AreaDisplacementPlanner(){}
    public record Plan(Vec3 destination,double distance,String reason){}
    public static Plan plan(Vec3 position,Vec3 center,boolean pull,double requested,double controlScale,boolean grounded,
            ToDoubleBiFunction<Vec3,Vec3> collisionFraction,Predicate<Vec3> supported){
        if(!Double.isFinite(requested)||requested<0||requested>8||!Double.isFinite(controlScale)||controlScale<0||controlScale>1)
            throw new IllegalArgumentException("Invalid bounded area displacement");
        if(controlScale==0)return new Plan(position,0,"CONTROL_RESISTANT");
        if(!grounded||!supported.test(position))return new Plan(position,0,"NO_GROUNDED_ORIGIN");
        Vec3 offset=center.subtract(position);double toward=offset.horizontalLength();
        if(toward<1e-9)return new Plan(position,0,"AT_EFFECT_CENTER");
        double distance=pull?Math.min(requested*controlScale,toward):requested*controlScale;
        Vec3 direction=offset.horizontalNormalized().multiply(pull?1:-1),current=position;double remaining=distance;
        // At most32 swept segments plus the origin-support check. Current content needs <=18 segments.
        for(int step=0;remaining>1e-9&&step<32;step++){
            Vec3 segment=direction.multiply(Math.min(.25,remaining));double fraction=collisionFraction.applyAsDouble(current,segment);
            if(!Double.isFinite(fraction)||fraction<0||fraction>1)return new Plan(current,current.subtract(position).horizontalLength(),"COLLISION_UNAVAILABLE");
            Vec3 next=current.add(segment.multiply(Math.clamp(fraction,0,1)));
            if(!supported.test(next))return new Plan(current,current.subtract(position).horizontalLength(),"NO_SUPPORTED_PATH");
            current=next;
            if(fraction<1-1e-6)return new Plan(current,current.subtract(position).horizontalLength(),"NATIVE_COLLISION");
            remaining-=segment.horizontalLength();
        }
        return new Plan(current,current.subtract(position).horizontalLength(),"APPLIED");
    }
}
