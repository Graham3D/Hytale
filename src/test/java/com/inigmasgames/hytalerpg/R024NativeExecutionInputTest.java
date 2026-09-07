package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.inigmasgames.hytalerpg.input.HytaleAbilitySkillInputAdapter;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class R024NativeExecutionInputTest {
    private final UUID player = UUID.randomUUID();
    @Test void serverCallbackMapsTwoSlotsOncePerActualChainAndKeepsCorrelation() {
        var observations = new ArrayList<HytaleAbilitySkillInputAdapter.Observation>();
        var inputs = new HytaleAbilitySkillInputAdapter(observations::add);
        inputs.useNativeExecution();
        Object chain = new Object();
        inputs.acceptNativeExecution(player, InteractionType.Ability2, 7, chain, "RPG_Ability_Quick_Slash", 0);
        inputs.acceptNativeExecution(player, InteractionType.Ability2, 7, chain, "RPG_Ability_Quick_Slash", 0);
        inputs.acceptNativeExecution(player, InteractionType.Ability3, 8, new Object(), "RPG_Ability_Fire_Bolt", 3);
        var requests = new ArrayList<HytaleAbilitySkillInputAdapter.Request>();
        assertEquals(2, inputs.drain(requests::add, 10));
        assertEquals(2, observations.size());
        assertEquals(observations.getFirst().correlationId(), requests.getFirst().correlationId());
        assertEquals("skill01", requests.getFirst().slot().externalId());
        assertEquals("skill02", requests.getLast().slot().externalId());
    }
    @Test void inboundTrafficCannotAlsoActivateInProduction() {
        var inputs = new HytaleAbilitySkillInputAdapter();
        inputs.useNativeExecution();
        var chain = new SyncInteractionChain(); chain.initial = true;
        chain.interactionType = InteractionType.Ability2; chain.chainId = 7;
        var packet = new SyncInteractionChains(); packet.updates = new SyncInteractionChain[]{chain};
        inputs.observe(player, packet);
        assertEquals(0, inputs.drain(ignored -> fail("Packet observation executed RPG"), 10));
    }
    @Test void signatureAbility4ForeignRunesAndWrongSlotsNeverExecute() {
        var inputs = new HytaleAbilitySkillInputAdapter();
        for (var type : new InteractionType[]{InteractionType.Ability1, InteractionType.Ability4})
            inputs.acceptNativeExecution(player, type, 1, new Object(), "RPG_Ability_Fire_Bolt", 0);
        inputs.acceptNativeExecution(player, InteractionType.Ability2, 1, new Object(), "Rune_Fireball", 0);
        inputs.acceptNativeExecution(player, InteractionType.Ability3, 1, new Object(), "RPG_Ability_Fire_Bolt", 0);
        assertEquals(0, inputs.drain(ignored -> fail(), 10));
    }
    @Test void controlSuppressionAppliesAtBothEnqueueAndDrain() {
        var inputs = new HytaleAbilitySkillInputAdapter();
        boolean[] suppressed = {true};
        inputs.configureControl(ignored -> suppressed[0], (id, packet) -> { });
        inputs.acceptNativeExecution(player, InteractionType.Ability2, 1, new Object(), "RPG_Ability_Quick_Slash", 0);
        assertEquals(0, inputs.drain(ignored -> fail(), 10));
        suppressed[0] = false;
        inputs.acceptNativeExecution(player, InteractionType.Ability2, 2, new Object(), "RPG_Ability_Quick_Slash", 0);
        suppressed[0] = true;
        inputs.drain(ignored -> fail(), 10);
    }
    @Test void perPlayerQueueIsBoundedAndTeardownClearsIt() {
        var inputs = new HytaleAbilitySkillInputAdapter();
        for (int i = 0; i < 100; i++) inputs.acceptNativeExecution(player, InteractionType.Ability2,
                i, new Object(), "RPG_Ability_Quick_Slash", 0);
        assertEquals(64, inputs.drain(ignored -> { }, 200));
        inputs.acceptNativeExecution(player, InteractionType.Ability3, 201, new Object(), "RPG_Ability_Fire_Bolt", 3);
        inputs.clear(player);
        assertEquals(0, inputs.drain(ignored -> fail(), 10));
    }
}
