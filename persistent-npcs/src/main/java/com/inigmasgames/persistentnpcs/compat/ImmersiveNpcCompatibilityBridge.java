package com.inigmasgames.persistentnpcs.compat;

import com.inigmasgames.compat.immersivenpcs.v1.BridgeCapability;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHandshake;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHello;
import com.inigmasgames.compat.immersivenpcs.v1.GameplayEvent;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Converts the versioned neutral contract into ImmersiveNPC-owned observations. */
public final class ImmersiveNpcCompatibilityBridge {
    private static final int API_VERSION = 1;
    private final String providerVersion;
    private final NpcEventBus events;
    private final Predicate<UUID> persistentNpc;
    private final Consumer<String> diagnostics;
    private volatile boolean connected;

    public ImmersiveNpcCompatibilityBridge(String providerVersion, NpcEventBus events,
            Predicate<UUID> persistentNpc, Consumer<String> diagnostics) {
        this.providerVersion = providerVersion;
        this.events = java.util.Objects.requireNonNull(events);
        this.persistentNpc = persistentNpc == null ? ignored -> false : persistentNpc;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public BridgeHandshake handshake(BridgeHello hello) {
        if (hello == null || hello.apiVersion() != API_VERSION) {
            connected = false;
            return disabled("API_VERSION_MISMATCH");
        }
        if (!"InigmasGames:HyARPG".equals(hello.clientId())) {
            connected = false;
            return disabled("UNRECOGNIZED_CLIENT");
        }
        if (!hello.offeredCapabilities().contains(BridgeCapability.COMBAT_EVENTS)) {
            connected = false;
            return disabled("NO_SHARED_CAPABILITY");
        }
        connected = true;
        diagnostics.accept("IMMERSIVENPCS_HYARPG_BRIDGE status=CONNECTED api=1 capabilities="
                + "COMBAT_EVENTS,NPC_EVENT_OBSERVATION clientVersion=" + hello.clientVersion());
        return new BridgeHandshake(true, API_VERSION, "InigmasGames:ImmersiveNPCs",
                providerVersion,
                Set.of(BridgeCapability.COMBAT_EVENTS,
                        BridgeCapability.NPC_EVENT_OBSERVATION),
                "NEGOTIATED");
    }

    public void publish(GameplayEvent event) {
        if (!connected) throw new IllegalStateException("BRIDGE_NOT_NEGOTIATED");
        java.util.Objects.requireNonNull(event, "event");
        if (event.type() != GameplayEvent.EventType.ENTITY_DAMAGED) {
            throw new IllegalArgumentException("UNSUPPORTED_EVENT_TYPE");
        }
        UUID npcId = persistentNpc.test(event.targetId()) ? event.targetId()
                : persistentNpc.test(event.actorId()) ? event.actorId() : null;
        events.emit(new NpcFrameworkEvent(event.eventId(), NpcEventType.ENTITY_DAMAGED,
                npcId, event.actorId(), event.targetId(), event.occurredAt(), event.facts()));
        diagnostics.accept("IMMERSIVENPCS_HYARPG_EVENT type=ENTITY_DAMAGED eventId="
                + event.eventId() + " npc=" + (npcId == null ? "NONE" : npcId));
    }

    private BridgeHandshake disabled(String reason) {
        diagnostics.accept("IMMERSIVENPCS_HYARPG_BRIDGE status=DISABLED reason=" + reason);
        return new BridgeHandshake(false, API_VERSION, "InigmasGames:ImmersiveNPCs",
                providerVersion, Set.of(), reason);
    }
}
