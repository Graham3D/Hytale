package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Component-local, finite secondary effects. Native callers still validate every target and apply real damage. */
public final class ProjectileSecondaryEffects {
    public enum TerminalCause { RANGE, LIFETIME, ENEMY, TERRAIN, RETURN_CAUGHT, CANCELLED, BUDGET_REJECTED, WORLD_UNLOAD }
    public static final class Burst {
        private final String id;private final AreaGeometry geometry;private final double coefficientFactor;
        private final Set<String> victims=new HashSet<>();
        private Burst(String id,AreaGeometry geometry,double factor){this.id=id;this.geometry=geometry;this.coefficientFactor=factor;}
        public String id(){return id;} public AreaGeometry geometry(){return geometry;} public double coefficientFactor(){return coefficientFactor;}
        public boolean canProc(){return false;}
        public boolean acceptTarget(String id){return id!=null&&!id.isBlank()&&victims.size()<64&&victims.add(id);}
    }
    public record BurstResult(String reason,Optional<Burst> burst) { }
    private final ProjectileLifecycleRegistry registry;
    public ProjectileSecondaryEffects(ProjectileLifecycleRegistry registry){this.registry=registry;}
    public BurstResult afterDamage(ProjectileInstance parent,CompiledSkillPlan compiled,Vec3 point,double healthLost) {
        if(!Double.isFinite(healthLost)||healthLost<=0||!parent.spend("SHRAPNEL"))return new BurstResult("NO_DAMAGING_TRIGGER",Optional.empty());
        String id=parent.plan().projectileInstanceId()+"/shrapnel",admission=registry.reserveSecondary(parent,id);
        if(!admission.equals("PASS"))return new BurstResult(admission,Optional.empty());
        double radius=2.5*compiled.executionModifiers().radiusFactor()*(compiled.foundationModifiers().concentration()?.7:1);
        double coefficient=.5*(compiled.radiusOnlyOnShrapnel()?.9:1);
        return new BurstResult("PASS",Optional.of(new Burst(id,new AreaGeometry(AreaGeometry.Kind.DISC,
                point.add(new Vec3(0,-1.5,0)),Vec3.FORWARD,radius,360,0,0,3),coefficient)));
    }
    public ProjectileContinuation.Decision terminal(ProjectileInstance parent,Vec3 point,TerminalCause cause,long now) {
        if(registry.get(parent.plan().projectileInstanceId()).filter(value->value==parent).isEmpty())return end(parent,"UNKNOWN_PROJECTILE_ROOT");
        if(cause==TerminalCause.CANCELLED||cause==TerminalCause.BUDGET_REJECTED||cause==TerminalCause.WORLD_UNLOAD
                ||parent.termination().isPresent()||!parent.spend("SPLINTERBURST"))return end(parent,cause.name());
        if(parent.plan().generation()>=3)return end(parent,"MAX_GENERATION");
        var children=new ArrayList<ProjectileInstance>();var original=parent.plan();
        double distance=parent.originalMaxDistance()*.5,speed=original.velocity().length();
        // Never restore spent continuation credits. Each new carrier owns its own direct-hit ledger and half-distance Return leg.
        for(int index=0;index<3;index++) {
            var budgets=new HashMap<>(parent.budgets());budgets.put("SPLINTERBURST",0);budgets.put("IS_LAUNCH",0);
            budgets.put("SHRAPNEL",original.remainingContinuationBudgets().getOrDefault("SHRAPNEL",0));
            var plan=new ProjectileExecutionPlan(original.rootCastId(),original.skillInstanceId(),original.projectileInstanceId()+"/splinter-"+index,
                    original.ownerId(),original.skillId(),original.compiledPlanHash(),original.snapshot().withMagnitudeFactor(.35),original.generation()+1,
                    budgets,0,original.remainingTriggeredSecondaries(),now,original.configId(),point,
                    ProjectileContinuation.yaw(parent.direction(),(index-1)*25).multiply(speed),original.radius(),distance,
                    Math.min(distance/speed,parent.originalMaxLifetimeSeconds()));
            children.add(new ProjectileInstance(plan));
        }
        try{registry.registerTriggeredAll(children);}catch(IllegalStateException error){return end(parent,error.getMessage());}
        return new ProjectileContinuation.Decision(ProjectileContinuation.Action.SPLINTERBURST,parent.direction(),children,cause.name());
    }
    private static ProjectileContinuation.Decision end(ProjectileInstance instance,String reason) {
        return new ProjectileContinuation.Decision(ProjectileContinuation.Action.TERMINATE,instance.direction(),List.of(),reason);
    }
}
