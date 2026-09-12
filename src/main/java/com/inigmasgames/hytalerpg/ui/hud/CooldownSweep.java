package com.inigmasgames.hytalerpg.ui.hud;

/** Presentation only. Always follows authoritative remaining time, never a local countdown. */
public final class CooldownSweep {
    private CooldownSweep(){}
    public static float progress(double remaining,double duration){
        if(!Double.isFinite(remaining)||!Double.isFinite(duration)||remaining<=0||duration<=0)return 0;
        // Native CircularProgressBar receives the remaining sector: fully covered
        // at commit, then the red sector clears continuously toward readiness.
        return (float)Math.clamp(remaining/duration,0,1);
    }
}
