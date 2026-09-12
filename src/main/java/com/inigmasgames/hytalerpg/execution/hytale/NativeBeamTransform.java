package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import java.util.ArrayList;

/** Beam_Heal_Green3 authors offsets and positive velocity along local +Z.
 * Hytale lookAt faces -Z, so it is deliberately not used for this particle. */
public final class NativeBeamTransform {
    private NativeBeamTransform() { }
    public static Rotation3f rotation(Vec3 from, Vec3 to) {
        var d=to.subtract(from).normalized();
        return new Rotation3f((float)-Math.asin(Math.clamp(d.y(),-1,1)),
                (float)Math.atan2(d.x(),d.z()),0);
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
