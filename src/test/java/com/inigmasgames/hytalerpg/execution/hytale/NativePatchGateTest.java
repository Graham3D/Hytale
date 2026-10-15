package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytale.patch.NativeMutationHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativePatchGateTest {
    @Test void compiledHookTypesWithoutNativeCodecOwnershipNeverEnableProtectedPromotion() {
        var combat=new HytaleDifficultyCombat(record->{});
        assertTrue(PackboundNativeMutationBridge.tryInstall(combat).isEmpty());
        assertFalse(NativeMutationHook.installed());
    }
}
