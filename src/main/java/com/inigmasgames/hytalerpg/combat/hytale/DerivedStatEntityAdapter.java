package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;

/** Projects RPG maxima into native EntityStatMap modifiers while preserving current percentage. */
public final class DerivedStatEntityAdapter {
    private static final String HEALTH_KEY = "hytalerpg:derived-max-health";
    private static final String STAMINA_KEY = "hytalerpg:derived-max-stamina";
    private static final String MANA_KEY = "hytalerpg:derived-max-mana";

    public void apply(EntityStatMap stats, DerivedStats derived) {
        applyOne(stats, DefaultEntityStatTypes.getHealth(), HEALTH_KEY, derived.maxHealth());
        applyOne(stats, DefaultEntityStatTypes.getStamina(), STAMINA_KEY, derived.maxStamina());
        applyOne(stats, DefaultEntityStatTypes.getMana(), MANA_KEY, derived.maxMana());
    }
    private static void applyOne(EntityStatMap stats, int index, String key, double desiredMaximum) {
        EntityStatValue before = stats.get(index);
        if (before == null) throw new IllegalStateException("EntityStatMap is missing required stat index " + index);
        double percentage = before.getMax() <= 0.0 ? 1.0 : before.get() / before.getMax();
        stats.removeModifier(index, key);
        stats.update();
        EntityStatValue base = stats.get(index);
        float additive = (float) (desiredMaximum - base.getMax());
        stats.putModifier(index, key, new StaticModifier(Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE, additive));
        stats.update();
        EntityStatValue after = stats.get(index);
        stats.setStatValue(index, (float) Math.max(after.getMin(), Math.min(after.getMax(), percentage * after.getMax())));
    }
}
