package com.inigmasgames.hytalerpg.combat.damage;

/** Typed post-mitigation, pre-absorption component. No root ledger: every eligible packet is capped independently. */
public record MaxHealthDamageCap(double maximumHealthFraction) {
    public enum Origin { DIRECT_HOSTILE, PERIODIC, REFLECTED, REDIRECTED, ENVIRONMENT, HEALTH_COST, NON_DAMAGE }
    public MaxHealthDamageCap {
        if(!Double.isFinite(maximumHealthFraction)||maximumHealthFraction<=0||maximumHealthFraction>1)
            throw new IllegalArgumentException("Invalid maximum Health damage-cap fraction");
    }
    public double apply(double postMitigationDamage,double currentMaximumHealth,Origin origin) {
        if(!Double.isFinite(postMitigationDamage)||postMitigationDamage<0||!Double.isFinite(currentMaximumHealth)||currentMaximumHealth<=0)
            throw new IllegalArgumentException("Invalid damage-cap input");
        return origin==Origin.DIRECT_HOSTILE?Math.min(postMitigationDamage,currentMaximumHealth*maximumHealthFraction):postMitigationDamage;
    }
}
