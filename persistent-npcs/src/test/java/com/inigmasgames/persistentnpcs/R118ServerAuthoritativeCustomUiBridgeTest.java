package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Structural safety gate for isolated Option 2 / Probe 11. */
public final class R118ServerAuthoritativeCustomUiBridgeTest {
    private R118ServerAuthoritativeCustomUiBridgeTest() { }

    public static void main(String[] arguments) throws Exception {
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryTransactionBridge.java");
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryBridgeProbePage.java");
        String bridgeUi = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryBridgeUi.java");
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/"
                + "NativeInventoryProbeCommand.java");
        String profile = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NpcProfilePage.java");
        String profileUi = read("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui");
        String repository = read("src/main/java/com/inigmasgames/persistentnpcs/"
                + "profile/NpcInventoryRepository.java");
        String nativeWindow = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NativeInventoryControlWindow.java");

        assert command.contains("isBridgeRequest(request)");
        assert command.contains("openCustomInventoryBridge(");
        assert command.contains("openCustomPageWithWindows(");
        assert command.contains("new SimpleItemContainer((short) 40)");
        assert command.contains("Probe 11");

        assert bridge.contains("record InventoryMoveIntent(");
        for (String field : new String[] {"sessionId", "pageGeneration",
                "sourceSectionId", "sourceSlotId", "targetSectionId",
                "targetSlotId", "requestedQuantity", "mouseButton",
                "eventSequence"}) {
            assert bridge.contains(field) : field;
        }
        assert bridge.contains("InventoryUtils.getSectionById(");
        assert bridge.contains("InventoryUtils.moveItem(ref,");
        assert bridge.contains("value == playerStorage");
        assert bridge.contains("value == npcInventory");
        assert bridge.contains("getCustomPage() != expectedPage");
        assert bridge.contains("getWindow(npcSection) != npcWindow");
        assert bridge.contains("store.isInThread()");
        assert bridge.contains("getWorld().execute(task)");
        assert bridge.contains("DUPLICATE_WINDOW_NANOS");
        assert bridge.contains("CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED");
        assert bridge.contains("manualStackMutation=false");
        assert !bridge.contains("setItemStackForSlot(");
        assert !bridge.contains("removeItemStackFromSlot(");
        assert !bridge.contains("addItemStack(");

        for (String marker : new String[] {"CUSTOM_BRIDGE_INTENT",
                "CUSTOM_BRIDGE_VALIDATED", "CUSTOM_BRIDGE_REJECTED",
                "CUSTOM_BRIDGE_NATIVE_MOVE", "CUSTOM_BRIDGE_NATIVE_RESULT",
                "CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED",
                "CUSTOM_BRIDGE_SESSION_CLOSE"}) {
            assert bridge.contains(marker) : marker;
        }
        assert bridgeUi.contains("CustomUIEventBindingType.Dropped");
        assert !page.contains("CustomUIEventBindingType.SlotMouseDragCompleted");
        assert !page.contains("CustomUIEventBindingType.SlotClickReleaseWhileDragging");
        assert page.contains("#NpcInventoryGrid.Slots");
        assert page.contains("#PlayerInventoryGrid.Slots");
        assert bridgeUi.contains("InventorySlotIndex");
        assert bridgeUi.contains("IsActivatable");
        assert page.contains("CUSTOM_BRIDGE_REFRESH");
        assert page.contains("CUSTOM_BRIDGE_DRAG_RESET");
        assert page.contains("DIFFERENTIAL_A_SLOTS_REFRESH_ONLY");
        assert page.contains("bridge.close();");
        assert page.indexOf("bridge.close();")
                < page.indexOf("npcInventory.moveAllItemStacksTo(playerStorage)")
                : "session must reject late intent before recovery begins";

        assert profile.contains("CustomInventoryTransactionBridge")
                : "R119 promotes the connected-proven R118 bridge into production";
        assert !profileUi.contains("CUSTOM_BRIDGE");
        assert !repository.contains("CustomInventoryTransactionBridge");
        assert !nativeWindow.contains("CustomInventoryTransactionBridge");

        System.out.println("R118 isolated server-authoritative Custom UI bridge gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
