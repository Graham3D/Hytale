package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealingProbeLiveTest {
    @Test void liveRecipientNeverUsesNativeSpawn(){
        for(var mode:new HealingProbePolicy.Mode[]{HealingProbePolicy.Mode.RECIPIENT_ONCE,HealingProbePolicy.Mode.RECIPIENT_OVERWRITE}){
            assertDoesNotThrow(()->HealingProbePolicy.validateLiveTarget(mode,"self"));
            assertDoesNotThrow(()->HealingProbePolicy.validateLiveTarget(mode,java.util.UUID.randomUUID().toString()));
            assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.validateLiveTarget(mode,"native"));
            assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.validateLiveTarget(mode,"none"));
        }
    }
    @Test void transientPacketsOnlyCarryFiniteCosmeticsAndExactCleanup(){
        for(float duration:new float[]{12,.3f,0}){
            boolean remove=duration==0;
            var packet=HealingPresentationProbe.transientEffectPacket(123,456,duration,remove);
            assertNull(packet.removed);assertEquals(1,packet.updates.length);
            var entity=packet.updates[0];assertEquals(123,entity.networkId);assertNull(entity.removed);
            assertEquals(1,entity.updates.length);
            var effects=assertInstanceOf(EntityEffectsUpdate.class,entity.updates[0]);
            assertEquals(1,effects.entityEffectUpdates.length);var effect=effects.entityEffectUpdates[0];
            assertEquals(456,effect.id);assertFalse(effect.infinite);assertFalse(effect.debuff);assertNull(effect.statusEffectIcon);
            assertEquals(remove?EffectOp.Remove:EffectOp.Add,effect.type);assertEquals(duration,effect.remainingTime);
            assertTrue(packet.computeSize()>0);
        }
    }
    @Test void packetBoundsRejectUnboundedOrInvalidState(){
        for(float bad:new float[]{-1,13,Float.NaN,Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->HealingPresentationProbe.transientEffectPacket(1,1,bad,false));
        assertThrows(IllegalArgumentException.class,()->HealingPresentationProbe.transientEffectPacket(-1,1,1,false));
        assertThrows(IllegalArgumentException.class,()->HealingPresentationProbe.transientEffectPacket(1,-1,1,false));
    }
}
