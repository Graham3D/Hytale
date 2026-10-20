package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;

record StatSnapshot(String health, String mana, String stamina) {
    static StatSnapshot read(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return new StatSnapshot("missing", "missing", "missing");
        }
        return new StatSnapshot(format(stats.get("Health")), format(stats.get("Mana")),
                format(stats.get("Stamina")));
    }

    private static String format(EntityStatValue value) {
        return value == null ? "missing" : String.format(Locale.ROOT, "%.1f / %.1f", value.get(), value.getMax());
    }
}
