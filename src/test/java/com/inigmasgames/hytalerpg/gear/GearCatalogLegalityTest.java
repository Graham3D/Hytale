package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authored item legality. Runtime effect proofs live with the consuming owners. */
final class GearCatalogLegalityTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void everyAuthoredScopeHasACatalogCarrierOrAnExplicitStarBoundary() {
        for(var affix:catalog.affixes()) {
            assertTrue(catalog.bases().stream().anyMatch(base->GearDropGenerator.eligible(affix,base)),affix.id());
            if(!affix.armorExtension().equals("none."))
                assertTrue(catalog.bases().stream().filter(base->base.category()==GearCatalog.Category.ARMOR)
                        .anyMatch(base->GearDropGenerator.eligible(affix,base)),affix.id()+" armor extension");
        }
    }

    @Test void armorExtensionsUseTheirOwnSlotAndAttributeRules() {
        var intChest=catalog.base("gm.cloth_linen.chest.h");
        var wisChest=catalog.base("gm.robes_oracle.chest.h");
        var wisHead=catalog.base("gm.robes_oracle.head.h");
        var strChest=catalog.base("gm.plate_mithril.chest.h");
        assertTrue(GearDropGenerator.eligible(catalog.affix("WA-004"),intChest));
        assertFalse(GearDropGenerator.eligible(catalog.affix("WA-004"),wisChest));
        assertTrue(GearDropGenerator.eligible(catalog.affix("WA-103"),wisChest));
        assertFalse(GearDropGenerator.eligible(catalog.affix("WA-103"),intChest));
        assertTrue(GearDropGenerator.eligible(catalog.affix("WA-009"),wisHead));
        assertFalse(GearDropGenerator.eligible(catalog.affix("WA-009"),strChest));
        assertEquals(.5,GearDropGenerator.armorFactor(catalog.affix("WA-012"),intChest));
        assertEquals(.6,GearDropGenerator.armorFactor(catalog.affix("WA-078"),wisHead));
        assertFalse(GearDropGenerator.eligible(catalog.affix("WA-001"),intChest));
    }

    @Test void rankBandsAndLegacyBudgetsRemainDistinct() {
        var all=catalog.affix("WA-121");
        var tiers=GearAffixTiers.compile(all);
        assertFalse(GearAffixTiers.rarityAllows(all,tiers.getFirst(),GearRarity.RARE));
        assertTrue(GearAffixTiers.rarityAllows(all,tiers.getFirst(),GearRarity.VERY_RARE));
        assertFalse(GearAffixTiers.rarityAllows(all,tiers.getLast(),GearRarity.VERY_RARE));
        assertTrue(GearAffixTiers.rarityAllows(all,tiers.getLast(),GearRarity.LEGENDARY));
        assertFalse(GearRarity.MAGIC.legalNewBudget(1,1));
        assertTrue(GearRarity.MAGIC.legalBudget(1,1));
        assertFalse(GearRarity.RARE.legalNewBudget(3,3));
        assertTrue(GearRarity.RARE.legalBudget(3,3));
        assertTrue(GearRarity.LEGENDARY.eligible(60,DifficultyId.HELL));
        assertFalse(GearRarity.LEGENDARY.eligible(60,DifficultyId.NIGHTMARE));
        var sword=catalog.base("gm.sword_mithril.h");
        var historical=new GearInstance.AffixRoll(all.id(),all.side(),all.exclusionGroup(),1,1,
                new GearRequirements.Gate(63,Map.of()),"historical rare paragon",all.name());
        var legacy=GearInstance.authoredQa(sword,UUID.randomUUID(),95,950,GearRarity.RARE,List.of(historical),BigDecimal.ZERO);
        assertTrue(legacy.legacyIssues().contains("WA121_RARE_LEGACY"));
        assertEquals(legacy.affixes(),GearInstance.fromJson(legacy.toJson()).affixes());
    }

    @Test void namedSelectorUsesExecutableWeaponKindsAndSurvivesPayloadRoundTrip() {
        var sword=catalog.base("gm.sword_mithril.h");
        var selected=GearDropGenerator.matchingSkillIds(sword);
        assertTrue(selected.contains("quick_slash"));
        assertTrue(GearDropGenerator.eligible(catalog.affix("WA-122"),sword));
        assertFalse(GearDropGenerator.eligible(catalog.affix("WA-122"),catalog.base("gm.cloth_linen.chest.h")));
        var affix=catalog.affix("WA-122");
        var tier=GearAffixTiers.compile(affix).getFirst();
        var roll=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),tier.tier(),tier.low(),
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),"named skill",affix.name(),"quick_slash");
        var item=GearInstance.authoredQa(sword,UUID.randomUUID(),95,950,GearRarity.RARE,List.of(roll),BigDecimal.ZERO);
        assertEquals("quick_slash",GearInstance.fromJson(item.toJson()).affixes().getFirst().selector());
        var old=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),tier.tier(),tier.low(),
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),"old named skill",affix.name());
        var historical=GearInstance.authoredQa(sword,UUID.randomUUID(),95,950,GearRarity.RARE,List.of(old),BigDecimal.ZERO);
        var loaded=GearInstance.fromJson(historical.toJson());
        assertNull(loaded.affixes().getFirst().selector());
        assertTrue(loaded.legacyIssues().contains("MISSING_FROZEN_SELECTOR"));
    }
}
