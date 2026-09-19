package com.inigmasgames.hytalerpg.execution.support;

/** Typed support/Aura parameters; indefinite Auras have no artificial finite lifetime. */
public record SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                             double durationSeconds,double movementIncreased,double upkeepPerSecond,double damageInterval,double chillInterval,String element,
                             ResourceMode resourceMode) {
    public enum ResourceMode { LEGACY, TRIGGERED_VARIABLE_MANA_SPEND }
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                          double durationSeconds,double movementIncreased,double upkeepPerSecond,double damageInterval,double chillInterval,String element){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased,upkeepPerSecond,damageInterval,chillInterval,element,ResourceMode.LEGACY);
    }
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds,
                          double durationSeconds,double movementIncreased){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased,0,0,0,"");
    }
    public SupportProfile(Kind kind,double range,double radius,double coefficient,double reservationFraction,double toggleLockSeconds){
        this(kind,range,radius,coefficient,reservationFraction,toggleLockSeconds,0,0);
    }
    public enum Kind { HEAL, MANAGUARD, MANA_REGEN, TAUNT, WEAKEN, MARK, FEAR, RALLY, HOWL, REFLECT, SHIELD, IMBUE,
        THORNS, CHILL_AURA, COOLDOWN_AURA, DAMAGE_AURA, OVERFLOW, SHARED_SHIELD, CONSUME_MINION, MAX_HEALTH_DAMAGE_CAP, MANTLE_OF_FLAME,
        MANTLE_OF_THUNDER }
    public SupportProfile {
        if(kind==null)throw new IllegalArgumentException("Support kind missing");
        for(double value:new double[]{range,radius,coefficient,reservationFraction,toggleLockSeconds,durationSeconds,movementIncreased,upkeepPerSecond,damageInterval,chillInterval})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid support parameter");
        if(reservationFraction>1||toggleLockSeconds>120||durationSeconds>120)throw new IllegalArgumentException("Invalid support lifetime/commitment");
        element=element==null?"":element;
        resourceMode=resourceMode==null?ResourceMode.LEGACY:resourceMode;
        if((kind==Kind.MANTLE_OF_FLAME)!=(resourceMode==ResourceMode.TRIGGERED_VARIABLE_MANA_SPEND))
            throw new IllegalArgumentException("MANTLE_RESOURCE_MODE_REQUIRED");
        if(kind==Kind.MANTLE_OF_FLAME&&(reservationFraction!=0||upkeepPerSecond!=0||durationSeconds!=0||damageInterval!=0||chillInterval!=0))
            throw new IllegalArgumentException("MANTLE_HAS_NO_RESERVATION_UPKEEP_OR_TIMER_PAYLOAD");
    }
    public boolean aura(){return kind==Kind.MANAGUARD||kind==Kind.MANA_REGEN||kind==Kind.THORNS||kind==Kind.CHILL_AURA||kind==Kind.COOLDOWN_AURA||kind==Kind.DAMAGE_AURA||kind==Kind.MANTLE_OF_FLAME||kind==Kind.MANTLE_OF_THUNDER;}
    public boolean allyAura(){return kind==Kind.MANA_REGEN||kind==Kind.THORNS||kind==Kind.COOLDOWN_AURA||kind==Kind.MANTLE_OF_THUNDER;}
    public boolean hostileAura(){return kind==Kind.CHILL_AURA||kind==Kind.COOLDOWN_AURA||kind==Kind.DAMAGE_AURA;}
    public boolean finiteEffect(){return !aura()&&kind!=Kind.HEAL;}
    public boolean hostileTarget(){return kind==Kind.TAUNT||kind==Kind.WEAKEN||kind==Kind.MARK||kind==Kind.FEAR;}
    public boolean recipientBurst(){return kind==Kind.RALLY||kind==Kind.HOWL;}
    public boolean allyTarget(){return kind==Kind.HEAL||kind==Kind.SHIELD||kind==Kind.MAX_HEALTH_DAMAGE_CAP;}
}
