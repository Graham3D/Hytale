package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.InventoryAction;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Observation-only boundary telemetry for the isolated Custom UI differential probe.
 * The registered PlayerPacketWatcher cannot consume a packet; it runs before the
 * ordinary packet handler and returns no decision to the adapter chain.
 */
public final class CustomGridDifferentialTelemetry {
    private static final ConcurrentHashMap<UUID, Session> ACTIVE =
            new ConcurrentHashMap<>();

    private CustomGridDifferentialTelemetry() { }

    public static void activate(PlayerRef viewer, String mode, int npcSectionId,
            Consumer<String> diagnostics) {
        Session session = new Session(mode, npcSectionId,
                diagnostics == null ? ignored -> { } : diagnostics);
        ACTIVE.put(viewer.getUuid(), session);
        session.log("CUSTOM_GRID_PACKET_WATCH_ACTIVE"
                + " timestamp=" + Instant.now()
                + " mode=" + mode
                + " viewerUuid=" + viewer.getUuid()
                + " npcSectionId=" + npcSectionId
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " observationOnly=true packetConsumed=false"
                + " position=BEFORE_NORMAL_INBOUND_HANDLER");
    }

    public static void deactivate(UUID viewerId, String reason) {
        Session session = ACTIVE.remove(viewerId);
        if (session == null) return;
        session.log("CUSTOM_GRID_PACKET_WATCH_INACTIVE"
                + " timestamp=" + Instant.now()
                + " mode=" + session.mode
                + " viewerUuid=" + viewerId
                + " reason=" + token(reason)
                + " observedInboundPackets=" + session.inboundSequence.get()
                + " observedMovePackets=" + session.moveSequence.get()
                + " observedOutboundPackets=" + session.outboundSequence.get()
                + " inboundPacketClasses=" + session.inboundClasses
                + " outboundPacketClasses=" + session.outboundClasses);
    }

    public static void observeInbound(PlayerRef viewer, Packet packet) {
        if (viewer == null || packet == null) return;
        Session session = ACTIVE.get(viewer.getUuid());
        if (session == null) return;
        long inboundSequence = session.inboundSequence.incrementAndGet();
        session.inboundClasses.merge(packet.getClass().getName(), 1L, Long::sum);
        if (isRelevant(packet)) {
            session.log("CUSTOM_GRID_INBOUND_PACKET"
                    + " timestamp=" + Instant.now()
                    + " nanoTime=" + System.nanoTime()
                    + " sequence=" + inboundSequence
                    + " mode=" + session.mode
                    + " viewerUuid=" + viewer.getUuid()
                    + " packetClass=" + packet.getClass().getName()
                    + " packetId=" + packet.getId()
                    + " channel=" + packet.getChannel()
                    + packetDetails(packet)
                    + " observationOnly=true packetConsumed=false"
                    + " position=BEFORE_NORMAL_INBOUND_HANDLER");
        }
        if (packet instanceof MoveItemStack move) {
            long sequence = session.moveSequence.incrementAndGet();
            session.log("CUSTOM_GRID_MOVE_PACKET"
                    + " timestamp=" + Instant.now()
                    + " nanoTime=" + System.nanoTime()
                    + " sequence=" + sequence
                    + " mode=" + session.mode
                    + " viewerUuid=" + viewer.getUuid()
                    + " packet=MoveItemStack"
                    + " fromSectionId=" + move.fromSectionId
                    + " fromSlotId=" + move.fromSlotId
                    + " quantity=" + move.quantity
                    + " toSectionId=" + move.toSectionId
                    + " toSlotId=" + move.toSlotId
                    + " fromIdentity=" + session.identity(move.fromSectionId)
                    + " toIdentity=" + session.identity(move.toSectionId)
                    + " expectedNpcSectionId=" + session.npcSectionId
                    + " expectedPlayerStorageSectionId="
                    + InventoryComponent.STORAGE_SECTION_ID
                    + " observationOnly=true packetConsumed=false"
                    + " position=BEFORE_NORMAL_INBOUND_HANDLER");
        } else if (packet instanceof SmartMoveItemStack move) {
            long sequence = session.moveSequence.incrementAndGet();
            session.log("CUSTOM_GRID_MOVE_PACKET"
                    + " timestamp=" + Instant.now()
                    + " nanoTime=" + System.nanoTime()
                    + " sequence=" + sequence
                    + " mode=" + session.mode
                    + " viewerUuid=" + viewer.getUuid()
                    + " packet=SmartMoveItemStack"
                    + " fromSectionId=" + move.fromSectionId
                    + " fromSlotId=" + move.fromSlotId
                    + " quantity=" + move.quantity
                    + " moveType=" + move.moveType
                    + " fromIdentity=" + session.identity(move.fromSectionId)
                    + " expectedNpcSectionId=" + session.npcSectionId
                    + " expectedPlayerStorageSectionId="
                    + InventoryComponent.STORAGE_SECTION_ID
                    + " observationOnly=true packetConsumed=false"
                    + " position=BEFORE_NORMAL_INBOUND_HANDLER");
        }
    }

    public static void observeOutbound(PlayerRef viewer, Packet packet) {
        if (viewer == null || packet == null) return;
        Session session = ACTIVE.get(viewer.getUuid());
        if (session == null) return;
        long sequence = session.outboundSequence.incrementAndGet();
        session.outboundClasses.merge(packet.getClass().getName(), 1L, Long::sum);
        if (!isRelevant(packet)) return;
        session.log("CUSTOM_GRID_OUTBOUND_PACKET"
                + " timestamp=" + Instant.now()
                + " nanoTime=" + System.nanoTime()
                + " sequence=" + sequence
                + " mode=" + session.mode
                + " viewerUuid=" + viewer.getUuid()
                + " packetClass=" + packet.getClass().getName()
                + " packetId=" + packet.getId()
                + " channel=" + packet.getChannel()
                + packetDetails(packet)
                + " observationOnly=true packetConsumed=false"
                + " position=BEFORE_NORMAL_OUTBOUND_WRITE");
    }

    public static void observeUiEvent(UUID viewerId, String mode, String rawPayload) {
        Session session = ACTIVE.get(viewerId);
        Consumer<String> log = session == null ? ignored -> { } : session.diagnostics;
        log.accept("CUSTOM_GRID_DRAG_EVENT"
                + " timestamp=" + Instant.now()
                + " nanoTime=" + System.nanoTime()
                + " mode=" + token(mode)
                + " viewerUuid=" + viewerId
                + " rawPayload=" + quoted(rawPayload)
                + " observationOnly=true");
    }

    private static boolean isRelevant(Packet packet) {
        String name = packet.getClass().getName();
        return name.startsWith("com.hypixel.hytale.protocol.packets.inventory.")
                || name.startsWith("com.hypixel.hytale.protocol.packets.window.")
                || packet instanceof CustomPageEvent;
    }

    private static String packetDetails(Packet packet) {
        if (packet instanceof InventoryAction action) {
            return " inventorySectionId=" + action.inventorySectionId
                    + " inventoryActionType=" + action.inventoryActionType
                    + " actionData=" + action.actionData;
        }
        if (packet instanceof DropItemStack drop) {
            return " inventorySectionId=" + drop.inventorySectionId
                    + " slotId=" + drop.slotId
                    + " quantity=" + drop.quantity;
        }
        if (packet instanceof MoveItemStack move) {
            return " fromSectionId=" + move.fromSectionId
                    + " fromSlotId=" + move.fromSlotId
                    + " quantity=" + move.quantity
                    + " toSectionId=" + move.toSectionId
                    + " toSlotId=" + move.toSlotId;
        }
        if (packet instanceof SmartMoveItemStack move) {
            return " fromSectionId=" + move.fromSectionId
                    + " fromSlotId=" + move.fromSlotId
                    + " quantity=" + move.quantity
                    + " moveType=" + move.moveType;
        }
        if (packet instanceof CustomPageEvent event) {
            return " customPageEventType=" + event.type
                    + " data=" + quoted(event.data);
        }
        return "";
    }

    private static String token(String value) {
        if (value == null || value.isBlank()) return "NONE";
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private static String quoted(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\")
                .replace("\r", "\\r").replace("\n", "\\n")
                .replace("\"", "\\\"") + '"';
    }

    private static final class Session {
        private final String mode;
        private final int npcSectionId;
        private final Consumer<String> diagnostics;
        private final AtomicLong inboundSequence = new AtomicLong();
        private final AtomicLong moveSequence = new AtomicLong();
        private final AtomicLong outboundSequence = new AtomicLong();
        private final ConcurrentHashMap<String, Long> inboundClasses =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> outboundClasses =
                new ConcurrentHashMap<>();

        private Session(String mode, int npcSectionId, Consumer<String> diagnostics) {
            this.mode = mode;
            this.npcSectionId = npcSectionId;
            this.diagnostics = diagnostics;
        }

        private String identity(int sectionId) {
            if (sectionId == npcSectionId) return "NPC_WINDOW_" + npcSectionId;
            if (sectionId == InventoryComponent.STORAGE_SECTION_ID) {
                return "PLAYER_STORAGE";
            }
            return sectionId < 0 ? "OTHER_BUILTIN_" + sectionId
                    : "OTHER_WINDOW_" + sectionId;
        }

        private void log(String message) {
            diagnostics.accept(message);
        }
    }
}
