package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealingChannelObserverTest {
    @Test void onlyArmedOwnerChannelIsObserved(){
        var owner=UUID.randomUUID();
        for(var mode:HealingProbePolicy.Mode.values())assertEquals(mode==HealingProbePolicy.Mode.CHANNEL,
            HealingPresentationProbe.acceptsChannel(mode,owner,owner,false,false));
        assertFalse(HealingPresentationProbe.acceptsChannel(HealingProbePolicy.Mode.CHANNEL,owner,UUID.randomUUID(),false,false));
    }
    @Test void stoppedAndClosedObserversCannotCaptureLaterFrames(){
        var owner=UUID.randomUUID();
        assertFalse(HealingPresentationProbe.acceptsChannel(HealingProbePolicy.Mode.CHANNEL,owner,owner,true,false));
        assertFalse(HealingPresentationProbe.acceptsChannel(HealingProbePolicy.Mode.CHANNEL,owner,owner,false,true));
    }
    @Test void observerHasBoundedWindowAndNoTargetRequirement(){
        assertEquals(HealingProbePolicy.Mode.CHANNEL,HealingProbePolicy.mode("channel"));
        assertEquals(30,HealingProbePolicy.duration(HealingProbePolicy.Mode.CHANNEL));
        for(var mode:HealingProbePolicy.Mode.values())if(mode!=HealingProbePolicy.Mode.CHANNEL)assertEquals(10,HealingProbePolicy.duration(mode));
        assertFalse(HealingProbePolicy.needsRecipient(HealingProbePolicy.Mode.CHANNEL));
        assertDoesNotThrow(()->HealingProbePolicy.validateLiveTarget(HealingProbePolicy.Mode.CHANNEL,"none"));
        assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.validateLiveTarget(HealingProbePolicy.Mode.CHANNEL,"self"));
    }
}
