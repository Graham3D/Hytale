package com.inigmasgames.hytalerpg.progress;

/** No resource/cooldown mutation. A reconnect starts a conservative ten-second observation window. */
public final class RespecGate {
    public static String rejection(double secondsSinceHostile,boolean pendingCast){
        if(pendingCast)return "RESPEC_PENDING_CAST";
        if(Double.isNaN(secondsSinceHostile)||secondsSinceHostile<10)return "RESPEC_REQUIRES_TEN_SECONDS_OUT_OF_HOSTILE_COMBAT";
        return "";
    }
    private RespecGate(){}
}
