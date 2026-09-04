package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deterministic regression for the native /npc create inventory/profile editor. */
public final class R092NpcCreateInventoryUiTest {
    private R092NpcCreateInventoryUiTest() { }

    public static void main(String[] args) throws Exception {
        System.out.println("R092 stage=ui");
        nativeUiCompositionAndSelectorsAreAuthoritative();
        System.out.println("R092 stage=persistence");
        inventoryStateRoundTripsLosslessly();
        System.out.println("R092 stage=transactions");
        nativeTransactionsRemainSdkAuthoritative();
        authoritativeInventoryCoversEmptyPartialFullAndDamagedStates();
        System.out.println("R092 stage=equipment-grid-snapshots");
        fixedGridSnapshotsPreserveCapacityAndIsolation();
        System.out.println("R092 stage=validation");
        malformedSlotsAreRejected();
        System.out.println("R092 NPC create native inventory/profile UI tests passed.");
    }

    private static void nativeUiCompositionAndSelectorsAreAuthoritative() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java"));
        String storageGrid = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/NativeInventoryProbe/GridCommon.ui"));
        assert ui.contains("CharacterPreviewComponent #NpcCharacterPreview")
                : "The connected-validated client-local preview target must remain present";
        assert ui.contains("Group #NpcAppearancePanel");
        assert ui.contains("#NpcPreviewName");
        assert !ui.contains("#NpcPreviewSkin");
        assert !ui.contains("#NpcPreviewPreset");
        assert ui.contains("ItemGrid #ArmorGrid")
                : "Armor must use the native window-backed ItemGrid drag target";
        for (String grid : List.of("PrimaryWeaponGrid", "OffhandGrid", "AmmunitionGrid")) {
            assert ui.contains("ItemGrid #" + grid)
                    : grid + " must use an exact native equipment section/slot";
        }
        assert ui.contains("Group #NpcGridHost");
        assert ui.contains("Group #PlayerGridHost")
                : "The profile page must expose the player's authoritative inventory beside the NPC inventory";
        assert command.contains("InventoryComponent.Storage.getComponentType()")
                : "Player inventory must come from the viewing player's Storage ECS component";
        assert command.contains("storage.getInventory()")
                : "The real player storage ItemContainer must be supplied to the page";
        assert page.contains("#PlayerInventoryGrid.Slots")
                : "Player storage must receive the proven R118 presentation snapshot";
        assert page.contains("#NpcInventoryGrid.Slots")
                : "NPC storage must receive the proven R118 presentation snapshot";
        assert page.contains("Pages/NativeInventoryProbe/PlayerStorage.ui")
                : "Player storage needs its section-bound construction document";
        assert page.contains("InventoryComponent.STORAGE_SECTION_ID")
                : "Player transfers must target the viewing player's Storage component";
        assert page.contains("boundNpcGridDocument(storageWindow.getId())")
                : "NPC storage needs its opened ContainerWindow ID during construction";
        assert page.contains("profileDirectoryForBrowsing(npcName)")
                : "File selection must begin in the selected NPC's profile directory";
        assert !page.contains("System.getProperty(\"user.home\")")
                : "The NPC profile browser must not default to the operator home directory";
        assert page.contains("commands.set(\"#ProfilePage.Visible\", true)")
                : "Browser Cancel must return directly to the existing profile page";
        assert page.contains("commands.set(\"#BrowserPage.Visible\", false)");
        assert page.contains("new KeyedCodec<>(\"BrowserCancel\", Codec.STRING)")
                : "Constant EventData values arrive as JSON strings in the installed SDK";
        assert !page.contains("new KeyedCodec<>(\"BrowserCancel\", Codec.BOOLEAN)")
                : "A boolean codec rejects the native event payload and leaves Loading visible";
        assert storageGrid.contains("SlotsPerRow: 10;");
        assert storageGrid.contains("RenderEmptySlots: true;")
                : "The authoritative 40-slot inventory must remain visible while empty";
        assert storageGrid.contains("@ProbeGridStyle = ItemGridStyle(");
        assert storageGrid.contains("Style: @ProbeGridStyle;");
        assert storageGrid.contains("ImmersiveNpcInventory/Slot.png");
        assert ui.contains("$C.@DefaultTextTooltipStyle");
        assert !ui.contains("$C.@ButtonTextTooltipStyle")
                : "Custom UI pages may only reference symbols exported by Custom/Common.ui";
        assert storageGrid.contains("AreItemsDraggable: true;")
                : "All authoritative grids must use the installed native drag flow";
        assert storageGrid.contains("AllowMaxStackDraggableItems: true;");
        assert ui.contains("Group #AdvancedFileControls")
                : "Profile and skin file controls must remain available as secondary controls";
        assert ui.contains("#PROFILEFilename") && ui.contains("#SKINFilename")
                : "The Authoring Studio must retain both authoritative file identities";
        for (String slot : List.of("Head", "Chest", "Hands", "Legs")) {
            assert ui.contains("#" + slot + "EmptyIcon")
                    : "Missing native empty equipment artwork for " + slot;
        }
        assert ui.contains("ItemGrid #ArmorGrid")
                : "NPC armor must be a real native drag target";
        assert ui.contains("ItemGrid #PrimaryWeaponGrid")
                && ui.contains("ItemGrid #OffhandGrid")
                && ui.contains("ItemGrid #AmmunitionGrid")
                : "NPC loadout endpoints must be distinct native drag targets";
        for (String armor : List.of("Helmet", "Cuirass", "Gauntlets", "Pants")) {
            assert ui.contains("#Toggle" + armor + "VisibilityButton");
            assert ui.contains("#" + armor + "ArmorVisible");
            assert ui.contains("#" + armor + "ArmorHidden");
        }
        for (String region : List.of("Head", "Chest", "Hands", "Legs")) {
            assert ui.contains("#" + region + "EmptyIcon")
                    : "Missing native empty armor artwork for " + region;
            assert ui.contains("ArmorSlotIcon" + region + ".png")
                    : "Missing installed Player Gear texture for " + region;
        }
        assert page.contains("prefix + \".Visible\", equipped")
                : "Armor visibility eyes must only appear for equipped armor";
        assert ui.contains("#InfiniteAmmoCheckBox");
        assert ui.contains("TooltipText: \"Toggle for infinite ammunition.\"");
        assert page.contains(".rootSelectorId(null)")
                : "Disabled file-browser root selector must not retain the SDK default selector";
        assert !page.contains("\"#RootSelector")
                : "No command may target an element absent from the authoritative UI tree";
        assert command.contains("openCustomPageWithWindows");
        for (String id : List.of("PROFILE", "SKIN")) {
            assert ui.contains("#" + id + "Open") : "Missing Open button for " + id;
            assert ui.contains("#" + id + "Filename") : "Missing filename label for " + id;
        }
        for (String type : List.of("REFERENCE", "AFFECTIONATE", "AMUSED", "EXCITED",
                "ANGRY", "SAD", "SCARED")) {
            assert ui.contains("@VoiceSummaryRow #Voice" + type);
            assert !ui.contains("#" + type + "Open")
                    : "Voice samples must be discovered rather than manually assigned";
        }
        assert ui.contains("@VoiceSummaryRow = Group")
                : "Voice statuses must use the reusable compact summary row";
        assert page.contains("#VoiceFilename.Text");
        assert page.contains("#VoiceState.Text");
        assert ui.contains("#VoiceRescanButton");
        assert NpcInventoryState.ARMOR_CAPACITY == 4;
        assert NpcInventoryState.LOADOUT_CAPACITY == 3;
        assert NpcInventoryState.INVENTORY_CAPACITY == 40;
        assert page.contains("ArmorVisibility");
        assert page.contains("CustomInventoryTransactionBridge")
                : "Update mode must route untrusted drop intent through the shared bridge";
        assert page.contains("#ArmorGrid.InventorySectionId");
        assert page.contains("#PrimaryWeaponGrid.InventorySectionId");
        assert page.contains("#OffhandGrid.InventorySectionId");
        assert page.contains("#AmmunitionGrid.InventorySectionId");
        assert page.contains("#NpcCharacterPreview.Visible\", preview != null")
                : "Create mode must hide the viewer-backed preview until an NPC exists";
        assert command.contains("if (update)")
                : "Only an existing NPC may start a client-local preview session";
    }

    private static void fixedGridSnapshotsPreserveCapacityAndIsolation() {
        SimpleItemContainer npc = new SimpleItemContainer((short) 40);
        SimpleItemContainer player = new SimpleItemContainer((short) 36);
        ItemGridSlot[] npcSlots = NpcProfilePage.itemGridSlots(npc);
        ItemGridSlot[] playerSlots = NpcProfilePage.itemGridSlots(player);
        assert npcSlots.length == 40 : "Empty NPC cells must remain physically represented";
        assert playerSlots.length == 36 : "Player storage capacity must remain authoritative";
        assert npcSlots != playerSlots : "Each authoritative container needs an isolated snapshot";
        assert ItemStack.isEmpty(npc.getItemStack((short) 7));
        assert ItemStack.isEmpty(player.getItemStack((short) 7));
    }

    private static void inventoryStateRoundTripsLosslessly() throws Exception {
        Path data = Files.createTempDirectory("immersive-npc-r092-state");
        ProfileRepository profiles = new ProfileRepository(data);
        profiles.createProfileDirectory("Rowan");
        UUID identity = UUID.randomUUID();
        String metadata = "{\"custom\":\"kept\",\"rank\":7}";
        NpcInventoryState.PersistedItemStack persisted =
                new NpcInventoryState.PersistedItemStack((short) 39, "Test_Trade_Good",
                        17, 8.5, 12.0, 3, metadata, true);
        NpcInventoryState state = new NpcInventoryState(1, identity, List.of(), List.of(),
                List.of(persisted), false);
        try (NpcInventoryRepository repository = new NpcInventoryRepository(profiles)) {
            repository.save("Rowan", state);
            NpcInventoryState restored = repository.load("Rowan");
            assert restored.stableNpcId().equals(identity);
            assert restored.inventory().size() == 1;
            NpcInventoryState.PersistedItemStack roundTrip = restored.inventory().getFirst();
            assert roundTrip.itemId().equals("Test_Trade_Good");
            assert roundTrip.quantity() == 17;
            assert roundTrip.durability() == 8.5;
            assert roundTrip.maxDurability() == 12.0;
            assert roundTrip.qualityIndex() == 3;
            assert roundTrip.overrideDroppedItemAnimation();
            assert roundTrip.metadataJson().contains("custom");
            assert repository.path("Rowan").getParent().equals(profiles.profileDirectory("Rowan"));

            NpcInventoryState visibility = new NpcInventoryState(
                    NpcInventoryState.CURRENT_SCHEMA_VERSION, identity,
                    List.of(), List.of(), List.of(), false,
                    true, false, true, false);
            repository.save("Rowan", visibility);
            NpcInventoryState visibleRoundTrip = repository.load("Rowan");
            assert visibleRoundTrip.hideHelmet();
            assert !visibleRoundTrip.hideCuirass();
            assert visibleRoundTrip.hideGauntlets();
            assert !visibleRoundTrip.hidePants();
        }
    }

    private static void nativeTransactionsRemainSdkAuthoritative() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java"));
        assert repository.contains("new ContainerWindow(armor)");
        assert repository.contains("new ContainerWindow(hotbar)");
        assert repository.contains("new ContainerWindow(utility)");
        assert repository.contains("new ContainerWindow(inventory)");
        assert repository.contains("ItemContainerUtil.trySetArmorFilters(armor)");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, (short) 0, primary)");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, (short) 0, offhand)");
        assert repository.contains("setSlotFilter(FilterActionType.ADD, (short) 1, ammunition)");
        assert repository.contains("inventory.swapItems")
                : "Equipping must use the SDK ItemContainer transaction path";
        assert repository.contains("target.moveItemStackFromSlot(physicalTarget, inventory)")
                : "Unequipping must return items to the authoritative NPC inventory";
        assert repository.contains("restoreOne(hotbar, (short) 1")
                : "Preferred ammunition must occupy native hotbar slot 1 ahead of storage ammo";
        assert repository.contains("new InventoryComponent.Utility(utility, (byte) 0)")
                : "Shield/offhand must use Hytale's active Utility slot";
        assert repository.contains("PlayerSettings.getComponentType()")
                : "Armor visibility must use the native ECS PlayerSettings component";
        assert repository.contains("storage.registerChangeEvent")
                : "Native pickups must persist from the live NPC storage container";
        String adapter = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/hytale/HytaleNpcAdapter.java"));
        assert adapter.contains("applyToSpawnedNpc(currentProfile.name(), store, selected.ref())")
                : "Updating a live NPC must rebind the authored inventory in place";
        assert !repository.contains("removeAllItemStacks()")
                : "UI persistence must never empty a native container during close/save";
        assert !repository.contains("dropAllItemStacks()")
                : "Full-container rejection must never become an item drop";
    }

    private static void authoritativeInventoryCoversEmptyPartialFullAndDamagedStates()
            throws Exception {
        Path data = Files.createTempDirectory("immersive-npc-r092-capacity");
        ProfileRepository profiles = new ProfileRepository(data);
        profiles.createProfileDirectory("InventoryNpc");
        NpcInventoryState empty = NpcInventoryState.empty();
        assert empty.inventory().isEmpty();
        assert NpcInventoryState.INVENTORY_CAPACITY == 40
                : "An empty NPC inventory must still retain its 40-slot UI capacity";

        List<NpcInventoryState.PersistedItemStack> items = new ArrayList<>();
        for (short slot = 0; slot < 40; slot++) {
            items.add(new NpcInventoryState.PersistedItemStack(slot, "Test_Trade_Good",
                    slot == 0 ? 17 : slot + 1,
                    slot == 0 ? 8.5 : 0.0,
                    slot == 0 ? 12.0 : 0.0,
                    slot == 0 ? 3 : 0,
                    slot == 0 ? "{\"custom\":\"kept\"}" : "{}", false));
        }
        NpcInventoryState full = new NpcInventoryState(
                NpcInventoryState.CURRENT_SCHEMA_VERSION, UUID.randomUUID(),
                List.of(), List.of(), items, false,
                false, false, false, false);
        try (NpcInventoryRepository repository = new NpcInventoryRepository(profiles)) {
            repository.save("InventoryNpc", full);
            NpcInventoryState reopened = repository.load("InventoryNpc");
            assert reopened.inventory().size() == 40
                    : "All authoritative NPC inventory slots must persist";
            NpcInventoryState.PersistedItemStack first = reopened.inventory().getFirst();
            assert first.quantity() == 17 : "Stack quantities must persist";
            assert first.durability() == 8.5 : "Damaged-item durability must persist";
            assert first.maxDurability() == 12.0;
            assert first.qualityIndex() == 3 : "Native quality backgrounds need quality metadata";

            NpcInventoryState partial = new NpcInventoryState(
                    NpcInventoryState.CURRENT_SCHEMA_VERSION, reopened.stableNpcId(),
                    List.of(), List.of(), reopened.inventory().subList(0, 39), false,
                    false, false, false, false);
            repository.save("InventoryNpc", partial);
            assert repository.load("InventoryNpc").inventory().size() == 39
                    : "Authoritative removals must persist without changing capacity";
        }
    }

    private static void malformedSlotsAreRejected() {
        boolean threw = false;
        try {
            new NpcInventoryState(1, null, List.of(), List.of(),
                    List.of(new NpcInventoryState.PersistedItemStack((short) 40,
                            "Out_Of_Range", 1, 0, 0, 0, "{}", false)), false);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assert threw;
    }

}
