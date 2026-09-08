package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import java.util.*;

/** Immediate, nonrecursive strike children. Damage stays in the existing native damage port. */
public final class StrikeSecondaryRuntime {
    public record Hit<T>(StrikeGeometryService.Candidate<T> target,double preMitigation,double healthLost,boolean cancelled,double increasedUnit){
        public Hit(StrikeGeometryService.Candidate<T> target,double preMitigation,double healthLost,boolean cancelled){this(target,preMitigation,healthLost,cancelled,Double.NaN);}
    }
    public interface Port<T>{
        /** Must fail closed on spatial budget overflow and return only current hostile candidates. */
        List<StrikeGeometryService.Candidate<T>> candidates(Vec3 center,double radius);
        boolean lineOfSight(Vec3 origin,StrikeGeometryService.Candidate<T> target);
        default AreaGeometry.Bounds bounds(StrikeGeometryService.Candidate<T> target){return new AreaGeometry.Bounds(target.position(),target.position());}
        default List<StrikeGeometryService.Candidate<T>> burstCandidates(AreaGeometry geometry){return candidates(geometry.origin(),geometry.radius());}
        /** null resolvedAmount: calculate from inherited snapshot; otherwise submit exactly the resolved amount. */
        void damage(SkillExecutionContext child,StrikeGeometryService.Candidate<T> target,Double resolvedAmount);
        default void presentCleave(Vec3 origin,Vec3 facing,double range,double angle){}
        default void presentPhantom(Vec3 impact,Vec3 destination){}
        default void presentShockwave(AreaGeometry geometry){}
        default void rejected(String effect,String reason){}
    }
    public <T> int afterPrimary(SkillExecutionContext root,int hitIndex,Vec3 origin,Vec3 facing,List<Hit<T>> primary,Port<T> port){
        if(root.derivedRelease())return 0;
        var mods=root.compiledPlan().strikes();int count=0;
        Set<String> excluded=new HashSet<>();primary.forEach(hit->excluded.add(hit.target.stableId()));
        if(mods.cleavingEdge()&&hitIndex==0&&root.effects().once("CLEAVING_EDGE")){
            double range=3*root.compiledPlan().foundationModifiers().rangeFactor();
            double angle=80*(root.compiledPlan().foundationModifiers().concentration()?.7:1);
            var shape=new AreaGeometry(AreaGeometry.Kind.SECTOR,origin,facing,range,angle,0,0,2.5);
            var candidates=valid(port.candidates(origin,range),origin,excluded,port);int ordinal=0;
            try{port.presentCleave(origin,facing,range,angle);}catch(RuntimeException ignored){} // Cosmetics cannot reject a paid hit.
            for(var target:candidates.stream().filter(c->shape.intersects(port.bounds(c))).limit(4).toList()){
                var child=root.secondaryCopy("cleaving_edge",++ordinal,.60);
                if(root.compiledPlan().concentrationOnlyOnSecondary())child=child.withSnapshot(child.snapshot().withModifiers(child.snapshot().modifiers().withIncreased(.30)));
                String admission=root.effects().claim(child.skillInstanceId(),1,true);
                if(admission.equals("PASS")){port.damage(child,target,null);count++;}else port.rejected(child.skillInstanceId(),admission);
            }
        }
        if(mods.phantomReach()&&hitIndex==0&&!primary.isEmpty()){
            var hit=primary.getFirst();
            if(!hit.cancelled&&Double.isFinite(hit.healthLost)&&hit.healthLost>0&&Double.isFinite(hit.preMitigation)&&hit.preMitigation>=0&&root.effects().once("PHANTOM_REACH")){
                Vec3 impact=hit.target.position();
                var targets=valid(port.candidates(impact,3),impact,excluded,port).stream()
                        .filter(t->ConnectionShape.pointDistanceSquared(impact,port.bounds(t))<=9+1e-9).toList();
                if(!targets.isEmpty()){
                    var child=root.secondaryCopy("phantom_reach",1,.60);
                    String admission=root.effects().claim(child.skillInstanceId(),1,true);
                    if(admission.equals("PASS")){
                        port.damage(child,targets.getFirst(),hit.preMitigation*.60);count++;
                        try{port.presentPhantom(impact,targets.getFirst().position());}catch(RuntimeException ignored){}
                    }else port.rejected(child.skillInstanceId(),admission);
                }
            }
        }
        if(mods.shockwave()){
            var first=primary.stream().filter(hit->!hit.cancelled&&Double.isFinite(hit.healthLost)&&hit.healthLost>0&&Double.isFinite(hit.preMitigation)&&hit.preMitigation>=0).findFirst();
            if(first.isPresent()&&root.effects().once("SHOCKWAVE")){
                var hit=first.get();var plan=root.compiledPlan();
                double radius=3*plan.executionModifiers().radiusFactor()*(plan.foundationModifiers().concentration()?.7:1);
                var shape=new AreaGeometry(AreaGeometry.Kind.DISC,hit.target.position(),Vec3.FORWARD,radius,360,0,0,3);
                var child=root.secondaryCopy("shockwave",1,.40);String admission=root.effects().claim(child.skillInstanceId(),1,true);
                if(admission.equals("PASS")){
                    double amount=shockwaveAmount(plan,hit);
                    // One burst effect/secondary admission, not one allocation per radial target.
                    for(var target:valid(port.burstCandidates(shape),shape.origin(),Set.of(),port).stream().filter(t->shape.intersects(port.bounds(t))).limit(64).toList()){
                        port.damage(child,target,amount);count++;
                    }
                    try{port.presentShockwave(shape);}catch(RuntimeException ignored){}
                }else port.rejected(child.skillInstanceId(),admission);
            }
        }
        return count;
    }
    public static double shockwaveAmount(com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Hit<?> hit){
        double base=hit.preMitigation;
        if(plan.concentrationOnlyOnSecondary()){
            if(!Double.isFinite(hit.increasedUnit)||hit.increasedUnit<0)throw new IllegalArgumentException("RESOLVED_ADDITIVE_UNIT_UNAVAILABLE");
            base+=.30*hit.increasedUnit;
        }
        double result=base*.40*(plan.radiusOnlyOnShockwave()?.90:1);
        if(!Double.isFinite(result)||result<0||result>Float.MAX_VALUE)throw new IllegalArgumentException("SHOCKWAVE_DAMAGE_OVERFLOW");
        return result;
    }
    private static <T> List<StrikeGeometryService.Candidate<T>> valid(List<StrikeGeometryService.Candidate<T>> candidates,Vec3 origin,Set<String> excluded,Port<T> port){
        if(candidates.size()>256)return List.of();
        // Deterministic distance/UUID ties, and de-duplicate even if a spatial index repeats a handle.
        var unique=new TreeMap<String,StrikeGeometryService.Candidate<T>>();
        for(var c:candidates)if(c!=null&&c.damageable()&&!c.protectedTarget()&&!excluded.contains(c.stableId())&&port.lineOfSight(origin,c))unique.putIfAbsent(c.stableId(),c);
        return unique.values().stream().sorted(Comparator.<StrikeGeometryService.Candidate<T>>comparingDouble(c->ConnectionShape.pointDistanceSquared(origin,port.bounds(c))).thenComparing(StrikeGeometryService.Candidate::stableId)).toList();
    }
}
