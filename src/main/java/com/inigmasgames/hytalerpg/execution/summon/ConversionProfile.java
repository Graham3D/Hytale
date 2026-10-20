package com.inigmasgames.hytalerpg.execution.summon;

/** A temporary relationship lease on an existing NPC, not a created summon or a damage skill. */
public record ConversionProfile(double range,double duration){
    public ConversionProfile{
        if(!Double.isFinite(range)||range<=0||range>24||!Double.isFinite(duration)||duration<=0||duration>60)
            throw new IllegalArgumentException("Invalid conversion profile");
    }
}
