package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.input.NativeAbilityBridgeAudit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class R022NativeAbilitySynchronizationTest {
    private static final Path ROOT = Path.of(
            "src/main/resources/Server/Item/RootInteractions/RPG/Root_RPG_Ability_Bridge.json");

    @Test void bridgeIsBranchlessAndContainsNoGameplayMutation() throws Exception {
        String root = Files.readString(ROOT);
        assertTrue(root.contains("\"Type\": \"FirstClick\""));
        assertTrue(root.contains("\"RequireNewClick\": true"));
        assertFalse(root.contains("\"Click\""));
        assertFalse(root.contains("\"Held\""));
        for (String forbidden : new String[]{"Damage", "ChangeStat", "ModifyInventory", "Launch",
                "Projectile", "Effect", "Cooldown", "Cost", "Resource"}) {
            assertFalse(root.contains(forbidden), forbidden);
        }
    }

    @Test void runtimeAuditTargetsThePackagedRootId() {
        assertEquals("Root_RPG_Ability_Bridge", NativeAbilityBridgeAudit.ROOT_ID);
    }
}
