package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import java.nio.file.Files;
import java.nio.file.Path;

/** R102 structural gate for native window-owned inventory and layout polish. */
public final class R102NpcProfileNativeWindowTest {
    private R102NpcProfileNativeWindowTest() { }

    public static void main(String[] args) throws Exception {
        nativeWindowsOwnEveryInteractiveGrid();
        sdkTransactionsRemainAtomicAndFiltered();
        previewAndLayoutRemainTruthful();
        System.out.println("R102 NPC profile native-window tests passed.");
    }

    private static void nativeWindowsOwnEveryInteractiveGrid() throws Exception {
        String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String command = source("src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java");
        String repository = source("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java");
        String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        String grid = source("src/main/resources/Common/UI/Custom/Pages/NativeInventoryProbe/GridCommon.ui");

        assert command.contains("openCustomPageWithWindows");
        assert repository.contains("new ContainerWindow(armor)");
        assert repository.contains("new ContainerWindow(loadout)");
        assert repository.contains("new ContainerWindow(inventory)");
        assert repository.contains("return new ContainerWindow[] { armorWindow, loadoutWindow, inventoryWindow }");
        assert page.contains("#ArmorGrid.InventorySectionId\", inventory.armorSectionId()")
                : "ArmorGrid must bind to the armor ContainerWindow ID";
        assert page.contains("#LoadoutGrid.InventorySectionId\", inventory.loadoutSectionId()")
                : "LoadoutGrid must bind to the loadout ContainerWindow ID";
        assert page.contains("boundNpcGridDocument(storageWindow.getId())")
                : "NPC grid must be constructed with the storage ContainerWindow ID";
        assert page.contains("InventoryComponent.STORAGE_SECTION_ID")
                : "Player grid must bind to the ECS Storage section";
        assert InventoryComponent.STORAGE_SECTION_ID < 0;
        for (String equipmentGrid : new String[] {"ArmorGrid", "LoadoutGrid"}) {
            assert ui.contains("ItemGrid #" + equipmentGrid);
        }
        assert ui.contains("Group #NpcGridHost") && ui.contains("Group #PlayerGridHost");
        assert count(ui, "AreItemsDraggable: true;") == 2;
        assert grid.contains("AreItemsDraggable: true;");
        assert page.contains("#NpcInventoryGrid.Slots");
        assert page.contains("#PlayerInventoryGrid.Slots");
        assert page.contains("#ArmorGrid.Slots");
        assert page.contains("#LoadoutGrid.Slots");
        assert page.contains("CustomInventoryBridgeUi.setNativeSlots")
                : "R118 snapshots are presentation-only and must use the shared encoder";
        assert !command.contains("page.bindNativeStorageAfterWindowsOpen()")
                : "R107 proved post-mount rebinding is not the transaction solution";
        assert command.indexOf("openCustomPageWithWindows")
                < command.indexOf("page.applyPreviewAfterPageMount()")
                : "The preview overlay must still follow page/window construction";
    }

    private static void sdkTransactionsRemainAtomicAndFiltered() throws Exception {
        String repository = source("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java");
        Class.forName("com.hypixel.hytale.protocol.packets.inventory.MoveItemStack");
        InventoryUtils.class.getMethod("moveItem", com.hypixel.hytale.component.Ref.class,
                int.class, int.class, int.class, int.class, int.class,
                com.hypixel.hytale.component.ComponentAccessor.class);
        ItemContainer.class.getMethod("moveItemStackFromSlotToSlot",
                short.class, int.class, ItemContainer.class, short.class);
        assert ContainerWindow.class.getInterfaces().length > 0;
        assert repository.contains("ItemContainerUtil.trySetArmorFilters(armor)");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, PRIMARY_SLOT");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, OFFHAND_SLOT");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, AMMUNITION_SLOT");
        assert repository.contains("armor.registerChangeEvent(ignored -> changed())");
        assert repository.contains("loadout.registerChangeEvent(ignored -> changed())");
        assert repository.contains("inventory.registerChangeEvent(ignored -> changed())");
        assert !repository.contains("removeAllItemStacks()");
    }

    private static void previewAndLayoutRemainTruthful() throws Exception {
        String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        assert ui.contains("CharacterPreviewComponent #NpcCharacterPreview");
        assert page.contains("applyPreviewAfterPageMount")
                : "The overlay must start only after the page and native windows mount";
        assert page.contains("preview.refreshEquipment")
                : "Authoritative gear mutations must refresh the visible NPC state";
        assert !ui.contains("#NpcPreviewSkin");
        assert !ui.contains("#NpcPreviewPreset");
        assert !ui.contains("#NpcPreviewGearState");
        assert !page.contains("#NpcPreviewSkin.Text");
        assert !page.contains("#NpcPreviewPreset.Text");
        assert ui.contains("@PanelGap = 12;");
        assert ui.contains("@HalfPanelGap = 6;");
        assert ui.contains("@InternalPadding = 12;");
        assert ui.contains("Anchor: (Width: 1420, Height: 990);");
        assert ui.contains("Anchor: (Height: 330, Bottom: @FooterGap);");
        assert ui.indexOf("#SKINFilename") < ui.indexOf("VOICE SAMPLES");
        assert ui.contains("@GearRowHeight = 68;");
        assert ui.contains("@GearRowGap = 26;");
        assert ui.contains("@GearRowsHeight = 350;");
        assert ui.contains("Group #ArmorSlotColumn");
        assert ui.contains("Group #LoadoutSlotColumn");
        assert ui.contains("Group #NpcPreviewContainer");
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int count(String source, String needle) {
        int result = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) result++;
        return result;
    }
}
