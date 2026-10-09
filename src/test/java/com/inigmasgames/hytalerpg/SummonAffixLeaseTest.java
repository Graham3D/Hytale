package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.execution.summon.SummonNativeMovement;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the same reservation, activation, attack and expiry path as native created summons. */
final class SummonAffixLeaseTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void committedOwnerSourceIsFrozenAcrossLeaseLifecycleAndCannotLeakToControl(){
        var harness=new Stage10SummonTest.Harness();
        assertTrue(harness.cast().committed());
        var source=item("WA-119",25);
        var effects=new GearEffectSnapshot(List.of(source));
        var registry=new SummonRegistry();
        var modified=registry.reserve(harness.context,0,null,effects).getFirst();
        var control=new SummonRegistry().reserve(harness.context,0).getFirst();
        assertEquals(source.identity(),modified.ownerEffects().sources(GearEffectSnapshot.Operator.MINION_DURATION).getFirst().itemId());
        assertTrue(modified.ownerEffects().forItem(source.identity()).value("WA-119")>0);
        assertTrue(control.ownerEffects().empty());
        assertTrue(registry.activate(modified,UUID.randomUUID(),0));
        assertEquals(1,registry.claimAttack(modified.token(),modified.interval()));
        assertEquals(25,modified.expires(),1e-9);
        assertEquals(20,control.expires(),1e-9);
        assertEquals(25,modified.ownerEffects().value("WA-119"),1e-9);
        assertTrue(registry.end(modified.token(),SummonRegistry.EndReason.NATURAL_EXPIRY).isPresent());
        assertEquals(0,registry.claimAttack(modified.token(),30));
    }

    @Test void admittedOwnerDamageHealthAndRateReachTheActualLease(){
        var harness=new Stage10SummonTest.Harness();
        assertTrue(harness.cast().committed());
        var baseline=new SummonRegistry().reserve(harness.context,0).getFirst();
        for(Object[] row:List.<Object[]>of(new Object[]{"WA-113",25d},new Object[]{"WA-114",30d},new Object[]{"WA-117",10d})){
            String id=(String)row[0];double roll=(Double)row[1];
            var lease=new SummonRegistry().reserve(harness.context,0,null,new GearEffectSnapshot(List.of(item(id,roll)))).getFirst();
            double fraction=roll/100;
            switch(id){
                case "WA-113" -> {assertEquals(baseline.coefficient()*(1+fraction),lease.coefficient(),1e-9);assertEquals(baseline.maximumHealth(),lease.maximumHealth(),1e-9);}
                case "WA-114" -> {assertEquals(baseline.maximumHealth()*(1+fraction),lease.maximumHealth(),1e-9);assertEquals(baseline.coefficient(),lease.coefficient(),1e-9);}
                case "WA-117" -> {assertEquals(baseline.interval()/(1+fraction),lease.interval(),1e-9);assertEquals(baseline.coefficient(),lease.coefficient(),1e-9);}
                default -> fail(id);
            }
        }
    }

    @Test void nativeDamageGuardUsesOwnerResistanceOnlyForTheCreatedSummonAndElementalCause(){
        var harness=new Stage10SummonTest.Harness();assertTrue(harness.cast().committed());
        var source=item("WA-116",10);
        var modified=new SummonRegistry().reserve(harness.context,0,null,new GearEffectSnapshot(List.of(source))).getFirst();
        var control=new SummonRegistry().reserve(harness.context,0).getFirst();
        double expected=.10;
        assertEquals(expected,HytaleSummonSystem.incomingResistance(modified,"Ice"),1e-9);
        assertEquals(expected,HytaleSummonSystem.incomingResistance(modified,"Fire"),1e-9);
        assertEquals(expected,HytaleSummonSystem.incomingResistance(modified,"RPG_Nature"),1e-9);
        assertEquals(0,HytaleSummonSystem.incomingResistance(modified,"Physical"),1e-9);
        assertEquals(0,HytaleSummonSystem.incomingResistance(control,"Ice"),1e-9);
        assertEquals(0,HytaleSummonSystem.incomingResistance(modified,"RPG_Necrotic"),1e-9);
    }

    @Test void nativePursuitEffectSelectsTheCommittedSourceRoll(){
        var harness=new Stage10SummonTest.Harness();assertTrue(harness.cast().committed());
        var source=item("WA-118",15);
        var lease=new SummonRegistry().reserve(harness.context,0,null,new GearEffectSnapshot(List.of(source))).getFirst();
        assertEquals("RPG_Summon_Pursuit_150",SummonNativeMovement.assetId(lease.ownerEffects().value("WA-118")));
        assertEquals(source.identity(),lease.ownerEffects().sources(GearEffectSnapshot.Operator.MINION_MOVEMENT).getFirst().itemId());
        assertEquals("",SummonNativeMovement.assetId(new SummonRegistry().reserve(harness.context,0).getFirst().ownerEffects().value("WA-118")));
    }

    private GearInstance item(String id,double value){
        var base=catalog.base("gm.staff_oracle.h");var affix=catalog.affix(id);
        assertTrue(base.sourceWindow().getLast()>=affix.firstItemLevel());
        assertEquals("C",affix.eligibility());
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,value,
                new GearRequirements.Gate(80,Map.of(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS,85)),
                "Summon lease source",affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
}
