package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.EquipmentUpdate;
import com.hypixel.hytale.protocol.ModelUpdate;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.PlayerSkinUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client-local visual session for the native NPC Profile preview. It never mutates
 * authoritative ECS components. The authentic viewer baseline is immutable and is
 * restored on every close path owned by the page.
 */
public final class NpcMeshPreviewSession implements AutoCloseable {
    private static final Map<UUID, NpcMeshPreviewSession> ACTIVE = new HashMap<>();
    private static final AtomicLong EPOCHS = new AtomicLong();

    private final PlayerRef viewer;
    private final UUID viewerId;
    private final int viewerNetworkId;
    private final long epoch;
    private final String npcName;
    private final String baselineModelId;
    private final String targetModelId;
    private final ModelUpdate baseline;
    private final ModelUpdate target;
    private final PlayerSkinUpdate baselineSkin;
    private final PlayerSkinUpdate targetSkin;
    private final EquipmentUpdate baselineEquipment;
    private volatile EquipmentUpdate targetEquipment;
    private final Consumer<String> diagnostics;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean targetApplied;

    private NpcMeshPreviewSession(
            PlayerRef viewer,
            int viewerNetworkId,
            String npcName,
            String baselineModelId,
            String targetModelId,
            ModelUpdate baseline,
            ModelUpdate target,
            PlayerSkinUpdate baselineSkin,
            PlayerSkinUpdate targetSkin,
            EquipmentUpdate baselineEquipment,
            EquipmentUpdate targetEquipment,
            Consumer<String> diagnostics) {
        this.viewer = viewer;
        this.viewerId = viewer.getUuid();
        this.viewerNetworkId = viewerNetworkId;
        this.epoch = EPOCHS.incrementAndGet();
        this.npcName = npcName;
        this.baselineModelId = baselineModelId;
        this.targetModelId = targetModelId;
        this.baseline = new ModelUpdate(baseline);
        this.target = new ModelUpdate(target);
        this.baselineSkin = new PlayerSkinUpdate(baselineSkin);
        this.targetSkin = new PlayerSkinUpdate(targetSkin);
        this.baselineEquipment = new EquipmentUpdate(baselineEquipment);
        this.targetEquipment = new EquipmentUpdate(targetEquipment);
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public static NpcMeshPreviewSession begin(
            PlayerRef viewer,
            Ref<EntityStore> viewerRef,
            Store<EntityStore> store,
            String npcName,
            Model targetModel,
            PlayerSkin targetPlayerSkin,
            EquipmentUpdate targetEquipment,
            Consumer<String> diagnostics) {
        if (viewer == null || viewerRef == null || store == null || targetModel == null
                || targetPlayerSkin == null || targetEquipment == null) {
            throw new IllegalArgumentException(
                    "Viewer, store, target model, target skin, and target equipment are required");
        }
        NetworkId network = store.getComponent(viewerRef, NetworkId.getComponentType());
        ModelComponent authentic = store.getComponent(
                viewerRef, ModelComponent.getComponentType());
        PlayerSkinComponent authenticSkin = store.getComponent(
                viewerRef, PlayerSkinComponent.getComponentType());
        if (network == null || network.getId() <= 0 || authentic == null
                || authentic.getModel() == null || authenticSkin == null
                || authenticSkin.getPlayerSkin() == null) {
            throw new IllegalStateException(
                    "The authentic player model/skin baseline is not available");
        }
        EntityScaleComponent scale = store.getComponent(
                viewerRef, EntityScaleComponent.getComponentType());
        PlayerSettings settings = store.getComponent(
                viewerRef, PlayerSettings.getComponentType());
        InventoryComponent.Armor armor = store.getComponent(
                viewerRef, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Utility utility = store.getComponent(
                viewerRef, InventoryComponent.Utility.getComponentType());
        EquipmentUpdate authenticEquipment = InventoryUtils.createEquipmentUpdate(
                viewerRef, store, settings, armor, utility);
        float authenticScale = scale == null ? 1.0f : scale.getScale();
        NpcMeshPreviewSession created = new NpcMeshPreviewSession(
                viewer,
                network.getId(),
                npcName,
                authentic.getModel().getModelAssetId(),
                targetModel.getModelAssetId(),
                new ModelUpdate(authentic.getModel().toPacket(), authenticScale),
                new ModelUpdate(targetModel.toPacket(), targetModel.getScale()),
                new PlayerSkinUpdate(new PlayerSkin(authenticSkin.getPlayerSkin())),
                new PlayerSkinUpdate(new PlayerSkin(targetPlayerSkin)),
                new EquipmentUpdate(authenticEquipment),
                new EquipmentUpdate(targetEquipment),
                diagnostics);
        created.log("BASELINE_CAPTURED", "baselineModelId=" + created.baselineModelId
                + " targetModelId=" + created.targetModelId
                + " baselineSkinFingerprint="
                + skinFingerprint(authenticSkin.getPlayerSkin())
                + " targetSkinFingerprint=" + skinFingerprint(targetPlayerSkin)
                + " baselineEquipment=" + equipmentSummary(authenticEquipment)
                + " targetEquipment=" + equipmentSummary(targetEquipment));
        synchronized (ACTIVE) {
            NpcMeshPreviewSession previous = ACTIVE.remove(viewer.getUuid());
            if (previous != null) previous.restoreAndMarkClosed();
            ACTIVE.put(viewer.getUuid(), created);
        }
        return created;
    }

    /** Must be called only after the NPC Profile page has been opened/mounted. */
    public void applyAfterPageMount() {
        if (closed.get()) throw new IllegalStateException("NPC preview is closed");
        log("PREVIEW_OPENED",
                "ordering=MODEL_THEN_PLAYER_SKIN_THEN_EQUIPMENT_IMMEDIATELY_AFTER_OPEN_CUSTOM_PAGE");
        // Mark first so a write that reaches the client and then reports failure
        // still takes the restoration path in the caller's cleanup handler.
        targetApplied = true;
        try {
            send(target);
            log("MODEL_UPDATE_SENT", "targetModelId=" + targetModelId);
            send(targetSkin);
            log("PLAYER_SKIN_UPDATE_SENT", "targetSkinFingerprint="
                    + skinFingerprint(targetSkin.skin));
            send(targetEquipment);
            log("EQUIPMENT_UPDATE_SENT", "targetEquipment="
                    + equipmentSummary(targetEquipment));
        } catch (RuntimeException failure) {
            log("PREVIEW_UPDATE_FAILED", "targetModelId=" + targetModelId
                    + " failure=" + safe(failure.getMessage()));
            throw failure;
        }
    }

    public long epoch() { return epoch; }

    public int viewerNetworkId() { return viewerNetworkId; }

    public boolean targetApplied() { return targetApplied; }

    /** Refreshes only the target equipment overlay after an authoritative gear edit. */
    public void refreshEquipment(EquipmentUpdate equipment) {
        if (equipment == null) throw new IllegalArgumentException("Equipment is required");
        synchronized (ACTIVE) {
            if (closed.get() || !targetApplied || ACTIVE.get(viewerId) != this) return;
            EquipmentUpdate copy = new EquipmentUpdate(equipment);
            send(copy);
            targetEquipment = copy;
            log("EQUIPMENT_REFRESH_SENT", "targetEquipment=" + equipmentSummary(copy));
        }
    }

    /** Reasserts the NPC overlay after a viewer inventory transaction updates equipment. */
    public void refreshEquipment() {
        refreshEquipment(new EquipmentUpdate(targetEquipment));
    }

    @Override
    public void close() {
        synchronized (ACTIVE) {
            if (closed.get()) return;
            restoreAndMarkClosed();
            ACTIVE.remove(viewerId, this);
        }
    }

    public static void close(UUID viewerId) {
        if (viewerId == null) return;
        synchronized (ACTIVE) {
            NpcMeshPreviewSession session = ACTIVE.remove(viewerId);
            if (session != null) session.restoreAndMarkClosed();
        }
    }

    public static void closeAll() {
        synchronized (ACTIVE) {
            for (NpcMeshPreviewSession session : ACTIVE.values()) {
                session.restoreAndMarkClosed();
            }
            ACTIVE.clear();
        }
    }

    private void restoreAndMarkClosed() {
        if (!closed.compareAndSet(false, true)) return;
        if (targetApplied && viewer.getPacketHandler().stillActive()) {
            try {
                send(baseline);
                log("RESTORATION_SENT", "baselineModelId=" + baselineModelId);
                send(baselineSkin);
                log("RESTORATION_SKIN_SENT", "baselineSkinFingerprint="
                        + skinFingerprint(baselineSkin.skin));
                send(baselineEquipment);
                log("RESTORATION_EQUIPMENT_SENT", "baselineEquipment="
                        + equipmentSummary(baselineEquipment));
                log("RESTORATION_COMPLETED_ASSUMED",
                        "components=MODEL,PLAYER_SKIN,EQUIPMENT delivery=WRITE_NO_CACHE_NO_CLIENT_ACK");
            } catch (RuntimeException failure) {
                log("RESTORATION_FAILED", "baselineModelId=" + baselineModelId
                        + " failure=" + safe(failure.getMessage()));
            }
        } else if (targetApplied) {
            log("RESTORATION_COMPLETED_ASSUMED",
                    "delivery=CHANNEL_INACTIVE authoritativeStateUnchanged=true");
        } else {
            log("RESTORATION_NOT_REQUIRED", "targetApplied=false");
        }
        log("PREVIEW_SESSION_CLOSED", "closed=true");
    }

    private void send(ComponentUpdate update) {
        EntityUpdate entity = new EntityUpdate(
                viewerNetworkId,
                new ComponentUpdateType[0],
                new ComponentUpdate[] { update });
        viewer.getPacketHandler().writeNoCache(
                new EntityUpdates(null, new EntityUpdate[] { entity }));
    }

    private void log(String event, String detail) {
        diagnostics.accept("NPC_MESH_PREVIEW event=" + event
                + " epoch=" + epoch
                + " viewerId=" + viewerId
                + " viewerNetworkId=" + viewerNetworkId
                + " npc=" + safe(npcName)
                + " " + detail);
    }

    private static String safe(String value) {
        return value == null ? "UNKNOWN" : value.replaceAll("\\s+", "_");
    }

    private static String skinFingerprint(PlayerSkin skin) {
        return skin == null ? "UNKNOWN"
                : Integer.toUnsignedString(skin.hashCode(), 16).toUpperCase();
    }

    private static String equipmentSummary(EquipmentUpdate equipment) {
        if (equipment == null) return "UNKNOWN";
        return "armor=" + java.util.Arrays.toString(equipment.armorIds)
                + ",right=" + safe(equipment.rightHandItemId)
                + ",left=" + safe(equipment.leftHandItemId);
    }
}
