package com.inigmasgames.hytalerpg.combat.damage;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Authored coefficient substitutions, evaluated at native Gather, never at cast commitment. */
public enum VictimCoefficient {
    NONE, EXECUTION_STRIKE, BACKSTAB;

    public double factor(double health, double maximum, Vec3 targetForward, Vec3 casterMinusTarget) {
        return switch (this) {
            case NONE -> 1;
            case EXECUTION_STRIKE -> {
                if (!Double.isFinite(health) || !Double.isFinite(maximum) || health <= 0 || maximum <= 0)
                    throw new IllegalArgumentException("LIVE_VICTIM_HEALTH_UNAVAILABLE");
                yield health / maximum < .25 ? 2.40 / 1.40 : 1;
            }
            case BACKSTAB -> {
                if (targetForward == null || casterMinusTarget == null || !Double.isFinite(targetForward.length())
                        || !Double.isFinite(casterMinusTarget.length()) || targetForward.length() <= 1e-9
                        || casterMinusTarget.length() <= 1e-9)
                    throw new IllegalArgumentException("LIVE_VICTIM_FACING_UNAVAILABLE");
                var a=targetForward.normalized();var b=casterMinusTarget.normalized();
                yield a.x()*b.x()+a.y()*b.y()+a.z()*b.z() <= -.5 ? 1.90 / .90 : 1;
            }
        };
    }
}
