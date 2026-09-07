package com.inigmasgames.hytalerpg.execution.connection;

/** Exact finite line/beam/orb parameters; none are inferred from presentation assets. */
public record ConnectionProfile(Kind kind,double range,double width,double height,double depth,double speed,
        double lifetimeSeconds,double intervalSeconds,double coefficient,double upkeepPerSecond,double radius,
        double originHeight,String element,Details details) {
    public enum Kind { WAVE,BEAM,ORB,LINE,TETHER,CHAIN,ORBIT,DRAIN }
    public record Details(double jumpRadius,java.util.List<Double> jumpCoefficients,int bladeCount,double degreesPerSecond,
                          double contactCooldown,double healFraction,String status,double statusSeconds) {
        public static final Details NONE=new Details(0,java.util.List.of(),0,0,0,0,"",0);
        public Details {
            jumpCoefficients=java.util.List.copyOf(jumpCoefficients==null?java.util.List.of():jumpCoefficients);status=status==null?"":status;
            for(double value:new double[]{jumpRadius,degreesPerSecond,contactCooldown,healFraction,statusSeconds})
                if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid connection detail");
            if(jumpCoefficients.size()>16||jumpCoefficients.stream().anyMatch(v->v==null||!Double.isFinite(v)||v<=0)
                    ||bladeCount<0||bladeCount>8||healFraction>1)throw new IllegalArgumentException("Unbounded connection detail");
        }
    }
    public ConnectionProfile {
        if(kind==null||element==null||element.isBlank())throw new IllegalArgumentException("Incomplete connection profile");
        for(double value:new double[]{range,width,height,depth,speed,lifetimeSeconds,intervalSeconds,coefficient,upkeepPerSecond,radius,originHeight})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid connection magnitude/time/geometry");
        details=details==null?Details.NONE:details;
        boolean ticking=kind==Kind.BEAM||kind==Kind.ORB||kind==Kind.CHAIN||kind==Kind.ORBIT||kind==Kind.DRAIN;
        if(range<=0||height<=0||lifetimeSeconds<=0||lifetimeSeconds>120||coefficient<=0
                ||kind!=Kind.ORB&&kind!=Kind.ORBIT&&width<=0||(kind==Kind.ORB||kind==Kind.ORBIT)&&radius<=0
                ||ticking&&(intervalSeconds<.05||lifetimeSeconds/intervalSeconds>256)
                ||kind==Kind.WAVE&&(depth<=0||speed<=0)||kind==Kind.ORB&&speed<=0
                ||(kind==Kind.BEAM||kind==Kind.DRAIN)&&upkeepPerSecond<=0
                ||kind==Kind.CHAIN&&(details.jumpRadius<=0||details.jumpCoefficients.isEmpty())
                ||kind==Kind.ORBIT&&(details.bladeCount<1||details.degreesPerSecond<=0||details.contactCooldown<=0)
                ||kind==Kind.DRAIN&&details.healFraction<=0)throw new IllegalArgumentException("Unsupported connection parameters");
    }
    public boolean channel(){return kind==Kind.BEAM||kind==Kind.DRAIN;}
    public boolean requiresTarget(){return kind==Kind.TETHER||kind==Kind.CHAIN||kind==Kind.DRAIN;}
}
