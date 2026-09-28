package com.inigmasgames.hytalerpg.ui.inventory;

import java.util.Map;

/** Small audited native fixture set, NOT a production footprint catalog. Unknown items stay native. */
public final class InventoryProbeBindings {
    private InventoryProbeBindings() { }
    public static final Map<String, SpatialLayout.Size> FOOTPRINTS = Map.of(
            "Weapon_Shortbow_Iron", new SpatialLayout.Size(2, 4),
            "Weapon_Sword_Iron", new SpatialLayout.Size(1, 3),
            "Weapon_Daggers_Iron", new SpatialLayout.Size(1, 2),
            "Armor_Iron_Head", new SpatialLayout.Size(2, 2),
            "Armor_Iron_Chest", new SpatialLayout.Size(2, 3),
            "Potion_Health", new SpatialLayout.Size(1, 1),
            "Rock_Stone", new SpatialLayout.Size(1, 1));
}
