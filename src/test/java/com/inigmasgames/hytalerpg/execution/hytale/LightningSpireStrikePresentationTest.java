package com.inigmasgames.hytalerpg.execution.hytale;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.execution.lightning.LightningSpireRuntime;
import org.junit.jupiter.api.Test;

final class LightningSpireStrikePresentationTest {
    @Test void strikeShakeReusesTheEmergenceEnvelopeWithoutASecondRiseCoordinate(){
        assertEquals(0,NativeLightningSpireVisuals.shake(0),1e-12);
        assertNotEquals(0,NativeLightningSpireVisuals.shake(.1),1e-12);
        assertNotEquals(0,NativeLightningSpireVisuals.shake(1.9),1e-12);
        assertEquals(0,NativeLightningSpireVisuals.shake(LightningSpireRuntime.EMERGENCE_SECONDS),1e-12);
        assertEquals(0,NativeLightningSpireVisuals.shake(-.1),1e-12);
        assertEquals(0,NativeLightningSpireVisuals.shake(Double.NaN),1e-12);
    }

    @Test void spireFallbackAcceptsOnlyPhysicalCauseOrItsDirectDerivative(){
        assertTrue(NativeBasicAttackObserver.directPhysical(new DamageCause("Physical")));
        assertTrue(NativeBasicAttackObserver.directPhysical(new DamageCause("Melee_Physical","Physical",true,true,false)));
        assertFalse(NativeBasicAttackObserver.directPhysical(new DamageCause("Projectile")));
        assertFalse(NativeBasicAttackObserver.directPhysical(new DamageCause("Magic")));
        assertFalse(NativeBasicAttackObserver.directPhysical(null));
    }
}
