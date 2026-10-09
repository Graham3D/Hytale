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

    @Test void aliasesOnlyChangePresentationAndKeepAffixOrder() {
        assertEquals("Extra Strong · Stoneskin · Armor Breaker · Manaburn · Aura Ench.: Fire",
                MonsterAffixLabel.row(List.of(tag("ME-002","Extra Strong"),tag("ME-004","Stone Skin"),
                        tag("ME-025","Armor Breaker"),tag("ME-013","Mana Burn"),
                        tag("ME-023","Aura: Fire"))));
        assertEquals("Avenger 2/5 · Packbound: 1",
                MonsterAffixLabel.row(List.of(tag("ME-020","Avenger 2/5"),tag("ME-024","Packbound: 1"))));
        assertEquals("Wind Enchanted · Magic Resist",
                MonsterAffixLabel.row(List.of(tag("ME-011","Wind Enchanted"),
                        tag("ME-005","Magic Resistant"))));
    }
}
