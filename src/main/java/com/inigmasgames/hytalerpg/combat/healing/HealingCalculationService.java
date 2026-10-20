package com.inigmasgames.hytalerpg.combat.healing;

/** Damage conversion starts from observed Health loss, never calculated/absorbed/overkill damage. */
public final class HealingCalculationService {
    /** WIS and mastery apply once to the authored HealingPower baseline, never Magic/INT. */
    public double direct(double healingPower,double coefficient,double wisdomMultiplier,double masteryMultiplier,double increased){
        for(double value:new double[]{healingPower,coefficient,wisdomMultiplier,masteryMultiplier,increased})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid direct healing input");
        double result=healingPower*coefficient*wisdomMultiplier*masteryMultiplier*(1+increased);
        if(!Double.isFinite(result))throw new IllegalArgumentException("Direct healing overflow");
        return result;
    }
    public record Conversion(double actualHealthLost,double fraction,double baseHealing,double wisdomMultiplier,
                             double healingIncreased,double requestedHealing){ }
    public Conversion fromActualDamage(double lost,double fraction,double wisdomMultiplier,double healingIncreased){
        for(double value:new double[]{lost,fraction,wisdomMultiplier,healingIncreased})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid healing input");
        if(fraction>1)throw new IllegalArgumentException("Invalid conversion fraction");
        double base=lost*fraction,requested=base*wisdomMultiplier*(1+healingIncreased);
        if(!Double.isFinite(requested))throw new IllegalArgumentException("Healing overflow");
        // Do not reapply base power, INT, crit or the source's damage/repeat/area buckets to derived Health loss.
        return new Conversion(lost,fraction,base,wisdomMultiplier,healingIncreased,requested);
    }
}
