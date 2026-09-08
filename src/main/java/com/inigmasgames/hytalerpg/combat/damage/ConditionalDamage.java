package com.inigmasgames.hytalerpg.combat.damage;
import com.inigmasgames.hytalerpg.domain.HitConditionModifiers;
import java.util.Set;

/** Bounded immutable per-hit Gather input. Reuses the same additive/multiplicative buckets and
 * already-rolled crit. It cannot mutate Health, reroll crit, spend resources or spawn effects. */
public record ConditionalDamage(HitConditionModifiers conditions,ModifierBuckets buckets,double rawAfterCrit,double expectedAmount){
    public ConditionalDamage{
        if(conditions==null||buckets==null||!Double.isFinite(rawAfterCrit)||rawAfterCrit<0||!Double.isFinite(expectedAmount)||expectedAmount<0)
            throw new IllegalArgumentException("Invalid conditional damage input");
    }
    public static ConditionalDamage calculated(HitConditionModifiers conditions,ModifierBuckets buckets,DamageCalculationService.Result result,double critMultiplier){
        return !conditions.active()?null:new ConditionalDamage(conditions,buckets,result.skillRawDamage()*(result.critical()?critMultiplier:1),result.preMitigationDamage());
    }
    public double increased(double health,double maximum,Set<String> statuses){return conditions.increased(health,maximum,statuses);}
    public double amount(double increased){return increased==0?expectedAmount:rawAfterCrit*buckets.withIncreased(increased).factor();}
}
