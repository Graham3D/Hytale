package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelOwnerOnlyTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final int[] OWNER_IDS={3,4,9,12,85,86,87,88,89,90,92,93,95,97,98,99,
            100,101,102,103,104,105,106,107,108,109,110,112,144,151,152,153,154,155,156,
            113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133};

    static Stream<String> ownerOnlyIds(){
        assertEquals(56,OWNER_IDS.length);
        return Arrays.stream(OWNER_IDS).mapToObj(n->String.format(Locale.ROOT,"WA-%03d",n));
    }

    @ParameterizedTest(name="ownerOnlyBoundSourceDoesNotChangeSentinelRuntime[{0}]")
    @MethodSource("ownerOnlyIds")
    void ownerOnlyBoundSourceDoesNotChangeSentinelRuntime(String id){
        var source=item(id);var plain=item(null);
        IronSentinelAffixes.requireAdapted(source);
        var actual=lease(source);var control=lease(plain);
        assertEquals(source.identity(),actual.boundEffects().items().getFirst().identity(),id);
        assertEquals(25,actual.boundEffects().value(id),id);
        assertEquals(0,control.boundEffects().value(id),id);
        assertEquals(IronSentinelAffixes.Disposition.OWNER_ONLY,IronSentinelAffixes.classify(id).disposition(),id);
        // The bound source is present, yet owner-only stats never recurse through the minion projection.
        var a=actual.sentinelStats();var b=control.sentinelStats();
        assertEquals(b.finalPhysicalMin(),a.finalPhysicalMin(),1e-9,id);
        assertEquals(b.finalPhysicalMax(),a.finalPhysicalMax(),1e-9,id);
        assertEquals(b.finalMaxHealth(),a.finalMaxHealth(),1e-9,id);
        assertEquals(b.finalProtection(),a.finalProtection(),1e-9,id);
        assertEquals(b.attackInterval(),a.attackInterval(),1e-9,id);
        assertEquals(b.attackRateMultiplier(),a.attackRateMultiplier(),1e-9,id);
        // Accepted native attack and recipient owners consume only this NPC's bound snapshot.
        var hit=GearCombatEffects.attack(actual.boundEffects(),source.identity(),"owner-only/"+id,
                10,1,true,false,0,null,0,1.5,false,Vec3.ZERO);
        var baseline=GearCombatEffects.attack(control.boundEffects(),plain.identity(),"control/"+id,
                10,1,true,false,0,null,0,1.5,false,Vec3.ZERO);
        assertEquals(baseline.amounts(),hit.amounts(),id);
        assertEquals(baseline.penetration(),hit.penetration(),id);
        assertEquals(baseline.increased(),hit.increased(),id);
        assertEquals(baseline.criticalMultiplier(),hit.criticalMultiplier(),1e-9,id);
        assertEquals(HytaleSummonSystem.incomingResistance(control,"Fire"),
                HytaleSummonSystem.incomingResistance(actual,"Fire"),1e-9,id);
        assertEquals(HytaleSummonSystem.incomingResistance(control,"Physical"),
                HytaleSummonSystem.incomingResistance(actual,"Physical"),1e-9,id);
        assertEquals(HytaleSummonSystem.sentinelMeleeReach(control),
                HytaleSummonSystem.sentinelMeleeReach(actual),1e-9,id);
        assertEquals(HytaleSummonSystem.sentinelReceivedHealing(control,100),
                HytaleSummonSystem.sentinelReceivedHealing(actual,100),1e-9,id);
        assertEquals(100,HytaleSummonSystem.sentinelReceivedHealing(actual,100),1e-9,id);
    }

    private static SummonRegistry.Lease lease(GearInstance item){
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",item,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        return new SummonRegistry().restoreIronSentinel(binding,10);
    }
    private static GearInstance item(String id){
        var base=CATALOG.base("gm.sword_iron.n");
        List<GearInstance.AffixRoll> rolls=List.of();
        if(id!=null){var affix=CATALOG.affix(id);
            rolls=List.of(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,25,
                    new GearRequirements.Gate(1,Map.of()),"Owner-only source",affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                id==null?GearRarity.COMMON:GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
}
