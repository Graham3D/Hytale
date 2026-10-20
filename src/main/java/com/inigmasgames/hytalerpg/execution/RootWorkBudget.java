package com.inigmasgames.hytalerpg.execution;

/** One root counter shared by native projectile promises and all other gameplay effects. */
public final class RootWorkBudget {
    private int otherEffects=1,otherTriggered,nativeEffects,nativeTriggered;
    public synchronized String additionalEffect(boolean triggered) {
        if(totalEffects()>=48)return "ROOT_SPAWN_EFFECT_BUDGET";
        if(triggered&&totalTriggered()>=16)return "ROOT_TRIGGERED_SECONDARY_BUDGET";
        otherEffects++;if(triggered)otherTriggered++;return "PASS";
    }
    /** Monotonic lifetime reservation; failed native allocation cannot erase an ambiguous paid effect. */
    public synchronized String projectiles(int effects,int triggered) {
        if(effects<nativeEffects||triggered<nativeTriggered||effects<1||triggered<0)
            return "INVALID_PROJECTILE_BUDGET_TRANSITION";
        if(otherEffects+effects-1>48)return "ROOT_SPAWN_EFFECT_BUDGET";
        if(otherTriggered+triggered>16)return "ROOT_TRIGGERED_SECONDARY_BUDGET";
        nativeEffects=effects;nativeTriggered=triggered;return "PASS";
    }
    // PRIMARY and the first native carrier are the same effect, not two separate allocations.
    public synchronized int totalEffects(){return otherEffects+Math.max(0,nativeEffects-1);}
    public synchronized int totalTriggered(){return otherTriggered+nativeTriggered;}
}
