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
    @Test void nativeChargingCodecSupportsImmediateThresholdAndIndefiniteHold() throws Exception {
        var root=org.bson.BsonDocument.parse(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/Server/Item/RootInteractions/RPG/Root_RPG_Snipe_Release.json")));
        var json=root.getArray("Interactions").get(0).asDocument();
        assertEquals("Charging",json.getString("Type").getValue());
        var next=json.getDocument("Next");assertEquals(java.util.Set.of("0"),next.keySet());
        assertEquals("RPG_ActivateSkill",next.getDocument("0").getString("Type").getValue());
        assertEquals("Shortbow",json.getDocument("Effects").getString("ItemPlayerAnimationsId").getValue());
        assertEquals("ShootChargingHold",json.getDocument("Effects").getString("ItemAnimationId").getValue());
        var particle=json.getDocument("Effects").getArray("Particles").get(0).asDocument();
        assertEquals("RPG_Snipe_Ready",particle.getString("SystemId").getValue());
        assertEquals("PrimaryItem",particle.getString("TargetEntityPart").getValue());
        assertEquals("Handle",particle.getString("TargetNodeName").getValue());
        assertTrue(particle.getBoolean("ClearParticlesOnRemove").getValue());
        // This narrow codec fixture has no global AssetStore. Validate scalar hold semantics here;
        // full Next/Effects contained-asset resolution is a mandatory exact-JAR server audit.
        json.remove("Next");json.remove("Effects");
        var charge=com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction.CODEC
                .decode(json,new com.hypixel.hytale.assetstore.AssetExtraInfo<>(new com.hypixel.hytale.assetstore.AssetExtraInfo.Data(
                        com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class,"RPG_Test_Snipe",null)));
        assertEquals(WaitForDataFrom.Client,charge.getWaitForDataFrom());assertTrue(charge.needsRemoteSync());
        for(String field:java.util.List.of("allowIndefiniteHold","cancelOnOtherClick","failOnDamage")){
            var f=charge.getClass().getDeclaredField(field);f.setAccessible(true);assertEquals(true,f.get(charge));
        }
        assertTrue(root.getBoolean("RequireNewClick").getValue());
    }
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
