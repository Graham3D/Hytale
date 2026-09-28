package com.inigmasgames.persistentnpcs;

import com.inigmasgames.compat.immersivenpcs.v1.BridgeCapability;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHello;
import com.inigmasgames.compat.immersivenpcs.v1.GameplayEvent;
import com.inigmasgames.compat.immersivenpcs.v1.ImmersiveNpcBridge;
import com.inigmasgames.persistentnpcs.compat.ImmersiveNpcCompatibilityBridge;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class R172CompatibilityBridgeTest {
    private R172CompatibilityBridgeTest() { }

    public static void main(String[] args) {
        NpcEventBus bus = new NpcEventBus();
        AtomicReference<com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent> observed =
                new AtomicReference<>();
        bus.register(observed::set);
        UUID mara = UUID.randomUUID();
        ArrayList<String> diagnostics = new ArrayList<>();
        ImmersiveNpcCompatibilityBridge bridge = new ImmersiveNpcCompatibilityBridge(
                "R172-PRE4-COMPAT", bus, mara::equals, diagnostics::add);

        var incompatible = bridge.handshake(new BridgeHello(99,
                "InigmasGames:HyARPG", "R141", Set.of(BridgeCapability.COMBAT_EVENTS)));
        assert !incompatible.enabled();
        assert "API_VERSION_MISMATCH".equals(incompatible.reason());

        var compatible = bridge.handshake(new BridgeHello(ImmersiveNpcBridge.API_VERSION,
                "InigmasGames:HyARPG", "R141", Set.of(BridgeCapability.COMBAT_EVENTS)));
        assert compatible.enabled();
        assert compatible.acceptedCapabilities().contains(BridgeCapability.NPC_EVENT_OBSERVATION);

        UUID actor = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        bridge.publish(new GameplayEvent(eventId, GameplayEvent.EventType.ENTITY_DAMAGED,
                Instant.now(), actor, mara, Map.of("skillId", "LIGHTNING_SPIRE")));
        assert observed.get() != null;
        assert eventId.equals(observed.get().eventId());
        assert observed.get().type() == NpcEventType.ENTITY_DAMAGED;
        assert mara.equals(observed.get().npcId());
        assert actor.equals(observed.get().actorEntityId());
        assert diagnostics.stream().anyMatch(line -> line.contains("status=CONNECTED"));
        assert diagnostics.stream().anyMatch(line -> line.contains("type=ENTITY_DAMAGED"));
        System.out.println("R172 PASS: optional bridge negotiation, incompatibility isolation, and event conversion.");
    }
}
