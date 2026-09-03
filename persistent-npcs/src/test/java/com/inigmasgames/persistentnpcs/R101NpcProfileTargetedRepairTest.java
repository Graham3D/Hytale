package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** R101 regressions for authoritative profile-grid serialization and transfer wiring. */
public final class R101NpcProfileTargetedRepairTest {
    private R101NpcProfileTargetedRepairTest() { }

    public static void main(String[] args) throws Exception {
        System.out.println("R101 stage=empty-wire");
        emptySlotsEncodeAsAbsentItems();
        System.out.println("R101 stage=occupied-wire");
        occupiedSlotsUseTheSupportedStackPath();
        System.out.println("R101 stage=native-transfer");
        nativeCrossContainerTransferIsWired();
        System.out.println("R101 stage=armor-artwork");
        nativeArmorArtworkIsVisibleOnlyWhenEmpty();
        System.out.println("R101 stage=preview-api");
        arbitraryNpcPreviewRemainsTruthful();
        System.out.println("R101 NPC profile targeted repair tests passed.");
    }

    private static void emptySlotsEncodeAsAbsentItems() throws Exception {
        ItemGridSlot[] slots = NpcProfilePage.itemGridSlots(new SimpleItemContainer((short) 40));
        Field itemStack = ItemGridSlot.class.getDeclaredField("itemStack");
        itemStack.setAccessible(true);
        for (ItemGridSlot slot : slots) {
            assert itemStack.get(slot) == null
                    : "Empty cells must omit ItemStack instead of encoding ItemStack.EMPTY";
            assert !ItemGridSlot.CODEC.encode(slot).asDocument().containsKey("ItemStack")
                    : "The actual wire document for an empty cell must omit ItemStack";
        }
        assert itemStack.get(new ItemGridSlot(ItemStack.EMPTY)) == ItemStack.EMPTY
                : "The former representation must remain distinguishable in this regression";
    }

    private static void occupiedSlotsUseTheSupportedStackPath() throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("? new ItemGridSlot()")
                : "The production snapshot must use the no-item wire representation";
        assert page.contains(": new ItemGridSlot(stack)")
                : "Valid items must still use ItemGridSlot's supported stack constructor";
    }

    private static void nativeCrossContainerTransferIsWired() throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String command = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java"));
        String repository = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java"));
        String grid = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/NativeInventoryProbe/GridCommon.ui"));

        assert page.contains("boundNpcGridDocument(storageWindow.getId())");
        assert page.contains("inventory.inventorySectionId()")
                : "NPC transfers must resolve through its opened ContainerWindow";
        assert page.contains("Pages/NativeInventoryProbe/PlayerStorage.ui");
        assert page.contains("InventoryComponent.STORAGE_SECTION_ID");
        assert InventoryComponent.STORAGE_SECTION_ID < 0
                : "Native player inventory sections are ECS section identifiers";
        assert count(ui, "AreItemsDraggable: true;") == 2;
        assert count(ui, "AllowMaxStackDraggableItems: true;") == 2;
        assert grid.contains("AreItemsDraggable: true;")
                && grid.contains("AllowMaxStackDraggableItems: true;");
        assert page.contains("#NpcInventoryGrid.Slots")
                : "NPC storage needs the connected-proven R118 presentation snapshot";
        assert page.contains("#PlayerInventoryGrid.Slots")
                : "Player storage needs the connected-proven R118 presentation snapshot";
        assert command.contains("openCustomPageWithWindows")
                : "The NPC ContainerWindow must be registered before native drag packets arrive";
        assert repository.contains("registerChangeEvent")
                : "Authoritative container changes must trigger persistence and UI refresh";

        Class<?> packet = Class.forName(
                "com.hypixel.hytale.protocol.packets.inventory.MoveItemStack");
        packet.getField("fromSectionId");
        packet.getField("fromSlotId");
        packet.getField("quantity");
        packet.getField("toSectionId");
        packet.getField("toSlotId");
        ItemContainer.class.getMethod("moveItemStackFromSlotToSlot",
                short.class, int.class, ItemContainer.class, short.class);
    }

    private static void nativeArmorArtworkIsVisibleOnlyWhenEmpty() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert ui.contains("ItemGrid #ArmorGrid");
        assert !ui.contains("ItemSlotButton #HeadSlotButton");
        for (String region : new String[] {"Head", "Chest", "Hands", "Legs"}) {
            int grid = ui.indexOf("ItemGrid #ArmorGrid");
            int icon = ui.indexOf("Group #" + region + "EmptyIcon", grid);
            assert grid >= 0 && icon > grid
                    : "Empty armor artwork must render above the native armor grid for " + region;
        }
        assert page.contains("armorEmptyIconId(slot) + \".Visible\", !equipped")
                : "Empty artwork must disappear when real armor is equipped";
        assert page.contains("prefix + \".Visible\", equipped")
                : "Armor visibility controls must remain hidden for empty slots";
    }

    private static void arbitraryNpcPreviewRemainsTruthful() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java"));
        assert ui.contains("CharacterPreviewComponent #NpcCharacterPreview")
                : "The connected-validated viewer overlay must target the native preview";
        assert page.contains("#NpcCharacterPreview.Visible\", preview != null")
                : "The native preview must not expose the viewer as a create-mode fallback";
        assert command.contains("NpcMeshPreviewSession.begin")
                : "An update must populate the preview through the validated packet session";
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }
}
