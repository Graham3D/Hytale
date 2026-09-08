package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;

public final class SupportMagnitude {
    private SupportMagnitude(){}
    public static double wardReflection(SkillExecutionContext context,double absorbed){
        if(!Double.isFinite(absorbed)||absorbed<0)throw new IllegalArgumentException("Invalid actual absorption");
        return context.compiledPlan().supportModifiers().reflectiveWard()?absorbed*.2:0;
    }
    public static double shield(SkillExecutionContext context,double mastery){
        return new HealingCalculationService().direct(20,context.profile().support().coefficient(),
                context.snapshot().derivedStats().healingMultiplier(),mastery,0)*context.snapshot().modifiers().factor()*context.compiledPlan().supportModifiers().barrierFactor();
    }
    public static double healing(SkillExecutionContext context,double mastery,double current,double maximum){
        var old=context.snapshot().modifiers();var increased=new java.util.ArrayList<>(old.increased());
        increased.add(context.compiledPlan().supportModifiers().healingIncreased(current,maximum));
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(increased,old.reduced(),old.more(),old.less());
        return new HealingCalculationService().direct(20,context.profile().support().coefficient(),
                context.snapshot().derivedStats().healingMultiplier(),mastery,0)*buckets.factor();
    }
}
