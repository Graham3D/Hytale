package com.inigmasgames.hytalerpg.execution.lightning;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class LightningSpireEntityLifecycleTest {
    @Test void commandBufferMaterializationGapIsNotDestructionDuringEmergence(){
        var lifecycle=new LightningSpireEntityLifecycle();
        assertEquals(LightningSpireEntityLifecycle.State.MATERIALIZING,
                lifecycle.observe(LightningSpireRuntime.Phase.EMERGING,true,false,Double.NaN,Double.NaN));
        assertEquals(LightningSpireEntityLifecycle.State.MATERIALIZING,
                lifecycle.observe(LightningSpireRuntime.Phase.EMERGING,false,false,Double.NaN,Double.NaN));
        assertFalse(lifecycle.healthyEntityObserved());
        assertEquals(LightningSpireEntityLifecycle.State.ALIVE,
                lifecycle.observe(LightningSpireRuntime.Phase.EMERGING,true,true,50,0));
        assertTrue(lifecycle.healthyEntityObserved());
    }

    @Test void observedCarrierRemovalAndZeroHealthAreDestruction(){
        var lifecycle=new LightningSpireEntityLifecycle();
        assertEquals(LightningSpireEntityLifecycle.State.ALIVE,
                lifecycle.observe(LightningSpireRuntime.Phase.EMERGING,true,true,50,0));
        assertEquals(LightningSpireEntityLifecycle.State.DESTROYED,
                lifecycle.observe(LightningSpireRuntime.Phase.EMERGING,false,false,Double.NaN,Double.NaN));
        assertEquals(LightningSpireEntityLifecycle.State.DESTROYED,
                lifecycle.observe(LightningSpireRuntime.Phase.READY,true,true,0,0));
    }

    @Test void carrierMustMaterializeBeforeReady(){
        var lifecycle=new LightningSpireEntityLifecycle();
        assertEquals(LightningSpireEntityLifecycle.State.DESTROYED,
                lifecycle.observe(LightningSpireRuntime.Phase.READY,true,false,Double.NaN,Double.NaN));
    }
}
