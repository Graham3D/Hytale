package com.inigmasgames.hytalerpg.execution.lightning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LightningSpireRuntimeTest {
    @Test void emergenceIsExcludedAndEveryFourDistinctReadyHitsCreatesAnotherWave(){
        var r=new LightningSpireRuntime();var owner=UUID.randomUUID();var spec=LightningSpireRuntime.Spec.base(20,1,1,1);
        var deployed=r.deploy("spire",owner,spec,10);
        assertEquals(97.5,deployed.maximumHealth(),1e-9);assertEquals(2.2275,deployed.coefficient(),1e-9);
        assertEquals("EMERGING",r.friendlyMelee("spire","early",11.9).code());
        for(int wave=1;wave<=3;wave++){
            for(int hit=1;hit<=4;hit++){
                var result=r.friendlyMelee("spire","w"+wave+"h"+hit,12+wave*.01+hit*.0001);
                assertEquals(hit==4?"DISCHARGE":"CHARGED",result.code());
                assertEquals(hit==4?0:hit*25,result.chargePercent());
            }
            var waves=r.drainWaves(owner);assertEquals(1,waves.size());assertEquals(wave,waves.getFirst().sequence());
        }
        assertTrue(r.active(owner));assertEquals(3,r.inspectOwner(owner,12.5).orElseThrow().completedWaves());
    }

    @Test void duplicateNativeHitIdentityCannotDoubleChargeAndExpiryDropsPartialCharge(){
        var r=new LightningSpireRuntime();var owner=UUID.randomUUID();r.deploy("spire",owner,LightningSpireRuntime.Spec.base(1,1,1,1),0);
        assertEquals("CHARGED",r.friendlyMelee("spire","root/op/target",2.1).code());
        assertEquals("DUPLICATE",r.friendlyMelee("spire","root/op/target",2.11).code());
        assertTrue(r.expire(owner,11.99).isEmpty());
        var ended=r.expire(owner,12.01).orElseThrow();assertEquals(LightningSpireRuntime.EndReason.READY_EXPIRED,ended.reason());
        assertFalse(r.active(owner));assertTrue(r.drainWaves(owner).isEmpty());
    }

    @Test void sameCasterInstancesCoexistAndKeepIndependentState(){
        var r=new LightningSpireRuntime();var owner=UUID.randomUUID();
        var spec=LightningSpireRuntime.Spec.base(1,1.4,.7,1.3);
        assertEquals(14,spec.readySeconds(),1e-9);assertEquals(4.2,spec.radius(),1e-9);assertEquals(2.34,spec.coefficient(),1e-9);
        assertEquals(50,spec.maximumHealth(),1e-9);r.deploy("a",owner,spec,0);
        assertDoesNotThrow(()->r.deploy("b",owner,spec,0));assertEquals(2,r.inspectOwnerAll(owner,2.1).size());
        assertEquals("CHARGED",r.friendlyMelee("a","a-hit",2.1).code());
        assertEquals(25,r.inspect("a",2.1).orElseThrow().chargePercent());assertEquals(0,r.inspect("b",2.1).orElseThrow().chargePercent());
        assertEquals(LightningSpireRuntime.EndReason.DESTROYED,r.destroy("a").orElseThrow().reason());
        assertTrue(r.inspect("b",2.1).isPresent());assertEquals(1,r.inspectOwnerAll(owner,2.1).size());
    }
}
