package com.inigmasgames.hytalerpg.execution.projectile;

/** Optional caster-owned periodic-status payoff attached to one authored explosion payload. */
public record ProjectileBurnPayoff(double multiplier,boolean consumeCasterOwnedBurn) {
    public static final ProjectileBurnPayoff NONE=new ProjectileBurnPayoff(1,false);
    public ProjectileBurnPayoff {
        if(!Double.isFinite(multiplier)||multiplier<1||multiplier>4
                ||consumeCasterOwnedBurn==(multiplier==1))throw new IllegalArgumentException("INVALID_PROJECTILE_BURN_PAYOFF");
    }
    public boolean active(){return consumeCasterOwnedBurn;}
}
