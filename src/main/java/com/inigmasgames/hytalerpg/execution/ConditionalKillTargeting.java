package com.inigmasgames.hytalerpg.execution;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Bounds;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
/** Selection only; each candidate solution must already satisfy native caster reach, ground and LOS. */
public final class ConditionalKillTargeting {
    private ConditionalKillTargeting(){}
    public record Target(UUID id,Bounds bounds,CommittedTarget solution,boolean alive,boolean hostile,boolean protectedTarget,boolean lineOfSight,boolean legalReach){}
    public static CommittedTarget nearest(UUID dead,Vec3 death,List<Target> candidates,boolean overflow){
        if(overflow||candidates.size()>256)throw new IllegalStateException("KILL_TRIGGER_CANDIDATE_BUDGET");
        var seen=new HashSet<UUID>();
        return candidates.stream().filter(t->t.id()!=null&&!t.id().equals(dead)&&seen.add(t.id())&&t.solution()!=null&&t.alive()&&t.hostile()&&!t.protectedTarget()&&t.lineOfSight()&&t.legalReach())
                .filter(t->ConnectionShape.pointDistanceSquared(death,t.bounds())<=144+1e-9)
                .min(Comparator.<Target>comparingDouble(t->ConnectionShape.pointDistanceSquared(death,t.bounds())).thenComparing(t->t.id().toString()))
                .map(Target::solution).orElse(null);
    }
}
