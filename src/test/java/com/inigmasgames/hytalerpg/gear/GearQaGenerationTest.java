package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class GearQaGenerationTest {
    private final GearCatalog catalog=GearCatalog.load();
    private final GearBindings bindings=new GearBindings();
    private final GearDropGenerator generator=new GearDropGenerator(catalog,bindings,GearAffixRuntime.ENABLED);

    @Test void requestedMappedTypesUseProductionAffixRulesAndPersistProtectedQaIdentity() {
        String[][] cases={{"helmet","normal","normal"},{"helmet","magic","normal"},
                {"chest","rare","nightmare"},{"gloves","rare","hell"},
                {"legs","rare","hell"},{"sword","rare","nightmare"}};
        for(String[] row:cases){
            var request=GearQaRequest.parse(row[0],row[1],row[2],null,"accept-"+String.join("-",row));
            var gear=generator.generateQa(request);
            var base=catalog.base(gear.baseId());
            assertTrue(request.matches(base),Arrays.toString(row));
            assertEquals(request.era(),base.era());assertEquals(request.rarity(),gear.rarity());
            assertTrue(bindings.require(base.id()).mapped());assertTrue(base.eligible(request.era(),gear.itemLevel()));
            assertTrue(GearAffixRuntime.supported(gear));assertTrue(gear.qaOnly());
            assertTrue(gear.rngVersion().startsWith("admin-qa/"));
            assertEquals(Optional.empty(),GearEconomy.salvage(gear));
            assertEquals(gear,GearInstance.fromJson(gear.toJson()));
            assertEquals(gear.affixes().size(),GearTooltip.describe(gear,99,Map.of()).stream()
                    .filter(line->line.style()==GearTooltip.Style.AFFIX).count());
            switch(gear.rarity()){
                case NORMAL->assertEquals(0,gear.affixes().size());
                case MAGIC->assertTrue(gear.affixes().size()>=1&&gear.affixes().size()<=2);
                case RARE->assertTrue(gear.affixes().size()>=3&&gear.affixes().size()<=6);
                default->fail("Legacy rarity returned by QA generation");
            }
            assertEquals(gear, generator.generateQa(request));
        }
    }

    @Test void aliasesLevelsMaxModeAndBlockedNativeCategoryFailClosed() {
        assertEquals("head",GearQaRequest.parse("helm","rare","hell",null,"x").type());
        assertEquals("shortbow",GearQaRequest.parse("bow","magic","hell",null,"x").type());
        assertEquals("book",GearQaRequest.parse("spellbook","rare","normal",null,"x").type());
        var replay=GearQaRequest.parse("helmet","rare","nightmare","seed:replay-this",null);
        assertEquals(generator.generateQa(replay),generator.generateQa(
                GearQaRequest.parse("helmet","rare","nightmare","seed:replay-this",null)));
        assertThrows(IllegalArgumentException.class,()->GearQaRequest.parse("head","epic","hell",null,null));
        assertThrows(IllegalArgumentException.class,()->GearQaRequest.parse("head","rare","hell","100",null));
        var exact=GearQaRequest.parse("helmet","rare","hell","90","level-90");
        assertEquals(90,generator.generateQa(exact).itemLevel());
        var max=GearQaRequest.parse("sword","rare","hell","max","max-sword");
        var item=generator.generateQa(max);
        assertEquals(1000,item.intrinsicThousandths());assertTrue(item.affixes().size()>=3&&item.affixes().size()<=6);
        assertTrue(item.qaOnly());
        var blocked=GearQaRequest.parse("shield","rare","hell",null,"shield-blocked");
        assertTrue(assertThrows(IllegalArgumentException.class,()->generator.generateQa(blocked)).getMessage().startsWith("No valid HELL-era shield base"));
        for(String type:List.of("longbow","spear","shield","staff","wand","spellbook","rifle","blunderbuss","bomb")){
            var unsupported=GearQaRequest.parse(type,"normal","normal",null,"blocked-"+type);
            assertThrows(IllegalArgumentException.class,()->generator.generateQa(unsupported),type);
        }
        assertThrows(IllegalArgumentException.class,()->generator.generateQa(
                GearQaRequest.parse("helmet","rare","normal","90","outside-window")));
        assertThrows(IllegalArgumentException.class,()->generator.generateQa(
                GearQaRequest.parse("helmet","rare","normal","1","wrong-level")));
        assertNotEquals("a710b780471e616a298e9023bd1e8aa919d0c442119e6a43516463260f00e998",generator.revision());
    }

    @Test void normalEraLegsCanRollFortifiedOrLaminatedAndHellHelmetHasSix() {
        var families=new HashSet<String>();
        for(int i=0;i<64&&!families.contains("GA-159")&&!families.contains("GA-160");i++){
            var request=GearQaRequest.parse("legs","magic","normal",null,"leg-affix-"+i);
            var gear=generator.generateQa(request);
            assertEquals(DifficultyId.NORMAL,gear.sourceEra());assertEquals(1,gear.affixes().size());
            families.add(gear.affixes().getFirst().familyId());
        }
        assertTrue(families.contains("GA-159")||families.contains("GA-160"),families.toString());
        var helmet=generator.generateQa(GearQaRequest.parse("helmet","rare","hell",null,"rare-helmet"));
        assertTrue(helmet.affixes().size()>=3&&helmet.affixes().size()<=6);
    }

}
