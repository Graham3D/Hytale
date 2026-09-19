package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production execution service/compiler/support owner; not connected native weapon proof. */
class MantleRuntimeTest {
    @Test void eventPaidAuraCommitsWithoutFeeReservationIdleDrainOrDamageTimer(){
        var h=new Stage09AuraRuntimeTest.Harness("mantle_of_flame");
        assertEquals(SupportProfile.ResourceMode.TRIGGERED_VARIABLE_MANA_SPEND,h.profile.support().resourceMode());
        assertTrue(h.cast().committed());assertEquals(100,h.mana);assertEquals(0,h.reserved);
        h.advance(60);assertTrue(h.runtime.active(h.actor,"mantle_of_flame"));assertEquals(100,h.mana);
        assertTrue(h.slices.isEmpty());assertTrue(h.damagePulses.isEmpty());assertTrue(h.chillPulses.isEmpty());
        assertEquals(9,SupportRuntime.radius(h.context));assertSame(h.context,h.runtime.activeContext(h.actor,"mantle_of_flame"));
    }
    @Test void threeSecondToggleLockAndTerminalCleanupUseExistingAuraOwner(){
        var h=new Stage09AuraRuntimeTest.Harness("mantle_of_flame");h.cast();assertEquals("AURA_TOGGLE_LOCK",h.cast().code());
        h.advance(3);h.mana=0;assertEquals("AURA_OFF",h.cast().code());assertEquals(0,h.budget.size());assertEquals(0,h.mana);
        assertNull(h.runtime.activeContext(h.actor,"mantle_of_flame"));
        for(String why:List.of("OWNER_DEAD","WORLD_CHANGED","LOGOUT")){
            var next=new Stage09AuraRuntimeTest.Harness("mantle_of_flame");next.cast();next.runtime.cancel(next.actor,why,next);
            assertEquals(0,next.budget.size());assertEquals(0,next.runtime.auraCount());assertEquals(100,next.mana);
        }
    }
    @Test void onlyAuthoredProportionalComponentsAreLinkable(){
        var b=Stage01BTestSupport.bundle();var skill=b.catalog().skill(new SkillId("mantle_of_flame")).orElseThrow();
        var allowed=Set.of("potency","efficiency","overcharge","concentration","expanded_radius");
        for(var passive:b.catalog().passives())assertEquals(allowed.contains(passive.id().value()),
                new com.inigmasgames.hytalerpg.links.CompatibilityService().assess(skill,passive).accepted(),passive.name());
    }
    @Test void compiledMagnitudeAndResourceFactorsRemainSeparate(){
        var h=new Stage09AuraRuntimeTest.Harness("mantle_of_flame");
        h.link("potency",PassiveSlot.PASSIVE01);h.link("overcharge",PassiveSlot.PASSIVE02);
        h.link("efficiency",PassiveSlot.PASSIVE03);h.link("expanded_radius",PassiveSlot.PASSIVE04);
        assertTrue(h.cast().committed());assertEquals(100,h.mana);assertEquals(11.25,SupportRuntime.radius(h.context));
        assertEquals(1.4*.9,h.context.snapshot().modifiers().factor(),1e-10);
        assertEquals(1.2*.85,h.context.compiledPlan().kernelModifiers().resourceCostMultiplier(),1e-10);
        var c=new Stage09AuraRuntimeTest.Harness("mantle_of_flame");c.link("concentration",PassiveSlot.PASSIVE01);c.cast();
        assertEquals(6.3,SupportRuntime.radius(c.context),1e-10);assertEquals(1.3,c.context.snapshot().modifiers().factor(),1e-10);
    }
    @Test void factorizedCostMatchesRetainedSourceFormula(){
        for(double s:new double[]{.01,1,100,100000})for(int count:new int[]{0,1,4,8,20,30,64})
            for(double k:new double[]{1,1.15,1.38,1.4*.9})for(double r:new double[]{1,.85,1.2,1.2*.85})
                assertEquals(WeaponFireDecision.eventCost(173,s,s*.25*k*count,r),
                        WeaponFireDecision.proportionalEventCost(173,k*count,r),1e-9);
    }
    @Test void ledgerOwnsEvaluationNotItsRepeatedConsumers(){
        var execution=WeaponFireDecisionTest.execution();var id=execution.identity();var ledger=new WeaponExecutionLedger(id.actorId(),id.rootId());
        var count=new java.util.concurrent.atomic.AtomicInteger();
        var first=ledger.produce(id,()->{count.incrementAndGet();return execution;});
        for(int i=0;i<64;i++)assertSame(first,ledger.produce(id,()->{count.incrementAndGet();throw new AssertionError("reroll");}));
        assertEquals(1,count.get());ledger.close();assertThrows(IllegalStateException.class,()->ledger.produce(id,()->execution));
    }
}
