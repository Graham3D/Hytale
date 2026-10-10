package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MonsterAffixLabelTest {
    private static EnemyDisplayDto.EnemyTag tag(String id,String text) {
        return new EnemyDisplayDto.EnemyTag("affix/"+id,EnemyDisplayDto.SourceKind.AFFIX,
                id,"enemies.tags."+id,Map.of(),text,EnemyDisplayDto.Style.AFFIX,0,"enemies.details."+id);
    }

    @Test void overheadRowUsesFullAffixNamesAndKeepsFrozenOrder() {
        assertEquals("Extra Strong · Stone Skin · Armor Breaker · Mana Burn · Aura Enchanted: Fire",
                MonsterAffixLabel.row(List.of(tag("ME-002","Extra Strong"),tag("ME-004","Stone Skin"),
                        tag("ME-025","Armor Breaker"),tag("ME-013","Mana Burn"),
                        tag("ME-023","Aura: Fire"))));
        assertEquals("Fire Enchanted · Cold Enchanted · Lightning Enchanted · Poison Enchanted · "
                        +"Wind Enchanted · Earth Enchanted · Void Enchanted · Magic Resistant",
                MonsterAffixLabel.row(List.of(tag("ME-006","Fire Enchanted"),tag("ME-007","Cold Enchanted"),
                        tag("ME-008","Lightning Enchanted"),tag("ME-009","Poison Enchanted"),
                        tag("ME-011","Wind Enchanted"),tag("ME-010","Earth Enchanted"),
                        tag("ME-012","Void Enchanted"),tag("ME-005","Magic Resistant"))));
    }

    @Test void packboundOverheadNameDoesNotExposeChangingGuardCount() {
        assertEquals("Avenger 2/5 · Packbound",
                MonsterAffixLabel.row(List.of(tag("ME-020","Avenger 2/5"),tag("ME-024","Packbound: 1"))));
        assertEquals("Packbound",MonsterAffixLabel.row(List.of(tag("ME-024","Packbound: Broken"))));
    }
}
