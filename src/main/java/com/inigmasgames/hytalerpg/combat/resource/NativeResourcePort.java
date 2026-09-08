package com.inigmasgames.hytalerpg.combat.resource;

/** Adapter seam over Hytale EntityStatMap; it never owns a parallel current-value pool. */
public interface NativeResourcePort {
    double current(ResourceType type);
    double maximum(ResourceType type);
    void setCurrent(ResourceType type, double value);
    /** Optional native capacity projection. maximum(MANA) must continue reporting total, pre-reservation maximum. */
    default void setReservedMana(double amount) { }
}
