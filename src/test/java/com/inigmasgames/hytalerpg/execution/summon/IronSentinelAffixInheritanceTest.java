package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelAffixInheritanceTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void sixNativeRecipientFamiliesAndExplicitChassisConditions(){
        for(String id:List.of("WA-079","WA-080","WA-081","WA-082","WA-084","WA-111")){
            assertTrue(IronSentinelAffixes.classify(id).adapted(),id);
            IronSentinelAffixes.requireAdapted(item(id,20));
        }
        for(String id:List.of("WA-083","WA-147","WA-141")){
            assertTrue(IronSentinelAffixes.classify(id).adapted(),id);
            assertTrue(IronSentinelAffixes.classify(id).reason().startsWith("INAPPLICABLE"),id);
            IronSentinelAffixes.requireAdapted(item(id,10));
        }
    }

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
        assertEquals(Map.of(IronSentinelAffixes.Disposition.SELF_STAT,70,
                IronSentinelAffixes.Disposition.ATTACK_PROC,31,IronSentinelAffixes.Disposition.AURA,3,
                IronSentinelAffixes.Disposition.OWNER_ONLY,56),totals);
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify("WA-085").disposition());
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify("WA-113").disposition());
        assertEquals(IronSentinelAffixes.Disposition.SELF_STAT,IronSentinelAffixes.classify("WA-111").disposition());
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify("WA-151").disposition());
        assertEquals(IronSentinelAffixes.Disposition.AURA,IronSentinelAffixes.classify("WA-148").disposition());
        assertEquals(IronSentinelAffixes.Disposition.ATTACK_PROC,IronSentinelAffixes.classify("WA-053").disposition());
        assertTrue(IronSentinelAffixes.classify("WA-148").adapted());
        for(int n=17;n<=40;n++)assertTrue(IronSentinelAffixes.classify(String.format(java.util.Locale.ROOT,"WA-%03d",n)).adapted());
        assertTrue(IronSentinelAffixes.classify("WA-007").adapted());
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
        var water=item("WA-073",20);
        IronSentinelAffixes.requireAdapted(water);
        assertEquals(.20,IronSentinelAffixes.resistances(water).get("Ice"),1e-9);
        assertEquals(0,IronSentinelAffixes.resistances(water).get("Fire"),1e-9);
        var earth=item("WA-075",20);
        IronSentinelAffixes.requireAdapted(earth);
        assertEquals(.20,IronSentinelAffixes.resistances(earth).get("RPG_Nature"),1e-9);
        assertEquals(.20,IronSentinelAffixes.resistances(earth).get("Earth"),1e-9);
        var prism=item("WA-078",20);
        IronSentinelAffixes.requireAdapted(prism);
        assertEquals(.20,IronSentinelAffixes.resistances(prism).get("RPG_Void"),1e-9);
        assertEquals(.20,IronSentinelAffixes.resistances(prism).get("Ice"),1e-9);
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

    @Test void sourceItemMinionBonusDoesNotRecursivelyBuffTheSentinel(){
        var base=catalog.base("gm.staff_oracle.h");var affix=catalog.affix("WA-113");
        var roll=new GearInstance.AffixRoll("WA-113",affix.side(),affix.exclusionGroup(),1,25,
                new GearRequirements.Gate(80,Map.of()),"Owner-only minion effect",affix.name());
        var source=GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
        var control=GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        IronSentinelAffixes.requireAdapted(source);
        var inherited=IronSentinelStatProjection.project(20,new GearEffectSnapshot(List.of(source)),2);
        var baseline=IronSentinelStatProjection.project(20,new GearEffectSnapshot(List.of(control)),2);
        assertEquals(baseline.finalPhysicalMin(),inherited.finalPhysicalMin(),1e-9);
        assertEquals(baseline.finalMaxHealth(),inherited.finalMaxHealth(),1e-9);
        assertEquals(baseline.attackInterval(),inherited.attackInterval(),1e-9);
    }

    private GearInstance item(String family,double value){
        var base=catalog.base("gm.sword_iron.n");var affix=catalog.affix(family);
        var roll=new GearInstance.AffixRoll(family,affix.side(),affix.exclusionGroup(),1,value,
                new GearRequirements.Gate(1,Map.of()),"Sentinel inheritance test",affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
}
