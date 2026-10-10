package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeOriginalHitHealthGroupTest {
    @Test void oneEnchantedOriginalHitUsesBothAppliedComponentsOnceAndActualNonOverkillLoss(){
        var group=new NativeOriginalHitHealthGroup();
        assertTrue(group.add(0,100,70,false));
        assertTrue(group.add(1,70,0,false));
        assertFalse(group.add(1,70,0,false));
        var completed=group.snapshot();
        assertEquals(100,completed.actualHealthLoss());
        assertEquals(0,completed.healthAfter());
        assertEquals(2,completed.components());
    }
    @Test void cancelledOrRepeatedComponentCannotIncreaseTheOriginalHitLoss(){
        var group=new NativeOriginalHitHealthGroup();
        assertTrue(group.add(0,100,80,false));
        assertTrue(group.add(1,80,80,true));
        assertFalse(group.add(0,100,80,false));
        assertEquals(20,group.snapshot().actualHealthLoss());
    }
}
