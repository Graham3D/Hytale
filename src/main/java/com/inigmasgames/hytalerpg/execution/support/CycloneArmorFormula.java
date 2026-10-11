package com.inigmasgames.hytalerpg.execution.support;

/** Intrinsic magnitude only; compiled barrier and gear factors are applied by the caller once. */
public final class CycloneArmorFormula {
    private CycloneArmorFormula() { }

    public static double capacity(double effectiveHealingPower,int effectiveLevel){
        if(!Double.isFinite(effectiveHealingPower)||effectiveHealingPower<0||effectiveLevel<1)
            throw new IllegalArgumentException("INVALID_CYCLONE_MAGNITUDE");
        double capacity=1.5*effectiveHealingPower*(1+0.0125*(effectiveLevel-1));
        if(!Double.isFinite(capacity)||capacity>Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_CYCLONE_CAPACITY");
        return capacity;
    }
}
