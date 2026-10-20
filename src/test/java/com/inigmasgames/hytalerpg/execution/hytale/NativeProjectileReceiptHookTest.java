package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytale.patch.NativeProjectileReceiptHook;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeProjectileReceiptHookTest {
    @Test void noProviderReturnsBeforeReadingOrChangingTheNativeLaunch(){
        assertFalse(NativeProjectileReceiptHook.installed());
        NativeProjectileReceiptHook.beforeQueue(null,null);
        assertFalse(NativeProjectileReceiptHook.installed());
    }
    @Test void providerRegistrationHasOneOwnerAndRestoresTheNoOpPath()throws Exception{
        var identity=new NativeProjectileReceiptHook.Context(UUID.randomUUID(),UUID.randomUUID(),null,
                UUID.randomUUID(),1,List.of(2,3),"native-root","shoot-leaf",4,5);
        assertEquals("shoot-leaf",identity.operationRoot());
        assertThrows(UnsupportedOperationException.class,()->identity.forkPath().add(7));
        var provider=(NativeProjectileReceiptHook.Provider)(context,holder)->{};
        try(var registration=NativeProjectileReceiptHook.install(provider)){
            assertTrue(NativeProjectileReceiptHook.installed());
            assertThrows(IllegalStateException.class,()->NativeProjectileReceiptHook.install(provider));
        }
        assertFalse(NativeProjectileReceiptHook.installed());
        NativeProjectileReceiptHook.beforeQueue(null,null);
    }
}
