package com.inigmasgames.hytalerpg.gear;

import java.util.SplittableRandom;

/** One range sample per stable authored strike, reused by every victim/carrier. */
public final class GearPower {
    private GearPower() {}
    public static double sample(double minimum,double maximum,String strikeIdentity) {
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<.1||maximum<minimum||strikeIdentity==null)
            throw new IllegalArgumentException("Invalid gear strike range");
        long low=Math.round(minimum*10),high=Math.round(maximum*10);
        long seed=0xcbf29ce484222325L;
        for(int i=0;i<strikeIdentity.length();i++) { seed^=strikeIdentity.charAt(i);seed*=0x100000001b3L; }
        return new SplittableRandom(seed).nextLong(low,Math.addExact(high,1))/10.0;
    }
}
