package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.packets.interface_.Page;
import java.nio.file.Files;
import java.nio.file.Path;

/** Structural gate for Probe 8's exact native InventorySee/chest control path. */
public final class R113NativeInventoryControlTest {
    public static void main(String[] args) throws Exception {
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/NativeInventoryProbeCommand.java");
        String control = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeInventoryControlWindow.java");
        String profilePage = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String profileUi = read("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");

        assert Page.Bench != null : "Current server API must expose the native Bench page";
        assert command.contains("variant == 8")
                && command.contains("setPageWithWindows(")
                && command.contains("Page.Bench, true, window")
                : "Probe 8 must clone InventorySeeCommand's native Page.Bench path";
        assert command.contains("customPage=false customItemGrid=false customTransferHandler=false")
                : "Native control must not use a Custom UI movement bridge";
        assert control.contains("extends ContainerWindow")
                && control.contains("InventoryUtils.getSectionById(")
                && control.contains("resolvedNpc == npcInventory")
                && control.contains("resolvedStorage == playerStorage")
                : "Native control must assert exact runtime section identity";
        assert control.contains("registerChangeEvent")
                && control.contains("packetCapture=false")
                && control.contains("authoritativeState=")
                : "Diagnostics must observe committed authoritative transactions";
        assert control.contains("moveAllItemStacksTo(playerStorage)")
                && control.contains("addOrDropItemStack")
                : "Ephemeral control must retain loss-prevention recovery";
        assert !command.contains("InventoryUtils.moveItem(")
                && !command.contains("InventoryUtils.smartMoveItem(")
                : "Probe diagnostics must not perform native moves";

        // Production freeze sentinels: these are read-only expectations, not Probe 8 edits.
        assert profilePage.contains("inventory.flush()")
                && profilePage.contains("boundNpcGridDocument(storageWindow.getId())")
                && profileUi.contains("CharacterPreviewComponent #NpcCharacterPreview")
                && profileUi.contains("Group #NpcGridHost")
                && profileUi.contains("Group #PlayerGridHost")
                : "Production NPC Profile features must remain present";
        System.out.println("R113 native inventory control structural gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
