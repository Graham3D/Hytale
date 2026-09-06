package com.inigmasgames.hytalerpg.ui;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.inigmasgames.hytalerpg.ui.model.NativeResourceView;

/** Read-only projection from the one authoritative native EntityStatMap. */
public final class HytaleResourceViewAdapter {
    public Snapshot read(EntityStatMap stats) {
        return new Snapshot(read(stats, DefaultEntityStatTypes.getMana()),
                read(stats, DefaultEntityStatTypes.getHealth()),
                read(stats, DefaultEntityStatTypes.getStamina()));
    }

    private static NativeResourceView read(EntityStatMap stats, int index) {
        EntityStatValue value = stats.get(index);
        if (value == null) throw new IllegalStateException("Missing native EntityStatMap index " + index);
        return new NativeResourceView(value.get(), value.getMax());
    }

    public record Snapshot(NativeResourceView mana, NativeResourceView health,
                           NativeResourceView stamina) { }
}
