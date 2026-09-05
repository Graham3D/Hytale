package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import java.util.List;

/** Persist asset IDs; resolve registry indexes afresh for every native access. */
public final class VanillaNpcStats {
    public static final List<String> IDS = List.of("Health", "Stamina", "Mana");
    private VanillaNpcStats() { }
    public static int index(String id) {
        return switch (id) {
            case "Health" -> DefaultEntityStatTypes.getHealth();
            case "Stamina" -> DefaultEntityStatTypes.getStamina();
            case "Mana" -> DefaultEntityStatTypes.getMana();
            default -> -1;
        };
    }
}
