package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelStatProjectionTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void nativeChassisContinuesBeyondBaseRankTwentyAndRetainsCurrentHealth(){
        var swordBase=catalog.base("gm.sword_iron.h");
        var sword=GearQaFixtures.create(catalog,swordBase,UUID.randomUUID(),swordBase.sourceWindow().getLast(),950,GearRarity.COMMON);
        var one=IronSentinelStatProjection.project(1,sword,3);
        var twenty=IronSentinelStatProjection.project(20,sword,3);
        var twentyFive=IronSentinelStatProjection.project(25,sword,3);
        assertEquals(1,one.skillScale(),1e-9);
        assertEquals(1.475,twenty.skillScale(),1e-9);
        assertEquals(1.6,twentyFive.skillScale(),1e-9);
        assertEquals(150,one.nativeMaxHealth(),1e-9);
        assertEquals(8,one.nativePhysicalMin(),1e-9);
        assertEquals(12,one.nativePhysicalMax(),1e-9);
        assertTrue(twentyFive.nativeMaxHealth()>twenty.nativeMaxHealth());
        assertTrue(twentyFive.nativePhysicalMin()>twenty.nativePhysicalMin());
        assertTrue(twentyFive.nativeProtection()>twenty.nativeProtection());
        assertEquals(one.sourcePhysicalMin(),twentyFive.sourcePhysicalMin(),1e-9);
        assertEquals(100,IronSentinelStatProjection.retainCurrentHealth(100,200));
        assertEquals(90,IronSentinelStatProjection.retainCurrentHealth(100,90));
    }

    @Test void weaponAddsSourceLocalPowerOnceAndArmorAddsProtectionOnce(){
        var swordBase=catalog.base("gm.sword_iron.h");
        var weapon=GearQaFixtures.create(catalog,swordBase,UUID.randomUUID(),swordBase.sourceWindow().getLast(),950,GearRarity.UNCOMMON);
        var attack=IronSentinelStatProjection.project(1,weapon,3);
        assertEquals(attack.nativePhysicalMin()+GearAffixRuntime.physical(weapon).minimum(),attack.finalPhysicalMin(),1e-9);
        assertEquals(attack.nativePhysicalMax()+GearAffixRuntime.physical(weapon).maximum(),attack.finalPhysicalMax(),1e-9);
        assertEquals(0,attack.sourceProtection(),1e-9);
        var armorBase=catalog.base("gm.plate_iron.head.h");
        var armor=GearQaFixtures.create(catalog,armorBase,UUID.randomUUID(),armorBase.sourceWindow().getLast(),950,GearRarity.UNCOMMON);
        var defense=IronSentinelStatProjection.project(1,armor,3);
        assertEquals(0,defense.sourcePhysicalMin(),1e-9);
        assertEquals(GearAffixRuntime.protection(armor)/100,defense.sourceProtection(),1e-9);
        assertEquals(1-(1-defense.nativeProtection())*(1-defense.sourceProtection()),defense.finalProtection(),1e-9);
        assertEquals(defense.nativeMaxHealth()+defense.sourceHealth(),defense.finalMaxHealth(),1e-9);
    }

    @Test void twentyPercentAttackRateDividesIntervalByOnePointTwo(){
        assertEquals(1.2,1+.20,1e-9);
        assertEquals(2.5,IronSentinelStatProjection.attackInterval(3,.20),1e-9);
        assertNotEquals(3*.8,IronSentinelStatProjection.attackInterval(3,.20));
        assertThrows(IllegalArgumentException.class,()->IronSentinelStatProjection.attackInterval(3,-1));
        var base=catalog.base("gm.sword_iron.n");var alacrity=catalog.affix("WA-008");
        var roll=new GearInstance.AffixRoll("WA-008",alacrity.side(),alacrity.exclusionGroup(),1,20,
                new GearRequirements.Gate(1,Map.of()),"20% increased attack rate (operator QA roll)",alacrity.name());
        var source=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),java.math.BigDecimal.ZERO);
        IronSentinelAffixes.requireAdapted(source);
        assertEquals(2/1.2,IronSentinelStatProjection.project(1,source,2).attackInterval(),1e-9);
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify("WA-003").disposition());
        assertTrue(IronSentinelAffixes.classify("WA-003").adapted(),"Owner-only affixes are excluded, not copied to the summon");
        assertThrows(IllegalArgumentException.class,()->IronSentinelStatProjection.attackInterval(Double.NaN,.2));
    }
}
