package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GearEquipmentLifecycleTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final Map<RpgAttribute,Integer> BASELINE=Map.of(RpgAttribute.STR,500,RpgAttribute.DEX,500,
            RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);
    private static final CombatBalanceProfile BALANCE=CombatBalanceProfile.loadCanonical();
    private static final DerivedStatService DERIVED=new DerivedStatService(BALANCE,new EffectiveAttributeService(BALANCE));
    static GearInstance fixture(String id) {
        var affix=CATALOG.affix(id);
        var base=CATALOG.bases().stream().filter(b->b.category()!=GearCatalog.Category.TOOL
                &&b.sourceWindow().getLast()==99&&GearDropGenerator.eligible(affix,b)).findFirst().orElseThrow();
        var tier=GearAffixTiers.compile(affix).getLast();
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),tier.tier(),tier.low(),
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(affix.requirementAttribute(base),
                        affix.attributeFloor(4,tier.minimumItemLevel()))),affix.name(),affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    static GearEquipmentResolution.Candidate slot(GearInstance item) {
        return new GearEquipmentResolution.Candidate(item,true,true,true);
    }
    @ParameterizedTest(name="validEquipChangesDerivedOwnerAndUnequipRestoresExactBaseline({0})")
    @ValueSource(strings={"WA-085","WA-086","WA-087","WA-088","WA-089","WA-090","WA-091","WA-092","WA-093"})
    void validEquipChangesDerivedOwnerAndUnequipRestoresExactBaseline(String id) {
        var item=fixture(id);
        var control=GearEquipmentResolution.resolve(99,BASELINE,List.of()).effects().derive(DERIVED,BASELINE);
        var equipped=GearEquipmentResolution.resolve(99,BASELINE,List.of(slot(item)));
        assertEquals(List.of(item),equipped.validItems());
        var effects=equipped.effects();
        assertEquals(item.affixes().getFirst().value(),effects.snapshot().value(id));
        assertNotEquals(control,effects.derive(DERIVED,BASELINE));
        var reloaded=GearEquipmentResolution.resolve(99,BASELINE,List.of(slot(GearInstance.fromJson(item.toJson())))).effects();
        assertEquals(effects,reloaded);
        assertEquals(effects.snapshot().revision(),reloaded.snapshot().revision());
        var removed=GearEquipmentResolution.resolve(99,BASELINE,List.of()).effects();
        assertEquals(control,removed.derive(DERIVED,BASELINE));
        assertTrue(removed.snapshot().empty());
        assertEquals(item.affixes().getFirst().value(),effects.snapshot().value(id),"accepted snapshot stays frozen");
        for(var invalid:List.of(new GearEquipmentResolution.Candidate(item,false,true,true),
                new GearEquipmentResolution.Candidate(item,true,false,true),
                new GearEquipmentResolution.Candidate(item,true,true,false))) {
            var rejected=GearEquipmentResolution.resolve(99,BASELINE,List.of(invalid));
            assertTrue(rejected.validItems().isEmpty());
            assertFalse(rejected.rejected().isEmpty());
            assertEquals(control,rejected.effects().derive(DERIVED,BASELINE));
        }
        var duplicate=GearEquipmentResolution.resolve(99,BASELINE,List.of(slot(item),slot(item)));
        assertEquals("DUPLICATE_IDENTITY",duplicate.rejected().get(item.identity()));
        assertEquals(control,duplicate.effects().derive(DERIVED,BASELINE));
        var invalidRequirements=GearEquipmentResolution.resolve(1,Map.of(),List.of(slot(item)));
        assertTrue(invalidRequirements.effects().snapshot().empty());
        assertEquals("UNMET_REQUIREMENTS",invalidRequirements.rejected().get(item.identity()));
    }
    @Test void snapshotOrderingAndLocalSourcesAreStableAndImmutable() {
        var first=fixture("WA-085");var second=fixture("WA-087");
        var snapshot=new GearEffectSnapshot(List.of(first,second));
        assertEquals(snapshot.revision(),new GearEffectSnapshot(List.of(second,first)).revision());
        assertEquals(first.affixes().getFirst().value(),snapshot.forItem(first.identity()).value("WA-085"));
        assertEquals(0,snapshot.forItem(first.identity()).value("WA-087"));
        assertTrue(snapshot.forItem(UUID.randomUUID()).empty());
        assertThrows(UnsupportedOperationException.class,()->snapshot.items().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.sources(GearEffectSnapshot.Operator.ATTRIBUTE).clear());
        assertThrows(IllegalArgumentException.class,()->new GearEffectSnapshot(List.of(first,first)));
    }
}
