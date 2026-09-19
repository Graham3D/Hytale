package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Structural gate for the isolated, observation-only Probe 10 differential. */
public final class R117CustomGridPacketClosureTest {
    private R117CustomGridPacketClosureTest() { }

    public static void main(String[] arguments) throws Exception {
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/CustomGridDifferentialProbePage.java"));
        String telemetry = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/CustomGridDifferentialTelemetry.java"));
        String command = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/command/NativeInventoryProbeCommand.java"));
        String plugin = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/PersistentNpcsPlugin.java"));
        String profile = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));

        assert command.contains("isDifferentialRequest(request)");
        assert command.contains("openCustomPageWithWindows(");
        assert command.contains("Probe 10") || command.contains("PROBE10");
        assert command.contains("modeToken = parts.length < 2 ? \"baseline\"");
        assert page.contains("HIT_OCCUPIED(true, true, true)");

        assert page.contains("baseline=PROBE_5");
        assert page.contains("new SimpleItemContainer") == false;
        assert page.contains("#NpcInventoryGrid.Slots");
        assert page.contains("#PlayerInventoryGrid.Slots");
        assert page.contains("InventorySlotIndex");
        assert page.contains("IsActivatable");
        assert page.contains("IsItemIncompatible");
        assert page.contains("BsonBoolean.TRUE");
        assert page.contains("BsonBoolean.FALSE");
        assert occurrences(page, "HitTestVisible") == 2
                : "HitTestVisible should remain confined to one Set and its diagnostic";
        assert page.contains("commands.set(\"#NpcInventoryGrid.HitTestVisible\", true)");
        assert !page.contains("commands.set(\"#PlayerInventoryGrid.HitTestVisible\"");
        assert !page.contains("InventoryUtils.moveItem(");
        assert !page.contains(".ItemStacks");
        assert page.contains("moveAllItemStacksTo(playerStorage)")
                : "teardown recovery must remain present";
        assert page.contains("CUSTOM_GRID_AUTHORITATIVE_TRANSACTION");
        assert page.contains("registerChangeEvent")
                : "authoritative mutation observation must remain passive";

        for (String event : new String[] {"SlotMouseEntered", "SlotMouseExited",
                "SlotClicking", "SlotClickReleaseWhileDragging",
                "SlotMouseDragCompleted", "Dropped", "DragCancelled"}) {
            assert page.contains("CustomUIEventBindingType." + event) : event;
        }
        assert page.contains("events.addEventBinding(type, selector, data, false)")
                : "observation bindings must not lock the interface";
        assert page.contains("String rawData");
        assert page.contains("observeUiEvent");

        assert telemetry.contains("packet instanceof MoveItemStack");
        assert telemetry.contains("packet instanceof SmartMoveItemStack");
        assert telemetry.contains("packet instanceof InventoryAction");
        assert telemetry.contains("packet instanceof CustomPageEvent");
        assert telemetry.contains("CUSTOM_GRID_INBOUND_PACKET");
        assert telemetry.contains("CUSTOM_GRID_OUTBOUND_PACKET");
        assert telemetry.contains("fromSectionId=");
        assert telemetry.contains("toSectionId=");
        assert telemetry.contains("packetConsumed=false");
        assert telemetry.contains("BEFORE_NORMAL_INBOUND_HANDLER");
        assert plugin.contains("PacketAdapters.registerInbound(");
        assert plugin.contains("(PlayerPacketWatcher) CustomGridDifferentialTelemetry::observeInbound");
        assert plugin.contains("(PlayerPacketWatcher) CustomGridDifferentialTelemetry::observeOutbound");
        assert plugin.contains("PacketAdapters.deregisterInbound(customGridInboundWatcher)");
        assert plugin.contains("PacketAdapters.deregisterOutbound(customGridOutboundWatcher)");

        assert !profile.contains("CustomGridDifferential");
        assert !ui.contains("CustomGridDifferential");
        assert plugin.contains("public static final String REVISION")
                : "later isolated revisions may advance the build label without"
                        + " changing Probe 10 packet-closure behavior";
        System.out.println("R117 Custom UI packet-closure Probe 10 gate passed.");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0;
                index += needle.length()) count++;
        return count;
    }
}
