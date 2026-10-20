package com.inigmasgames.hytalerpg.execution.summon;

/** Consumes an existing owned actor or native corpse; never doubles as a spawn profile. */
public record SummonActionProfile(Kind kind,double range,double radius,double coefficient,
                                  double duration,double damageIncreased,double shieldFraction) {
    public enum Kind { CONSUME_MINION, CORPSE_BURST }
    public SummonActionProfile {
        if(kind==null)throw new IllegalArgumentException("Missing summon action");
        for(double value:new double[]{range,radius,coefficient,duration,damageIncreased,shieldFraction})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid summon action");
        if(range<=0||range>30||radius>8||duration>30||shieldFraction>1)throw new IllegalArgumentException("Unbounded summon action");
    }
}
