package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelSourceEligibilityTest {
    private final GearCatalog catalog=GearCatalog.load();
    private GearInstance item(String id){
        var base=catalog.base(id);
        return GearInstance.authoredQa(base,UUID.randomUUID(),
                base.category()==GearCatalog.Category.TOOL?1:base.sourceWindow().getLast(),
                1000,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
    }
    @Test void mappedCobaltWeaponAndArmorAreEligibleRegardlessOfEquipGate(){
        var daggers=item("gm.daggers_cobalt.n");
        assertTrue(daggers.requirements().level()>1 || !daggers.requirements().attributes().isEmpty());
        assertEquals("Weapon_Daggers_Cobalt",IronSentinelSourceEligibility.require(daggers).nativeItemId());
        var stats=IronSentinelStatProjection.project(1,daggers,2);
        assertEquals(stats.nativePhysicalMin()+GearAffixRuntime.physical(daggers).minimum(),stats.finalPhysicalMin(),1e-9);
        assertEquals(stats.nativePhysicalMax()+GearAffixRuntime.physical(daggers).maximum(),stats.finalPhysicalMax(),1e-9);
        var armor=item("gm.plate_iron.head.n");
        assertTrue(IronSentinelSourceEligibility.require(armor).mapped());
        var defense=IronSentinelStatProjection.project(1,armor,2);
        assertEquals(1-(1-defense.nativeProtection())*(1-defense.sourceProtection()),defense.finalProtection(),1e-9);
    }
    @Test void toolsAndUnmappedGearRemainIneligible(){
        assertEquals("TARGET_NOT_WEAPON_OR_ARMOR",assertThrows(IllegalArgumentException.class,
                ()->IronSentinelSourceEligibility.require(item("gm.tool.pickaxe.iron.n"))).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class,
                ()->IronSentinelSourceEligibility.require(item("gm.longbow_wood.n"))).getMessage().startsWith("TARGET_UNSUPPORTED_GEAR"));
    }
}
