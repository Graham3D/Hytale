package com.inigmasgames.hytalerpg.combat.attribute;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;

/** Continuous piecewise-linear diminishing-return curve shared by all five attributes. */
public final class EffectiveAttributeService {
    private final CombatBalanceProfile profile;
    public EffectiveAttributeService(CombatBalanceProfile profile) { this.profile = profile; }

    public double effective(double raw) {
        if (!Double.isFinite(raw) || raw < 0.0) throw new IllegalArgumentException("Raw attribute must be finite and non-negative");
        double remaining = raw;
        double prior = 0.0;
        double result = 0.0;
        for (int index = 0; index < profile.attributeCurve.breakpoints.length; index++) {
            double width = profile.attributeCurve.breakpoints[index] - prior;
            double used = Math.min(remaining, width);
            result += used * profile.attributeCurve.slopes[index];
            remaining -= used;
            if (remaining <= 0.0) return result;
            prior = profile.attributeCurve.breakpoints[index];
        }
        return result + remaining * profile.attributeCurve.slopes[profile.attributeCurve.slopes.length - 1];
    }
}
