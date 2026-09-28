package com.inigmasgames.hytalerpg.execution.summon;

/** Monotonic distance timer for an owned moving summon. */
public final class SummonDistanceRecovery {
    public static final double RANGE=24.0,DELAY_SECONDS=3.0;
    private SummonDistanceRecovery(){}
    public record Observation(double farSince,boolean recover){}
    public static Observation observe(double distance,double farSince,double now){
        if(!Double.isFinite(distance)||distance<0||!Double.isFinite(now)||now<=0)
            throw new IllegalArgumentException("Invalid summon recovery sample");
        if(distance<=RANGE)return new Observation(0,false);
        if(farSince<=0)return new Observation(now,false);
        return new Observation(farSince,now-farSince>=DELAY_SECONDS);
    }
}
