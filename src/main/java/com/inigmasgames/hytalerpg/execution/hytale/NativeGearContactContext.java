package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable accepted-action facts attached to the exact frozen managed hit before native submission. */
public record NativeGearContactContext(GearCombatEffects.Hit hit,UUID actorId,UUID worldId,String canonicalSkill,
        double itemProcCoefficient,Map<RpgStatusType,Double> skillChances,
        Map<PeriodicStatusRuntime.Kind,PeriodicProfile> periodicProfiles,
        com.inigmasgames.hytalerpg.execution.SkillExecutionContext skillContext) {
    public record PeriodicProfile(double damagePerSecond,double durationSeconds,int addedStacks,int sourceCap) {
        public PeriodicProfile {
            if(!Double.isFinite(damagePerSecond)||damagePerSecond<=0||!Double.isFinite(durationSeconds)
                    ||durationSeconds<=0||durationSeconds>120||addedStacks<1||sourceCap<1||sourceCap>12)
                throw new IllegalArgumentException("INVALID_ACCEPTED_PERIODIC_PROFILE");
        }
    }
    public NativeGearContactContext {
        Objects.requireNonNull(hit);Objects.requireNonNull(actorId);Objects.requireNonNull(worldId);
        if(canonicalSkill==null||canonicalSkill.isBlank()||!Double.isFinite(itemProcCoefficient)
                ||itemProcCoefficient<0||itemProcCoefficient>1)throw new IllegalArgumentException("INVALID_NATIVE_GEAR_CONTACT");
        skillChances=Map.copyOf(skillChances);periodicProfiles=Map.copyOf(periodicProfiles);
        if(skillContext!=null&&(!skillContext.request().actorId().equals(actorId)
                ||!skillContext.rootCastId().equals(hit.rootId())))
            throw new IllegalArgumentException("FOREIGN_ACCEPTED_SKILL_CONTEXT");
        if(skillChances.values().stream().anyMatch(v->!Double.isFinite(v)||v<0||v>1))
            throw new IllegalArgumentException("INVALID_ACCEPTED_SKILL_CHANCE");
    }
    /** Ordinary managed weapon attacks use the frozen channel vector and canonical shared status timings. */
    public static NativeGearContactContext basic(GearCombatEffects.Hit hit,UUID actorId,UUID worldId,double procCoefficient,
            double burnSeconds,double poisonSeconds) {
        double physical=hit.amount(GearCombatEffects.Channel.PHYSICAL);
        double earth=hit.amount(GearCombatEffects.Channel.EARTH);
        double fire=hit.amount(GearCombatEffects.Channel.FIRE);
        var profiles=new java.util.EnumMap<PeriodicStatusRuntime.Kind,PeriodicProfile>(PeriodicStatusRuntime.Kind.class);
        if(physical>0)profiles.put(PeriodicStatusRuntime.Kind.BLEED,new PeriodicProfile(physical*.12,4,1,1));
        if(fire>0)profiles.put(PeriodicStatusRuntime.Kind.BURN,new PeriodicProfile(fire*.10,burnSeconds,1,1));
        if(physical+earth>0)profiles.put(PeriodicStatusRuntime.Kind.POISON,
                new PeriodicProfile((physical+earth)*.06,poisonSeconds,1,3));
        return new NativeGearContactContext(hit,actorId,worldId,"GEAR_BASIC_ATTACK",procCoefficient,Map.of(),profiles,null);
    }
    public NativeGearContactContext fromSkill(com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        if(context==null||!context.request().actorId().equals(actorId)||!context.rootCastId().equals(hit.rootId()))
            throw new IllegalArgumentException("FOREIGN_ACCEPTED_SKILL_CONTEXT");
        var profiles=new java.util.EnumMap<PeriodicStatusRuntime.Kind,PeriodicProfile>(PeriodicStatusRuntime.Kind.class);
        profiles.putAll(periodicProfiles);
        double power=context.snapshot().basePower();
        for(var kind:new PeriodicStatusRuntime.Kind[]{PeriodicStatusRuntime.Kind.BURN,PeriodicStatusRuntime.Kind.POISON}){
            var original=profiles.get(kind);
            if(original!=null&&power>0){
                double coefficient=kind==PeriodicStatusRuntime.Kind.BURN?.10:.06;
                profiles.put(kind,new PeriodicProfile(power*coefficient,original.durationSeconds(),
                        original.addedStacks(),original.sourceCap()));
            }
        }
        var chances=new java.util.EnumMap<RpgStatusType,Double>(RpgStatusType.class);
        chances.putAll(skillChances);
        String status=context.profile().strike()!=null?context.profile().strike().statusId():
                context.profile().projectile()!=null?context.profile().projectile().statusId():"";
        if(java.util.Set.of("BLEED","BURN","POISON","CHILL","SLOW","STUN","SILENCE","BLIND","FEAR")
                .contains(status))chances.put(RpgStatusType.valueOf(status),1d);
        var projectile=context.profile().projectile();
        if(projectile!=null&&projectile.hasPeriodicStatus()&&power>0){
            var kind=PeriodicStatusRuntime.Kind.valueOf(projectile.statusId());
            var application=context.compiledPlan().dots().application(kind,
                    projectile.periodicCoefficient()/projectile.periodicIntervalSeconds());
            profiles.put(kind,new PeriodicProfile(power*application.coefficientPerSecond(),
                    projectile.statusSeconds(),application.addedStacks(),application.sourceCap()));
        }
        return new NativeGearContactContext(hit,actorId,worldId,context.profile().skillId(),itemProcCoefficient,
                chances,profiles,context);
    }
}
