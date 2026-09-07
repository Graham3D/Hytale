package com.inigmasgames.hytalerpg.combat.healing;

/** Damage conversion starts from observed Health loss, never calculated/absorbed/overkill damage. */
public final class HealingCalculationService {
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
