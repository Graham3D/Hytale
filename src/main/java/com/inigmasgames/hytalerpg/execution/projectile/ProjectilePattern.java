package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.*;

/** Authored generation-zero multiplicity is not a triggered child or another paid cast. */
public record ProjectilePattern(int count,double horizontalDegrees,double verticalDegrees,double intervalSeconds,
                                double homingTurnDegrees,double acquisitionRadius,boolean ballisticAim) {
    public static final ProjectilePattern SINGLE = new ProjectilePattern(1,0,0,0,0,0,false);
    public ProjectilePattern {
        for(double n:new double[]{horizontalDegrees,verticalDegrees,intervalSeconds,homingTurnDegrees,acquisitionRadius})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("INVALID_AUTHORED_PROJECTILE_PATTERN");
        if(count<1||count>8||horizontalDegrees>180||verticalDegrees>90||intervalSeconds>.5||homingTurnDegrees>180
                ||acquisitionRadius>8||(homingTurnDegrees==0)!=(acquisitionRadius==0)
                ||count==1&&(horizontalDegrees>0||verticalDegrees>0||intervalSeconds>0)
                ||ballisticAim&&(count!=1||homingTurnDegrees>0))
            throw new IllegalArgumentException("INVALID_AUTHORED_PROJECTILE_PATTERN");
    }
    public int rootLaunches(CompiledSkillPlan plan) {
        return Math.multiplyExact(count,plan.projectileModifiers().rootLaunches(plan.executionModifiers().echoDelaySeconds()>0));
    }
    public double procCoefficient(CompiledSkillPlan plan) { return 1d/(count*plan.projectileModifiers().batchSize()); }
    public Vec3 direction(Vec3 forward,int index) {
        if(index<0||index>=count)throw new IllegalArgumentException("PATTERN_INDEX_OUT_OF_RANGE");
        if(count==1)return forward.normalized();
        // Two rows, mirrored pairs. Every stratum is deterministic; no random spread or aim bias.
        int columns=(count+1)/2,row=index%2,column=index/2;
        double yaw=columns==1?0:((column+.5)/columns-.5)*horizontalDegrees;
        double pitch=(row==0?-1:1)*verticalDegrees*.25;
        Vec3 center=ProjectileContinuation.yaw(forward,yaw).normalized();
        Vec3 right=Math.abs(center.y())<.999?new Vec3(-center.z(),0,center.x()).normalized():new Vec3(1,0,0);
        Vec3 up=new Vec3(right.y()*center.z()-right.z()*center.y(),right.z()*center.x()-right.x()*center.z(),right.x()*center.y()-right.y()*center.x());
        double angle=Math.toRadians(pitch);
        return center.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))).normalized();
    }
}
