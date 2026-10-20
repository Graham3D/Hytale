package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter;
import com.inigmasgames.hytalerpg.input.NativeSkillActivationInteraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class R024NativeInteractionAssetTest {
    @Test void registeredServerOperationSerializesAsSupportedSimpleWithServerWait() {
        var interaction = new NativeSkillActivationInteraction(new HytaleAbilitySkillInputAdapter());
        assertNotNull(NativeSkillActivationInteraction.codec(new HytaleAbilitySkillInputAdapter()));
        assertTrue(interaction.needsRemoteSync());
        assertEquals(WaitForDataFrom.Server, interaction.getWaitForDataFrom());
        var packet = interaction.toPacket();
        assertInstanceOf(com.hypixel.hytale.protocol.SimpleInteraction.class, packet);
        assertEquals(WaitForDataFrom.Server, packet.waitForDataFrom);
    }
    @Test void simulationDoesNotCallGameplayAndMissingRuntimeContextFailsClosed() throws Exception {
        var inputs = new HytaleAbilitySkillInputAdapter();
        var interaction = new NativeSkillActivationInteraction(inputs);
        var simulate = NativeSkillActivationInteraction.class.getDeclaredMethod("simulateFirstRun",
                InteractionType.class, InteractionContext.class, CooldownHandler.class);
        simulate.setAccessible(true);
        // Null context would fail if the inherited simulateFirstRun called the real callback.
        simulate.invoke(interaction, InteractionType.Ability2, null, null);
        assertEquals(0, inputs.drain(ignored -> fail(), 10));
    }
}
