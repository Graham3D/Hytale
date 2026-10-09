package com.inigmasgames.hytalerpg.enemies;

/** Owner-corrected ME-015 precedence. Native velocity never enters the distance domain. */
public final class EnemyDisplacementMerge {
    private EnemyDisplacementMerge(){}
    public record Request(boolean preserveNativeImpulse,double horizontalMeters){}
    public static Request resolve(boolean nativeImpulse,double existingMeters,double affixMeters){
        for(double value:new double[]{existingMeters,affixMeters})if(!Double.isFinite(value)||value<0||value>8)
            throw new IllegalArgumentException("DISPLACEMENT_DISTANCE_DOMAIN");
        if(nativeImpulse)return new Request(true,0);
        return new Request(false,Math.max(existingMeters,affixMeters));
    }
}
