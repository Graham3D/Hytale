package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto;
import java.util.List;
import java.util.stream.Collectors;

/** Presentation-only spelling over the already frozen affix display snapshot. */
final class MonsterAffixLabel {
    static String row(EnemyDisplayDto display) {
        if (display == null || display.packRoleLabel().equals("Minion")) return "";
        return row(display.ownAffixTags());
    }

    static String row(List<EnemyDisplayDto.EnemyTag> tags) {
        return tags.stream().map(MonsterAffixLabel::label).collect(Collectors.joining(" · "));
    }

    private static String label(EnemyDisplayDto.EnemyTag tag) {
        String text = tag.fallbackText();
        if (tag.sourceId().equals("ME-024")) return "Packbound";
        if (tag.sourceId().equals("ME-023") && text.startsWith("Aura: "))
            return "Aura Enchanted: " + text.substring("Aura: ".length());
        return text;
    }

    private MonsterAffixLabel() { }
}
