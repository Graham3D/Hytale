package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.*;

/** Authored generation-zero multiplicity is not a triggered child or another paid cast. */
public record ProjectilePattern(int count,double horizontalDegrees,double verticalDegrees,double intervalSeconds,
                                double homingTurnDegrees,double acquisitionRadius,boolean ballisticAim,int minimumCount) {
    public static final ProjectilePattern SINGLE = new ProjectilePattern(1,0,0,0,0,0,false,1);
    public ProjectilePattern(int count,double horizontalDegrees,double verticalDegrees,double intervalSeconds,
                             double homingTurnDegrees,double acquisitionRadius,boolean ballisticAim) {
        this(count,horizontalDegrees,verticalDegrees,intervalSeconds,homingTurnDegrees,acquisitionRadius,ballisticAim,count);
    }
    public ProjectilePattern {
        if(minimumCount==0)minimumCount=count;
        for(double n:new double[]{horizontalDegrees,verticalDegrees,intervalSeconds,homingTurnDegrees,acquisitionRadius})
            if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("INVALID_AUTHORED_PROJECTILE_PATTERN");
        if(count<1||count>8||minimumCount<1||minimumCount>count||horizontalDegrees>180||verticalDegrees>90||intervalSeconds>.5||homingTurnDegrees>180
                ||acquisitionRadius>8||(homingTurnDegrees==0)!=(acquisitionRadius==0)
                ||count==1&&(horizontalDegrees>0||verticalDegrees>0||intervalSeconds>0)
                ||ballisticAim&&(count!=1||homingTurnDegrees>0))
            throw new IllegalArgumentException("INVALID_AUTHORED_PROJECTILE_PATTERN");
    }
    public int selectedCount(String rootCastId) {
        if(rootCastId==null||rootCastId.isBlank())throw new IllegalArgumentException("ROOT_ID_REQUIRED");
        return minimumCount==count?count:minimumCount+Math.floorMod(rootCastId.hashCode(),count-minimumCount+1);
    }
    public int rootLaunches(CompiledSkillPlan plan) {
        return Math.multiplyExact(count,plan.projectileModifiers().rootLaunches(plan.executionModifiers().echoDelaySeconds()>0));
    }
    /** Exact root reservation after deterministic variable-count selection. */
    public int selectedRootLaunches(CompiledSkillPlan plan,String rootCastId) {
        return Math.multiplyExact(selectedCount(rootCastId),plan.projectileModifiers().rootLaunches(plan.executionModifiers().echoDelaySeconds()>0));
    }
    public double procCoefficient(CompiledSkillPlan plan) { return 1d/(count*plan.projectileModifiers().batchSize()); }
    public Vec3 direction(Vec3 forward,int index) {
        return direction(forward,index,count);
    }
    public Vec3 direction(Vec3 forward,int index,int selectedCount) {
        if(selectedCount<minimumCount||selectedCount>count||index<0||index>=selectedCount)throw new IllegalArgumentException("PATTERN_INDEX_OUT_OF_RANGE");
        if(selectedCount==1)return forward.normalized();
        // Two rows, mirrored pairs. Every stratum is deterministic; no random spread or aim bias.
        int columns=(selectedCount+1)/2,row=index%2,column=index/2;
        double yaw=columns==1?0:((column+.5)/columns-.5)*horizontalDegrees;
        double pitch=(row==0?-1:1)*verticalDegrees*.25;
        Vec3 center=ProjectileContinuation.yaw(forward,yaw).normalized();
        Vec3 right=Math.abs(center.y())<.999?new Vec3(-center.z(),0,center.x()).normalized():new Vec3(1,0,0);
        Vec3 up=new Vec3(right.y()*center.z()-right.z()*center.y(),right.z()*center.x()-right.x()*center.z(),right.x()*center.y()-right.y()*center.x());
        double angle=Math.toRadians(pitch);
        return center.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))).normalized();
    }
    /** Replay-stable authored scatter: visually random without making server outcomes nondeterministic. */
    public Vec3 randomizedDirection(Vec3 forward,int index,int selectedCount,String rootCastId){
        if(selectedCount<minimumCount||selectedCount>count||index<0||index>=selectedCount||rootCastId==null||rootCastId.isBlank())
            throw new IllegalArgumentException("PATTERN_RANDOM_INDEX_OUT_OF_RANGE");
        var random=new SplittableRandom(Objects.hash(rootCastId,index,selectedCount));
        double yaw=(random.nextDouble()-.5)*horizontalDegrees;
        double pitch=(random.nextDouble()-.5)*verticalDegrees;
        Vec3 center=ProjectileContinuation.yaw(forward,yaw).normalized();
        Vec3 right=Math.abs(center.y())<.999?new Vec3(-center.z(),0,center.x()).normalized():new Vec3(1,0,0);
        Vec3 up=new Vec3(right.y()*center.z()-right.z()*center.y(),right.z()*center.x()-right.x()*center.z(),right.x()*center.y()-right.y()*center.x());
        double angle=Math.toRadians(pitch);
        return center.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))).normalized();
    }
}
