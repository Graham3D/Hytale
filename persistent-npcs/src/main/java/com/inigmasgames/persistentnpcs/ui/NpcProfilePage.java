package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.EquipmentUpdate;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserConfig;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserEventData;
import com.hypixel.hytale.server.core.ui.browser.ServerFileBrowser;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService.ProfileFileField;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceSampleType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Update 6 native NPC Profile screen composed with the server-side file browser. */
public final class NpcProfilePage extends InteractiveCustomUIPage<NpcProfilePage.PageData> {
    /**
     * ItemGrid must receive a literal positive window section in its construction
     * document. WindowManager allocates monotonically increasing positive IDs for
     * the lifetime of a connection, so the original probe-only 1..8 bundle was not
     * sufficient for repeated Profile opens.
     */
    private static final int MAX_PACKAGED_NPC_SECTION_ID = 1024;
    private static final AtomicLong INVENTORY_GENERATIONS = new AtomicLong();

    private final String npcName;
    private final boolean update;
    private final NpcProfileEditorService editor;
    private final Consumer<NpcProfile> committed;
    private final NpcInventoryRepository.Session inventory;
    private final ItemContainer playerInventory;
    private final NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority;
    private final ContainerWindow storageWindow;
    private final UUID inventorySessionId = UUID.randomUUID();
    private final long inventoryPageGeneration = INVENTORY_GENERATIONS.incrementAndGet();
    private final AtomicLong inventoryEventSequence = new AtomicLong();
    private final AtomicLong inventoryRefreshGeneration = new AtomicLong();
    private final CustomInventoryTransactionBridge inventoryBridge;
    private final Consumer<String> diagnostics;
    private final NpcMeshPreviewSession preview;
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> deleted;
    private final EnumMap<ProfileFileField, Path> selections =
            new EnumMap<>(ProfileFileField.class);
    private ProfileFileField activeField;
    private ServerFileBrowser browser;
    private String status = "";
    private boolean error;
    private VoicePresetRepository.VoiceSampleScan voiceSamples;
    private boolean built;

    public NpcProfilePage(
            PlayerRef playerRef,
            String npcName,
            boolean update,
            NpcProfileEditorService editor,
            ItemContainer playerInventory,
            NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority,
            NpcMeshPreviewSession preview,
            Consumer<NpcProfile> committed,
            BiConsumer<Ref<EntityStore>, Store<EntityStore>> deleted,
            Consumer<String> diagnostics) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.npcName = npcName;
        this.update = update;
        this.editor = editor;
        if (playerInventory == null) {
            throw new IllegalArgumentException("Player storage inventory is required.");
        }
        this.playerInventory = playerInventory;
        this.liveStorageAuthority = liveStorageAuthority;
        this.preview = preview;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.committed = committed == null ? ignored -> { } : committed;
        this.deleted = deleted == null
                ? (ignoredRef, ignoredStore) -> {
                    throw new IllegalStateException("NPC deletion is unavailable.");
                } : deleted;
        this.inventory = liveStorageAuthority == null
                ? editor.openInventory(npcName)
                : editor.inventories().openWithLiveStorage(
                        npcName, liveStorageAuthority.storage());
        this.inventory.onChanged(this::onInventoryChanged);
        this.storageWindow = inventory.windows()[2];
        this.inventoryBridge = !update ? null
                : new CustomInventoryTransactionBridge(
                        inventorySessionId, inventoryPageGeneration, playerRef,
                        this, storageWindow, inventory.inventory(), playerInventory,
                        liveStorageAuthority == null
                                ? (ignoredRef, ignoredStore) -> null
                                : liveStorageAuthority::invalidReason,
                        this.diagnostics);
        this.voiceSamples = editor.rescanVoiceSamples(npcName);
    }

    public ContainerWindow[] windows() {
        return inventory.windows();
    }

    /** Connected-validation evidence after WindowManager has assigned window IDs. */
    public String nativeInventoryDiagnostics() {
        return "npcInventoryWindowId=" + inventory.inventorySectionId()
                + " npcInventorySectionId=" + inventory.inventorySectionId()
                + " npcInventoryCapacity=" + inventory.inventory().getCapacity()
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " playerStorageCapacity=" + playerInventory.getCapacity()
                + " authority=" + inventoryAuthority()
                + " profileId=" + profileId()
                + " npcEntityId=" + (liveStorageAuthority == null
                        ? "NOT_SPAWNED" : liveStorageAuthority.npcEntityId())
                + " inventorySessionId=" + inventorySessionId
                + " inventoryPageGeneration=" + inventoryPageGeneration;
    }

    public int nativeInventorySectionId() {
        return inventory.inventorySectionId();
    }

    public void applyPreviewAfterPageMount() {
        if (preview != null) preview.applyAfterPageMount();
    }

    /**
     * PageManager emits the CustomPage before its affiliated OpenWindow packets.
     * Re-assigning the two storage section IDs after that method returns makes the
     * client resolve the now-present sections and materialize native draggable cells.
     */
    public void bindNativeStorageAfterWindowsOpen() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#NpcInventoryGrid.InventorySectionId", inventory.inventorySectionId());
        commands.set("#PlayerInventoryGrid.InventorySectionId",
                InventoryComponent.STORAGE_SECTION_ID);
        sendUpdate(commands, false);
    }

    @Override
    public void build(
            Ref<EntityStore> ref,
            UICommandBuilder commands,
            UIEventBuilder events,
            Store<EntityStore> store) {
        commands.append("Pages/ImmersiveNpcProfile.ui");
        // R120: ItemGrid rejects .Slots if its inventory section was not present
        // during construction. Reuse the connected-proven R118 child documents so
        // each grid is born section-bound before any slot snapshot is sent.
        commands.append("#NpcGridHost",
                boundNpcGridDocument(storageWindow.getId()));
        commands.append("#PlayerGridHost",
                "Pages/NativeInventoryProbe/PlayerStorage.ui");
        commands.set("#ProfileTitle.Text", npcName + "'s Profile");
        commands.set("#ProfilePanelTitle.Text", npcName + "'s Profile");
        commands.set("#NpcInventoryTitle.Text", npcName + "'s Inventory");
        commands.set("#PlayerInventoryTitle.Text", playerRef.getUsername() + "'s Inventory");
        setNpcProfileUi(commands);
        if (inventoryBridge != null) {
            CustomInventoryBridgeUi.bindDrop(events, "#NpcInventoryGrid",
                    storageWindow.getId());
            CustomInventoryBridgeUi.bindDrop(events, "#PlayerInventoryGrid",
                    InventoryComponent.STORAGE_SECTION_ID);
            diagnostics.accept("NPC_PROFILE_INVENTORY_BRIDGE_BUILD"
                    + " timestamp=" + Instant.now()
                    + " npc=" + npcName
                    + " profileId=" + profileId()
                    + " npcEntityId=" + (liveStorageAuthority == null
                            ? "NOT_SPAWNED" : liveStorageAuthority.npcEntityId())
                    + " viewerUuid=" + playerRef.getUuid()
                    + " sessionId=" + inventorySessionId
                    + " pageGeneration=" + inventoryPageGeneration
                    + " npcWindowId=" + storageWindow.getId()
                    + " playerStorageSectionId="
                    + InventoryComponent.STORAGE_SECTION_ID
                    + " supportedOperation=LEFT_CLICK_FULL_STACK_TO_EMPTY_SLOT_CROSS_OR_INTERNAL"
                    + " mutationAuthority=InventoryUtils.moveItem"
                    + " persistenceAuthority=NpcInventoryRepository_RUNTIME_PIPELINE");
        }
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,
                "#InfiniteAmmoCheckBox",
                EventData.of("InfiniteAmmo", "#InfiniteAmmoCheckBox.Value"));
        for (short slot = 0; slot < 4; slot++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#" + armorToggleId(slot),
                    EventData.of("ArmorVisibility", Short.toString(slot)));
        }
        for (ProfileFileField field : ProfileFileField.values()) {
            String id = field.name();
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#" + id + "Open", EventData.of("Open", field.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceRescanButton", EventData.of("RescanVoice", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CancelButton", EventData.of("Cancel", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteButton", EventData.of("Delete", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#EnterButton", EventData.of("Enter", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteNoButton", EventData.of("DeleteNo", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteYesButton", EventData.of("DeleteYes", "true"));
        commands.set("#DeleteButton.Visible", update);
        commands.set("#DeleteConfirmName.Text", "Delete " + npcName + "?");
        commands.set("#StatusText.Text", status);
        commands.set("#StatusText.Visible", !status.isBlank());
        commands.set("#StatusText.Style.TextColor", error ? "#e76f6f" : "#9ed7a6");
        boolean browsing = activeField != null && browser != null;
        commands.set("#ProfilePage.Visible", !browsing);
        commands.set("#BrowserPage.Visible", browsing);
        if (browsing) {
            commands.set("#BrowserTitle.Text", "Select " + activeField.label());
            browser.buildUI(commands, events);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#BrowserCancelButton", EventData.of("BrowserCancel", "true"));
        }
        built = true;
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> ref, Store<EntityStore> store, String rawData) {
        if (rawData != null && rawData.contains(CustomInventoryBridgeUi.DROP_MARKER)) {
            diagnostics.accept("NPC_PROFILE_INVENTORY_RAW_EVENT"
                    + " timestamp=" + Instant.now()
                    + " npc=" + npcName
                    + " sessionId=" + inventorySessionId
                    + " pageGeneration=" + inventoryPageGeneration
                    + " payload=" + quoted(rawData));
        }
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        try {
            if (isInventoryDrop(data)) {
                handleInventoryDrop(ref, store, data);
                return;
            }
            if (action(data.cancel)) {
                closeInventoryBridge();
                closePreview();
                close();
                return;
            }
            if (action(data.enter)) {
                NpcProfile profile = editor.commit(npcName, update, selections);
                inventory.bindStableIdentity(profile.stableId());
                inventory.flush();
                committed.accept(profile);
                closeInventoryBridge();
                closePreview();
                close();
                if (update) {
                    playerRef.sendMessage(Message.raw("NPC " + profile.name()
                            + " has been updated. Type \"/npc spawn " + profile.name()
                            + "\" to add them to your world if they are not already present."));
                } else {
                    playerRef.sendMessage(Message.raw("NPC " + profile.name()
                            + " has been created. Type \"/npc spawn " + profile.name()
                            + "\" to add them to your world. Type \"/npc update "
                            + profile.name() + "\" to open their profile again."));
                }
                return;
            }
            if (action(data.delete)) {
                UICommandBuilder commands = new UICommandBuilder();
                commands.set("#ProfilePage.Visible", false);
                commands.set("#DeleteConfirmPage.Visible", true);
                sendUpdate(commands, false);
                return;
            }
            if (action(data.deleteNo)) {
                UICommandBuilder commands = new UICommandBuilder();
                commands.set("#DeleteConfirmPage.Visible", false);
                commands.set("#ProfilePage.Visible", true);
                sendUpdate(commands, false);
                return;
            }
            if (action(data.deleteYes)) {
                closeInventoryBridge();
                closePreview();
                inventory.close();
                deleted.accept(ref, store);
                close();
                playerRef.sendMessage(Message.raw("NPC " + npcName
                        + " and its authored profile folder were deleted."));
                return;
            }
            if (data.open != null) {
                activeField = ProfileFileField.valueOf(data.open.toUpperCase(Locale.ROOT));
                Path root = editor.profileDirectoryForBrowsing(npcName);
                FileBrowserConfig config = FileBrowserConfig.builder()
                        .listElementId("#BrowserPage #FileList")
                        .searchInputId("#BrowserPage #SearchInput")
                        .currentPathId("#BrowserPage #CurrentPath")
                        .rootSelectorId(null)
                        .allowedExtensions(activeField.extension())
                        .enableRootSelector(false)
                        .enableSearch(true)
                        .enableDirectoryNav(true)
                        .enableMultiSelect(false)
                        .maxResults(100)
                        .build();
                browser = new ServerFileBrowser(config, root, root.getFileSystem().getPath(""));
                status = "";
                rebuild();
                return;
            }
            if (data.infiniteAmmo != null) {
                inventory.setInfiniteAmmunition(data.infiniteAmmo);
                status = inventory.infiniteAmmunition()
                        ? "Infinite ammunition enabled." : "Infinite ammunition disabled.";
                error = false;
                rebuild();
                return;
            }
            if (data.armorVisibility != null) {
                short slot = Short.parseShort(data.armorVisibility);
                inventory.toggleArmorVisibility(slot);
                status = armorLabel(slot) + (inventory.armorHidden(slot)
                        ? " armor hidden; character skin is visible."
                        : " armor visible.");
                error = false;
                rebuild();
                return;
            }
            if (action(data.rescanVoice)) {
                voiceSamples = editor.rescanVoiceSamples(npcName);
                status = voiceSamples.ready()
                        ? "Voice samples rescanned. Reference is ready."
                        : "Voice samples rescanned. Reference is missing or invalid; cloned voice is not ready.";
                error = !voiceSamples.ready();
                rebuild();
                return;
            }
            if (action(data.browserCancel)) {
                activeField = null;
                browser = null;
                UICommandBuilder commands = new UICommandBuilder();
                commands.set("#ProfilePage.Visible", true);
                commands.set("#BrowserPage.Visible", false);
                sendUpdate(commands, false);
                return;
            }
            if (browser != null && data.searchQuery != null) {
                browser.setSearchQuery(data.searchQuery.strip().toLowerCase(Locale.ROOT));
                rebuildBrowser();
                return;
            }
            if (browser != null && activeField != null && data.file != null) {
                if (browser.handleEvent(FileBrowserEventData.file(data.file))) {
                    rebuildBrowser();
                    return;
                }
                Path root = browser.getRoot().toAbsolutePath().normalize();
                Path selected = root.resolve(browser.getCurrentDir()).resolve(data.file)
                        .toAbsolutePath().normalize();
                if (!selected.startsWith(root) || !Files.isRegularFile(selected)) {
                    throw new IllegalArgumentException("Unsafe or missing file selection.");
                }
                selections.put(activeField, selected);
                activeField = null;
                browser = null;
                status = "Selected " + selected.getFileName();
                error = false;
                rebuild();
            }
        } catch (RuntimeException failure) {
            status = failure.getMessage() == null ? "Profile operation failed."
                    : failure.getMessage();
            error = true;
            if (activeField != null) {
                activeField = null;
                browser = null;
            }
            rebuild();
        }
    }

    private void rebuildBrowser() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        browser.buildFileList(commands, events);
        browser.buildCurrentPath(commands);
        sendUpdate(commands, events, false);
    }

    private boolean isInventoryDrop(PageData data) {
        return data != null && inventoryBridge != null
                && CustomInventoryBridgeUi.DROP_MARKER.equals(data.marker)
                && "Dropped".equals(data.event);
    }

    private void handleInventoryDrop(Ref<EntityStore> ref,
            Store<EntityStore> store, PageData data) {
        int sourceSection = value(data.sourceInventorySectionId, Integer.MIN_VALUE);
        int sourceSlot = value(data.sourceSlotId, -1);
        int targetSection = parseSection(data.section);
        int targetSlot = value(data.slotIndex, -1);
        int clientQuantity = value(data.itemStackQuantity, -1);
        int requestedQuantity = authoritativeQuantityAtIntent(
                sourceSection, sourceSlot, clientQuantity);
        var intent = new CustomInventoryTransactionBridge.InventoryMoveIntent(
                inventorySessionId,
                inventoryPageGeneration,
                sourceSection,
                sourceSlot,
                targetSection,
                targetSlot,
                requestedQuantity,
                value(data.pressedMouseButton, -1),
                inventoryEventSequence.incrementAndGet(),
                data.itemStackId,
                clientQuantity);
        inventoryBridge.submit(ref, store, intent,
                this::reconcileInventoryFromAuthority);
    }

    private int authoritativeQuantityAtIntent(int sectionId, int slot,
            int clientQuantityDiagnostic) {
        ItemContainer container;
        if (sectionId == InventoryComponent.STORAGE_SECTION_ID) {
            container = playerInventory;
        } else if (sectionId == storageWindow.getId()) {
            container = inventory.inventory();
        } else {
            return clientQuantityDiagnostic;
        }
        if (slot < 0 || slot >= container.getCapacity()) return clientQuantityDiagnostic;
        ItemStack stack = container.getItemStack((short) slot);
        return ItemStack.isEmpty(stack)
                ? clientQuantityDiagnostic : stack.getQuantity();
    }

    private void reconcileInventoryFromAuthority(
            CustomInventoryTransactionBridge.BridgeResult result) {
        if (!built || inventoryBridge == null) return;
        long refresh = inventoryRefreshGeneration.incrementAndGet();
        UICommandBuilder commands = new UICommandBuilder();
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#NpcInventoryGrid.Slots", inventory.inventory());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerInventory);
        if (result.type() != CustomInventoryTransactionBridge.ResultType.COMMITTED) {
            status = "Inventory move rejected: " + result.reason();
            error = result.type() != CustomInventoryTransactionBridge.ResultType.NO_OP;
            commands.set("#StatusText.Text", status);
            commands.set("#StatusText.Visible", true);
            commands.set("#StatusText.Style.TextColor",
                    error ? "#e76f6f" : "#9ed7a6");
        }
        sendUpdate(commands, false);
        if (result.type() == CustomInventoryTransactionBridge.ResultType.COMMITTED
                && preview != null && preview.targetApplied()) {
            preview.refreshEquipment();
            diagnostics.accept("NPC_PROFILE_PREVIEW_REASSERT_AFTER_INVENTORY_MOVE"
                    + " timestamp=" + Instant.now()
                    + " npc=" + npcName
                    + " BridgeOperationId=" + result.operationId());
        }
        diagnostics.accept("NPC_PROFILE_INVENTORY_REFRESH"
                + " timestamp=" + Instant.now()
                + " npc=" + npcName
                + " profileId=" + profileId()
                + " npcEntityId=" + (liveStorageAuthority == null
                        ? "NOT_SPAWNED" : liveStorageAuthority.npcEntityId())
                + " sessionId=" + inventorySessionId
                + " pageGeneration=" + inventoryPageGeneration
                + " uiRefreshGeneration=" + refresh
                + " BridgeOperationId=" + result.operationId()
                + " result=" + result.type()
                + " reason=" + result.reason()
                + " sourceAfter=" + result.sourceAfter()
                + " targetAfter=" + result.targetAfter()
                + " mechanism=ATOMIC_FIXED_CAPACITY_SLOTS_REPLACEMENT"
                + " authoritativeReread=true"
                + " persistencePath=" + editor.inventories().path(npcName));
    }

    private void closeInventoryBridge() {
        if (inventoryBridge != null) inventoryBridge.close();
    }

    private static int parseSection(String value) {
        if (value == null) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static String boundNpcGridDocument(int sectionId) {
        if (sectionId < 1 || sectionId > MAX_PACKAGED_NPC_SECTION_ID) {
            throw new IllegalStateException("NPC Profile requires an allocated window ID"
                    + " between 1 and " + MAX_PACKAGED_NPC_SECTION_ID
                    + "; actual ID was " + sectionId
                    + ". Reconnect to reset the per-player WindowManager allocator.");
        }
        return "Pages/NativeInventoryProbe/NpcSection" + sectionId + ".ui";
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String quoted(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static boolean action(String value) {
        return "true".equalsIgnoreCase(value);
    }

    private String inventoryAuthority() {
        if (!update) return "UNBOUND_CREATE";
        return liveStorageAuthority == null
                ? "CUSTOM_BRIDGE_TO_PERSISTED_AUTHORING_STORAGE"
                : "CUSTOM_BRIDGE_TO_LIVE_NPC_STORAGE";
    }

    private String profileId() {
        if (liveStorageAuthority != null) {
            return liveStorageAuthority.profile().id().toString();
        }
        return editor.currentProfile(npcName)
                .map(profile -> profile.id().toString()).orElse("UNBOUND_CREATE");
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        built = false;
        closeInventoryBridge();
        closePreview();
        inventory.close();
        super.onDismiss(ref, store);
    }

    private void onInventoryChanged() {
        if (preview != null && preview.targetApplied()) {
            var equipment = NpcProfileEditorService.previewEquipmentFrom(
                    inventory.snapshot());
            preview.refreshEquipment(new EquipmentUpdate(
                    equipment.visibleArmorIds(), equipment.rightHandItemId(),
                    equipment.leftHandItemId()));
        }
        refreshNpcProfileUi();
    }

    private void closePreview() {
        if (preview != null) preview.close();
    }

    private void refreshNpcProfileUi() {
        if (!built) return;
        UICommandBuilder commands = new UICommandBuilder();
        setNpcProfileUi(commands);
        sendUpdate(commands, false);
    }

    private void setNpcProfileUi(UICommandBuilder commands) {
        setProfileFilesUi(commands);
        setEquipmentUi(commands);
        setAppearanceUi(commands);
        setVoiceSampleUi(commands);
        // Equipment retains bounded snapshots for its icon/visibility presentation.
        // Storage uses the exact R118 bridge presentation: fixed-capacity snapshots
        // are UI only; InventoryUtils remains the sole mutation authority.
        commands.set("#ArmorGrid.Slots", itemGridSlots(inventory.armor()));
        commands.set("#LoadoutGrid.Slots", itemGridSlots(inventory.loadout()));
        commands.set("#ArmorGrid.InventorySectionId", inventory.armorSectionId());
        commands.set("#LoadoutGrid.InventorySectionId", inventory.loadoutSectionId());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#NpcInventoryGrid.Slots", inventory.inventory());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerInventory);
    }

    private void setProfileFilesUi(UICommandBuilder commands) {
        for (ProfileFileField field : ProfileFileField.values()) {
            Path selected = selections.get(field);
            String shown = selected == null
                    ? editor.presentFilename(npcName, field)
                    : selected.getFileName().toString();
            commands.set("#" + field.name() + "Filename.Text", shown);
        }
    }

    /**
     * Converts one authoritative Hytale container into a fixed-capacity Custom UI
     * grid snapshot. Every physical slot is represented, including empty slots;
     * non-empty slots retain the complete server ItemStack metadata consumed by
     * ItemGridSlot.CODEC (quantity, durability, quality and item metadata).
     */
    public static ItemGridSlot[] itemGridSlots(ItemContainer container) {
        ItemGridSlot[] slots = new ItemGridSlot[container.getCapacity()];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            // The no-argument form serializes an absent item. Encoding
            // ItemStack.EMPTY creates an explicit invalid item on the client.
            slots[slot] = ItemStack.isEmpty(stack)
                    ? new ItemGridSlot()
                    : new ItemGridSlot(stack);
        }
        return slots;
    }

    private void setEquipmentUi(UICommandBuilder commands) {
        setAmmunitionUi(commands);
        setArmorVisibilityUi(commands);
    }

    private void setAppearanceUi(UICommandBuilder commands) {
        commands.set("#NpcPreviewName.Text", npcName);
        commands.set("#NpcCharacterPreview.Visible", preview != null);
        commands.set("#NpcPreviewUnavailable.Visible", preview == null);
    }

    private void setAmmunitionUi(UICommandBuilder commands) {
        boolean relevant = inventory.ammunitionPolicyRelevant();
        commands.set("#InfiniteAmmoCheckBox.Value", inventory.infiniteAmmunition());
        commands.set("#InfiniteAmmoCheckBox.Disabled", !relevant);
        commands.set("#InfiniteAmmoHint.Text", relevant
                ? "Selected ammunition is authoritative."
                : "Requires a ranged weapon and compatible preferred ammunition.");
    }

    private void setArmorVisibilityUi(UICommandBuilder commands) {
        for (short slot = 0; slot < 4; slot++) {
            boolean equipped = !ItemStack.isEmpty(inventory.armorItem(slot));
            boolean hidden = inventory.armorHidden(slot);
            String prefix = "#" + armorToggleId(slot);
            commands.set(prefix + ".Visible", equipped);
            commands.set("#" + armorEmptyIconId(slot) + ".Visible", !equipped);
            commands.set("#" + armorIconId(slot) + "ArmorVisible.Visible", !hidden);
            commands.set("#" + armorIconId(slot) + "ArmorHidden.Visible", hidden);
            commands.set(prefix + ".TooltipText", hidden
                    ? "Show " + armorLabel(slot).toLowerCase(Locale.ROOT) + " armor"
                    : "Hide " + armorLabel(slot).toLowerCase(Locale.ROOT)
                            + " armor and reveal the character skin");
        }
    }

    private static String armorEmptyIconId(short slot) {
        return switch (slot) {
            case 0 -> "HeadEmptyIcon";
            case 1 -> "ChestEmptyIcon";
            case 2 -> "HandsEmptyIcon";
            case 3 -> "LegsEmptyIcon";
            default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
        };
    }

    private static String armorToggleId(short slot) {
        return switch (slot) {
            case 0 -> "ToggleHelmetVisibilityButton";
            case 1 -> "ToggleCuirassVisibilityButton";
            case 2 -> "ToggleGauntletsVisibilityButton";
            case 3 -> "TogglePantsVisibilityButton";
            default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
        };
    }

    private static String armorLabel(short slot) {
        return switch (slot) {
            case 0 -> "Head";
            case 1 -> "Chest";
            case 2 -> "Hands";
            case 3 -> "Legs";
            default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
        };
    }

    private static String armorIconId(short slot) {
        return switch (slot) {
            case 0 -> "Helmet";
            case 1 -> "Cuirass";
            case 2 -> "Gauntlets";
            case 3 -> "Pants";
            default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
        };
    }

    private void setVoiceSampleUi(UICommandBuilder commands) {
        for (VoiceSampleType type : VoiceSampleType.values()) {
            VoicePresetRepository.VoiceSampleStatus sample = voiceSamples.samples().get(type);
            String prefix = "#Voice" + type.name();
            commands.set(prefix + " #VoiceFilename.Text", sample.filename());
            commands.set(prefix + " #VoiceState.Text", switch (sample.state()) {
                case FOUND -> "Found";
                case MISSING -> "Missing";
                case INVALID -> "Invalid";
            });
            commands.set(prefix + " #VoiceState.Style.TextColor", switch (sample.state()) {
                case FOUND -> "#72d58b";
                case MISSING -> type == VoiceSampleType.REFERENCE ? "#e76f6f" : "#d0a65a";
                case INVALID -> "#e76f6f";
            });
        }
        commands.set("#VoiceReadyText.Text", voiceSamples.ready()
                ? "Chatterbox cloned voice: READY"
                : "Chatterbox cloned voice: NOT READY (Reference required)");
        commands.set("#VoiceReadyText.Style.TextColor",
                voiceSamples.ready() ? "#72d58b" : "#e76f6f");
    }

    public Map<ProfileFileField, Path> selections() {
        return Map.copyOf(selections);
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec
                .builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Open", Codec.STRING),
                        (data, value) -> data.open = value, data -> data.open).add()
                .append(new KeyedCodec<>("Cancel", Codec.STRING),
                        (data, value) -> data.cancel = value, data -> data.cancel).add()
                .append(new KeyedCodec<>("Enter", Codec.STRING),
                        (data, value) -> data.enter = value, data -> data.enter).add()
                .append(new KeyedCodec<>("BrowserCancel", Codec.STRING),
                        (data, value) -> data.browserCancel = value,
                        data -> data.browserCancel).add()
                .append(new KeyedCodec<>("InfiniteAmmo", Codec.BOOLEAN),
                        (data, value) -> data.infiniteAmmo = value,
                        data -> data.infiniteAmmo).add()
                .append(new KeyedCodec<>("ArmorVisibility", Codec.STRING),
                        (data, value) -> data.armorVisibility = value,
                        data -> data.armorVisibility).add()
                .append(new KeyedCodec<>("RescanVoice", Codec.STRING),
                        (data, value) -> data.rescanVoice = value,
                        data -> data.rescanVoice).add()
                .append(new KeyedCodec<>("Delete", Codec.STRING),
                        (data, value) -> data.delete = value, data -> data.delete).add()
                .append(new KeyedCodec<>("DeleteNo", Codec.STRING),
                        (data, value) -> data.deleteNo = value, data -> data.deleteNo).add()
                .append(new KeyedCodec<>("DeleteYes", Codec.STRING),
                        (data, value) -> data.deleteYes = value, data -> data.deleteYes).add()
                .append(new KeyedCodec<>("Marker", Codec.STRING),
                        (data, value) -> data.marker = value, data -> data.marker).add()
                .append(new KeyedCodec<>("Event", Codec.STRING),
                        (data, value) -> data.event = value, data -> data.event).add()
                .append(new KeyedCodec<>("Section", Codec.STRING),
                        (data, value) -> data.section = value, data -> data.section).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER),
                        (data, value) -> data.slotIndex = value,
                        data -> data.slotIndex).add()
                .append(new KeyedCodec<>("SourceSlotId", Codec.INTEGER),
                        (data, value) -> data.sourceSlotId = value,
                        data -> data.sourceSlotId).add()
                .append(new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER),
                        (data, value) -> data.sourceInventorySectionId = value,
                        data -> data.sourceInventorySectionId).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING),
                        (data, value) -> data.itemStackId = value,
                        data -> data.itemStackId).add()
                .append(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER),
                        (data, value) -> data.itemStackQuantity = value,
                        data -> data.itemStackQuantity).add()
                .append(new KeyedCodec<>("PressedMouseButton", Codec.INTEGER),
                        (data, value) -> data.pressedMouseButton = value,
                        data -> data.pressedMouseButton).add()
                .append(new KeyedCodec<>("File", Codec.STRING),
                        (data, value) -> data.file = value, data -> data.file).add()
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING),
                        (data, value) -> data.searchQuery = value,
                        data -> data.searchQuery).add()
                .build();

        private String open;
        private String cancel;
        private String enter;
        private String browserCancel;
        private Boolean infiniteAmmo;
        private String armorVisibility;
        private String rescanVoice;
        private String delete;
        private String deleteNo;
        private String deleteYes;
        private String marker;
        private String event;
        private String section;
        private Integer slotIndex;
        private Integer sourceSlotId;
        private Integer sourceInventorySectionId;
        private String itemStackId;
        private Integer itemStackQuantity;
        private Integer pressedMouseButton;
        private String file;
        private String searchQuery;
    }
}
