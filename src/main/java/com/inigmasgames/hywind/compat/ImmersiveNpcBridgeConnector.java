package com.inigmasgames.hywind.compat;

import com.inigmasgames.compat.immersivenpcs.v1.BridgeCapability;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHandshake;
import com.inigmasgames.compat.immersivenpcs.v1.BridgeHello;
import com.inigmasgames.compat.immersivenpcs.v1.GameplayEvent;
import com.inigmasgames.compat.immersivenpcs.v1.ImmersiveNpcBridge;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Optional, fail-open adapter loaded only after Hytale discovers ImmersiveNPCs. */
public final class ImmersiveNpcBridgeConnector {
    private ImmersiveNpcBridgeConnector() { }

    public static RpgGameplayEventPublisher connect(
            Object candidate,
            String clientVersion,
            Consumer<String> info,
            Consumer<String> warning) {
        if (!(candidate instanceof ImmersiveNpcBridge bridge)) {
            warning.accept("HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=INCOMPATIBLE_PROVIDER_CONTRACT");
            return RpgGameplayEventPublisher.NO_OP;
        }
        BridgeHandshake handshake;
        try {
            handshake = bridge.handshake(new BridgeHello(
                    ImmersiveNpcBridge.API_VERSION,
                    "InigmasGames:HyARPG",
                    clientVersion,
                    Set.of(BridgeCapability.COMBAT_EVENTS)));
        } catch (RuntimeException failure) {
            warning.accept("HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=HANDSHAKE_FAILED error="
                    + failure.getClass().getSimpleName());
            return RpgGameplayEventPublisher.NO_OP;
        }
        if (handshake == null || !handshake.enabled()
                || handshake.apiVersion() != ImmersiveNpcBridge.API_VERSION
                || !handshake.acceptedCapabilities().contains(BridgeCapability.COMBAT_EVENTS)) {
            String reason = handshake == null ? "EMPTY_HANDSHAKE"
                    : handshake.reason().isBlank() ? "INCOMPATIBLE_API_OR_CAPABILITY" : handshake.reason();
            warning.accept("HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=" + compact(reason));
            return RpgGameplayEventPublisher.NO_OP;
        }
        info.accept("HYARPG_IMMERSIVE_BRIDGE status=CONNECTED api=1 capabilities=COMBAT_EVENTS provider="
                + compact(handshake.providerId()) + " providerVersion="
                + compact(handshake.providerVersion()));
        return new ConnectedPublisher(bridge, warning);
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t ]+", "_");
    }

    private static final class ConnectedPublisher implements RpgGameplayEventPublisher {
        private final ImmersiveNpcBridge bridge;
        private final Consumer<String> warning;
        private final AtomicBoolean enabled = new AtomicBoolean(true);
        private final AtomicBoolean failureLogged = new AtomicBoolean(false);

        private ConnectedPublisher(ImmersiveNpcBridge bridge, Consumer<String> warning) {
            this.bridge = bridge;
            this.warning = warning;
        }

        @Override
        public void entityDamaged(DamageObservation observation) {
            if (!enabled.get() || observation == null) return;
            LinkedHashMap<String, String> facts = new LinkedHashMap<>();
            facts.put("source", "HYARPG");
            facts.put("skillId", observation.skillId() == null ? "" : observation.skillId());
            facts.put("correlationId", observation.correlationId() == null
                    ? "" : observation.correlationId());
            facts.put("amount", Double.toString(observation.amount()));
            facts.put("healthAfter", Double.toString(observation.healthAfter()));
            try {
                bridge.publish(new GameplayEvent(UUID.randomUUID(),
                        GameplayEvent.EventType.ENTITY_DAMAGED, Instant.now(),
                        observation.actorId(), observation.targetId(), facts));
            } catch (RuntimeException failure) {
                enabled.set(false);
                if (failureLogged.compareAndSet(false, true)) {
                    warning.accept("HYARPG_IMMERSIVE_BRIDGE status=DISABLED reason=PUBLISH_FAILED error="
                            + failure.getClass().getSimpleName());
                }
            }
        }
    }
}
