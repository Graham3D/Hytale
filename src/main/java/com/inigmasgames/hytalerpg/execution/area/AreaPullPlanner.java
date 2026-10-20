package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

/** Horizontal, swept, supported translation only. It never teleports an airborne NPC to the field. */
public final class AreaPullPlanner {
    private AreaPullPlanner() { }
    public record Plan(Vec3 destination, double distance, String reason) { }
    public static Plan plan(Vec3 position, Vec3 centre, double requested, double core, double controlScale,
            boolean grounded, ToDoubleBiFunction<Vec3,Vec3> collisionFraction, Predicate<Vec3> supported) {
        if (!Double.isFinite(requested) || !Double.isFinite(core) || !Double.isFinite(controlScale)
                || requested < 0 || requested > 4 || core < 0 || controlScale < 0 || controlScale > 1)
            throw new IllegalArgumentException("Invalid bounded pull");
        if (controlScale == 0) return new Plan(position,0,"CONTROL_RESISTANT");
        if (!grounded) return new Plan(position,0,"AIRBORNE");
        Vec3 toward=centre.subtract(position);double distance=Math.min(requested*controlScale,Math.max(0,toward.horizontalLength()-core));
        if (distance < 1e-9) return new Plan(position,0,"INSIDE_CORE");
        Vec3 direction=toward.horizontalNormalized();Vec3 current=position;double remaining=distance;
        while(remaining > 1e-9) {
            Vec3 segment=direction.multiply(Math.min(.25,remaining));
            double fraction=collisionFraction.applyAsDouble(current,segment);
            if(!Double.isFinite(fraction)) return new Plan(current,current.subtract(position).horizontalLength(),"COLLISION_UNAVAILABLE");
            Vec3 next=current.add(segment.multiply(Math.clamp(fraction,0,1)));
            if (!supported.test(next)) return new Plan(current,current.subtract(position).horizontalLength(),"NO_SUPPORTED_PATH");
            current=next;
            if(fraction < 1-1e-6) return new Plan(current,current.subtract(position).horizontalLength(),"NATIVE_COLLISION");
            remaining-=segment.horizontalLength();
        }
        return new Plan(current,current.subtract(position).horizontalLength(),"APPLIED");
    }
}
