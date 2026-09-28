package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.domain.SkillId;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GearTooltipV12Test {
    private final GearCatalog catalog=GearCatalog.load();
    @Test void everyStableFamilyHasOneAuthoredTemplateIncludingGatedFamilies() {
        assertEquals(160,catalog.affixes().size());
        for(int i=1;i<=160;i++){
            String id=(i<=158?"WA-":"GA-")+String.format(Locale.ROOT,"%03d",i);
            var definition=catalog.affix(id);
            assertNotNull(definition.playerTooltipTemplate(),id);
            assertFalse(definition.playerTooltipTemplate().isBlank(),id);
            assertDoesNotThrow(()->GearAffixDisplay.validateTemplate(definition.playerTooltipTemplate()),id);
        }
        assertEquals("+V% Enhanced Armor",catalog.affix("GA-160").playerTooltipTemplate());
        assertEquals("V% Life Stolen per Hit",catalog.affix("WA-096").playerTooltipTemplate());
        assertEquals("+V to [Skill]",catalog.affix("WA-122").playerTooltipTemplate());
    }
    @Test void formatterSubstitutesPersistedPrecisionAndCanonicalSkillName() {
        assertEquals("+11.7% Enhanced Armor",GearAffixDisplay.resolve(catalog.affix("GA-160").playerTooltipTemplate(),11.7,null));
        assertEquals("+4 Luck",GearAffixDisplay.resolve(catalog.affix("WA-089").playerTooltipTemplate(),4,null));
        assertEquals("+1.25 m Melee Reach",GearAffixDisplay.resolve(catalog.affix("WA-014").playerTooltipTemplate(),1.25,null));
        assertEquals("+8% Fire Resistance",GearAffixDisplay.resolve(catalog.affix("WA-074").playerTooltipTemplate(),8,null));
        assertEquals("6% Chance to Burn on Hit",GearAffixDisplay.resolve(catalog.affix("WA-054").playerTooltipTemplate(),6,null));
        assertEquals("+0.5 Armor",GearAffixDisplay.resolve(catalog.affix("GA-159").playerTooltipTemplate(),0.5,null));
        assertEquals("+3 to Minimum and Maximum Physical Damage",GearAffixDisplay.resolve(catalog.affix("WA-001").playerTooltipTemplate(),3,null));
        assertEquals("+2 to All Active Skills",GearAffixDisplay.resolve(catalog.affix("WA-121").playerTooltipTemplate(),2,null));
        assertEquals("8.5% Life Stolen per Hit",GearAffixDisplay.resolve(catalog.affix("WA-096").playerTooltipTemplate(),8.5,null));
        assertEquals("Execute Eligible Enemies at or Below 5% Health",GearAffixDisplay.resolve(catalog.affix("WA-139").playerTooltipTemplate(),5,null));
        var skillRoll=new GearInstance.AffixRoll("WA-122",GearCatalog.Side.SUFFIX,"skill",1,1,
                new GearRequirements.Gate(1,Map.of()),"legacy mechanics","of Mastery");
        assertEquals("+1 to Quick Slash",GearAffixDisplay.format(skillRoll,new SkillId("quick_slash")));
        assertEquals("Skill modifier unavailable",GearAffixDisplay.format(skillRoll));
        assertThrows(IllegalArgumentException.class,()->GearAffixDisplay.validateTemplate("+V [InternalId]"));
    }
    @Test void fourAffixesRenderOnceAndDoNotMutatePersistedItemOrGenerationRevision() {
        var gear=GearQaFixtures.create(catalog,catalog.base("gm.plate_mithril.head.h"),UUID.randomUUID(),99,950,GearRarity.VERY_RARE);
        String original=gear.toJson();
        var loaded=GearInstance.fromJson(original);
        String beforeRender=loaded.toJson();
        var lines=GearTooltip.describe(loaded,99,Map.of());
        var modifiers=lines.stream().filter(line->line.style()==GearTooltip.Style.AFFIX).toList();
        assertEquals(4,modifiers.size());
        for(int i=0;i<4;i++)assertEquals(GearAffixDisplay.format(gear.affixes().get(i)),modifiers.get(i).text());
        assertEquals(beforeRender,loaded.toJson());
        assertEquals(gear,loaded);
        assertEquals(gear.identity(),loaded.identity());
        assertEquals(gear.affixes(),loaded.affixes());
        assertEquals(gear.requirements(),loaded.requirements());
        assertNotEquals("a710b780471e616a298e9023bd1e8aa919d0c442119e6a43516463260f00e998",
                new GearDropGenerator(catalog,new GearBindings(),GearAffixRuntime.ENABLED).revision());
    }
    @Test void actualPreV12ScoutLeatherRollGainsNewLineWithoutMigration() throws Exception {
        String saved;
        try(var source=getClass().getResourceAsStream("/gear/scout-leather-leggings-ga160-v11.json")){
            assertNotNull(source);saved=new String(source.readAllBytes(),StandardCharsets.UTF_8);
        }
        var item=GearInstance.fromJson(saved);
        var identity=item.identity();var affixes=item.affixes();var requirements=item.requirements();
        var lines=GearTooltip.describe(item,99,Map.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.DEX,10));
        assertEquals("8925cba6-57ea-3d37-8b38-f21a14b189bc",identity.toString());
        assertEquals(2.6,item.intrinsicStats().get("protectionPoints"));
        assertEquals(2.5,item.intrinsicStats().get("health"));
        assertEquals(List.of("+11.7% Enhanced Armor"),lines.stream().filter(l->l.style()==GearTooltip.Style.AFFIX).map(GearTooltip.Line::text).toList());
        assertTrue(lines.stream().anyMatch(l->l.text().equals("Armor: 2.6")));
        assertTrue(lines.stream().anyMatch(l->l.text().equals("Health: +2.5")));
        assertTrue(lines.stream().anyMatch(l->l.text().equals("Required Dexterity: 10")));
        assertEquals(identity,item.identity());assertEquals(affixes,item.affixes());assertEquals(requirements,item.requirements());
        assertEquals(item,GearInstance.fromJson(item.toJson()));
    }
    @Test void legendarySixAffixesHaveSixLinesAndNoSyntheticSeventh() {
        var item=GearQaFixtures.create(catalog,catalog.base("gm.plate_mithril.head.h"),UUID.randomUUID(),99,950,GearRarity.LEGENDARY);
        var modifiers=GearTooltip.describe(item,99,Map.of()).stream().filter(l->l.style()==GearTooltip.Style.AFFIX).toList();
        assertEquals(6,item.affixes().size());assertEquals(6,modifiers.size());
        for(int i=0;i<6;i++)assertEquals(GearAffixDisplay.format(item.affixes().get(i)),modifiers.get(i).text());
    }
}
