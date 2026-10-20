package com.inigmasgames.hytalerpg.combat.resource;

/** Adapter seam over Hytale EntityStatMap; it never owns a parallel current-value pool. */
public interface NativeResourcePort {
    double current(ResourceType type);
    double maximum(ResourceType type);
    void setCurrent(ResourceType type, double value);
    /** Optional native capacity projection. maximum(MANA) must continue reporting total, pre-reservation maximum. */
    default void setReservedMana(double amount) { }
    /** Bounded credit with readback. Native adapters must round down, never above the allowed increase. */
    default double restoreResourceAtMost(ResourceType type,double amount,double cap){
        double before=current(type);setCurrent(type,Math.min(cap,before+amount));return current(type)-before;
    }
}
