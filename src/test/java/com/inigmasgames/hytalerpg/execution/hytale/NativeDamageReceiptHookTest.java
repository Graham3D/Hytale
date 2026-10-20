package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDamageReceiptHookTest {
    @Test void absentConsumerDoesNotMutateOrdinaryDamage(){
        // The index constructor preserves the native event's metadata without starting the asset server.
        var event=new Damage(Damage.NULL_SOURCE,0,7);
        assertNull(NativeDamageReceiptHook.receipt(event));
        assertFalse(NativeDamageReceiptHook.installed());
        NativeDamageReceiptHook.stamp(null,null,event,0);
        assertNull(NativeDamageReceiptHook.receipt(event));
        assertEquals(7,event.getAmount());
        assertFalse(event.isCancelled());
    }

    @Test void receiptProviderHasSingleInstallAndDeterministicTeardown()throws Exception{
        var identity=new NativeDamageReceiptHook.Context(UUID.randomUUID(),UUID.randomUUID(),null,null,
                UUID.randomUUID(),1,List.of(2,3),"native-root","damage-leaf",4,5,0,"Physical");
        assertEquals("damage-leaf",identity.operationRoot());
        assertThrows(UnsupportedOperationException.class,()->identity.forkPath().add(7));
        var provider=(NativeDamageReceiptHook.Provider)context->"existing-root/"+context.componentIndex();
        try(var close=NativeDamageReceiptHook.install(provider)){
            assertTrue(NativeDamageReceiptHook.installed());
            assertThrows(IllegalStateException.class,()->NativeDamageReceiptHook.install(context->null));
        }
        assertFalse(NativeDamageReceiptHook.installed());
    }
}
