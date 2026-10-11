package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.Map;

public final class SupportMagnitude {
    private SupportMagnitude(){}
    public static double tetherHealing(SkillExecutionContext context,double coefficient,double current,double maximum){
        var old=context.snapshot().modifiers();var increased=new java.util.ArrayList<>(old.increased());
        increased.add(context.compiledPlan().supportModifiers().healingIncreased(current,maximum));
        increased.add(context.gearSnapshot().percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_DONE));
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(increased,old.reduced(),old.more(),old.less());
        // Commit snapshot already includes mastery. Do not apply it for a second time per recipient.
        return new HealingCalculationService().direct(context.snapshot().basePower()
                +context.gearSnapshot().total(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_POWER),
                coefficient,context.snapshot().derivedStats().healingMultiplier(),1,0)*buckets.factor();
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
    public record ShieldCalculation(double finalCapacity, CycloneArmorFormula.Breakdown cyclone) { }

    public static CycloneArmorFormula.Breakdown cyclone(SkillExecutionContext context) {
        if (!"spirit_shield".equals(context.profile().skillId()))
            throw new IllegalArgumentException("NOT_CYCLONE_ARMOR");
        return CycloneArmorFormula.calculate(context.gearSnapshot().total(GearEffectSnapshot.Operator.HEALING_POWER),
                context.snapshot().derivedStats().effective(RpgAttribute.WIS),
                context.snapshot().derivedStats().effective(RpgAttribute.INT), context.effectiveSkillLevel());
    }

    public static ShieldCalculation shieldCalculation(SkillExecutionContext context,double mastery){
        double healingPower=20+context.gearSnapshot().total(GearEffectSnapshot.Operator.HEALING_POWER);
        CycloneArmorFormula.Breakdown cyclone="spirit_shield".equals(context.profile().skillId())?cyclone(context):null;
        double intrinsic=cyclone!=null?cyclone.intrinsicBarrier()
                :new HealingCalculationService().direct(healingPower,context.profile().support().coefficient(),
                        context.snapshot().derivedStats().healingMultiplier(),mastery,0);
        double capacity=intrinsic*context.snapshot().modifiers().factor()*context.compiledPlan().supportModifiers().barrierFactor()
                *(1+context.gearSnapshot().percent(GearEffectSnapshot.Operator.BARRIER_STRENGTH));
        if(!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_BARRIER_CAPACITY");
        return new ShieldCalculation(capacity,cyclone);
    }
    public static double shield(SkillExecutionContext context,double mastery){
        return shieldCalculation(context,mastery).finalCapacity();
    }
    public static Map<String,Object> cycloneTrace(SkillExecutionContext context,ShieldCalculation result){
        var c=result.cyclone();
        if(c==null)throw new IllegalArgumentException("NOT_CYCLONE_ARMOR");
        var stats=context.snapshot().derivedStats();
        return Map.ofEntries(
                Map.entry("target",context.target().entityId().toString()),
                Map.entry("rawWisdom",stats.raw(RpgAttribute.WIS)),
                Map.entry("effectiveWisdom",stats.effective(RpgAttribute.WIS)),
                Map.entry("rawIntelligence",stats.raw(RpgAttribute.INT)),
                Map.entry("effectiveIntelligence",stats.effective(RpgAttribute.INT)),
                Map.entry("startingEffectiveAttribute",c.startingEffective()),
                Map.entry("flatHealingPowerFromGear",context.gearSnapshot().total(GearEffectSnapshot.Operator.HEALING_POWER)),
                Map.entry("baseBarrier",c.baseBarrier()),
                Map.entry("wisdomContribution",c.wisdomContribution()),
                Map.entry("intelligenceContribution",c.intelligenceContribution()),
                Map.entry("effectiveSkillLevel",c.effectiveSkillLevel()),
                Map.entry("skillLevelFactor",c.skillLevelFactor()),
                Map.entry("intrinsicBarrier",c.intrinsicBarrier()),
                Map.entry("combatModifierFactor",context.snapshot().modifiers().factor()),
                Map.entry("supportBarrierFactor",context.compiledPlan().supportModifiers().barrierFactor()),
                Map.entry("gearBarrierStrengthPercent",context.gearSnapshot().percent(GearEffectSnapshot.Operator.BARRIER_STRENGTH)),
                Map.entry("finalCapacity",result.finalCapacity()));
    }
    public static double healing(SkillExecutionContext context,double mastery,double current,double maximum){
        var old=context.snapshot().modifiers();var increased=new java.util.ArrayList<>(old.increased());
        increased.add(context.compiledPlan().supportModifiers().healingIncreased(current,maximum));
        increased.add(context.gearSnapshot().percent(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_DONE));
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(increased,old.reduced(),old.more(),old.less());
        return new HealingCalculationService().direct(20+context.gearSnapshot().total(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_POWER),context.profile().support().coefficient(),
                context.snapshot().derivedStats().healingMultiplier(),mastery,0)*buckets.factor();
    }
}
