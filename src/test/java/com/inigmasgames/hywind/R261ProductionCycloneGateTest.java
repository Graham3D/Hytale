package com.inigmasgames.hywind;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the packaged entrypoint, since the legacy Phase00Plugin source is excluded. */
class R261ProductionCycloneGateTest {
    @Test void productionEntrypointContainsTheDurableGateAndAllLiveBindings() throws Exception {
        String resource = "com/inigmasgames/hywind/HyArpgPlugin.class";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "production Hywind entrypoint must be compiled");
            String compiled = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            for (String required : new String[] {
                    "hywind.cyclone.durable", "RPG_CYCLONE_DURABLE_GATE enabled=%s",
                    "CycloneGateConfiguration", "configureDurableCyclone", "configureTrustedPlayerAllies",
                    "RpgTrustedAllyCommand", "CycloneDeath", "NpcSharedBindings",
                    "NpcSharedRemoval", "NpcSharedDeath" }) {
                assertTrue(compiled.contains(required), "production entrypoint omits " + required);
            }
        }
    }

    @Test void normalLaunchEnablesDurableCycloneAndExplicitFalseRollsBack() {
        assertTrue(CycloneGateConfiguration.enabledValue(null));
        assertTrue(CycloneGateConfiguration.enabledValue("true"));
        assertFalse(CycloneGateConfiguration.enabledValue("false"));
        assertFalse(CycloneGateConfiguration.enabledValue(" FALSE "));
    }
}
