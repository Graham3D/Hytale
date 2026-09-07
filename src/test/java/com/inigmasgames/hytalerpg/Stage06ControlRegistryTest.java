package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.status.ControlProfileRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06ControlRegistryTest {
    @Test void exactAuditedRolesSelectControlPolicyWithoutInferringNamesOrLevels() {
        var registry = ControlProfileRegistry.loadCanonical();
        assertEquals(.5, registry.resolve("Skeleton_Elite", false, false).durationMultiplier());
        assertEquals(.5, registry.resolve("Skeleton_Elite_Phase_2", false, false).displacementMultiplier());
        assertTrue(registry.resolve("Test_Boss_Basic_Intro", false, false).boss());
        assertFalse(registry.resolve("Unaudited_Elite_Boss", false, false).boss());
        assertEquals(1, registry.resolve("Unaudited_Elite_Boss", false, false).durationMultiplier());
        assertEquals(1, registry.resolve(null, false, false).displacementMultiplier());
    }
    @Test void NativeProtectionAndObservedBossOverrideProjectRank() {
        var registry = ControlProfileRegistry.loadCanonical();
        assertTrue(registry.resolve("Skeleton_Elite", true, false).protectedEntity());
        assertEquals(0, registry.resolve("Skeleton_Elite", true, false).displacementMultiplier());
        assertTrue(registry.resolve("Skeleton_Elite", false, true).boss());
        assertEquals(0, registry.resolve("Skeleton_Elite", false, true).displacementMultiplier());
    }
}
