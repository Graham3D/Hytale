package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelMaterialTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static GearInstance item(String baseId){
        var base=CATALOG.base(baseId);
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
    }
    @Test void onlyCurrentAuditedMappedIronWeaponAndArmorBasesQualify(){
        assertEquals(30,IronSentinelMaterial.auditedBases().size());
        assertEquals("Weapon_Sword_Iron",IronSentinelMaterial.require(item("gm.sword_iron.n")).nativeItemId());
        assertEquals("Armor_Iron_Chest",IronSentinelMaterial.require(item("gm.plate_iron.chest.n")).nativeItemId());
        assertThrows(IllegalArgumentException.class,()->IronSentinelMaterial.require(item("gm.sword_copper.n")));
        assertThrows(IllegalArgumentException.class,()->IronSentinelMaterial.require(item("gm.shield_iron.n")));
        assertThrows(IllegalArgumentException.class,()->IronSentinelMaterial.require(item("gm.spear_iron.n")));
    }
}
