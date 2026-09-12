package com.inigmasgames.hytalerpg.ui.hud;

/** Presentation only. Always follows authoritative remaining time, never a local countdown. */
public final class CooldownSweep {
    private CooldownSweep(){}
    public static float progress(double remaining,double duration){
        if(!Double.isFinite(remaining)||!Double.isFinite(duration)||remaining<=0||duration<=0)return 0;
        return (float)Math.clamp(1-remaining/Math.max(remaining,duration),0,1);
    }
}
