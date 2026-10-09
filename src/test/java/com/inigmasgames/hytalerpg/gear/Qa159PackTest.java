package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.execution.summon.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Qa159PackTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite SUITE=new GearAffixQaSuite(CATALOG);
    private static final List<GearAffixQaSuite.Fixture> PACK=SUITE.fixtures().stream().filter(f->f.group().equals("qa159")).toList();
    @Test void exactCoverageUniqueLegalCarriersAndPersistedRolls() throws Exception {
        assertEquals(17,PACK.size());assertEquals(17,PACK.stream().map(GearAffixQaSuite.Fixture::itemBaseId).distinct().count());
        assertEquals(6,PACK.stream().filter(f->f.affixIds().size()==10).count());
        assertEquals(11,PACK.stream().filter(f->f.affixIds().size()==9).count());
        var ids=PACK.stream().flatMap(f->f.affixIds().stream()).toList();
        assertEquals(159,ids.size());assertEquals(159,new HashSet<>(ids).size());
        assertEquals(GearAffixRuntime.ENABLED,new HashSet<>(ids));assertFalse(ids.contains("WA-155"));
        assertEquals(Set.of(GearCatalog.Slot.HEAD,GearCatalog.Slot.CHEST,GearCatalog.Slot.HANDS,GearCatalog.Slot.LEGS),
                PACK.stream().limit(4).map(f->CATALOG.base(f.itemBaseId()).slot()).collect(java.util.stream.Collectors.toSet()));
        var owner=UUID.randomUUID();var report=new StringBuilder("# QA159 connected gear pack — R195\n\n17 distinct legal carriers; 159 unique functional affixes. WA-155 excluded. Six items have ten affixes; eleven have nine.\n\nThe approved 17-item adjustment preserves the thirteen mutually exclusive skill-rank affixes on thirteen held items plus four armor pieces.\n\nRolls use the upper legal endpoint of the first legal tier, with the native source window, requirements and selector restrictions retained. Legendary is a QA carrier quality, not a new random loot outcome.\n\n## Fixtures\n\n");
        for(var row:PACK){
            var item=SUITE.create(row.fixtureId(),owner);var base=CATALOG.base(item.baseId());
            assertTrue(SUITE.spawnable(row));assertTrue(GearAffixRuntime.supported(item));
            assertEquals(item,SUITE.create(row.fixtureId(),owner));assertEquals(item,GearInstance.fromJson(item.toJson()));
            assertEquals(row.fixtureId(),Qa159Pack.fixtureId(item));
            assertDoesNotThrow(()->IronSentinelAffixes.requireAdapted(item));
            assertTrue(item.affixes().stream().allMatch(a->GearDropGenerator.eligible(CATALOG.affix(a.familyId()),base)));
            assertEquals(item.affixes().size(),item.affixes().stream().map(GearInstance.AffixRoll::exclusionGroup).distinct().count());
            assertEquals(base.category()==GearCatalog.Category.HELD?1:0,item.affixes().stream().filter(a->a.exclusionGroup().equals("skiller")).count());
            var binding=new GearBindings().require(base.id());
            try(var stream=getClass().getResourceAsStream("/Server/Item/Items/RPG/Gear/"+binding.carrier(item.rarity())+".json")){
                assertNotNull(stream,"Pack carrier missing from shipped assets: "+binding.carrier(item.rarity()));
                assertTrue(JsonParser.parseReader(new java.io.InputStreamReader(stream)).isJsonObject());
            }
            assertThrows(IllegalArgumentException.class,()->copy(item,item.affixes(),item.rngVersion(),false));
            var snapshot=GearAffixRuntime.effects(List.of(item)).snapshot();
            for(var roll:item.affixes())assertEquals(roll.value(),snapshot.value(roll.familyId()),0,roll.familyId());
            var attrs=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);for(var a:RpgAttribute.values())attrs.put(a,500);
            var accepted=GearRequirements.resolve(99,attrs,List.of(new GearRequirements.Equipped(item.identity(),item.requirements(),GearAffixRuntime.attributes(item))));
            assertTrue(accepted.valid().contains(item.identity()));
            assertTrue(GearAffixRuntime.effects(List.of()).snapshot().empty());
            report.append("- `").append(Qa159Pack.listing(row,CATALOG)).append("`\n  Requirements: ").append(item.requirements()).append("\n");
        }
        report.append("\n## Per-affix test matrix\n\nEXPECTED means a check is applicable, not that connected QA has passed. NOT_APPLICABLE is not a failure. OWNER_ONLY/INAPPLICABLE source affixes must remain excluded from bound-item inheritance; their rejection is itself observable in the Sentinel snapshot. Conditional, block, status, cleanse, corpse and minion effects need their real trigger; a tooltip is not proof.\n\n| Fixture | Affix | Name | Roll | UI stats | Gameplay | Sentinel inheritance | Disposition | Stat field / runtime owner |\n|---|---|---|---|---|---|---|---|---|\n");
        var manifest=new ArrayList<Object>();
        for(var row:PACK){var item=SUITE.create(row.fixtureId(),owner);manifest.add(Qa159Pack.source(item));
            for(var a:item.affixes()) {var checks=Qa159Pack.checks(a.familyId());
                report.append("| ").append(row.fixtureId()).append(" | ").append(a.familyId()).append(" | ").append(a.name())
                        .append(" | ").append(a.value()).append(a.selector()==null?"":" / "+a.selector()).append(" | ")
                        .append(checks.get("inventoryStats")).append(" | ").append(checks.get("gameplay")).append(" | ")
                        .append(checks.get("sentinel")).append(" | ").append(checks.get("sentinelDisposition")).append(" | ")
                        .append(checks.get("statsField")).append(" / ").append(checks.get("runtimeOwner")).append(" |\n");
            }
        }
        var dir=Path.of("build/reports/qa159");Files.createDirectories(dir);
        Files.writeString(dir.resolve("QA159-PACK.md"),report);
        Files.writeString(dir.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
    }
    @Test void rejectsTamperedMagnitudesAndKeepsOldPayloadsReadable(){
        var item=SUITE.create("qa159-05",UUID.randomUUID());var json=JsonParser.parseString(item.toJson()).getAsJsonObject();
        json.getAsJsonArray("affixes").get(0).getAsJsonObject().addProperty("value",1000000);
        assertThrows(RuntimeException.class,()->GearInstance.fromJson(json.toString()));
        var older=SUITE.create("ab-wa-001-affixed",UUID.randomUUID());assertEquals(older,GearInstance.fromJson(older.toJson()));
    }
    @Test void qaFlagDoesNotChangeAnyOperatorMagnitude(){
        for(var row:PACK) {
            var pack=SUITE.create(row.fixtureId(),UUID.randomUUID());
            // Construct an ordinary legal three-prefix/three-suffix legacy Legendary payload
            // where possible. The QA runtime must consume identical frozen operator values.
            var prefixes=pack.affixes().stream().filter(a->a.side()==GearCatalog.Side.PREFIX).limit(3).toList();
            var suffixes=pack.affixes().stream().filter(a->a.side()==GearCatalog.Side.SUFFIX).limit(3).toList();
            if(prefixes.size()!=3||suffixes.size()!=3)continue;
            var rolls=new ArrayList<>(prefixes);rolls.addAll(suffixes);
            var qa=copy(pack,rolls,"parity",true);var production=copy(pack,rolls,"parity",false);
            var a=GearAffixRuntime.effects(List.of(qa));var b=GearAffixRuntime.effects(List.of(production));
            for(var op:GearEffectSnapshot.Operator.values())assertEquals(a.snapshot().total(op),b.snapshot().total(op),0);
            assertEquals(HytaleGearEquipment.magicFindBreakdown(320,List.of(qa)),HytaleGearEquipment.magicFindBreakdown(320,List.of(production)));
            assertEquals(HytaleGearEquipment.goldFind(a.snapshot()),HytaleGearEquipment.goldFind(b.snapshot()),0);
        }
    }
    private static GearInstance copy(GearInstance i,List<GearInstance.AffixRoll> rolls,String rng,boolean qa){
        return new GearInstance(i.schemaVersion(),i.identity(),i.definitionRevision(),i.baseId(),i.baseName(),i.category(),i.sourceEra(),
                i.itemLevel(),i.rarity(),i.intrinsicThousandths(),i.intrinsicStats(),i.requirements(),rolls,rng,qa);
    }
}
