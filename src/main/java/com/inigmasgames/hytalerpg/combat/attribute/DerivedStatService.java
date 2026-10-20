package com.inigmasgames.hytalerpg.combat.attribute;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.EnumMap;
import java.util.Map;

/** Pure attribute-to-derived-stat projection. Current resource values remain outside this service. */
public final class DerivedStatService {
    private final CombatBalanceProfile profile;
    private final EffectiveAttributeService effective;
    public DerivedStatService(CombatBalanceProfile profile, EffectiveAttributeService effective) {
        this.profile = profile; this.effective = effective;
    }

    public DerivedStats derive(Map<RpgAttribute, Integer> raw) { return derive(raw, 0.0, 0.0, 0.0); }

    public DerivedStats derive(Map<RpgAttribute, Integer> raw, double flatHealth, double flatStamina, double flatMana) {
        EnumMap<RpgAttribute, Integer> normalized = new EnumMap<>(RpgAttribute.class);
        EnumMap<RpgAttribute, Double> values = new EnumMap<>(RpgAttribute.class);
        for (RpgAttribute attribute : RpgAttribute.values()) {
            int rawValue = raw.getOrDefault(attribute, (int) profile.startingRawAttribute);
            if (rawValue < 0) throw new IllegalArgumentException(attribute + " cannot be negative");
            normalized.put(attribute, rawValue);
            values.put(attribute, effective.effective(rawValue));
        }
        double baselineEffective = effective.effective(profile.startingRawAttribute);
        double strength = values.get(RpgAttribute.STR);
        double dexterity = values.get(RpgAttribute.DEX);
        double intelligence = values.get(RpgAttribute.INT);
        double wisdom = values.get(RpgAttribute.WIS);
        double luck = values.get(RpgAttribute.LUCK);
        double primary = profile.primaryScalingPerEffectivePoint;
        return new DerivedStats(normalized, values,
                profile.startingResourceMaximum + profile.healthPerEffectiveStrength * (strength - baselineEffective) + flatHealth,
                profile.startingResourceMaximum + profile.staminaPerEffectiveDexterity * (dexterity - baselineEffective) + flatStamina,
                profile.startingResourceMaximum + profile.manaPerEffectiveIntelligence * (intelligence - baselineEffective) + flatMana,
                1.0 + primary * strength, 1.0 + primary * dexterity,
                1.0 + primary * intelligence, 1.0 + primary * wisdom,
                profile.wisdomCooldownNumerator * wisdom / (wisdom + profile.wisdomCooldownDenominator),
                Math.min(profile.wisdomLearnCap, profile.wisdomLearnPerPoint * wisdom),
                Math.min(profile.criticalChanceCap,
                        profile.baseCriticalChance + profile.luckCritNumerator * luck / (luck + profile.luckCritDenominator)),
                profile.baseCriticalMultiplier,
                profile.luckUpgradeNumerator * luck / (luck + profile.luckUpgradeDenominator),
                profile.luckMagicFindPerPoint * luck);
    }
}
