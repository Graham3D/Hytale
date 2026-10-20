package com.inigmasgames.hytalerpg.execution.summon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SummonDistanceRecoveryTest {
    @Test void requiresThreeContinuousSecondsBeyondTheOwnerRange(){
        var first=SummonDistanceRecovery.observe(25,0,100);
        assertFalse(first.recover());
        assertFalse(SummonDistanceRecovery.observe(25,first.farSince(),102.9).recover());
        assertTrue(SummonDistanceRecovery.observe(25,first.farSince(),103).recover());
        var reset=SummonDistanceRecovery.observe(20,first.farSince(),103);
        assertEquals(0,reset.farSince());assertFalse(reset.recover());
        assertFalse(SummonDistanceRecovery.observe(25,reset.farSince(),103.1).recover());
    }
}
