package com.inigmasgames.hytalerpg.combat.damage;
import com.inigmasgames.hytalerpg.domain.HitConditionModifiers;
import java.util.Set;

/** Bounded immutable per-hit Gather input. Reuses the same additive/multiplicative buckets and
 * already-rolled crit. It cannot mutate Health, reroll crit, spend resources or spawn effects. */
public record ConditionalDamage(HitConditionModifiers conditions,ModifierBuckets buckets,double rawAfterCrit,double expectedAmount,VictimCoefficient victimCoefficient){
    public ConditionalDamage(HitConditionModifiers conditions,ModifierBuckets buckets,double rawAfterCrit,double expectedAmount){
        this(conditions,buckets,rawAfterCrit,expectedAmount,VictimCoefficient.NONE);
    }
    public ConditionalDamage{
        victimCoefficient=victimCoefficient==null?VictimCoefficient.NONE:victimCoefficient;
        if(conditions==null||buckets==null||!Double.isFinite(rawAfterCrit)||rawAfterCrit<0||!Double.isFinite(expectedAmount)||expectedAmount<0)
            throw new IllegalArgumentException("Invalid conditional damage input");
    }
    public static ConditionalDamage calculated(HitConditionModifiers conditions,ModifierBuckets buckets,DamageCalculationService.Result result,double critMultiplier){
        return calculated(conditions,buckets,result,critMultiplier,VictimCoefficient.NONE);
    }
    public static ConditionalDamage calculated(HitConditionModifiers conditions,ModifierBuckets buckets,DamageCalculationService.Result result,double critMultiplier,VictimCoefficient rule){
        return !conditions.active()&&rule==VictimCoefficient.NONE?null:new ConditionalDamage(conditions,buckets,result.skillRawDamage()*(result.critical()?critMultiplier:1),result.preMitigationDamage(),rule);
    }
    public boolean active(){return conditions.active()||victimCoefficient!=VictimCoefficient.NONE;}
    public double increased(double health,double maximum,Set<String> statuses){return conditions.increased(health,maximum,statuses);}
    public double amount(double increased){return increased==0?expectedAmount:rawAfterCrit*buckets.withIncreased(increased).factor();}
}
