package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Immediate, nonrecursive strike children. Damage stays in the existing native damage port. */
public final class StrikeSecondaryRuntime {
    public record Hit<T>(StrikeGeometryService.Candidate<T> target,double preMitigation,double healthLost,boolean cancelled){}
    public interface Port<T>{
        /** Must fail closed on spatial budget overflow and return only current hostile candidates. */
        List<StrikeGeometryService.Candidate<T>> candidates(Vec3 center,double radius);
        boolean lineOfSight(Vec3 origin,StrikeGeometryService.Candidate<T> target);
        /** null resolvedAmount: calculate from inherited snapshot; otherwise submit exactly the resolved amount. */
        void damage(SkillExecutionContext child,StrikeGeometryService.Candidate<T> target,Double resolvedAmount);
        default void presentCleave(Vec3 origin,Vec3 facing,double range,double angle){}
        default void presentPhantom(Vec3 impact,Vec3 destination){}
        default void rejected(String effect,String reason){}
    }
    public <T> int afterPrimary(SkillExecutionContext root,int hitIndex,Vec3 origin,Vec3 facing,List<Hit<T>> primary,Port<T> port){
        if(root.derivedRelease())return 0;
        var mods=root.compiledPlan().strikes();int count=0;
        Set<String> excluded=new HashSet<>();primary.forEach(hit->excluded.add(hit.target.stableId()));
        if(mods.cleavingEdge()&&hitIndex==0&&root.effects().once("CLEAVING_EDGE")){
            double range=3*root.compiledPlan().foundationModifiers().rangeFactor();
            double angle=80*(root.compiledPlan().foundationModifiers().concentration()?.7:1);
            var shape=new Stage04SkillProfile.Strike(Stage04SkillProfile.Geometry.ARC,range,angle,0,1,0,4,0,"",0);
            var candidates=valid(port.candidates(origin,range),origin,excluded,port);int ordinal=0;
            try{port.presentCleave(origin,facing,range,angle);}catch(RuntimeException ignored){} // Cosmetics cannot reject a paid hit.
            for(var target:new StrikeGeometryService().query(origin,facing,shape,candidates).accepted()){
                var child=root.secondaryCopy("cleaving_edge",++ordinal,.60);
                String admission=root.effects().claim(child.skillInstanceId(),1,true);
                if(admission.equals("PASS")){port.damage(child,target,null);count++;}else port.rejected(child.skillInstanceId(),admission);
            }
        }
        if(mods.phantomReach()&&hitIndex==0&&!primary.isEmpty()){
            var hit=primary.getFirst();
            if(!hit.cancelled&&Double.isFinite(hit.healthLost)&&hit.healthLost>0&&Double.isFinite(hit.preMitigation)&&hit.preMitigation>=0&&root.effects().once("PHANTOM_REACH")){
                Vec3 impact=hit.target.position();
                var targets=valid(port.candidates(impact,3),impact,excluded,port).stream()
                        .filter(t->t.position().distanceSquared(impact)<=9+1e-9).toList();
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
        return count;
    }
    private static <T> List<StrikeGeometryService.Candidate<T>> valid(List<StrikeGeometryService.Candidate<T>> candidates,Vec3 origin,Set<String> excluded,Port<T> port){
        if(candidates.size()>256)return List.of();
        // Deterministic distance/UUID ties, and de-duplicate even if a spatial index repeats a handle.
        var unique=new TreeMap<String,StrikeGeometryService.Candidate<T>>();
        for(var c:candidates)if(c!=null&&c.damageable()&&!c.protectedTarget()&&!excluded.contains(c.stableId())&&port.lineOfSight(origin,c))unique.putIfAbsent(c.stableId(),c);
        return unique.values().stream().sorted(Comparator.<StrikeGeometryService.Candidate<T>>comparingDouble(c->c.position().distanceSquared(origin)).thenComparing(StrikeGeometryService.Candidate::stableId)).toList();
    }
}
