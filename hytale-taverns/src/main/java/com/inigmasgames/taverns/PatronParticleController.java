package com.inigmasgames.taverns;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.joml.Vector3f;

/**
 * Sends short-lived, model-attached particle systems as world-space patron UI.
 *
 * <p>Hytale 0.5.9 has no stop-model-particle packet. Order particles therefore
 * use a short finite lifespan and are refreshed by the existing patron tick;
 * no independent task or callback retains an entity reference.
 */
final class PatronParticleController {
    static final float ORDER_PULSE_SECONDS = 0.35f;
    static final float ORDER_PARTICLE_LIFETIME_SECONDS = 0.55f;
    static final String TARGET_NODE = "Head";
    static final String FRAME_TEXTURE = "UI/ItemQualities/Slots/SlotDefault@2x.png";

    private static final float ORDER_HEIGHT = 1.10f;
    private static final float EMOTION_HEIGHT = 0.42f;

    private final Consumer<Throwable> error;
    private final Set<String> rejectedItems = ConcurrentHashMap.newKeySet();

    PatronParticleController(Consumer<String> info, Consumer<Throwable> error) {
        this.error = error;
    }

    void pulseOrder(
            Ref<EntityStore> patronRef,
            String itemId,
            World world,
            ComponentAccessor<EntityStore> accessor) {
        if (itemId == null || patronRef == null || !patronRef.isValid()) {
            return;
        }
        try {
            String systemId = orderSystemId(itemId);
            if (ParticleSystem.getAssetMap().getAsset(systemId) == null) {
                if (rejectedItems.add(itemId)) {
                    error.accept(new IllegalStateException(
                            "Missing packaged patron order particle system: " + systemId));
                }
                return;
            }
            SpawnModelParticles packet = packetFor(
                    patronRef, systemId, ORDER_HEIGHT, accessor);
            if (packet == null) {
                return;
            }
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                playerRef.getPacketHandler().write(packet);
            }
        } catch (Throwable throwable) {
            if (rejectedItems.add(itemId)) {
                error.accept(throwable);
            }
        }
    }

    void spawnEmotion(
            Ref<EntityStore> patronRef,
            String particleSystem,
            World world,
            ComponentAccessor<EntityStore> accessor) {
        if (patronRef == null || !patronRef.isValid()) {
            return;
        }
        try {
            SpawnModelParticles packet = packetFor(
                    patronRef, particleSystem, EMOTION_HEIGHT, accessor);
            if (packet == null) {
                return;
            }
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                playerRef.getPacketHandler().write(packet);
            }
        } catch (Throwable throwable) {
            error.accept(throwable);
        }
    }

    private static SpawnModelParticles packetFor(
            Ref<EntityStore> patronRef,
            String particleSystem,
            float height,
            ComponentAccessor<EntityStore> accessor) {
        NetworkId networkId = accessor.getComponent(
                patronRef, NetworkId.getComponentType());
        if (networkId == null) {
            return null;
        }
        ModelParticle particle = new ModelParticle(
                particleSystem,
                1.0f,
                null,
                EntityPart.Entity,
                TARGET_NODE,
                new Vector3f(0.0f, height, 0.0f),
                null,
                false);
        return new SpawnModelParticles(
                networkId.getId(), new ModelParticle[] {particle});
    }

    static String normalizeIconTexture(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        String normalized = icon.replace('\\', '/');
        if (normalized.startsWith("Common/")) {
            normalized = normalized.substring("Common/".length());
        }
        return normalized;
    }

    static String safeAssetId(String itemId) {
        return itemId.replaceAll("[^A-Za-z0-9_]", "_");
    }

    static String orderSystemId(String itemId) {
        return "Taverns_Order_" + safeAssetId(itemId);
    }
}
