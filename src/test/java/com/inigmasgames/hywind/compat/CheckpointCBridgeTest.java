package com.inigmasgames.hywind.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.inigmasgames.compat.immersivenpcs.v1.BridgeCapability;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHandshake;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHello;
import com.inigmasgames.compat.immersivenpcs.v1.GameplayEvent;
import com.inigmasgames.compat.immersivenpcs.v1.ImmersiveNpcBridge;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CheckpointCBridgeTest {
    @Test
    void compatibleProviderNegotiatesAndReceivesAuthoritativeDamage() {
        AtomicReference<GameplayEvent> received = new AtomicReference<>();
        ImmersiveNpcBridge provider = new ImmersiveNpcBridge() {
            @Override public BridgeHandshake handshake(BridgeHello hello) {
                assertEquals(API_VERSION, hello.apiVersion());
                assertEquals(Set.of(BridgeCapability.COMBAT_EVENTS), hello.offeredCapabilities());
                return new BridgeHandshake(true, API_VERSION, "InigmasGames:ImmersiveNPCs",
                        "R172", Set.of(BridgeCapability.COMBAT_EVENTS,
                        BridgeCapability.NPC_EVENT_OBSERVATION), "NEGOTIATED");
            }
            @Override public void publish(GameplayEvent event) { received.set(event); }
        };
        ArrayList<String> info = new ArrayList<>();
        RpgGameplayEventPublisher publisher = ImmersiveNpcBridgeConnector.connect(
                provider, "R141", info::add, fail());
        publisher.entityDamaged(new RpgGameplayEventPublisher.DamageObservation(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "LIGHTNING_SPIRE", "cast-1", 12.5, 37.5));
        assertNotNull(received.get());
        assertEquals(GameplayEvent.EventType.ENTITY_DAMAGED, received.get().type());
        assertEquals("12.5", received.get().facts().get("amount"));
        assertTrue(info.getFirst().contains("status=CONNECTED"));
    }

    @Test
    void incompatibleProviderDisablesOnlyTheBridge() {
        ArrayList<String> warnings = new ArrayList<>();
        RpgGameplayEventPublisher publisher = ImmersiveNpcBridgeConnector.connect(
                new Object(), "R141", ignored -> { }, warnings::add);
        assertDoesNotThrow(() -> publisher.entityDamaged(
                new RpgGameplayEventPublisher.DamageObservation(null, null, "", "", 1, 1)));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("INCOMPATIBLE_PROVIDER_CONTRACT"));
    }

    @Test
    void providerPublishFailureIsContainedAndLoggedOnce() {
        ImmersiveNpcBridge provider = new ImmersiveNpcBridge() {
            @Override public BridgeHandshake handshake(BridgeHello hello) {
                return new BridgeHandshake(true, API_VERSION, "InigmasGames:ImmersiveNPCs",
                        "R172", Set.of(BridgeCapability.COMBAT_EVENTS), "NEGOTIATED");
            }
            @Override public void publish(GameplayEvent event) { throw new IllegalStateException(); }
        };
        ArrayList<String> warnings = new ArrayList<>();
        RpgGameplayEventPublisher publisher = ImmersiveNpcBridgeConnector.connect(
                provider, "R141", ignored -> { }, warnings::add);
        var observation = new RpgGameplayEventPublisher.DamageObservation(
                null, null, "", "", 1, 1);
        assertDoesNotThrow(() -> publisher.entityDamaged(observation));
        assertDoesNotThrow(() -> publisher.entityDamaged(observation));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("PUBLISH_FAILED"));
    }

    private static java.util.function.Consumer<String> fail() {
        return message -> org.junit.jupiter.api.Assertions.fail(message);
    }
}
