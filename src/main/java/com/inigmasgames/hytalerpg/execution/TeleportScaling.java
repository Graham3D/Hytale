package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Teleport's intrinsic structural rank is capped; linked reach is applied afterward. */
public final class TeleportScaling {
    private TeleportScaling() { }

    public static int rank(int effectiveSkillLevel) {
        return Math.clamp(effectiveSkillLevel, 1, 20);
    }

    public static double range(int effectiveSkillLevel) {
        return 10 + rank(effectiveSkillLevel) / 2;
    }

    public static double manaFraction(int effectiveSkillLevel) {
        return (20 - rank(effectiveSkillLevel) / 2) / 100.0;
    }

    public static double effectiveRange(int effectiveSkillLevel, Stage04SkillProfile compiled) {
        return range(effectiveSkillLevel) * compiled.movement().maxDistance() / 10.0;
    }

    public static boolean withinBounds(Vec3 start,Vec3 landing,double range) {
        return start!=null&&landing!=null&&Double.isFinite(range)&&range>=0
                &&landing.y()-start.y()<=5.0001&&landing.subtract(start).length()<=range+1e-6;
    }
}
