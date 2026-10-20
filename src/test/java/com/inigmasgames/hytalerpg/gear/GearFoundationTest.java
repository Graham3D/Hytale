package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.*;

class GearFoundationTest {
    final GearCatalog catalog=GearCatalog.load();
    @Test void completeAuthoredImportAndEraWindows() {
        assertEquals(447,catalog.bases().size()); assertEquals(160,catalog.affixes().size());
        assertEquals(261,catalog.bases().stream().filter(b->b.category()==GearCatalog.Category.HELD).count());
        assertEquals(156,catalog.bases().stream().filter(b->b.category()==GearCatalog.Category.ARMOR).count());
        assertEquals(30,catalog.bases().stream().filter(b->b.category()==GearCatalog.Category.TOOL).count());
        for(var base:catalog.bases()) if(base.worldDropCandidate()) {
            assertTrue(base.eligible(base.era(),base.sourceWindow().getFirst()));
            assertTrue(base.eligible(base.era(),base.sourceWindow().getLast()));
            assertFalse(base.eligible(base.era(),base.sourceWindow().getFirst()-1));
            assertFalse(base.eligible(base.era(),base.sourceWindow().getLast()+1));
            for(var era:DifficultyId.values()) if(era!=base.era()) assertFalse(base.eligible(era,base.sourceWindow().getFirst()));
        }
    }
    @Test void allIntrinsicRollsFreezeOnlyPowerFields() {
        var base=catalog.base("gm.sword_crude.n");
        for(int roll=900;roll<=1000;roll++) {
            var item=GearInstance.authoredQa(base,UUID.randomUUID(),1,roll,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
            assertEquals(item,GearInstance.fromJson(item.toJson()));
            assertEquals(Map.of(DEX,10),item.requirements().attributes());
            assertEquals(1,item.requirements().level());
            assertEquals(roll==1000,item.perfectCommon());
        }
        var minimum=GearInstance.authoredQa(base,UUID.randomUUID(),1,900,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        assertEquals(9.2,minimum.intrinsicStats().get("physicalMin"));
        assertEquals(12.4,minimum.intrinsicStats().get("physicalMax"));
        assertThrows(IllegalArgumentException.class,()->GearInstance.authoredQa(base,UUID.randomUUID(),1,899,GearRarity.COMMON,List.of(),BigDecimal.ZERO));
    }
    @Test void requirementsTakeMaximumThenEaseAndNeverReduceLevel() {
        var gate=GearRequirements.combine(new GearRequirements.Gate(30,Map.of(STR,50,DEX,10)),
                List.of(new GearRequirements.Gate(45,Map.of(STR,85,INT,25))),new BigDecimal("0.18"));
        assertEquals(45,gate.level()); assertEquals(Map.of(STR,70,DEX,10,INT,21),gate.attributes());
        assertEquals(4,gate.failures(1,Map.of(),true).size());
        assertThrows(IllegalArgumentException.class,()->GearRequirements.combine(new GearRequirements.Gate(1,Map.of(STR,20,DEX,20)),
                List.of(new GearRequirements.Gate(1,Map.of(INT,20,WIS,20))),BigDecimal.ZERO));
    }
    @Test void fixedPointRejectsSelfAndCyclesAndRecomputesAfterRespec() {
        var a=new GearRequirements.Equipped(UUID.randomUUID(),new GearRequirements.Gate(1,Map.of(STR,20)),Map.of(DEX,10));
        var b=new GearRequirements.Equipped(UUID.randomUUID(),new GearRequirements.Gate(1,Map.of(DEX,20)),Map.of(STR,10));
        var self=new GearRequirements.Equipped(UUID.randomUUID(),new GearRequirements.Gate(1,Map.of(STR,20)),Map.of(STR,30));
        assertTrue(GearRequirements.resolve(99,Map.of(STR,10,DEX,10),List.of(a,b,self)).valid().isEmpty());
        assertEquals(3,GearRequirements.resolve(99,Map.of(STR,20,DEX,10),List.of(b,a,self)).valid().size());
        assertTrue(GearRequirements.resolve(99,Map.of(STR,10,DEX,10),List.of(a,b,self)).valid().isEmpty());
        assertThrows(IllegalArgumentException.class,()->GearRequirements.resolve(99,Map.of(STR,50),List.of(a,a)));
    }
    @Test void rarityBudgetsAndLegendaryProvenance() {
        assertFalse(GearRarity.LEGENDARY.eligible(99,DifficultyId.NORMAL));
        assertFalse(GearRarity.LEGENDARY.eligible(59,DifficultyId.HELL));
        assertTrue(GearRarity.LEGENDARY.eligible(60,DifficultyId.HELL));
        assertFalse(GearRarity.RARE.legalBudget(3,0)); assertTrue(GearRarity.RARE.legalBudget(2,1));
        assertFalse(GearRarity.VERY_RARE.legalBudget(3,3)); assertTrue(GearRarity.LEGENDARY.legalBudget(3,3));
        assertEquals("Particles/Drop/Common/Drop_Common.particlesystem",GearRarity.VERY_RARE.particlePath());
    }
    @Test void affixRequirementsUseAuthoredPoliciesAndQGates() {
        for(var affix:catalog.affixes()) assertFalse(GearAffixTiers.compile(affix).isEmpty(),affix.id());
        var speed=catalog.affix("WA-008");
        assertEquals(List.of(5,27,48,69,90),java.util.stream.IntStream.range(0,5).mapToObj(speed::ordinaryTierMinimum).toList());
        assertEquals(85,catalog.affix("WA-004").attributeFloor(4,0));
        assertEquals(45,catalog.affix("WA-151").attributeFloor(4,0));
        assertEquals(76,catalog.affix("WA-122").attributeFloor(0,94));
        assertEquals(DEX,catalog.affix("WA-085").requirementAttribute(catalog.base("gm.sword_crude.n")));
    }
    @Test void strikeSampleIsSharedAndNeverMultipliesNativeVariance() {
        for(int seed=0;seed<1000;seed++) {
            double first=GearPower.sample(10.2,13.8,"strike/"+seed);
            assertEquals(first,GearPower.sample(10.2,13.8,"strike/"+seed));
            assertTrue(first>=10.2 && first<=13.8);
            assertEquals(Math.rint(first*10),first*10,1e-8);
        }
    }
    @Test void hoverReportsEveryFailureAndArmorVerb() {
        var item=GearInstance.authoredQa(catalog.base("gm.plate_iron.head.n"),UUID.randomUUID(),10,1000,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        var lines=GearTooltip.describe(item,1,Map.of(STR,10));
        assertTrue(lines.stream().anyMatch(l->l.text().startsWith("Required Strength:")&&l.style()==GearTooltip.Style.ERROR));
        assertTrue(lines.stream().anyMatch(l->l.text().equals("Armor: "+GearTooltip.number(item.intrinsicStats().get("protectionPoints")))));
        assertEquals("#ffffff",lines.getFirst().color());
        assertTrue(lines.stream().noneMatch(l->l.style()==GearTooltip.Style.AFFIX));
    }
    @Test void qaRarityFixturesFreezeRequirementsAndBlueAffixLines() {
        for(var rarity:GearRarity.values()) {
            for(String baseId:List.of("gm.sword_mithril.h","gm.plate_mithril.head.h")) {
                var gear=GearQaFixtures.create(catalog,catalog.base(baseId),UUID.randomUUID(),99,1000,rarity);
                assertEquals(gear,GearInstance.fromJson(gear.toJson()));
                if(baseId.contains("plate") && rarity==GearRarity.LEGENDARY) {
                    assertTrue(GearAffixRuntime.supported(gear));
                    assertEquals(0,gear.affixes().stream().filter(a->a.familyId().equals("WA-072")).count());
                }
                assertTrue(gear.affixes().size()>=rarity.minAffixes && gear.affixes().size()<=rarity.maxAffixes);
                var lines=GearTooltip.describe(gear,99,Map.of());
                assertEquals(rarity.color,lines.getFirst().color());
                assertEquals(gear.affixes().size(),lines.stream().filter(l->l.style()==GearTooltip.Style.AFFIX && GearRarity.AFFIX_COLOR.equals(l.color())).count());
            }
        }
    }
    @Test void playerTooltipUsesFrozenRollsWithoutGenerationDiagnostics() {
        for(var rarity:GearRarity.values()) {
            var gear=GearQaFixtures.create(catalog,catalog.base("gm.plate_mithril.head.h"),UUID.randomUUID(),99,1000,rarity);
            var loaded=GearInstance.fromJson(gear.toJson());
            var lines=GearTooltip.describe(loaded,99,Map.of());
            assertEquals(gear.displayName(),lines.getFirst().text());
            assertFalse(lines.getFirst().text().contains(gear.identity().toString().substring(0,8)));
            var modifiers=lines.stream().filter(l->l.style()==GearTooltip.Style.AFFIX).toList();
            assertEquals(gear.affixes().size(),modifiers.size());
            for(int i=0;i<modifiers.size();i++)assertEquals(GearAffixDisplay.format(gear.affixes().get(i)),modifiers.get(i).text());
            String visible=lines.stream().map(GearTooltip.Line::text).reduce("",(a,b)->a+"\n"+b);
            for(String hidden:List.of("Base variant:","Source era:","Intrinsic base roll:","RPG_Gear_","server.gear.","(T5,"))
                assertFalse(visible.contains(hidden),hidden);
            assertFalse(visible.contains("GA-159"));
        }
    }
    @Test void armorIncreaseIsOneConciseLineFromPersistedValue() {
        var affix=new GearInstance.AffixRoll("GA-160",GearCatalog.Side.PREFIX,"local-armor",5,11.7,
                new GearRequirements.Gate(1,Map.of()),"11.7% increased local armor protection of this piece, after its local protection-point additions.","Fortified");
        assertEquals("+11.7% Enhanced Armor",GearAffixDisplay.format(affix));
    }
}
