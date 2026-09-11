package com.inigmasgames.hytalerpg.execution.support;

/** Typed support/Aura parameters; indefinite Auras have no artificial finite lifetime. */
public record SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                             double durationSeconds,double movementIncreased,double upkeepPerSecond,double damageInterval,double chillInterval,String element) {
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                          double durationSeconds,double movementIncreased){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased,0,0,0,"");
    }
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,0,0);
    }
    public enum Kind { HEAL, MANAGUARD, MANA_REGEN, TAUNT, WEAKEN, MARK, FEAR, RALLY, HOWL, REFLECT, SHIELD, IMBUE,
        THORNS, CHILL_AURA, COOLDOWN_AURA, DAMAGE_AURA, OVERFLOW, SHARED_SHIELD, CONSUME_MINION, MAX_HEALTH_DAMAGE_CAP }
    public SupportProfile {
        if(kind==null)throw new IllegalArgumentException("Support kind missing");
        for(double value:new double[]{range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased,upkeepPerSecond,damageInterval,chillInterval})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid support parameter");
        if(reservationFraction>1||toggleLockSeconds>120||durationSeconds>120)throw new IllegalArgumentException("Invalid support lifetime/commitment");
        element=element==null?"":element;
    }
    public boolean aura(){return kind==Kind.MANAGUARD||kind==Kind.MANA_REGEN||kind==Kind.THORNS||kind==Kind.CHILL_AURA||kind==Kind.COOLDOWN_AURA||kind==Kind.DAMAGE_AURA;}
    public boolean allyAura(){return kind==Kind.MANA_REGEN||kind==Kind.THORNS||kind==Kind.COOLDOWN_AURA;}
    public boolean hostileAura(){return kind==Kind.CHILL_AURA||kind==Kind.COOLDOWN_AURA||kind==Kind.DAMAGE_AURA;}
    public boolean finiteEffect(){return !aura()&&kind!=Kind.HEAL;}
    public boolean hostileTarget(){return kind==Kind.TAUNT||kind==Kind.WEAKEN||kind==Kind.MARK||kind==Kind.FEAR;}
    public boolean recipientBurst(){return kind==Kind.RALLY||kind==Kind.HOWL;}
    public boolean allyTarget(){return kind==Kind.HEAL||kind==Kind.SHIELD||kind==Kind.MAX_HEALTH_DAMAGE_CAP;}
}
