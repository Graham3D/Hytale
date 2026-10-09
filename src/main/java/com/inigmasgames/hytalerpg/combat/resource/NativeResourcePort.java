package com.inigmasgames.hytalerpg.combat.resource;

/** Adapter seam over Hytale EntityStatMap; it never owns a parallel current-value pool. */
public interface NativeResourcePort {
    double current(ResourceType type);
    double maximum(ResourceType type);
    void setCurrent(ResourceType type, double value);
    default boolean hasResource(ResourceType type){return type!=ResourceType.NONE&&Double.isFinite(maximum(type))&&maximum(type)>0;}
    /** Native implementations round the resulting value UP so the debit never exceeds its allowance. */
    default double drainManaAtMost(double amount,double minimum){
        double before=current(ResourceType.MANA);setCurrent(ResourceType.MANA,Math.max(minimum,before-amount));return Math.max(0,before-current(ResourceType.MANA));
    }
    default double restoreHealthAtMost(double amount){
        double before=current(ResourceType.HEALTH);if(before<=0)return 0;
        setCurrent(ResourceType.HEALTH,Math.min(maximum(ResourceType.HEALTH),before+amount));return Math.max(0,current(ResourceType.HEALTH)-before);
    }
    /** Optional native capacity projection. maximum(MANA) must continue reporting total, pre-reservation maximum. */
    default void setReservedMana(double amount) { }
    /** Bounded credit with readback. Native adapters must round down, never above the allowed increase. */
    default double restoreResourceAtMost(ResourceType type,double amount,double cap){
        double before=current(type);setCurrent(type,Math.min(cap,before+amount));return current(type)-before;
    }
}
