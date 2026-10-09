package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeStatusPhysicalMissIntegrationTest {
    @Test void oneAuthoredPhysicalContactGetsAStableBoundedDraw(){
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        double draw=NativeStatusPhysicalMiss.stableRoll(actor,victim,"root/contact");
        assertTrue(draw>=0&&draw<1);
        assertEquals(draw,NativeStatusPhysicalMiss.stableRoll(actor,victim,"root/contact"));
        assertNotEquals(draw,NativeStatusPhysicalMiss.stableRoll(actor,victim,"root/next"));
        assertNotEquals(draw,NativeStatusPhysicalMiss.stableRoll(actor,UUID.randomUUID(),"root/contact"));
        assertThrows(IllegalArgumentException.class,()->NativeStatusPhysicalMiss.stableRoll(actor,victim,""));
    }
}
