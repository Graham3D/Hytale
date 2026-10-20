package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Compact deterministic formation; native adapter still validates ground, LOS, range and body space. */
public final class SummonFormation {
    private SummonFormation(){}
    public static List<Vec3> points(Vec3 centre,int count){
        if(count<1||count>8)throw new IllegalArgumentException("INVALID_SUMMON_FORMATION");
        if(count==1)return List.of(centre);
        double radius=Math.max(1.2,1.05/(2*Math.sin(Math.PI/count)));
        var points=new ArrayList<Vec3>();
        for(int i=0;i<count;i++){double angle=2*Math.PI*i/count;points.add(centre.add(new Vec3(radius*Math.cos(angle),0,radius*Math.sin(angle))));}
        return List.copyOf(points);
    }
}
