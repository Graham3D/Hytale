package com.inigmasgames.hytalerpg.execution;

/** Shared typed cast-rate input. It deliberately has no cooldown, tick, projectile, or lifetime output. */
public record CastRateModifiers(double passiveRate,double equipmentRate,double temporaryRate) {
    public CastRateModifiers {
        if(!valid(passiveRate)||!valid(equipmentRate)||!valid(temporaryRate))throw new IllegalArgumentException("INVALID_CAST_RATE");
    }
    public static final CastRateModifiers NONE=new CastRateModifiers(0,0,0);
    public double total(){return passiveRate+equipmentRate+temporaryRate;}
    public double windup(double authoredSeconds){
        if(!Double.isFinite(authoredSeconds)||authoredSeconds<0)throw new IllegalArgumentException("INVALID_WINDUP");
        return authoredSeconds==0?0:Math.max(.05,authoredSeconds/(1+total()));
    }
    private static boolean valid(double value){return Double.isFinite(value)&&value>=0&&value<=10;}
}
