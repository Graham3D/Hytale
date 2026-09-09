package com.inigmasgames.hytalerpg.execution.strike;

/** Authoritative content retained behind an explicit native cadence gate; not a simulated speed buff. */
public record NativeStanceProfile(int maximumStacks,double attackSpeedPerStack,double inactivitySeconds,
                                  double staminaPerSecond,boolean cooldownOnEnd) {
    public static final String GATE="NATIVE_PER_ACTOR_BASIC_ATTACK_CADENCE_UNVERIFIED";
    public NativeStanceProfile {
        if(maximumStacks!=5||attackSpeedPerStack!=.04||inactivitySeconds!=3||staminaPerSecond!=2||!cooldownOnEnd)
            throw new IllegalArgumentException("INVALID_NATIVE_STANCE_CONTRACT");
    }
}
