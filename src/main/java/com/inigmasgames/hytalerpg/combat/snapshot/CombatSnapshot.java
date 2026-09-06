package com.inigmasgames.hytalerpg.combat.snapshot;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.power.LinkTreeWeaponClass;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import java.util.Map;
import java.util.UUID;

/** Immutable commit-time data inherited by later derived effects without mid-cast equipment resampling. */
public record CombatSnapshot(String rootCastId, String skillInstanceId, UUID actorId,
                             Map<RpgAttribute, Integer> rawAttributes,
                             Map<RpgAttribute, Double> effectiveAttributes,
                             DerivedStats derivedStats,
                             String itemId, LinkTreeWeaponClass weaponClass,
                             BasePowerSource basePowerSource, double basePower,
                             String compiledPlanHash, double skillCoefficient,
                             double criticalChance, double criticalMultiplier,
                             ModifierBuckets modifiers, ResourceCost resourceCost,
                             double cooldownSeconds, Map<String, Double> statusModifiers) {
    public CombatSnapshot {
        if (rootCastId == null || rootCastId.isBlank() || skillInstanceId == null || skillInstanceId.isBlank())
            throw new IllegalArgumentException("rootCastId and skillInstanceId are required");
        rawAttributes = Map.copyOf(rawAttributes);
        effectiveAttributes = Map.copyOf(effectiveAttributes);
        statusModifiers = Map.copyOf(statusModifiers == null ? Map.of() : statusModifiers);
        if (modifiers == null) modifiers = ModifierBuckets.NONE;
        if (resourceCost == null) resourceCost = ResourceCost.NONE;
    }
}
