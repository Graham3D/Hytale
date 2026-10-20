package com.inigmasgames.hytalerpg.combat.attribute;

import java.util.EnumMap;
import java.util.Map;

public record DerivedStats(Map<RpgAttribute, Integer> rawAttributes,
                           Map<RpgAttribute, Double> effectiveAttributes,
                           double maxHealth, double maxStamina, double maxMana,
                           double heavyDamageMultiplier, double lightDamageMultiplier,
                           double magicDamageMultiplier, double healingMultiplier,
                           double cooldownRecovery, double learnRate,
                           double criticalChance, double criticalMultiplier,
                           double upgradeSuccess, double magicFind) {
    public DerivedStats {
        rawAttributes = Map.copyOf(new EnumMap<>(rawAttributes));
        effectiveAttributes = Map.copyOf(new EnumMap<>(effectiveAttributes));
    }
    public double raw(RpgAttribute attribute) { return rawAttributes.get(attribute); }
    public double effective(RpgAttribute attribute) { return effectiveAttributes.get(attribute); }
}
