package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.server.npc.movement.Steering;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.execution.hytale.AreaNpcControlSystem;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06NativeControlPolicyTest {
    @Test void rootRemovesOnlyVoluntaryTranslationAndPreservesTurningAndAttacks() {
        var body = new Steering().setTranslation(1, 0, 1).setYaw(1);
        var head = new Steering().setYaw(2); var attacks = new AtomicInteger();
        AreaNpcControlSystem.constrain(Set.of(RpgStatusType.ROOT), body, head, attacks::incrementAndGet);
        assertFalse(body.hasTranslation()); assertEquals(1, body.getYaw()); assertEquals(2, head.getYaw());
        assertEquals(0, attacks.get());
    }
    @Test void frozenClearsSteeringAndInterruptsWhileStaggerDoesNotRoot() {
        var body = new Steering().setTranslation(1, 0, 1).setYaw(1);
        var head = new Steering().setYaw(2); var attacks = new AtomicInteger();
        AreaNpcControlSystem.constrain(Set.of(RpgStatusType.STAGGER), body, head, attacks::incrementAndGet);
        assertTrue(body.hasTranslation()); assertEquals(1, attacks.get());
        AreaNpcControlSystem.constrain(Set.of(RpgStatusType.FROZEN), body, head, attacks::incrementAndGet);
        assertFalse(body.hasTranslation()); assertFalse(body.hasYaw()); assertFalse(head.hasYaw());
        assertEquals(2, attacks.get());
        body.setTranslation(1, 0, 1); AreaNpcControlSystem.constrain(Set.of(), body, head, attacks::incrementAndGet);
        assertTrue(body.hasTranslation()); assertEquals(2, attacks.get());
    }
}
