package com.inigmasgames.hytalerpg.execution.connection;

/** Exact finite line/beam/orb parameters; none are inferred from presentation assets. */
public record ConnectionProfile(Kind kind,double range,double width,double height,double depth,double speed,
        double lifetimeSeconds,double intervalSeconds,double coefficient,double upkeepPerSecond,double radius,
        double originHeight,String element) {
    public enum Kind { WAVE,BEAM,ORB }
    public ConnectionProfile {
        if(kind==null||element==null||element.isBlank())throw new IllegalArgumentException("Incomplete connection profile");
        for(double value:new double[]{range,width,height,depth,speed,lifetimeSeconds,intervalSeconds,coefficient,upkeepPerSecond,radius,originHeight})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid connection magnitude/time/geometry");
        if(range<=0||height<=0||lifetimeSeconds<=0||lifetimeSeconds>120||coefficient<=0
                ||kind!=Kind.ORB&&width<=0||kind==Kind.ORB&&radius<=0
                ||kind!=Kind.WAVE&&(intervalSeconds<.05||lifetimeSeconds/intervalSeconds>256)
                ||kind==Kind.WAVE&&(depth<=0||speed<=0)||kind==Kind.ORB&&speed<=0
                ||kind==Kind.BEAM&&upkeepPerSecond<=0)throw new IllegalArgumentException("Unsupported connection parameters");
    }
    public boolean channel(){return kind==Kind.BEAM;}
}
