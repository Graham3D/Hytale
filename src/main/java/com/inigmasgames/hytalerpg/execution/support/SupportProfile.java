package com.inigmasgames.hytalerpg.execution.support;

/** Typed support/Aura parameters; indefinite Auras have no artificial finite lifetime. */
public record SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds) {
    public enum Kind { HEAL, MANAGUARD, MANA_REGEN }
    public SupportProfile {
        if(kind==null)throw new IllegalArgumentException("Support kind missing");
        for(double value:new double[]{range,radius,coefficient,reservationFraction,toggleLockSeconds})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid support parameter");
        if(reservationFraction>1||toggleLockSeconds>120)throw new IllegalArgumentException("Invalid Aura commitment");
    }
    public boolean aura(){return kind!=Kind.HEAL;}
}
