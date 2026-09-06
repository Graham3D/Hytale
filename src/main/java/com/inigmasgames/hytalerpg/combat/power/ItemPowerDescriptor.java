package com.inigmasgames.hytalerpg.combat.power;

import java.util.Set;

/** Audited item data supplied by tags or the versioned item registry; display names are never consulted. */
public record ItemPowerDescriptor(String itemId, Set<String> tags, Double weaponPower, Double magicPower) {
    public ItemPowerDescriptor { tags = tags == null ? Set.of() : Set.copyOf(tags); }
}
