package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelAffixInheritanceTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void everyAuthoredFamilyHasAnExplicitDispositionAndUnsupportedOnesFailBeforeForge(){
        assertEquals(160,catalog.affixes().size());
        var totals=new EnumMap<IronSentinelAffixes.Disposition,Integer>(IronSentinelAffixes.Disposition.class);
        for(var affix:catalog.affixes()){
            var decision=IronSentinelAffixes.classify(affix.id());
            assertNotNull(decision.disposition(),affix.id());
            assertFalse(decision.reason().isBlank(),affix.id());
            totals.merge(decision.disposition(),1,Integer::sum);
            if(!decision.adapted()){
                var failed=assertThrows(IllegalArgumentException.class,()->IronSentinelAffixes.requireAdapted(item(affix.id(),1)));
                assertTrue(failed.getMessage().contains(affix.id()),affix.id());
                assertTrue(failed.getMessage().contains(decision.reason()),affix.id());
            }
        }
        assertEquals(Map.of(IronSentinelAffixes.Disposition.SELF_STAT,86,
                IronSentinelAffixes.Disposition.ATTACK_PROC,29,IronSentinelAffixes.Disposition.AURA,3,
                IronSentinelAffixes.Disposition.OWNER_ONLY,20,IronSentinelAffixes.Disposition.UNSUPPORTED,22),totals);
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify("WA-151").disposition());
        assertEquals(IronSentinelAffixes.Disposition.AURA,IronSentinelAffixes.classify("WA-148").disposition());
        assertEquals(IronSentinelAffixes.Disposition.ATTACK_PROC,IronSentinelAffixes.classify("WA-053").disposition());
        assertFalse(IronSentinelAffixes.classify("WA-148").adapted());
    }

    @Test void increasedAttackAndPhysicalDamageUseOneBucketAndCritHasSharedCap(){
        var attack=item("WA-005",30);
        IronSentinelAffixes.requireAdapted(attack);
        assertEquals(1.3,IronSentinelAffixes.physicalModifiers(attack).factor(),1e-9);
        assertEquals(1.25,IronSentinelAffixes.physicalModifiers(item("WA-006",25)).factor(),1e-9);
        assertEquals(1.0,IronSentinelAffixes.physicalModifiers(item("WA-002",30)).factor(),1e-9);
        assertEquals(.75,IronSentinelAffixes.criticalChance(item("WA-010",90)),1e-9);
        assertEquals(1.7,IronSentinelAffixes.criticalMultiplier(item("WA-011",20)),1e-9);
        assertEquals(0,IronSentinelAffixes.criticalChance(item("WA-151",20)),1e-9);
        IronSentinelAffixes.requireAdapted(item("WA-151",20));
        assertEquals(.20,IronSentinelAffixes.resistances(item("WA-074",20)).get("Fire"),1e-9);
    }

    @Test void aSupportedWeaponPropertyOnArmorStillRejectsBeforeCustody(){
        var base=catalog.base("gm.plate_iron.head.n");var affix=catalog.affix("WA-005");
        var roll=new GearInstance.AffixRoll("WA-005",affix.side(),affix.exclusionGroup(),1,20,
                new GearRequirements.Gate(1,Map.of()),"Invalid armor-local attack damage",affix.name());
        var armor=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
        assertTrue(assertThrows(IllegalArgumentException.class,()->IronSentinelAffixes.requireAdapted(armor))
                .getMessage().contains("weapon-local property"));
    }

    private GearInstance item(String family,double value){
        var base=catalog.base("gm.sword_iron.n");var affix=catalog.affix(family);
        var roll=new GearInstance.AffixRoll(family,affix.side(),affix.exclusionGroup(),1,value,
                new GearRequirements.Gate(1,Map.of()),"Sentinel inheritance test",affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
}
