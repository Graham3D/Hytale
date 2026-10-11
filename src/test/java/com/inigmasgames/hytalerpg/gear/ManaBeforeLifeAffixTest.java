package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ManaBeforeLifeAffixTest {
    private final GearCatalog catalog=GearCatalog.load();
    private GearInstance armor(double percent){
        var base=catalog.base("gm.plate_mithril.head.h");var affix=catalog.affix("GA-161");
        var roll=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),5,percent,
                new GearRequirements.Gate(1,Map.of()),affix.effectContract(),affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    @Test void extensionDoesNotReassignAnyFrozenMasterAffix(){
        assertEquals(160,catalog.affixes().size());
        assertEquals(161,catalog.rollableAffixes().size());
        assertEquals("MANA_BEFORE_LIFE",catalog.affix("GA-161").operator());
        assertTrue(GearAffixRuntime.ENABLED.contains("GA-161"));
        assertTrue(GearDropGenerator.eligible(catalog.affix("GA-161"),catalog.base("gm.plate_mithril.head.h")));
        assertFalse(GearDropGenerator.eligible(catalog.affix("GA-161"),catalog.base("gm.sword_mithril.h")));
    }
    @Test void admittedArmorStacksAndCapsAndReloadKeepsTheRoll(){
        var first=armor(15);var second=armor(20);var third=armor(30);
        assertEquals(first,GearInstance.fromJson(first.toJson()));
        assertEquals(.35,GearAffixRuntime.effects(List.of(first,second)).snapshot().manaBeforeLifeFraction(),1e-9);
        assertEquals(.60,GearAffixRuntime.effects(List.of(first,second,third)).snapshot().manaBeforeLifeFraction(),1e-9);
        assertEquals(.15,GearAffixRuntime.effects(List.of(first)).snapshot().manaBeforeLifeFraction(),1e-9);
        assertEquals(0,GearAffixRuntime.effects(List.of()).snapshot().manaBeforeLifeFraction());
    }
    @Test void protectedQaFixtureIsAvailableAsControlAndAffixedPair(){
        var suite=new GearAffixQaSuite(catalog);
        var actor=UUID.randomUUID();
        assertEquals(0,GearAffixRuntime.effects(List.of(suite.create("ab-ga-161-control",actor))).snapshot().manaBeforeLifeFraction());
        assertTrue(GearAffixRuntime.effects(List.of(suite.create("ab-ga-161-affixed",actor))).snapshot().manaBeforeLifeFraction()>0);
    }
}
