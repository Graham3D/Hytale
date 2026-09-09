package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.function.*;

/** Bounded server target selection/steering; caller supplies live faction/LOS-validated targets. */
public final class ProjectileHoming {
    private String targetId;
    private double nextAcquisition=Double.NEGATIVE_INFINITY,lastUpdate=Double.NaN;
    public record Target(String id,Vec3 point,boolean visible) { }
    public record Update(Vec3 direction,String targetId,boolean reacquired) { }
    public static boolean withinAuthoredLock(Target target,Vec3 committedOrigin,double range) {
        if(committedOrigin==null||!Double.isFinite(range)||range<=0)throw new IllegalArgumentException("INVALID_HOMING_LOCK_RANGE");
        return target!=null&&target.visible()&&target.point().distanceSquared(committedOrigin)<=range*range+1e-9;
    }
    /** Authored lock policy: selected entity first; otherwise nearest valid enemy to committed aim. */
    public Update authored(double now,Vec3 point,Vec3 direction,String selectedId,Vec3 aim,ProjectilePattern pattern,
                           Supplier<List<Target>> candidates,Function<String,Optional<Target>> live) {
        if(!Double.isFinite(now)||pattern.homingTurnDegrees()<=0||aim==null)throw new IllegalArgumentException("INVALID_AUTHORED_HOMING");
        double delta=Double.isNaN(lastUpdate)?0:Math.max(0,now-lastUpdate);lastUpdate=Double.isNaN(lastUpdate)?now:Math.max(lastUpdate,now);
        boolean reacquired=false;
        if(now+1e-9>=nextAcquisition){
            var selected=selectedId==null?Optional.<Target>empty():live.apply(selectedId).filter(Target::visible);
            if(selected.isPresent())targetId=selectedId;
            else {
                var options=candidates.get();if(options.size()>64)throw new IllegalStateException("HOMING_CANDIDATE_BUDGET");
                targetId=options.stream().filter(Target::visible).filter(t->t.point().distanceSquared(aim)<=pattern.acquisitionRadius()*pattern.acquisitionRadius()+1e-9)
                        .sorted(Comparator.comparingDouble((Target t)->t.point().distanceSquared(aim)).thenComparing(Target::id))
                        .map(Target::id).findFirst().orElse(null);
            }
            nextAcquisition=now+.10;reacquired=true;
        }
        var selected=targetId==null?Optional.<Target>empty():live.apply(targetId).filter(Target::visible);
        if(selected.isEmpty()){targetId=null;return new Update(direction,null,reacquired);}
        Vec3 deltaTarget=selected.get().point().subtract(point);
        return new Update(deltaTarget.lengthSquared()<1e-12?direction:turn(direction,deltaTarget,Math.toRadians(pattern.homingTurnDegrees())*delta),targetId,reacquired);
    }
    public Update update(double now,Vec3 point,Vec3 direction,Supplier<List<Target>> candidates,Function<String,Optional<Target>> live) {
        if(!Double.isFinite(now))throw new IllegalArgumentException("Invalid homing clock");
        double delta=Double.isNaN(lastUpdate)?0:Math.max(0,now-lastUpdate);lastUpdate=Double.isNaN(lastUpdate)?now:Math.max(lastUpdate,now);
        boolean reacquired=false;
        if(now+1e-9>=nextAcquisition) {
            var options=candidates.get();if(options.size()>64)throw new IllegalStateException("HOMING_CANDIDATE_BUDGET");
            targetId=options.stream().filter(t->eligible(point,direction,t))
                    .sorted(Comparator.comparingDouble((Target t)->t.point.distanceSquared(point)).thenComparing(Target::id))
                    .map(Target::id).findFirst().orElse(null);
            nextAcquisition=now+.10;reacquired=true;
        }
        var selected=targetId==null?Optional.<Target>empty():live.apply(targetId);
        if(selected.isEmpty()||!selected.get().visible) {targetId=null;return new Update(direction,targetId,reacquired);}
        Vec3 towards=selected.get().point.subtract(point);
        if(towards.lengthSquared()<1e-12)return new Update(direction,targetId,reacquired);
        return new Update(turn(direction,towards,Math.toRadians(120)*delta),targetId,reacquired);
    }
    private static boolean eligible(Vec3 point,Vec3 direction,Target target) {
        if(!target.visible)return false;Vec3 delta=target.point.subtract(point);double distance=delta.length();
        return distance>1e-9&&distance<=8+1e-9&&dot(direction.normalized(),delta.multiply(1/distance))>=.5-1e-9;
    }
    public static Vec3 turn(Vec3 from,Vec3 to,double maxAngle) {
        if(!Double.isFinite(maxAngle)||maxAngle<0)throw new IllegalArgumentException("Invalid turn cap");
        from=from.normalized();to=to.normalized();double cosine=Math.clamp(dot(from,to),-1,1),angle=Math.acos(cosine);
        if(angle<=maxAngle+1e-12)return to;if(maxAngle==0)return from;
        Vec3 side=to.subtract(from.multiply(cosine));
        if(side.lengthSquared()<1e-12)side=Math.abs(from.y())<.9?new Vec3(-from.z(),0,from.x()):new Vec3(0,-from.z(),from.y());
        return from.multiply(Math.cos(maxAngle)).add(side.normalized().multiply(Math.sin(maxAngle))).normalized();
    }
    private static double dot(Vec3 a,Vec3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
}
