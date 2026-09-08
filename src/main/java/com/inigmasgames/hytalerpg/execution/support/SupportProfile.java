package com.inigmasgames.hytalerpg.execution.support;

/** Typed support/Aura parameters; indefinite Auras have no artificial finite lifetime. */
public record SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                             double durationSeconds,double movementIncreased) {
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,0,0);
    }
    public enum Kind { HEAL, MANAGUARD, MANA_REGEN, TAUNT, WEAKEN, MARK, FEAR, RALLY }
    public SupportProfile {
        if(kind==null)throw new IllegalArgumentException("Support kind missing");
        for(double value:new double[]{range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid support parameter");
        if(reservationFraction>1||toggleLockSeconds>120||durationSeconds>120)throw new IllegalArgumentException("Invalid support lifetime/commitment");
    }
    public boolean aura(){return kind==Kind.MANAGUARD||kind==Kind.MANA_REGEN;}
    public boolean finiteEffect(){return !aura()&&kind!=Kind.HEAL;}
    public boolean hostileTarget(){return kind==Kind.TAUNT||kind==Kind.WEAKEN||kind==Kind.MARK||kind==Kind.FEAR;}
}
