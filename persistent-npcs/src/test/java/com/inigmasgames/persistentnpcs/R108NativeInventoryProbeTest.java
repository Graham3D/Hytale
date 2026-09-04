package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.persistentnpcs.ui.NativeInventoryProbePage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonArray;
import org.bson.BsonDocument;

/** Static boundary gate for the isolated R108 native inventory experiment. */
public final class R108NativeInventoryProbeTest {
    private R108NativeInventoryProbeTest() { }

    public static void main(String[] args) throws Exception {
        String probeUi = read("src/main/resources/Common/UI/Custom/Pages/NativeInventoryProbe.ui");
        String probePage = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeInventoryProbePage.java");
        String probeCommand = read("src/main/java/com/inigmasgames/persistentnpcs/command/NativeInventoryProbeCommand.java");
        String profileUi = read("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        String profilePage = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");

        assert count(probeUi, "ItemGrid #") == 0
                : "The base document must not construct unbound ItemGrids";
        assert probeUi.contains("Group #NpcGridHost")
                && probeUi.contains("Group #PlayerGridHost");
        assert !probeUi.contains("Voice") && !probeUi.contains("CharacterPreviewComponent")
                && !probeUi.contains("ArmorGrid") && !probeUi.contains("ProfileFilename")
                : "Probe must stay isolated from production NPC Profile features";
        assert probePage.contains("#NpcInventoryGrid.Slots")
                && probePage.contains("#PlayerInventoryGrid.Slots")
                && probePage.contains("initialSlots(")
                : "Probe 4 must materialize the native grids with real slot descriptors";
        assert probePage.contains("transferAuthority=NATIVE_INVENTORY_SECTION")
                && probePage.contains("updateAuthority=OPEN_AND_UPDATE_WINDOW")
                : "Slot presentation must not replace native transaction authority";
        assert probePage.contains("setNativeSlots(commands")
                && probePage.contains("\"InventorySlotIndex\", new BsonInt32(slot)")
                && probePage.contains("slots[slot].setActivatable(true)")
                : "Probe 5 must encode client-native slot identity and activation";
        assert probePage.contains("#NpcInventoryGrid.InventorySectionId")
                && probePage.contains("InventoryComponent.STORAGE_SECTION_ID");
        assert !probePage.contains("appendInline(")
                : "The connected probe must not send runtime-parsed UI source";
        assert probePage.contains("commands.append(\"#NpcGridHost\", npcGridDocument)")
                && probePage.contains("Pages/NativeInventoryProbe/PlayerStorage.ui")
                && probePage.contains("sectionId < 1 || sectionId > 8")
                : "Probe 3 must select bounded packaged documents safely";
        for (int section = 1; section <= 8; section++) {
            String boundGrid = read("src/main/resources/Common/UI/Custom/Pages/"
                    + "NativeInventoryProbe/NpcSection" + section + ".ui");
            assert boundGrid.contains("InventorySectionId: " + section + ";")
                    : "NPC grid document must contain literal section " + section;
            assert boundGrid.contains("#NpcInventoryGrid");
        }
        String playerGrid = read("src/main/resources/Common/UI/Custom/Pages/"
                + "NativeInventoryProbe/PlayerStorage.ui");
        assert playerGrid.contains("InventorySectionId: -2;")
                && playerGrid.contains("#PlayerInventoryGrid");
        assert !probePage.contains("npcWindow.close(ref, store)")
                && probePage.contains("awaitingClientWindowClose=true")
                : "Page dismissal must not race the client's normal CloseWindow packet";
        assert probePage.contains("moveAllItemStacksTo(playerStorage)")
                && probePage.contains("SimpleItemContainer.addOrDropItemStack(")
                && probePage.contains("npcContainerEmpty=")
                : "Teardown must prevent loss when the ephemeral container closes";
        assert !probeCommand.contains("setId(")
                : "Probe must never invent a window ID";
        assert probeCommand.contains("windows.openWindows(ref, store, npcWindow)")
                : "Supported WindowManager allocation is required";
        assert probeCommand.contains("openLiteralAtConstruction")
                && probeCommand.contains("gridBinding=LITERAL_AT_ELEMENT_CREATION")
                && probeCommand.contains("wireOrder=CUSTOM_PAGE_THEN_OPEN_WINDOW")
                : "Probe 3 must use literal creation with lifecycle-safe packet order";
        assert probeCommand.contains("openMaterializedNativeGrid")
                && probeCommand.contains("gridBinding=LITERAL_SECTION_WITH_INITIAL_SLOTS")
                : "Probe 4 must combine initial slot materialization with native sections";
        assert probeCommand.contains("openIndexedNativeGrid")
                && probeCommand.contains(
                        "gridBinding=LITERAL_SECTION_WITH_INDEXED_ACTIVATABLE_SLOTS")
                : "Probe 5 must add native slot identity without a transfer handler";
        assert probeCommand.contains("openWindowThenIndexedPage")
                && probeCommand.indexOf("sendOpenWindowPackets(playerRef, packets, 6)")
                        < probeCommand.indexOf(
                                "player.getPageManager().openCustomPage(ref, store, page)",
                                probeCommand.indexOf(
                                        "private void openWindowThenIndexedPage"))
                && probeCommand.contains("wireOrder=OPEN_WINDOW_THEN_CUSTOM_PAGE")
                : "Probe 6 must put the registered ContainerWindow on the wire first";
        assert probePage.contains("setNativeItemStacks(commands")
                && probePage.contains("#NpcInventoryGrid.ItemStacks")
                && probePage.contains("#PlayerInventoryGrid.ItemStacks")
                && probePage.contains("encodedStacks.set(slot, BsonNull.VALUE)")
                : "Probe 7 must construct native ItemStacks arrays with positional empty cells";
        assert probeCommand.contains("openNativeItemStacksGrid")
                && probeCommand.contains("gridBinding=LITERAL_SECTION_WITH_NATIVE_ITEM_STACKS")
                && probeCommand.contains("emptyEncoding=BSON_NULL slotIdentity=ARRAY_INDEX")
                : "Probe 7 must preserve native array-index slot identity at first construction";
        assert probeCommand.contains("customHandler=false")
                && !probeCommand.contains("InventoryUtils.moveItem(")
                : "Ordinary movement must stay on Hytale's native packet path";

        UICommandBuilder nativeSlotCommand = new UICommandBuilder();
        ItemGridSlot[] nativeSlots = {new ItemGridSlot(), new ItemGridSlot()};
        for (ItemGridSlot slot : nativeSlots) slot.setActivatable(true);
        Method setNativeSlots = NativeInventoryProbePage.class.getDeclaredMethod(
                "setNativeSlots", UICommandBuilder.class, String.class,
                ItemGridSlot[].class);
        setNativeSlots.setAccessible(true);
        setNativeSlots.invoke(null, nativeSlotCommand, "#Grid.Slots", nativeSlots);
        BsonArray encoded = BsonDocument.parse(
                nativeSlotCommand.getCommands()[0].data).getArray("0");
        assert encoded.size() == 2;
        for (int slot = 0; slot < encoded.size(); slot++) {
            BsonDocument descriptor = encoded.get(slot).asDocument();
            assert descriptor.getInt32("InventorySlotIndex").getValue() == slot
                    : "Every rendered cell must retain its authoritative native slot index";
            assert descriptor.getBoolean("IsActivatable").getValue()
                    : "Native slot descriptors must remain activatable";
        }

        for (String preserved : new String[] {"CharacterPreviewComponent #NpcCharacterPreview",
                "ItemGrid #ArmorGrid", "ItemGrid #PrimaryWeaponGrid",
                "ItemGrid #OffhandGrid", "ItemGrid #AmmunitionGrid", "VOICE SAMPLES",
                "Group #NpcGridHost", "Group #PlayerGridHost"}) {
            assert profileUi.contains(preserved) : "Production UI lost: " + preserved;
        }
        assert profilePage.contains("inventory.flush()")
                && profilePage.contains("setVoiceSampleUi(commands)")
                && profilePage.contains("applyPreviewAfterPageMount")
                : "Production persistence/voice/preview paths must remain present";

        System.out.println("R108 isolated native inventory probe tests passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int count(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
