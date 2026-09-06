package com.inigmasgames.hytalerpg.combat.damage;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;

public final class SkillScalingService {
    private final CombatBalanceProfile profile;
    public SkillScalingService(CombatBalanceProfile profile) { this.profile = profile; }
    public double attributeMultiplier(double effectiveAttribute) {
        if (!Double.isFinite(effectiveAttribute) || effectiveAttribute < 0.0)
            throw new IllegalArgumentException("Effective attribute must be finite and non-negative");
        return 1.0 + profile.primaryScalingPerEffectivePoint * effectiveAttribute;
    }
    public double scaledBasePower(double basePower, double effectiveAttribute) {
        return basePower * attributeMultiplier(effectiveAttribute);
    }
}
