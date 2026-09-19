package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for the connected R036 lethal-Burn Store processing crash. */
class Stage13PeriodicDamageEcsSafetyR038Test {
    @Test void installedNativeDamageApiExposesSystemSafeCommandBufferDispatch() throws Exception {
        assertNotNull(DamageSystems.class.getDeclaredMethod(
                "executeDamage", Ref.class, CommandBuffer.class, Damage.class));
    }

    @Test void periodicTicksPropagateTheirOwningSystemBufferIntoNativeDamage() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));

        assertTrue(source.contains("periodicStatuses.tick(actor, System.nanoTime() / 1e9, periodicPort(buffer))"));
        assertTrue(source.contains("new Port(store, target.actor, owner, player, stats, target.victim, damageBuffer)"));
        assertTrue(source.contains("private ComponentAccessor<EntityStore> damageAccessor() { return buffer == null ? store : buffer; }"));
        assertTrue(source.contains("applyObserved(target.handle(), damageAccessor(), actor, cause"));
        assertTrue(source.contains("applyResolved(target.handle(),damageAccessor(),actor,cause"));
    }
}
