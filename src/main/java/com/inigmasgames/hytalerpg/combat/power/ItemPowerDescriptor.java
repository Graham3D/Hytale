package com.inigmasgames.hytalerpg.combat.power;

import java.util.Set;

/** Audited item data supplied by tags or the versioned item registry; display names are never consulted. */
public record ItemPowerDescriptor(String itemId, Set<String> tags, Double weaponPower, Double magicPower,
                                  Double physicalMinimum,Double physicalMaximum) {
    public ItemPowerDescriptor(String itemId,Set<String> tags,Double weaponPower,Double magicPower) {
        this(itemId,tags,weaponPower,magicPower,null,null);
    }
    public ItemPowerDescriptor {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        if((physicalMinimum==null)!=(physicalMaximum==null) || physicalMinimum!=null &&
                (!Double.isFinite(physicalMinimum)||!Double.isFinite(physicalMaximum)||physicalMinimum<.1||physicalMaximum<physicalMinimum))
            throw new IllegalArgumentException("Invalid authored item range");
    }
}
