package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;

/** Intrinsic magnitude only; compiled barrier and gear factors are applied by the caller once. */
public final class CycloneArmorFormula {
    private static final CombatBalanceProfile BALANCE = CombatBalanceProfile.loadCanonical();
    private static final double STARTING_EFFECTIVE = new EffectiveAttributeService(BALANCE)
            .effective(BALANCE.startingRawAttribute);

    private CycloneArmorFormula() { }

    public record Breakdown(double startingEffective, double baseBarrier,
                            double wisdomContribution, double intelligenceContribution,
                            int effectiveSkillLevel, double skillLevelFactor, double intrinsicBarrier) { }

    public static Breakdown calculate(double flatHealingPowerFromGear, double effectiveWisdom,
                                      double effectiveIntelligence, int effectiveLevel) {
        if (!Double.isFinite(flatHealingPowerFromGear) || flatHealingPowerFromGear < 0
                || !Double.isFinite(effectiveWisdom) || effectiveWisdom < 0
                || !Double.isFinite(effectiveIntelligence) || effectiveIntelligence < 0
                || effectiveLevel < 1)
            throw new IllegalArgumentException("INVALID_CYCLONE_MAGNITUDE");
        double baseBarrier = 1.5 * (20 + flatHealingPowerFromGear);
        double wisdomContribution = .35 * Math.max(0, effectiveWisdom - STARTING_EFFECTIVE);
        double intelligenceContribution = .15 * Math.max(0, effectiveIntelligence - STARTING_EFFECTIVE);
        double skillLevelFactor = 1 + .0125 * (effectiveLevel - 1);
        double capacity = (baseBarrier + wisdomContribution + intelligenceContribution) * skillLevelFactor;
        if (!Double.isFinite(capacity) || capacity > Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_CYCLONE_CAPACITY");
        return new Breakdown(STARTING_EFFECTIVE, baseBarrier, wisdomContribution,
                intelligenceContribution, effectiveLevel, skillLevelFactor, capacity);
    }

    /** R261-B capacity, frozen at activation. Whole barrier HP rounds down once, after level scaling. */
    public static double durableCapacity(double effectiveWisdom,double effectiveIntelligence,int effectiveLevel){
        if(!Double.isFinite(effectiveWisdom)||!Double.isFinite(effectiveIntelligence)
                ||effectiveWisdom<0||effectiveIntelligence<0||effectiveLevel<1||effectiveLevel>20)
            throw new IllegalArgumentException("INVALID_DURABLE_CYCLONE_ATTRIBUTES");
        double intrinsic=100+Math.max(0,effectiveWisdom-STARTING_EFFECTIVE)
                +Math.max(0,effectiveIntelligence-STARTING_EFFECTIVE);
        double capacity=Math.floor(intrinsic*(1+0.60*(effectiveLevel-1)/19));
        if(!Double.isFinite(capacity)||capacity<=0||capacity>Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_DURABLE_CYCLONE_CAPACITY");
        return capacity;
    }
}
