package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearAffixQaSuiteTest {
    @Test void managedMagicAndRarePresentationUsesApprovedNamesAndTextColors(){
        assertEquals("Rare",GearRarity.MAGIC.label);
        assertEquals("#1d4dff",GearRarity.MAGIC.color);
        assertEquals("Epic",GearRarity.RARE.label);
        assertEquals("#6b00ff",GearRarity.RARE.color);
        assertEquals("#6b00ff",GearQuality.RARE.color());
        assertEquals("Epic",GearRarity.VERY_RARE.label);
        assertEquals(7,GearRarity.values().length);
        assertEquals("MAGIC",GearRarity.MAGIC.name());
        assertEquals("RARE",GearRarity.RARE.name());
        assertEquals("Particles/Drop/Rare/Drop_Rare.particlesystem",GearRarity.MAGIC.particlePath());
        assertEquals("Particles/Drop/Common/Drop_Common.particlesystem",GearRarity.RARE.particlePath());
    }
    @Test void completeCoverageAndProductionLegality() throws Exception {
        var suite=new GearAffixQaSuite(GearCatalog.load());
        assertTrue(suite.fixtures().size()>329);
        assertEquals(9,suite.fixtures().stream().filter(f->!f.fixtureId().startsWith("ab-")&&!f.fixtureId().startsWith("combo-")&&!f.fixtureId().startsWith("tooltip-")&&!f.fixtureId().startsWith("qa159-")).count());
        assertEquals(3,suite.fixtures().stream().filter(f->f.fixtureId().startsWith("tooltip-")).count());
        assertTrue(suite.fixtures().stream().anyMatch(f->f.fixtureId().startsWith("combo-")&&f.affixIds().size()>1));
        assertTrue(suite.fixtures().stream().filter(f->f.fixtureId().startsWith("combo-"))
                .flatMap(f->f.affixIds().stream()).allMatch(GearAffixRuntime.ENABLED::contains));
        assertEquals(160,suite.fixtures().stream().filter(f->f.fixtureId().endsWith("-affixed")).count());
        assertEquals(160,suite.fixtures().stream().filter(f->f.fixtureId().endsWith("-control")).count());
        assertEquals(160,suite.coverage().size());
        assertTrue(suite.coverage().stream().allMatch(r->Set.of("GATED","FUNCTIONAL").contains(r.status())));
        assertTrue(suite.coverage().stream().filter(r->r.status().equals("FUNCTIONAL"))
                .allMatch(r->GearAffixRuntime.ENABLED.contains(r.affixId())));
        assertEquals(160,suite.coverage().stream().map(GearAffixQaSuite.Coverage::affixId).distinct().count());
        var player=UUID.randomUUID();
        for(var row:suite.fixtures()){
            var preview=suite.preview(row);
            assertEquals(row.affixIds().size(),preview.affixes().size());
            if(!suite.spawnable(row)){
                assertThrows(IllegalArgumentException.class,()->suite.create(row.fixtureId(),player));
                continue;
            }
            var item=suite.create(row.fixtureId(),player);
            assertTrue(item.qaOnly());
            assertTrue(GearAffixRuntime.supported(item));
            assertEquals(item.identity(),suite.create(row.fixtureId(),player).identity());
            assertEquals(row.affixIds().size(),item.affixes().size());
        }
        for(var row:suite.fixtures().stream().filter(f->f.fixtureId().endsWith("-affixed")&&suite.spawnable(f)).toList()){
            var control=suite.create(row.fixtureId().replace("-affixed","-control"),player);
            var treated=suite.create(row.fixtureId(),player);
            assertEquals(control.baseId(),treated.baseId());
            assertEquals(control.itemLevel(),treated.itemLevel());
            assertEquals(control.rarity(),treated.rarity());
            assertEquals(control.intrinsicStats(),treated.intrinsicStats());
            assertEquals(control.requirements(),treated.requirements());
            assertTrue(control.affixes().isEmpty());
            assertEquals(1,treated.affixes().size());
        }
        assertEquals(160,suite.coverage().stream().filter(r->r.fixtureId().endsWith("-affixed")).count());
    }
}
