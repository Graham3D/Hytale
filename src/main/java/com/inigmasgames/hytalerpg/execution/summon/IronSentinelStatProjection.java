package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.Objects;

/** Iron Sentinel's pure chassis and source-local composition; native combat owns mitigation. */
public final class IronSentinelStatProjection {
    private IronSentinelStatProjection() {}
    public record Stats(int effectiveLevel,double skillScale,double nativeMaxHealth,double sourceHealth,double finalMaxHealth,
                        double nativePhysicalMin,double nativePhysicalMax,double sourcePhysicalMin,double sourcePhysicalMax,
                        double finalPhysicalMin,double finalPhysicalMax,double nativeProtection,double sourceProtection,
                        double finalProtection,double attackRateMultiplier,double attackInterval) {
        public Stats {
            for(double value:new double[]{skillScale,nativeMaxHealth,sourceHealth,finalMaxHealth,nativePhysicalMin,
                    nativePhysicalMax,sourcePhysicalMin,sourcePhysicalMax,finalPhysicalMin,finalPhysicalMax,
                    nativeProtection,sourceProtection,finalProtection,attackRateMultiplier,attackInterval})
                if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite Sentinel projection");
        }
    }
    public static Stats project(int effectiveLevel,GearInstance bound,double nativeInterval){
        return project(effectiveLevel,new GearEffectSnapshot(java.util.List.of(bound)),nativeInterval);
    }
    public static Stats project(int effectiveLevel,GearEffectSnapshot boundSource,double nativeInterval){
        Objects.requireNonNull(boundSource);
        if(boundSource.items().size()!=1)throw new IllegalArgumentException("Sentinel requires one bound source item");
        GearInstance bound=boundSource.items().getFirst();
        Objects.requireNonNull(bound);
        if(effectiveLevel<1||nativeInterval<=0||!Double.isFinite(nativeInterval))
            throw new IllegalArgumentException("Invalid Sentinel rank or native interval");
        if(bound.category()!=GearCatalog.Category.ARMOR&&bound.category()!=GearCatalog.Category.HELD)
            throw new IllegalArgumentException("Sentinel source must be weapon or armor");
        double scale=1+.025*(effectiveLevel-1),nativeHealth=150*scale;
        double nativeMin=8*scale,nativeMax=12*scale;
        double nativeProtection=1-Math.pow(.92,scale);
        double sourceMin=0,sourceMax=0,sourceProtection=0;
        if(bound.category()==GearCatalog.Category.ARMOR){
            sourceProtection=GearAffixRuntime.protection(bound)/100;
            if(sourceProtection<0||sourceProtection>=1)throw new IllegalArgumentException("Invalid source armor protection");
        }else{
            var physical=GearAffixRuntime.physical(bound);
            sourceMin=physical.minimum();sourceMax=physical.maximum();
        }
        double sourceHealth=bound.intrinsicStats().getOrDefault("health",0d)+GearAffixRuntime.value(bound,"WA-091");
        double increasedAttackRate=GearAffixRuntime.value(bound,"WA-008")/100;
        double rate=1+increasedAttackRate;
        if(rate<=0||!Double.isFinite(rate))throw new IllegalArgumentException("Invalid inherited attack rate");
        double finalProtection=1-(1-nativeProtection)*(1-sourceProtection);
        return new Stats(effectiveLevel,scale,nativeHealth,sourceHealth,nativeHealth+sourceHealth,
                nativeMin,nativeMax,sourceMin,sourceMax,nativeMin+sourceMin,nativeMax+sourceMax,
                nativeProtection,sourceProtection,finalProtection,rate,attackInterval(nativeInterval,increasedAttackRate));
    }
    public static double attackInterval(double nativeInterval,double increasedAttackRate){
        if(!Double.isFinite(nativeInterval)||nativeInterval<=0||!Double.isFinite(increasedAttackRate)||increasedAttackRate<=-1)
            throw new IllegalArgumentException("Invalid Sentinel attack rate");
        return nativeInterval/(1+increasedAttackRate);
    }
    /** Capacity changes cannot restore lost Health. */
    public static double retainCurrentHealth(double oldCurrent,double newMaximum){
        if(!Double.isFinite(oldCurrent)||!Double.isFinite(newMaximum)||oldCurrent<0||newMaximum<=0)
            throw new IllegalArgumentException("Invalid Sentinel health transition");
        return Math.min(oldCurrent,newMaximum);
    }
}
