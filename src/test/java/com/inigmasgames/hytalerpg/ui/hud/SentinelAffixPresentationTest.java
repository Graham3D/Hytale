package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelAffixPresentationTest {
    private final GearCatalog catalog=GearCatalog.load();
    private GearInstance item(String family){
        var base=catalog.base("gm.daggers_cobalt.n");
        var rolls=new ArrayList<GearInstance.AffixRoll>();
        if(family!=null){
            var affix=catalog.affix(family);
            rolls.add(new GearInstance.AffixRoll(family,affix.side(),affix.exclusionGroup(),1,20,
                    new GearRequirements.Gate(1,Map.of()),"20% increased attack rate",affix.name()));
        }
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                family==null?GearRarity.COMMON:GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
    @Test void verticalLinesShowOnlyInheritedRolls(){
        assertEquals(List.of("No inherited affixes"),SentinelAffixPresentation.of(item(null)).rows());
        assertEquals(List.of("+20% Attack Speed"),SentinelAffixPresentation.of(item("WA-008")).rows());
        assertEquals(List.of("No inherited affixes"),SentinelAffixPresentation.of(item("WA-121")).rows());
    }
}
