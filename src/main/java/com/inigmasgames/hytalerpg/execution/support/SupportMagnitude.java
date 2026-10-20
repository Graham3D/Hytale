package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;

public final class SupportMagnitude {
    private SupportMagnitude(){}
    public static double tetherHealing(SkillExecutionContext context,double coefficient,double current,double maximum){
        var old=context.snapshot().modifiers();var increased=new java.util.ArrayList<>(old.increased());
        increased.add(context.compiledPlan().supportModifiers().healingIncreased(current,maximum));
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(increased,old.reduced(),old.more(),old.less());
        // Commit snapshot already includes mastery. Do not apply it for a second time per recipient.
        return new HealingCalculationService().direct(context.snapshot().basePower(),coefficient,context.snapshot().derivedStats().healingMultiplier(),1,0)*buckets.factor();
    }
    public static com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage reflectionConditional(SkillExecutionContext context,double actualHealthLost,double amount){
        var conditions=context.compiledPlan().hitConditions();if(!conditions.active())return null;
        var p=context.profile().support();
        if(p==null||p.kind()!=SupportProfile.Kind.REFLECT&&p.kind()!=SupportProfile.Kind.THORNS)throw new IllegalArgumentException("NO_REFLECTION_PAYLOAD");
        double raw=actualHealthLost*p.coefficient()*(p.kind()==SupportProfile.Kind.THORNS?context.compiledPlan().supportModifiers().beneficialFactor():1);
        return new com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage(conditions,context.snapshot().modifiers(),raw,amount);
    }
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
