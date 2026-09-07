package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Ordered continuation decisions after the hit payload. No cost, damage, asset lookup or native mutation. */
public final class ProjectileContinuation {
    public record Candidate(String id,Vec3 point,boolean visible) { }
    public enum Action { PIERCE, FORK, CHAIN, RETURN, RICOCHET, TERMINATE, RETURN_CONTINUE }
    public record Decision(Action action,Vec3 direction,List<ProjectileInstance> children,String reason,Vec3 surfaceNormal) {
        public Decision(Action action,Vec3 direction,List<ProjectileInstance> children,String reason) {this(action,direction,children,reason,Vec3.ZERO);}
        public Decision { children=List.copyOf(children); }
    }
    private final ProjectileLifecycleRegistry registry;
    public ProjectileContinuation(ProjectileLifecycleRegistry registry){this.registry=registry;}
    public Decision afterEnemy(ProjectileInstance instance,Vec3 point,Vec3 caster,List<Candidate> candidates,long now) {
        if(instance.returning())return decision(Action.RETURN_CONTINUE,instance,"RETURN_HIT");
        if(instance.remainingDistance()<=1e-6||instance.remainingSeconds()<=1e-6)return forwardEnd(instance,point,caster,"FORWARD_BUDGET_EXHAUSTED");
        if(instance.spend("PIERCE"))return decision(Action.PIERCE,instance,"PIERCE_CREDIT");
        if(instance.spend("FORK")) {
            if(instance.plan().generation()>=3)return decision(Action.TERMINATE,instance,"MAX_GENERATION");
            var children=new ArrayList<ProjectileInstance>();
            for(int index=0;index<2;index++) {
                var parent=instance.plan();var budgets=new HashMap<>(instance.budgets());budgets.put("IS_LAUNCH",0);budgets.put("FORK",0);
                Vec3 direction=yaw(instance.direction(),index==0?-20:20);double speed=parent.velocity().length();
                var plan=new ProjectileExecutionPlan(parent.rootCastId(),parent.skillInstanceId(),parent.projectileInstanceId()+"/fork-"+index,
                        parent.ownerId(),parent.skillId(),parent.compiledPlanHash(),parent.snapshot(),parent.generation()+1,budgets,
                        0,parent.remainingTriggeredSecondaries(),now,parent.configId(),point,direction.multiply(speed),parent.radius(),
                        instance.remainingDistance(),instance.remainingSeconds());
                var child=new ProjectileInstance(plan);child.inheritVisited(instance);children.add(child);
            }
            try {registry.registerAll(children);}catch(IllegalStateException error){return decision(Action.TERMINATE,instance,error.getMessage());}
            return new Decision(Action.FORK,instance.direction(),children,"FORK_DEPTH_ONE");
        }
        if(instance.remaining("CHAIN")>0) {
            var selected=candidates.stream().filter(c->c.visible&&!instance.previouslyHit(c.id))
                    .filter(c->c.point.distanceSquared(point)>1e-8 && c.point.distanceSquared(point)<=64+1e-9)
                    .sorted(Comparator.comparingDouble((Candidate c)->c.point.distanceSquared(point)).thenComparing(Candidate::id)).findFirst();
            if(selected.isPresent()) {
                instance.spend("CHAIN");instance.redirect(selected.get().point.subtract(point));
                return decision(Action.CHAIN,instance,"CHAIN_TARGET_"+selected.get().id);
            }
        }
        return forwardEnd(instance,point,caster,"ENEMY_CONTINUATION_EXHAUSTED");
    }
    public Decision forwardEnd(ProjectileInstance instance,Vec3 point,Vec3 caster,String reason) {
        if(!instance.returning() && instance.beginReturn(point,caster))return decision(Action.RETURN,instance,"RETURN_BEGIN_"+reason);
        return decision(Action.TERMINATE,instance,reason);
    }
    public Decision afterTerrain(ProjectileInstance instance,Vec3 point,Vec3 caster,Vec3 actualNormal) {
        if(!instance.returning()&&instance.remainingDistance()>1e-6&&instance.remainingSeconds()>1e-6&&instance.remaining("RICOCHET")>0) {
            if(!instance.bounceIntervalReady())return decision(Action.TERMINATE,instance,"RICOCHET_MIN_INTERVAL");
            if(instance.bounce(actualNormal))return new Decision(Action.RICOCHET,instance.direction(),List.of(),"TERRAIN_BOUNCE_CREDIT",actualNormal.normalized());
            return decision(Action.TERMINATE,instance,"INVALID_TERRAIN_NORMAL");
        }
        return forwardEnd(instance,point,caster,"TERRAIN_CONTINUATION_EXHAUSTED");
    }
    private static Decision decision(Action action,ProjectileInstance instance,String reason){return new Decision(action,instance.direction(),List.of(),reason);}
    public static Vec3 yaw(Vec3 direction,double degrees) {
        double r=Math.toRadians(degrees),c=Math.cos(r),s=Math.sin(r);
        return new Vec3(direction.x()*c+direction.z()*s,direction.y(),direction.z()*c-direction.x()*s).normalized();
    }
}
