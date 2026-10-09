package com.inigmasgames.hytalerpg.enemies;

import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonsterVisualProfileTest {
    @Test void rarityOwnsOnlySkinAndOrdinaryMonstersStayNative(){
        var champion=MonsterVisualProfile.resolve(EnemyRarity.CHAMPION,List.of(EXTRA_STRONG));
        assertEquals("#1d4dff",champion.skinTint());
        assertNull(champion.weaponVisualOwner());assertNull(champion.armorVisualOwner());
        var ordinary=MonsterVisualProfile.resolve(EnemyRarity.NORMAL,List.of());
        assertNull(ordinary.skinTint());assertNull(ordinary.weaponVisualOwner());assertNull(ordinary.armorVisualOwner());
        assertEquals("#ff9100",MonsterVisualProfile.resolve(EnemyRarity.SUPER_UNIQUE,List.of()).skinTint());
    }
    @Test void visualPriorityIsStableAndNeverBlends(){
        var first=MonsterVisualProfile.resolve(EnemyRarity.UNIQUE,
                List.of(SPECTRAL_HIT,STONE_SKIN,FIRE_ENCHANTED,EXTRA_STRONG,REFLECTIVE,PACKBOUND));
        var reversed=MonsterVisualProfile.resolve(EnemyRarity.UNIQUE,
                List.of(PACKBOUND,REFLECTIVE,EXTRA_STRONG,FIRE_ENCHANTED,STONE_SKIN,SPECTRAL_HIT));
        assertEquals(first,reversed);
        assertEquals("#a000ff",first.skinTint());
        assertEquals(FIRE_ENCHANTED,first.weaponVisualOwner());
        assertEquals(PACKBOUND,first.armorVisualOwner());
        assertEquals("Fire Enchanted",MonsterVisualProfile.display(first.weaponVisualOwner()));
        assertEquals("NONE",MonsterVisualProfile.display(null));
    }
    @Test void onlyAuthoredVisualCandidatesOwnPersistentTint(){
        assertEquals(MonsterVisualProfile.Classification.NO_COLOR,MonsterVisualProfile.classification(EXTRA_STRONG));
        assertEquals(MonsterVisualProfile.Classification.NO_COLOR,MonsterVisualProfile.classification(ARMOR_BREAKER));
        assertEquals(MonsterVisualProfile.Classification.WEAPON_TINT,MonsterVisualProfile.classification(FIRE_ENCHANTED));
        assertEquals(MonsterVisualProfile.Classification.ARMOR_TINT,MonsterVisualProfile.classification(STONE_SKIN));
    }
}
