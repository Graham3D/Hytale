package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Presentation-only spelling over the already frozen affix display snapshot. */
final class MonsterAffixLabel {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("Stone Skin", "Stoneskin"), Map.entry("Mana Burn", "Manaburn"),
            Map.entry("Magic Resistant", "Magic Resist"),
            Map.entry("Fire Enchanted", "Fire Ench."), Map.entry("Cold Enchanted", "Cold Ench."),
            Map.entry("Lightning Enchanted", "Lightning Ench."),
            Map.entry("Poison Enchanted", "Poison Ench."), Map.entry("Wind Enchanted", "Wind Enchanted"),
            Map.entry("Earth Enchanted", "Earth Ench."), Map.entry("Void Enchanted", "Void Ench."),
            Map.entry("Aura Enchanted", "Aura Ench."));

    static String row(EnemyDisplayDto display) {
        if (display == null || display.packRoleLabel().equals("Minion")) return "";
        return row(display.ownAffixTags());
    }

    static String row(List<EnemyDisplayDto.EnemyTag> tags) {
        return tags.stream().map(MonsterAffixLabel::label).collect(Collectors.joining(" · "));
    }

    private static String label(EnemyDisplayDto.EnemyTag tag) {
        String text = tag.fallbackText();
        if (tag.sourceId().equals("ME-023") && text.startsWith("Aura: "))
            return "Aura Ench.: " + text.substring("Aura: ".length());
        for (var entry : ALIASES.entrySet()) {
            if (text.equals(entry.getKey())) return entry.getValue();
        }
        return text;
    }

    private MonsterAffixLabel() { }
}
