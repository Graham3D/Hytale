package com.inigmasgames.hytalerpg.gear;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearCarrierIdentityTest {
    @Test void actionVariantsRetainExactFrozenWeaponSourceAndRejectForeignProfiles() {
        var catalog=GearCatalog.load(); var bindings=new GearBindings();
        int checked=0;
        for(var family:List.of("sword","daggers","longsword","battleaxe","mace","shortbow","crossbow","staff","wand","book","bomb")) {
            var base=catalog.bases().stream().filter(b->b.id().startsWith("gm."+family+"_")
                    && b.sourceWindow().getLast()==99 && bindings.require(b.id()).mapped()).findFirst();
            if(base.isEmpty()) continue; // Unmapped families remain outside production admission.
            var affix=catalog.affix("WA-008"); double value=affix.topRange().getFirst();
            var roll=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),1,value,
                    new GearRequirements.Gate(1,Map.of()),affix.name(),affix.name());
            var item=GearInstance.authoredQa(base.get(),UUID.randomUUID(),99,1000,GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
            var control=new GearInstance(item.schemaVersion(),UUID.randomUUID(),item.definitionRevision(),item.baseId(),
                    item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),item.intrinsicThousandths(),
                    item.intrinsicStats(),item.requirements(),List.of(),item.rngVersion(),true);
            var snapshot=new GearEffectSnapshot(List.of(item));
            var profile=NativeGearActionProfiles.primary(snapshot,item.identity());
            var carrier=bindings.require(item.baseId()).carrier(item.rarity());
            var variant=family.equals("sword")?NativeSwordActionAssets.variantId(carrier,profile):NativePrimaryActionAssets.variantId(carrier,profile);
            assertTrue(GearCombatEffects.carrierMatches(item,variant),family);
            assertEquals(item.identity(),GearCombatEffects.contributingItem(snapshot,variant));
            assertFalse(GearCombatEffects.carrierMatches(control,variant),family+" unaffixed source");
            assertFalse(GearCombatEffects.carrierMatches(item,variant+"_forged"));
            var changed=new GearInstance(item.schemaVersion(),item.identity(),item.definitionRevision(),item.baseId(),
                    item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),item.intrinsicThousandths(),
                    item.intrinsicStats(),item.requirements(),List.of(new GearInstance.AffixRoll(affix.id(),affix.side(),
                    affix.exclusionGroup(),1,value+.1,roll.requirements(),affix.name(),affix.name())),item.rngVersion(),true);
            assertFalse(GearCombatEffects.carrierMatches(changed,variant),family+" stale speed");
            assertNull(GearCombatEffects.contributingItem(GearEffectSnapshot.EMPTY,variant));
            checked++;
        }
        assertTrue(checked>=7,"All currently mapped native weapon families must be exercised");
    }
}
