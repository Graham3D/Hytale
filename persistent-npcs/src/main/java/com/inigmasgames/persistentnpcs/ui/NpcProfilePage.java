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
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringEventEnvelope;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringPermissions;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileDraft;
import com.inigmasgames.persistentnpcs.profile.NpcProfileGenerationService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService.ProfileFileField;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcEquipmentMovePolicy;
import com.inigmasgames.persistentnpcs.profile.NpcEquipmentCompatibilityResolver;
import com.inigmasgames.persistentnpcs.profile.NpcEquipmentRules;
import com.inigmasgames.persistentnpcs.profile.NpcStatsSnapshotService;
import com.inigmasgames.persistentnpcs.profile.NpcStatsSnapshotService.NpcStatsSnapshot;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceSampleType;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceAuthoringService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.PrimaryCategory;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceDraft;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearancePreviewService;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "INVENTORY_DROP", "CANCEL", "ENTER", "DELETE_PROMPT",
            "DELETE_CANCEL", "DELETE_CONFIRM", "ADVANCED_FILE_OPEN",
            "BROWSER_EVENT", "INFINITE_AMMO", "ARMOR_VISIBILITY",
            "VOICE_RESCAN", "OPEN_PROFILE_EDITOR", "OPEN_APPEARANCE_EDITOR",
            "OPEN_VOICE_EDITOR", "CLOSE_EDITOR", "DIRTY_SAVE",
            "DIRTY_DISCARD", "DIRTY_STAY", "PROFILE_FIELD", "PROFILE_SAVE",
            "PROFILE_RESET", "PROFILE_CANCEL", "PROFILE_GENERATE",
            "PROFILE_SCOPE",
            "PROFILE_PROPOSAL_ACCEPT", "PROFILE_PROPOSAL_ACCEPT_SELECTED",
            "PROFILE_PROPOSAL_DISCARD", "APPEARANCE_PRIMARY",
            "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH", "APPEARANCE_PAGE_PREV",
            "APPEARANCE_PAGE_NEXT", "APPEARANCE_OPTION", "APPEARANCE_COLOR",
            "APPEARANCE_COLOR_PREV", "APPEARANCE_COLOR_NEXT",
            "APPEARANCE_VARIANT", "APPEARANCE_VARIANT_PREV",
            "APPEARANCE_VARIANT_NEXT", "APPEARANCE_RANDOMIZE", "APPEARANCE_RESET",
            "APPEARANCE_CANCEL", "APPEARANCE_SAVE");
    private final String npcName;
    private final boolean update;
    private final NpcProfileEditorService editor;
    private final Consumer<NpcProfile> committed;
    private final NpcInventoryRepository.Session inventory;
    private final ItemContainer playerInventory;
    private final NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority;
    private final ContainerWindow storageWindow;
    private final NpcAuthoringSession authoringSession;
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
    private final NpcStatsSnapshotService statsService = new NpcStatsSnapshotService();
    private final ScheduledExecutorService statsScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "immersive-npc-stats-refresh");
                thread.setDaemon(true);
                return thread;
            });
    private ScheduledFuture<?> statsRefreshTask;
    private NpcStatsSnapshot statsSnapshot;
    private String statsFailure = "LIVE_NPC_UNAVAILABLE";
    private boolean initialEquipmentStateApplied;
    private NpcProfileDraft profileDraft;
    private NpcProfileGenerationService.Handle profileGeneration;
    private String profileEditorStatus = "Draft valid.";
    private boolean profileEditorError;
    private String profileGenerationScope = "BIOGRAPHY";
    private NpcAppearanceDraft appearanceDraft;
    private final NpcAppearancePreviewService appearancePreview;
    private PrimaryCategory appearancePrimary = PrimaryCategory.BODY;
    private Category appearanceCategory = Category.BODY_CHARACTERISTIC;
    private String appearanceSearch = "";
    private int appearancePage;
    private int appearanceColorPage;
    private int appearanceVariantPage;
    private String appearanceEditorStatus = "Choose a registry-backed appearance option.";
    private boolean appearanceEditorError;

    public NpcProfilePage(
            PlayerRef playerRef,
            String npcName,
            boolean update,
            NpcProfileEditorService editor,
            ItemContainer playerInventory,
            NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority,
            NpcAuthoringSession authoringSession,
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
        this.authoringSession = java.util.Objects.requireNonNull(
                authoringSession, "Authoring session is required.");
        this.preview = preview;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.appearancePreview = new NpcAppearancePreviewService(
                preview, editor.skinCodec(), this.diagnostics);
        this.committed = committed == null ? ignored -> { } : committed;
        this.deleted = deleted == null
                ? (ignoredRef, ignoredStore) -> {
                    throw new IllegalStateException("NPC deletion is unavailable.");
                } : deleted;
        this.inventory = liveStorageAuthority == null
                ? editor.openInventory(npcName)
                : editor.inventories().openWithLiveInventory(
                        npcName, liveStorageAuthority.armor(),
                        liveStorageAuthority.hotbar(), liveStorageAuthority.utility(),
                        liveStorageAuthority.storage());
        this.inventory.onChanged(() -> { });
        this.storageWindow = inventory.inventoryWindow();
        this.inventoryBridge = !update ? null
                : new CustomInventoryTransactionBridge(
                        authoringSession.sessionId(), authoringSession.pageGeneration(), playerRef,
                        this, List.of(
                                new CustomInventoryTransactionBridge.SectionBinding(
                                        inventory.inventoryWindow(), inventory.inventory(),
                                        CustomInventoryTransactionBridge.SectionRole.NPC_STORAGE),
                                new CustomInventoryTransactionBridge.SectionBinding(
                                        inventory.armorWindow(), inventory.armor(),
                                        CustomInventoryTransactionBridge.SectionRole.NPC_ARMOR),
                                new CustomInventoryTransactionBridge.SectionBinding(
                                        inventory.hotbarWindow(), inventory.hotbar(),
                                        CustomInventoryTransactionBridge.SectionRole.NPC_HOTBAR),
                                new CustomInventoryTransactionBridge.SectionBinding(
                                        inventory.utilityWindow(), inventory.utility(),
                                        CustomInventoryTransactionBridge.SectionRole.NPC_UTILITY)),
                        playerInventory,
                        liveStorageAuthority == null
                                ? (ignoredRef, ignoredStore) -> null
                                : liveStorageAuthority::invalidReason,
                        new NpcEquipmentMovePolicy(() -> inventory.loadoutItem(
                                NpcInventoryRepository.Session.PRIMARY_SLOT)),
                        this.diagnostics);
        this.voiceSamples = editor.rescanVoiceSamples(npcName);
        authoringSession.addCleanup("inventory-event-bridge", this::closeInventoryBridge);
        authoringSession.addCleanup("appearance-draft-preview", this::closeAppearanceDraft);
        authoringSession.addCleanup("viewer-preview-restoration", this::closePreview);
        authoringSession.addCleanup("inventory-persistence-flush", inventory::close);
        authoringSession.addCleanup("stats-refresh", this::closeStatsRefresh);
        authoringSession.addCleanup("profile-generation", this::cancelProfileGeneration);
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
                + " authoringSessionId=" + authoringSession.sessionId()
                + " pageGeneration=" + authoringSession.pageGeneration();
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
        commands.set("#PlayerInventoryTitle.Text", playerRef.getUsername() + "'s Storage");
        commands.set("#AuthoringSessionValue.Text", authoringSession.sessionId().toString());
        commands.set("#AuthoringViewerValue.Text", authoringSession.viewerPlayerId().toString());
        commands.set("#AuthoringNpcValue.Text", authoringSession.npcStableId().toString());
        commands.set("#AuthoringPageGeneration.Text",
                Long.toString(authoringSession.pageGeneration()));
        commands.set("#AuthoringEditorValue.Text", authoringSession.activeEditor().name());
        commands.set("#AuthoringEditorGeneration.Text",
                Long.toString(authoringSession.editorGeneration()));
        if (!initialEquipmentStateApplied) {
            applyEquipmentAndStats(store, "PROFILE_OPEN");
            initialEquipmentStateApplied = true;
        } else {
            captureStats(store, false);
        }
        setNpcProfileUi(commands);
        if (inventoryBridge != null) {
            CustomInventoryBridgeUi.bindDrop(events, "#NpcInventoryGrid",
                    storageWindow.getId(), authoringEvent("INVENTORY_DROP"));
            CustomInventoryBridgeUi.bindDrop(events, "#PlayerInventoryGrid",
                    InventoryComponent.STORAGE_SECTION_ID,
                    authoringEvent("INVENTORY_DROP"));
            CustomInventoryBridgeUi.bindDrop(events, "#ArmorGrid",
                    inventory.armorSectionId(), authoringEvent("INVENTORY_DROP"));
            CustomInventoryBridgeUi.bindDrop(events, "#PrimaryWeaponGrid",
                    inventory.primarySectionId(), authoringEvent("INVENTORY_DROP"));
            CustomInventoryBridgeUi.bindDrop(events, "#OffhandGrid",
                    inventory.offhandSectionId(), authoringEvent("INVENTORY_DROP"));
            CustomInventoryBridgeUi.bindDrop(events, "#AmmunitionGrid",
                    inventory.ammunitionSectionId(), authoringEvent("INVENTORY_DROP"));
            diagnostics.accept("NPC_PROFILE_INVENTORY_BRIDGE_BUILD"
                    + " timestamp=" + Instant.now()
                    + " npc=" + npcName
                    + " profileId=" + profileId()
                    + " npcEntityId=" + (liveStorageAuthority == null
                            ? "NOT_SPAWNED" : liveStorageAuthority.npcEntityId())
                    + " viewerUuid=" + playerRef.getUuid()
                    + " sessionId=" + authoringSession.sessionId()
                    + " pageGeneration=" + authoringSession.pageGeneration()
                    + " npcWindowId=" + storageWindow.getId()
                    + " armorWindowId=" + inventory.armorSectionId()
                    + " hotbarWindowId=" + inventory.primarySectionId()
                    + " utilityWindowId=" + inventory.offhandSectionId()
                    + " playerStorageSectionId="
                    + InventoryComponent.STORAGE_SECTION_ID
                    + " supportedOperation=DROP_FULL_OR_ONE_MOVE_MERGE_SWAP_CROSS_OR_INTERNAL"
                    + " mutationAuthority=NATIVE_ITEM_CONTAINER_TRANSACTIONS"
                    + " persistenceAuthority=NpcInventoryRepository_RUNTIME_PIPELINE");
        }
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,
                "#InfiniteAmmoCheckBox",
                authoringEvent("INFINITE_AMMO")
                        .append("InfiniteAmmo", "#InfiniteAmmoCheckBox.Value"));
        for (short slot = 0; slot < 4; slot++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#" + armorToggleId(slot),
                    authoringEvent("ARMOR_VISIBILITY")
                            .append("ArmorVisibility", Short.toString(slot)));
        }
        for (ProfileFileField field : ProfileFileField.values()) {
            String id = field.name();
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#" + id + "Open", authoringEvent("ADVANCED_FILE_OPEN")
                            .append("Open", field.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceRescanButton", authoringEvent("VOICE_RESCAN")
                        .append("RescanVoice", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileEditorButton", authoringEvent("OPEN_PROFILE_EDITOR"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceEditorButton", authoringEvent("OPEN_APPEARANCE_EDITOR"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceRecorderButton", authoringEvent("OPEN_VOICE_EDITOR"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ContextCloseButton", authoringEvent("CLOSE_EDITOR"));
        bindProfileEditorEvents(events);
        bindAppearanceEditorEvents(events);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DirtySaveButton", authoringEvent("DIRTY_SAVE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DirtyDiscardButton", authoringEvent("DIRTY_DISCARD"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DirtyStayButton", authoringEvent("DIRTY_STAY"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CancelButton", authoringEvent("CANCEL").append("Cancel", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteButton", authoringEvent("DELETE_PROMPT").append("Delete", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#EnterButton", authoringEvent("ENTER").append("Enter", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteNoButton", authoringEvent("DELETE_CANCEL")
                        .append("DeleteNo", "true"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#DeleteYesButton", authoringEvent("DELETE_CONFIRM")
                        .append("DeleteYes", "true"));
        commands.set("#DeleteButton.Visible", update);
        commands.set("#DeleteConfirmName.Text", "Delete " + npcName + "?");
        commands.set("#StatusText.Text", status);
        commands.set("#StatusText.Visible", !status.isBlank());
        commands.set("#StatusText.Style.TextColor", error ? "#e76f6f" : "#9ed7a6");
        boolean profileEditor = authoringSession.activeEditor()
                == NpcAuthoringSession.EditorKind.PROFILE;
        boolean appearanceEditor = authoringSession.activeEditor()
                == NpcAuthoringSession.EditorKind.APPEARANCE;
        boolean contextualEditor = authoringSession.activeEditor()
                != NpcAuthoringSession.EditorKind.NONE && !profileEditor && !appearanceEditor;
        commands.set("#ProfileEditorPage.Visible", profileEditor);
        if (profileEditor && profileDraft != null) setProfileEditorUi(commands);
        commands.set("#AppearanceEditorPage.Visible", appearanceEditor);
        if (appearanceEditor && appearanceDraft != null) setAppearanceEditorUi(commands);
        commands.set("#ContextEditorPage.Visible", contextualEditor);
        if (contextualEditor) {
            String title = switch (authoringSession.activeEditor()) {
                case PROFILE -> "PROFILE EDITOR";
                case APPEARANCE -> "NPC APPEARANCE";
                case VOICE -> "VOICE RECORDER";
                case NONE -> "NPC AUTHORING";
            };
            commands.set("#ContextEditorTitle.Text", title);
            commands.set("#ContextEditorStatus.Text",
                    title + " is reserved by the unified authoring session. "
                            + "Its domain editor activates in its gated implementation stage.");
        }
        boolean browsing = activeField != null && browser != null;
        commands.set("#ProfilePage.Visible", !browsing);
        commands.set("#BrowserPage.Visible", browsing);
        if (browsing) {
            commands.set("#BrowserTitle.Text", "Select " + activeField.label());
            browser.buildUI(commands, events);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#BrowserCancelButton", authoringEvent("BROWSER_EVENT")
                            .append("BrowserCancel", "true"));
        }
        built = true;
        startStatsRefresh(store);
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> ref, Store<EntityStore> store, String rawData) {
        if (rawData != null && rawData.contains(CustomInventoryBridgeUi.DROP_MARKER)) {
            diagnostics.accept("NPC_PROFILE_INVENTORY_RAW_EVENT"
                    + " timestamp=" + Instant.now()
                    + " npc=" + npcName
                    + " sessionId=" + authoringSession.sessionId()
                    + " pageGeneration=" + authoringSession.pageGeneration()
                    + " payload=" + quoted(rawData));
        }
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        try {
            String authoringAction = resolveAuthoringAction(data);
            authoringSession.validate(authoringEnvelope(data, authoringAction),
                    ALLOWED_ACTIONS, permissionFor(authoringAction, data));
            if (isInventoryDrop(data)) {
                handleInventoryDrop(ref, store, data);
                return;
            }
            if (action(data.cancel)) {
                authoringSession.close();
                close();
                return;
            }
            if (action(data.enter)) {
                authoringSession.beginCommit();
                NpcProfile profile = editor.commit(npcName, update, selections);
                inventory.bindStableIdentity(profile.stableId());
                inventory.flush();
                committed.accept(profile);
                authoringSession.close();
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
                authoringSession.close();
                deleted.accept(ref, store);
                close();
                playerRef.sendMessage(Message.raw("NPC " + npcName
                        + " and its authored profile folder were deleted."));
                return;
            }
            if ("OPEN_PROFILE_EDITOR".equals(authoringAction)) {
                long generation = authoringSession.openEditor(
                        NpcAuthoringSession.EditorKind.PROFILE);
                profileDraft = editor.authoring().begin(npcName,
                        authoringSession.sessionId(), generation);
                profileEditorStatus = "Draft valid. Unknown profile fields are preserved.";
                profileEditorError = false;
                rebuild();
                return;
            }
            if ("OPEN_APPEARANCE_EDITOR".equals(authoringAction)) {
                long generation = authoringSession.openEditor(
                        NpcAuthoringSession.EditorKind.APPEARANCE);
                try {
                    appearanceDraft = editor.appearanceAuthoring().begin(npcName,
                            authoringSession.npcStableId(), authoringSession.sessionId(),
                            generation);
                } catch (RuntimeException failure) {
                    authoringSession.closeEditor(false);
                    throw failure;
                }
                appearancePrimary = PrimaryCategory.BODY;
                appearanceCategory = Category.BODY_CHARACTERISTIC;
                appearanceSearch = "";
                appearancePage = 0;
                appearanceColorPage = 0;
                appearanceVariantPage = 0;
                appearanceEditorStatus = "Draft valid. Registry snapshot is pinned for this page.";
                appearanceEditorError = false;
                rebuild();
                return;
            }
            if ("OPEN_VOICE_EDITOR".equals(authoringAction)) {
                authoringSession.openEditor(NpcAuthoringSession.EditorKind.VOICE);
                rebuild();
                return;
            }
            if ("CLOSE_EDITOR".equals(authoringAction)) {
                if (authoringSession.isDirty(authoringSession.activeEditor())) {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#DirtyEditorConfirmPage.Visible", true);
                    sendUpdate(commands, false);
                    return;
                }
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE) {
                    restoreAndClearAppearanceDraft();
                }
                authoringSession.closeEditor(false);
                clearProfileDraft();
                rebuild();
                return;
            }
            if ("DIRTY_SAVE".equals(authoringAction)) {
                if (authoringSession.activeEditor() == NpcAuthoringSession.EditorKind.PROFILE) {
                    saveProfileDraft();
                } else if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE) {
                    saveAppearanceDraft(store);
                } else {
                    authoringSession.markSaved(authoringSession.activeEditor());
                }
                authoringSession.closeEditor(false);
                clearProfileDraft();
                clearAppearanceDraft(false);
                rebuild();
                return;
            }
            if ("DIRTY_DISCARD".equals(authoringAction)) {
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE) {
                    restoreAndClearAppearanceDraft();
                }
                authoringSession.closeEditor(true);
                clearProfileDraft();
                rebuild();
                return;
            }
            if ("DIRTY_STAY".equals(authoringAction)) {
                UICommandBuilder commands = new UICommandBuilder();
                commands.set("#DirtyEditorConfirmPage.Visible", false);
                sendUpdate(commands, false);
                return;
            }
            if (authoringAction.startsWith("APPEARANCE_")) {
                handleAppearanceAction(store, data, authoringAction);
                return;
            }
            if ("PROFILE_FIELD".equals(authoringAction)) {
                requireProfileDraft();
                NpcProfileDraft.Field field = NpcProfileDraft.Field.valueOf(
                        data.profileField.toUpperCase(Locale.ROOT));
                profileDraft.update(field, data.profileFieldValue);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.PROFILE);
                profileEditorStatus = "Draft changed. Save Profile commits it to canon.";
                profileEditorError = false;
                refreshProfileEditorUi();
                return;
            }
            if ("PROFILE_RESET".equals(authoringAction)) {
                requireProfileDraft();
                cancelProfileGeneration();
                profileDraft.reset();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.PROFILE);
                profileEditorStatus = "Draft reset to the persisted profile.";
                profileEditorError = false;
                rebuild();
                return;
            }
            if ("PROFILE_CANCEL".equals(authoringAction)) {
                requireProfileDraft();
                if (profileDraft.dirty()) {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#DirtyEditorConfirmPage.Visible", true);
                    sendUpdate(commands, false);
                } else {
                    authoringSession.closeEditor(false);
                    clearProfileDraft();
                    rebuild();
                }
                return;
            }
            if ("PROFILE_SAVE".equals(authoringAction)) {
                saveProfileDraft();
                profileEditorStatus = "Profile saved atomically. Draft is now current.";
                profileEditorError = false;
                long generation = authoringSession.editorGeneration();
                profileDraft = editor.authoring().begin(npcName,
                        authoringSession.sessionId(), generation);
                rebuild();
                return;
            }
            if ("PROFILE_GENERATE".equals(authoringAction)) {
                startProfileGeneration(store, data);
                return;
            }
            if ("PROFILE_SCOPE".equals(authoringAction)) {
                profileGenerationScope = NpcProfileGenerationService.Scope.parse(
                        data.profileGenerateScope).name();
                profileEditorStatus = "Generation scope: " + profileGenerationScope
                        + ". Generate creates a review-only proposal.";
                profileEditorError = false;
                refreshProfileEditorUi();
                return;
            }
            if ("PROFILE_PROPOSAL_ACCEPT".equals(authoringAction)) {
                requireProfileDraft();
                profileDraft.acceptProposal(Set.of());
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.PROFILE);
                profileEditorStatus = "Proposal accepted into the draft. Review, then Save Profile.";
                profileEditorError = false;
                rebuild();
                return;
            }
            if ("PROFILE_PROPOSAL_ACCEPT_SELECTED".equals(authoringAction)) {
                requireProfileDraft();
                Set<NpcProfileDraft.Field> selected = parseProfileFields(
                        data.profileProposalSelection);
                if (selected.isEmpty()) throw new IllegalArgumentException(
                        "Enter at least one proposed field to accept.");
                profileDraft.acceptProposal(selected);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.PROFILE);
                profileEditorStatus = "Selected proposal fields accepted into the draft. Review, then Save Profile.";
                profileEditorError = false;
                rebuild();
                return;
            }
            if ("PROFILE_PROPOSAL_DISCARD".equals(authoringAction)) {
                requireProfileDraft();
                profileDraft.discardProposal();
                profileEditorStatus = "Generated proposal discarded; canonical profile unchanged.";
                profileEditorError = false;
                rebuild();
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
                applyEquipmentAndStats(store, "INFINITE_AMMUNITION_POLICY");
                refreshNpcProfileUi();
                return;
            }
            if (data.armorVisibility != null) {
                short slot = Short.parseShort(data.armorVisibility);
                inventory.toggleArmorVisibility(slot);
                status = armorLabel(slot) + (inventory.armorHidden(slot)
                        ? " armor hidden; character skin is visible."
                        : " armor visible.");
                error = false;
                applyEquipmentAndStats(store, "ARMOR_VISIBILITY");
                refreshNpcProfileUi();
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
            if (authoringSession.state() == NpcAuthoringSession.WorkspaceState.COMMITTING) {
                authoringSession.commitFailed(authoringSession.activeEditor(),
                        failure.getMessage());
            }
            status = failure.getMessage() == null ? "Profile operation failed."
                    : failure.getMessage();
            error = true;
            if (authoringSession.activeEditor() == NpcAuthoringSession.EditorKind.PROFILE) {
                profileEditorStatus = status;
                profileEditorError = true;
            } else if (authoringSession.activeEditor()
                    == NpcAuthoringSession.EditorKind.APPEARANCE) {
                appearanceEditorStatus = status;
                appearanceEditorError = true;
            }
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

    private void bindProfileEditorEvents(UIEventBuilder events) {
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            String selector = "#" + profileFieldControl(field);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector,
                    authoringEvent("PROFILE_FIELD")
                            .append("ProfileField", field.name())
                            .append("ProfileFieldValue", selector + ".Value"));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileSaveButton", authoringEvent("PROFILE_SAVE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileResetButton", authoringEvent("PROFILE_RESET"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileCancelButton", authoringEvent("PROFILE_CANCEL"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileGenerateButton", authoringEvent("PROFILE_GENERATE")
                        .append("ProfileGenerateScope", "#ProfileGenerateScopeInput.Value"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,
                "#ProfileGenerateScopeInput", authoringEvent("PROFILE_SCOPE")
                        .append("ProfileGenerateScope", "#ProfileGenerateScopeInput.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileAcceptProposalButton", authoringEvent("PROFILE_PROPOSAL_ACCEPT"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileAcceptSelectedProposalButton",
                authoringEvent("PROFILE_PROPOSAL_ACCEPT_SELECTED")
                        .append("ProfileProposalSelection",
                                "#ProfileProposalSelectionInput.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileDiscardProposalButton", authoringEvent("PROFILE_PROPOSAL_DISCARD"));
    }

    private void bindAppearanceEditorEvents(UIEventBuilder events) {
        for (PrimaryCategory primary : PrimaryCategory.values()) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearancePrimary" + primary.name(),
                    authoringEvent("APPEARANCE_PRIMARY")
                            .append("AppearancePrimary", primary.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceSearchButton", authoringEvent("APPEARANCE_SEARCH")
                        .append("AppearanceSearch", "#AppearanceSearchInput.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearancePreviousButton", authoringEvent("APPEARANCE_PAGE_PREV"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceNextButton", authoringEvent("APPEARANCE_PAGE_NEXT"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceColorPreviousButton", authoringEvent("APPEARANCE_COLOR_PREV"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceColorNextButton", authoringEvent("APPEARANCE_COLOR_NEXT"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceVariantPreviousButton", authoringEvent("APPEARANCE_VARIANT_PREV"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceVariantNextButton", authoringEvent("APPEARANCE_VARIANT_NEXT"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceRandomizeButton", authoringEvent("APPEARANCE_RANDOMIZE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceResetButton", authoringEvent("APPEARANCE_RESET"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceCancelButton", authoringEvent("APPEARANCE_CANCEL"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceSaveButton", authoringEvent("APPEARANCE_SAVE"));
        if (appearanceDraft == null) return;
        List<Category> categories = editor.appearanceCatalog().categories(appearancePrimary);
        for (int index = 0; index < categories.size() && index < 7; index++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceCategory" + index,
                    authoringEvent("APPEARANCE_CATEGORY")
                            .append("AppearanceCategory", categories.get(index).name()));
        }
        var page = appearanceCatalogPage();
        for (int index = 0; index < page.options().size(); index++) {
            var option = page.options().get(index);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceOption" + index,
                    authoringEvent("APPEARANCE_OPTION")
                            .append("AppearanceOptionId", option.cosmeticId()));
        }
        var current = currentAppearanceDescriptor();
        if (current != null) {
            String encoded = currentAppearanceSelection();
            List<String> colors = current.colors(NpcSkinCodecAdapter.variantId(encoded));
            int colorFrom = Math.min(colors.size(), appearanceColorPage * 8);
            List<String> visibleColors = colors.subList(colorFrom,
                    Math.min(colors.size(), colorFrom + 8));
            for (int index = 0; index < visibleColors.size(); index++) {
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        "#AppearanceColor" + index,
                        authoringEvent("APPEARANCE_COLOR")
                                .append("AppearanceOptionId", current.cosmeticId())
                                .append("AppearanceColorId", visibleColors.get(index))
                                .append("AppearanceVariantId",
                                        NpcSkinCodecAdapter.variantId(encoded)));
            }
            List<String> variants = current.variants();
            int variantFrom = Math.min(variants.size(), appearanceVariantPage * 6);
            List<String> visibleVariants = variants.subList(variantFrom,
                    Math.min(variants.size(), variantFrom + 6));
            for (int index = 0; index < visibleVariants.size(); index++) {
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        "#AppearanceVariant" + index,
                        authoringEvent("APPEARANCE_VARIANT")
                                .append("AppearanceOptionId", current.cosmeticId())
                                .append("AppearanceColorId",
                                        NpcSkinCodecAdapter.colorId(encoded))
                                .append("AppearanceVariantId", visibleVariants.get(index)));
            }
        }
    }

    private void handleAppearanceAction(Store<EntityStore> store, PageData data,
            String action) {
        requireAppearanceDraft();
        switch (action) {
            case "APPEARANCE_PRIMARY" -> {
                appearancePrimary = PrimaryCategory.valueOf(
                        data.appearancePrimary.toUpperCase(Locale.ROOT));
                List<Category> categories = editor.appearanceCatalog()
                        .categories(appearancePrimary);
                appearanceCategory = categories.getFirst();
                appearanceSearch = "";
                appearancePage = 0;
                appearanceColorPage = 0;
                appearanceVariantPage = 0;
                appearanceEditorStatus = "Browsing " + appearancePrimary.name()
                        + " from the pinned live registry snapshot.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_CATEGORY" -> {
                Category category = Category.valueOf(
                        data.appearanceCategory.toUpperCase(Locale.ROOT));
                if (category.primary() != appearancePrimary) {
                    throw new IllegalArgumentException("Appearance category is stale.");
                }
                appearanceCategory = category;
                appearanceSearch = "";
                appearancePage = 0;
                appearanceColorPage = 0;
                appearanceVariantPage = 0;
                appearanceEditorStatus = "Choose " + category.label()
                        + ". Existing missing values remain retained until Save.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_SEARCH" -> {
                appearanceSearch = data.appearanceSearch == null ? ""
                        : data.appearanceSearch.strip();
                appearancePage = 0;
                appearanceEditorStatus = "Registry search applied locally; no model request was made.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_PAGE_PREV" -> {
                appearancePage = Math.max(0, appearancePage - 1);
                rebuild();
            }
            case "APPEARANCE_PAGE_NEXT" -> {
                var page = appearanceCatalogPage();
                appearancePage = Math.min(page.pageCount() - 1, appearancePage + 1);
                rebuild();
            }
            case "APPEARANCE_COLOR_PREV" -> {
                appearanceColorPage = Math.max(0, appearanceColorPage - 1);
                rebuild();
            }
            case "APPEARANCE_COLOR_NEXT" -> {
                var descriptor = currentAppearanceDescriptor();
                int count = descriptor == null ? 0 : descriptor.colors(
                        NpcSkinCodecAdapter.variantId(currentAppearanceSelection())).size();
                int pages = Math.max(1, (count + 7) / 8);
                appearanceColorPage = Math.min(pages - 1, appearanceColorPage + 1);
                rebuild();
            }
            case "APPEARANCE_VARIANT_PREV" -> {
                appearanceVariantPage = Math.max(0, appearanceVariantPage - 1);
                rebuild();
            }
            case "APPEARANCE_VARIANT_NEXT" -> {
                var descriptor = currentAppearanceDescriptor();
                int count = descriptor == null ? 0 : descriptor.variants().size();
                int pages = Math.max(1, (count + 5) / 6);
                appearanceVariantPage = Math.min(pages - 1, appearanceVariantPage + 1);
                rebuild();
            }
            case "APPEARANCE_OPTION", "APPEARANCE_COLOR", "APPEARANCE_VARIANT" -> {
                var selected = editor.appearanceAuthoring().select(appearanceDraft,
                        appearanceCategory, data.appearanceOptionId,
                        data.appearanceColorId, data.appearanceVariantId);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.APPEARANCE);
                appearancePreview.show(appearanceDraft);
                if ("APPEARANCE_OPTION".equals(action)) {
                    appearanceColorPage = 0;
                    appearanceVariantPage = 0;
                }
                appearanceEditorStatus = "Previewing " + selected.option().displayName()
                        + ". Save Appearance commits; Cancel restores persisted appearance.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_RANDOMIZE" -> {
                editor.appearanceAuthoring().randomize(appearanceDraft);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.APPEARANCE);
                appearancePreview.show(appearanceDraft);
                appearanceEditorStatus = "Randomized from Hytale's current registry. Review before saving.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_RESET" -> {
                appearanceDraft.reset();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.APPEARANCE);
                appearancePreview.restore(appearanceDraft);
                appearanceEditorStatus = "Draft reset to the persisted appearance.";
                appearanceEditorError = false;
                rebuild();
            }
            case "APPEARANCE_CANCEL" -> {
                if (appearanceDraft.dirty()) {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#DirtyEditorConfirmPage.Visible", true);
                    sendUpdate(commands, false);
                } else {
                    restoreAndClearAppearanceDraft();
                    authoringSession.closeEditor(false);
                    rebuild();
                }
            }
            case "APPEARANCE_SAVE" -> {
                if (!appearanceDraft.dirty()) {
                    appearanceEditorStatus = "No appearance changes to save.";
                    appearanceEditorError = false;
                    refreshAppearanceEditorUi();
                    return;
                }
                saveAppearanceDraft(store);
                long generation = authoringSession.editorGeneration();
                appearanceDraft = editor.appearanceAuthoring().begin(npcName,
                        authoringSession.npcStableId(), authoringSession.sessionId(), generation);
                rebuild();
            }
            default -> throw new IllegalArgumentException("Unknown appearance action.");
        }
    }

    private void setAppearanceEditorUi(UICommandBuilder commands) {
        var snapshot = editor.appearanceCatalog().snapshot();
        commands.set("#AppearanceEditorTitle.Text", npcName + " Appearance");
        commands.set("#AppearanceDraftMeta.Text", "Draft " + appearanceDraft.draftId()
                + "  |  base r" + appearanceDraft.baseRevision()
                + "  |  build " + snapshot.identity().hytaleBuildId());
        commands.set("#AppearanceRegistryMeta.Text", "Registry "
                + compact(snapshot.identity().registryHash(), 14) + "  |  packs "
                + compact(snapshot.identity().enabledAssetPackSetHash(), 14)
                + "  |  " + snapshot.identity().adapterVersion());
        commands.set("#AppearanceSearchInput.Value", appearanceSearch);
        List<Category> categories = editor.appearanceCatalog().categories(appearancePrimary);
        for (int index = 0; index < 7; index++) {
            boolean visible = index < categories.size();
            commands.set("#AppearanceCategory" + index + ".Visible", visible);
            if (visible) {
                Category category = categories.get(index);
                commands.set("#AppearanceCategory" + index + ".Text",
                        (category == appearanceCategory ? "> " : "") + category.label());
            }
        }
        var page = appearanceCatalogPage();
        appearancePage = page.pageIndex();
        commands.set("#AppearancePageText.Text", "Page " + (page.pageIndex() + 1)
                + " / " + page.pageCount() + "  ·  " + page.totalMatches() + " options");
        commands.set("#AppearancePreviousButton.Disabled", page.pageIndex() == 0);
        commands.set("#AppearanceNextButton.Disabled",
                page.pageIndex() + 1 >= page.pageCount());
        String currentId = currentAppearanceCosmeticId();
        for (int index = 0; index < NpcAppearanceCatalogService.PAGE_SIZE; index++) {
            boolean visible = index < page.options().size();
            commands.set("#AppearanceOption" + index + ".Visible", visible);
            if (visible) {
                var option = page.options().get(index);
                commands.set("#AppearanceOption" + index + ".Text",
                        (option.cosmeticId().equals(currentId) ? "✓ " : "")
                                + option.displayName() + "\n"
                                + (option.source()
                                        == NpcAppearanceCatalogService.SourceKind.HYTALE_DEFAULT
                                        ? "Hytale Default" : "Enabled Pack"));
            }
        }
        var descriptor = currentAppearanceDescriptor();
        String current = currentAppearanceSelection();
        List<String> colors = descriptor == null ? List.of()
                : descriptor.colors(NpcSkinCodecAdapter.variantId(current));
        int colorPages = Math.max(1, (colors.size() + 7) / 8);
        appearanceColorPage = Math.max(0, Math.min(appearanceColorPage, colorPages - 1));
        int colorFrom = Math.min(colors.size(), appearanceColorPage * 8);
        List<String> visibleColors = colors.subList(colorFrom,
                Math.min(colors.size(), colorFrom + 8));
        for (int index = 0; index < 8; index++) {
            boolean visible = index < visibleColors.size();
            commands.set("#AppearanceColor" + index + ".Visible", visible);
            if (visible) commands.set("#AppearanceColor" + index + ".Text",
                    (visibleColors.get(index).equals(NpcSkinCodecAdapter.colorId(current)) ? "✓ " : "")
                            + visibleColors.get(index));
        }
        commands.set("#AppearanceColorPageText.Text", "Colors "
                + (appearanceColorPage + 1) + " / " + colorPages);
        commands.set("#AppearanceColorPreviousButton.Disabled", appearanceColorPage == 0);
        commands.set("#AppearanceColorNextButton.Disabled",
                appearanceColorPage + 1 >= colorPages);
        List<String> variants = descriptor == null ? List.of() : descriptor.variants();
        int variantPages = Math.max(1, (variants.size() + 5) / 6);
        appearanceVariantPage = Math.max(0,
                Math.min(appearanceVariantPage, variantPages - 1));
        int variantFrom = Math.min(variants.size(), appearanceVariantPage * 6);
        List<String> visibleVariants = variants.subList(variantFrom,
                Math.min(variants.size(), variantFrom + 6));
        for (int index = 0; index < 6; index++) {
            boolean visible = index < visibleVariants.size();
            commands.set("#AppearanceVariant" + index + ".Visible", visible);
            if (visible) commands.set("#AppearanceVariant" + index + ".Text",
                    (visibleVariants.get(index).equals(NpcSkinCodecAdapter.variantId(current)) ? "✓ " : "")
                            + visibleVariants.get(index));
        }
        commands.set("#AppearanceVariantPageText.Text", "Variants "
                + (appearanceVariantPage + 1) + " / " + variantPages);
        commands.set("#AppearanceVariantPreviousButton.Disabled",
                appearanceVariantPage == 0);
        commands.set("#AppearanceVariantNextButton.Disabled",
                appearanceVariantPage + 1 >= variantPages);
        commands.set("#AppearanceColorSection.Visible", !colors.isEmpty());
        commands.set("#AppearanceVariantSection.Visible", !variants.isEmpty());
        boolean missing = descriptor == null && current != null && !current.isBlank();
        commands.set("#AppearanceSelectionInfo.Text", missing
                ? "Retained missing/incompatible registry value: " + current
                : descriptor == null ? "None selected (optional field)."
                : descriptor.displayName() + "\nID: " + descriptor.cosmeticId()
                        + "\nSource: " + descriptor.source()
                        + "\nCompatibility: " + descriptor.compatibility());
        commands.set("#AppearanceSelectionInfo.Style.TextColor",
                missing ? "#e76f6f" : "#d8e5f2");
        commands.set("#AppearanceValidationStatus.Text", appearanceEditorStatus);
        commands.set("#AppearanceValidationStatus.Style.TextColor",
                appearanceEditorError ? "#e76f6f" : "#9ed7a6");
        commands.set("#AppearancePreviewCharacter.Visible", preview != null);
        commands.set("#AppearancePreviewFallback.Visible", preview == null);
    }

    private void refreshAppearanceEditorUi() {
        UICommandBuilder commands = new UICommandBuilder();
        setAppearanceEditorUi(commands);
        sendUpdate(commands, false);
    }

    private NpcAppearanceCatalogService.CatalogPage appearanceCatalogPage() {
        return editor.appearanceCatalog().query(
                appearanceCategory, appearanceSearch, appearancePage);
    }

    private String currentAppearanceSelection() {
        return appearanceDraft == null ? null : NpcSkinCodecAdapter.selection(
                appearanceDraft.currentSkin(), appearanceCategory);
    }

    private String currentAppearanceCosmeticId() {
        String encoded = currentAppearanceSelection();
        return switch (appearanceCategory) {
            case BODY_CHARACTERISTIC, FACE, EARS, MOUTH -> encoded == null ? "" : encoded;
            default -> NpcSkinCodecAdapter.partId(encoded);
        };
    }

    private NpcAppearanceCatalogService.CosmeticOptionDescriptor
            currentAppearanceDescriptor() {
        String id = currentAppearanceCosmeticId();
        if (id == null || id.isBlank()) return null;
        return editor.appearanceCatalog().snapshot().options()
                .getOrDefault(appearanceCategory, List.of()).stream()
                .filter(option -> option.cosmeticId().equals(id)).findFirst().orElse(null);
    }

    private void saveAppearanceDraft(Store<EntityStore> store) {
        requireAppearanceDraft();
        authoringSession.beginCommit();
        try {
            NpcAppearanceAuthoringService.SaveResult result =
                    editor.appearanceAuthoring().save(appearanceDraft, playerRef.getUuid());
            appearancePreview.commit(appearanceDraft, result);
            boolean liveApplied = liveStorageAuthority == null || editor.applyAppearanceLive(
                    npcName, liveStorageAuthority.npcRef(), store);
            authoringSession.markSaved(NpcAuthoringSession.EditorKind.APPEARANCE);
            appearanceEditorStatus = liveApplied
                    ? "Appearance saved atomically at revision " + result.revision() + "."
                    : "Appearance saved at revision " + result.revision()
                            + "; live NPC refresh is degraded and will recover on reload.";
            appearanceEditorError = !liveApplied;
            status = appearanceEditorStatus;
            error = !liveApplied;
            if (!liveApplied) authoringSession.degraded("APPEARANCE_LIVE_APPLY_FAILED");
            diagnostics.accept("NPC_AUTHORING_APPEARANCE_SAVE_COMPLETED timestamp="
                    + Instant.now() + " npc=" + npcName + " sessionId="
                    + authoringSession.sessionId() + " revision=" + result.revision()
                    + " persisted=true liveNpcApplied=" + liveApplied
                    + " previewCommitted=true viewerEcsMutation=false");
        } catch (RuntimeException failure) {
            authoringSession.commitFailed(NpcAuthoringSession.EditorKind.APPEARANCE,
                    failure.getMessage());
            appearanceEditorStatus = failure.getMessage() == null
                    ? "Appearance save failed; draft preserved." : failure.getMessage();
            appearanceEditorError = true;
            throw failure;
        }
    }

    private void requireAppearanceDraft() {
        if (appearanceDraft == null
                || authoringSession.activeEditor()
                        != NpcAuthoringSession.EditorKind.APPEARANCE
                || appearanceDraft.editorGeneration() != authoringSession.editorGeneration()
                || !appearanceDraft.sessionId().equals(authoringSession.sessionId())
                || !appearanceDraft.stableNpcId().equals(authoringSession.npcStableId())) {
            throw new IllegalStateException("Appearance draft is missing or stale.");
        }
    }

    private void restoreAndClearAppearanceDraft() {
        if (appearanceDraft != null) appearancePreview.restore(appearanceDraft);
        clearAppearanceDraft(false);
    }

    private void clearAppearanceDraft(boolean closeService) {
        appearanceDraft = null;
        appearanceSearch = "";
        appearancePage = 0;
        appearanceColorPage = 0;
        appearanceVariantPage = 0;
        appearanceEditorStatus = "Choose a registry-backed appearance option.";
        appearanceEditorError = false;
        if (closeService) appearancePreview.close();
    }

    private void closeAppearanceDraft() {
        if (appearanceDraft != null) appearancePreview.restore(appearanceDraft);
        clearAppearanceDraft(true);
    }

    private void setProfileEditorUi(UICommandBuilder commands) {
        commands.set("#ProfileDraftMeta.Text", "Draft " + profileDraft.draftId()
                + "  |  base r" + profileDraft.baseRevision()
                + "  |  " + profileDraft.provenance());
        commands.set("#ProfileStableIdentity.Text", "Stable ID (immutable)\n"
                + profileDraft.stableNpcId());
        commands.set("#ProfileDisplayName.Text", "Display name (read-only): "
                + profileDraft.profileName());
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            commands.set("#" + profileFieldControl(field) + ".Value",
                    profileDraft.value(field));
        }
        commands.set("#ProfileValidationStatus.Text", profileEditorStatus);
        commands.set("#ProfileValidationStatus.Style.TextColor",
                profileEditorError ? "#e76f6f" : "#9ed7a6");
        boolean generating = profileGeneration != null;
        commands.set("#ProfileGenerateButton.Disabled", generating);
        commands.set("#ProfileGenerationStatus.Text", generating
                ? "Generating a structured proposal at low priority..."
                : profileEditorStatus + " Scopes: BIOGRAPHY, PERSONALITY_VALUES, "
                        + "MOTIVATIONS_GOALS, SPEECH_MANNERISMS, FILL_MISSING, REFINE_SELECTED.");
        commands.set("#ProfileGenerateScopeInput.Value", profileGenerationScope);
        NpcProfileDraft.Proposal proposal = profileDraft.proposal();
        commands.set("#ProfileProposalPanel.Visible", proposal != null);
        if (proposal != null) {
            StringBuilder diff = new StringBuilder();
            proposal.changes().forEach((field, value) -> diff.append(field.name())
                    .append("\n  CURRENT: ").append(compact(profileDraft.value(field), 90))
                    .append("\n  PROPOSED: ").append(compact(value, 130)).append("\n\n"));
            if (!proposal.warnings().isEmpty()) {
                diff.append("WARNINGS\n").append(String.join("\n", proposal.warnings()));
            }
            commands.set("#ProfileProposalDiff.Text", diff.toString());
        }
        editor.currentProfile(npcName).ifPresent(profile -> commands.set(
                "#ProfileRelationshipsSummary.Text", "Relationships (read-only): "
                        + profile.relationships().size()
                        + ". Managed by the authoritative relationship subsystem."));
    }

    private void refreshProfileEditorUi() {
        UICommandBuilder commands = new UICommandBuilder();
        setProfileEditorUi(commands);
        sendUpdate(commands, false);
    }

    private void saveProfileDraft() {
        requireProfileDraft();
        cancelProfileGeneration();
        authoringSession.beginCommit();
        try {
            var result = editor.authoring().save(profileDraft, playerRef.getUuid());
            committed.accept(result.profile());
            authoringSession.markSaved(NpcAuthoringSession.EditorKind.PROFILE);
            status = "Profile saved at revision " + result.revision() + ".";
            error = false;
        } catch (RuntimeException failure) {
            authoringSession.commitFailed(NpcAuthoringSession.EditorKind.PROFILE,
                    failure.getMessage());
            profileEditorStatus = failure.getMessage() == null
                    ? "Profile save failed; draft preserved." : failure.getMessage();
            profileEditorError = true;
            throw failure;
        }
    }

    private void startProfileGeneration(Store<EntityStore> store, PageData data) {
        requireProfileDraft();
        cancelProfileGeneration();
        NpcProfileGenerationService service = editor.generation().orElseThrow(() ->
                new IllegalStateException("Generation provider is unavailable; manual editing remains available."));
        NpcProfileGenerationService.Scope scope = NpcProfileGenerationService.Scope.parse(
                data.profileGenerateScope == null ? profileGenerationScope
                        : data.profileGenerateScope);
        profileGenerationScope = scope.name();
        String expectedHash = profileDraft.draftHash();
        UUID expectedDraft = profileDraft.draftId();
        long expectedGeneration = authoringSession.editorGeneration();
        profileEditorStatus = "Generation queued at low priority; canonical profile is unchanged.";
        profileEditorError = false;
        profileGeneration = service.generate(new NpcProfileGenerationService.Request(
                authoringSession.sessionId(), expectedGeneration, profileDraft.baseRevision(),
                expectedHash, profileDraft.stableNpcId(), playerRef.getUuid(), scope,
                profileDraft.dirtyFields(), profileDraft));
        NpcProfileGenerationService.Handle handle = profileGeneration;
        handle.future().whenComplete((proposal, failure) ->
                store.getExternalData().getWorld().execute(() -> {
                    if (profileGeneration != handle) return;
                    profileGeneration = null;
                    if (profileDraft == null || !profileDraft.draftId().equals(expectedDraft)
                            || authoringSession.activeEditor()
                                    != NpcAuthoringSession.EditorKind.PROFILE
                            || authoringSession.editorGeneration() != expectedGeneration
                            || !profileDraft.draftHash().equals(expectedHash)) {
                        diagnostics.accept("NPC_PROFILE_GENERATION_STALE_REJECTED timestamp="
                                + Instant.now() + " requestId=" + handle.requestId());
                        return;
                    }
                    if (failure != null) {
                        profileEditorStatus = "Generation unavailable: " + rootMessage(failure)
                                + ". Manual editing remains available.";
                        profileEditorError = true;
                    } else {
                        profileDraft.setProposal(proposal);
                        profileEditorStatus = "Proposal ready. Review the field-level diff; canon is unchanged.";
                        profileEditorError = false;
                    }
                    rebuild();
                }));
        rebuild();
    }

    private void requireProfileDraft() {
        if (profileDraft == null
                || authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.PROFILE
                || profileDraft.editorGeneration() != authoringSession.editorGeneration()
                || !profileDraft.sessionId().equals(authoringSession.sessionId())) {
            throw new IllegalStateException("Profile draft is missing or stale.");
        }
    }

    private void clearProfileDraft() {
        cancelProfileGeneration();
        profileDraft = null;
        profileEditorStatus = "Draft valid.";
        profileEditorError = false;
    }

    private void cancelProfileGeneration() {
        NpcProfileGenerationService.Handle handle = profileGeneration;
        profileGeneration = null;
        if (handle != null) handle.close();
    }

    private static String profileFieldControl(NpcProfileDraft.Field field) {
        return switch (field) {
            case ROLE -> "ProfileRoleInput";
            case SELF_IDENTITY -> "ProfileSelfIdentityInput";
            case SPECIES_ARCHETYPE -> "ProfileSpeciesInput";
            case AGE_CATEGORY -> "ProfileAgeInput";
            case HOME -> "ProfileHomeInput";
            case WORKPLACE -> "ProfileWorkplaceInput";
            case PERSONALITY -> "ProfilePersonalityInput";
            case PERSONALITY_TRAITS -> "ProfileTraitsInput";
            case VALUES -> "ProfileValuesInput";
            case LIKES -> "ProfileLikesInput";
            case DISLIKES -> "ProfileDislikesInput";
            case FEARS -> "ProfileFearsInput";
            case BIOGRAPHY -> "ProfileBiographyInput";
            case PURPOSE -> "ProfilePurposeInput";
            case GOALS -> "ProfileGoalsInput";
            case SPEAKING_STYLE -> "ProfileSpeakingInput";
            case KNOWLEDGE_DOMAINS -> "ProfileKnowledgeInput";
        };
    }

    private static String compact(String text, int maximum) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static Set<NpcProfileDraft.Field> parseProfileFields(String value) {
        if (value == null || value.isBlank()) return Set.of();
        java.util.EnumSet<NpcProfileDraft.Field> fields = java.util.EnumSet.noneOf(
                NpcProfileDraft.Field.class);
        for (String token : value.split("[,\\s]+")) {
            if (!token.isBlank()) fields.add(NpcProfileDraft.Field.valueOf(
                    token.strip().toUpperCase(Locale.ROOT)));
        }
        return Set.copyOf(fields);
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
        int mouseButton = value(data.pressedMouseButton, -1);
        int requestedQuantity = authoritativeQuantityAtIntent(
                sourceSection, sourceSlot, mouseButton, clientQuantity);
        var intent = new CustomInventoryTransactionBridge.InventoryMoveIntent(
                authoringSession.sessionId(),
                authoringSession.pageGeneration(),
                sourceSection,
                sourceSlot,
                targetSection,
                targetSlot,
                requestedQuantity,
                mouseButton,
                inventoryEventSequence.incrementAndGet(),
                data.itemStackId,
                clientQuantity);
        inventoryBridge.submit(ref, store, intent,
                result -> reconcileInventoryFromAuthority(ref, store, intent, result));
    }

    private int authoritativeQuantityAtIntent(int sectionId, int slot,
            int mouseButton, int clientQuantityDiagnostic) {
        ItemContainer container;
        if (sectionId == InventoryComponent.STORAGE_SECTION_ID) {
            container = playerInventory;
        } else if (sectionId == storageWindow.getId()) {
            container = inventory.inventory();
        } else if (sectionId == inventory.armorSectionId()) {
            container = inventory.armor();
        } else if (sectionId == inventory.primarySectionId()) {
            container = inventory.hotbar();
        } else if (sectionId == inventory.offhandSectionId()) {
            container = inventory.utility();
        } else {
            return clientQuantityDiagnostic;
        }
        if (slot < 0 || slot >= container.getCapacity()) return clientQuantityDiagnostic;
        ItemStack stack = container.getItemStack((short) slot);
        if (ItemStack.isEmpty(stack)) return clientQuantityDiagnostic;
        // The client-reported ItemStack quantity remains diagnostic only. The server
        // derives the allowed amount from authoritative state and the input gesture.
        return mouseButton == 2 ? 1 : stack.getQuantity();
    }

    private void reconcileInventoryFromAuthority(Ref<EntityStore> ref,
            Store<EntityStore> store,
            CustomInventoryTransactionBridge.InventoryMoveIntent intent,
            CustomInventoryTransactionBridge.BridgeResult result) {
        if (!built || inventoryBridge == null) return;
        long refresh = inventoryRefreshGeneration.incrementAndGet();
        boolean equipmentChanged = result.type()
                == CustomInventoryTransactionBridge.ResultType.COMMITTED
                && (isEquipmentSection(intent.sourceSectionId())
                        || isEquipmentSection(intent.targetSectionId()));
        if (equipmentChanged) {
            inventory.markEquipmentCommitted();
            inventory.flush();
            applyEquipmentAndStats(store, "GEAR_TRANSACTION");
        }
        UICommandBuilder commands = new UICommandBuilder();
        setNpcProfileUi(commands);
        if (result.type() != CustomInventoryTransactionBridge.ResultType.COMMITTED) {
            status = "Inventory move rejected: " + result.reason();
            error = result.type() != CustomInventoryTransactionBridge.ResultType.NO_OP;
            commands.set("#StatusText.Text", status);
            commands.set("#StatusText.Visible", true);
            commands.set("#StatusText.Style.TextColor",
                    error ? "#e76f6f" : "#9ed7a6");
        }
        sendUpdate(commands, false);
        if (!equipmentChanged
                && result.type() == CustomInventoryTransactionBridge.ResultType.COMMITTED
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
                + " sessionId=" + authoringSession.sessionId()
                + " pageGeneration=" + authoringSession.pageGeneration()
                + " uiRefreshGeneration=" + refresh
                + " BridgeOperationId=" + result.operationId()
                + " result=" + result.type()
                + " reason=" + result.reason()
                + " sourceAfter=" + result.sourceAfter()
                + " targetAfter=" + result.targetAfter()
                + " mechanism=ATOMIC_FIXED_CAPACITY_SLOTS_REPLACEMENT"
                + " authoritativeReread=true"
                + " equipmentChanged=" + equipmentChanged
                + " equipmentRevision=" + inventory.equipmentRevision()
                + " persistencePath=" + editor.inventories().path(npcName));
    }

    private boolean isEquipmentSection(int sectionId) {
        return sectionId == inventory.armorSectionId()
                || sectionId == inventory.primarySectionId()
                || sectionId == inventory.offhandSectionId();
    }

    private void closeInventoryBridge() {
        if (inventoryBridge != null) inventoryBridge.close();
    }

    private EventData authoringEvent(String action) {
        return EventData.of("AuthoringSchemaVersion",
                        Integer.toString(NpcAuthoringEventEnvelope.CURRENT_SCHEMA_VERSION))
                .append("AuthoringSessionId", authoringSession.sessionId().toString())
                .append("AuthoringViewerPlayerId", authoringSession.viewerPlayerId().toString())
                .append("AuthoringNpcStableId", authoringSession.npcStableId().toString())
                .append("AuthoringPageGeneration",
                        Long.toString(authoringSession.pageGeneration()))
                // Dropped ItemGrid events do not resolve arbitrary Text selectors in
                // EventData. Embed the server-owned values that were current when this
                // binding was built; stale editor bindings then fail generation checks.
                .append("AuthoringEditor", authoringSession.activeEditor().name())
                .append("AuthoringEditorGeneration",
                        Long.toString(authoringSession.editorGeneration()))
                .append("AuthoringAction", action);
    }

    private String resolveAuthoringAction(PageData data) {
        if (data == null) throw new IllegalArgumentException("Missing authoring event.");
        if (data.authoringAction != null && !data.authoringAction.isBlank()) {
            return data.authoringAction.strip().toUpperCase(Locale.ROOT);
        }
        // ServerFileBrowser currently emits its own File/SearchQuery fields and
        // cannot carry page-defined metadata. It is admitted only while the
        // server-owned browser instance is active, then receives a synthesized
        // current envelope and the ADVANCED permission check below.
        if (browser != null && (data.file != null || data.searchQuery != null)) {
            return "BROWSER_EVENT";
        }
        throw new IllegalArgumentException("Unknown Authoring Studio event schema/action.");
    }

    private NpcAuthoringEventEnvelope authoringEnvelope(PageData data, String action) {
        if (data.authoringSessionId == null && "BROWSER_EVENT".equals(action)) {
            return new NpcAuthoringEventEnvelope(
                    NpcAuthoringEventEnvelope.CURRENT_SCHEMA_VERSION,
                    authoringSession.sessionId(), authoringSession.viewerPlayerId(),
                    authoringSession.npcStableId(), authoringSession.pageGeneration(),
                    authoringSession.activeEditor(), authoringSession.editorGeneration(), action);
        }
        return NpcAuthoringEventEnvelope.parse(
                parseInteger(data.authoringSchemaVersion), data.authoringSessionId,
                data.authoringViewerPlayerId, data.authoringNpcStableId,
                parseLong(data.authoringPageGeneration), data.authoringEditor,
                parseLong(data.authoringEditorGeneration), action);
    }

    private String permissionFor(String action, PageData data) {
        return switch (action) {
            case "INVENTORY_DROP" -> data != null
                    && (isEquipmentSection(value(data.sourceInventorySectionId,
                                    Integer.MIN_VALUE))
                            || isEquipmentSection(parseSection(data.section)))
                    ? NpcAuthoringPermissions.GEAR : NpcAuthoringPermissions.INVENTORY;
            case "INFINITE_AMMO", "ARMOR_VISIBILITY" -> NpcAuthoringPermissions.GEAR;
            case "VOICE_RESCAN", "OPEN_VOICE_EDITOR" -> NpcAuthoringPermissions.VOICE;
            case "OPEN_APPEARANCE_EDITOR", "APPEARANCE_PRIMARY",
                    "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH",
                    "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT",
                    "APPEARANCE_OPTION", "APPEARANCE_COLOR",
                    "APPEARANCE_COLOR_PREV", "APPEARANCE_COLOR_NEXT",
                    "APPEARANCE_VARIANT", "APPEARANCE_VARIANT_PREV",
                    "APPEARANCE_VARIANT_NEXT",
                    "APPEARANCE_RANDOMIZE", "APPEARANCE_RESET",
                    "APPEARANCE_CANCEL", "APPEARANCE_SAVE"
                    -> NpcAuthoringPermissions.APPEARANCE;
            case "OPEN_PROFILE_EDITOR", "PROFILE_FIELD", "PROFILE_SAVE",
                    "PROFILE_RESET", "PROFILE_CANCEL", "PROFILE_PROPOSAL_ACCEPT",
                    "PROFILE_PROPOSAL_ACCEPT_SELECTED", "PROFILE_PROPOSAL_DISCARD",
                    "ENTER" -> NpcAuthoringPermissions.PROFILE;
            case "PROFILE_GENERATE" -> NpcAuthoringPermissions.GENERATE;
            case "PROFILE_SCOPE" -> NpcAuthoringPermissions.PROFILE;
            case "ADVANCED_FILE_OPEN", "BROWSER_EVENT", "DELETE_PROMPT",
                    "DELETE_CANCEL", "DELETE_CONFIRM" -> NpcAuthoringPermissions.ADVANCED;
            case "DIRTY_SAVE" -> switch (authoringSession.activeEditor()) {
                case PROFILE -> NpcAuthoringPermissions.PROFILE;
                case APPEARANCE -> NpcAuthoringPermissions.APPEARANCE;
                case VOICE -> NpcAuthoringPermissions.VOICE;
                case NONE -> NpcAuthoringPermissions.OPEN;
            };
            case "CANCEL", "CLOSE_EDITOR", "DIRTY_DISCARD", "DIRTY_STAY"
                    -> NpcAuthoringPermissions.OPEN;
            default -> throw new IllegalArgumentException("Unknown authoring action.");
        };
    }

    private static Integer parseInteger(String value) {
        try { return value == null ? null : Integer.valueOf(value); }
        catch (NumberFormatException invalid) { return null; }
    }

    private static Long parseLong(String value) {
        try { return value == null ? null : Long.valueOf(value); }
        catch (NumberFormatException invalid) { return null; }
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
        authoringSession.close();
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

    private void applyEquipmentAndStats(Store<EntityStore> store, String trigger) {
        var authoritative = inventory.snapshot();
        EquipmentUpdate equipment = liveStorageAuthority == null
                ? equipmentFrom(authoritative)
                : liveStorageAuthority.applyEquipmentState(store, authoritative);
        boolean previewUpdated = false;
        if (preview != null && preview.targetApplied()) {
            try {
                preview.refreshEquipment(equipment);
                previewUpdated = true;
            } catch (RuntimeException failure) {
                diagnostics.accept("NPC_EQUIPMENT_PREVIEW_DEGRADED"
                        + " timestamp=" + Instant.now()
                        + " npc=" + npcName
                        + " trigger=" + trigger
                        + " equipmentRevision=" + inventory.equipmentRevision()
                        + " reason=" + quoted(failure.toString())
                        + " itemStateRolledBack=false");
            }
        }
        captureStats(store, false);
        diagnostics.accept("NPC_EQUIPMENT_APPLIED"
                + " timestamp=" + Instant.now()
                + " npc=" + npcName
                + " trigger=" + trigger
                + " equipmentRevision=" + inventory.equipmentRevision()
                + " liveNpcApplied=" + (liveStorageAuthority != null)
                + " previewUpdated=" + previewUpdated
                + " persistenceFlushed=true"
                + " uiRefresh=COALESCED");
    }

    private static EquipmentUpdate equipmentFrom(
            com.inigmasgames.persistentnpcs.profile.NpcInventoryState state) {
        var equipment = NpcProfileEditorService.previewEquipmentFrom(state);
        return new EquipmentUpdate(equipment.visibleArmorIds(),
                equipment.rightHandItemId(), equipment.leftHandItemId());
    }

    private void captureStats(Store<EntityStore> store, boolean refreshUi) {
        String before = statsKey();
        if (liveStorageAuthority == null) {
            statsSnapshot = null;
            statsFailure = "LIVE_NPC_UNAVAILABLE";
        } else {
            try {
                NpcStatsSnapshot candidate = statsService.capture(store,
                        liveStorageAuthority, authoringSession.sessionId(),
                        authoringSession.pageGeneration(), inventory.equipmentRevision(),
                        diagnostics);
                if (!candidate.npcStableId().equals(authoringSession.npcStableId())
                        || !candidate.npcEntityUuid().equals(
                                liveStorageAuthority.npcEntityId())
                        || candidate.equipmentRevision() != inventory.equipmentRevision()
                        || !candidate.sessionId().equals(authoringSession.sessionId())
                        || candidate.pageGeneration() != authoringSession.pageGeneration()) {
                    diagnostics.accept("NPC_STATS_SNAPSHOT_REJECTED npc=" + npcName
                            + " reason=STALE_IDENTITY_OR_GENERATION");
                    return;
                }
                statsSnapshot = candidate;
                statsFailure = "";
            } catch (RuntimeException failure) {
                statsSnapshot = null;
                statsFailure = failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage();
                diagnostics.accept("NPC_STATS_DEGRADED"
                        + " timestamp=" + Instant.now()
                        + " npc=" + npcName
                        + " reason=" + quoted(statsFailure)
                        + " inventoryAvailable=true profileAvailable=true");
            }
        }
        if (refreshUi && built && !before.equals(statsKey())) {
            UICommandBuilder commands = new UICommandBuilder();
            setStatsUi(commands);
            sendUpdate(commands, false);
        }
    }

    private String statsKey() {
        return statsSnapshot == null ? "UNAVAILABLE:" + statsFailure
                : statsSnapshot.health() + ":" + statsSnapshot.stamina() + ":"
                        + statsSnapshot.mana() + ":" + statsSnapshot.defense() + ":"
                        + statsSnapshot.equipmentRevision();
    }

    private void startStatsRefresh(Store<EntityStore> store) {
        if (liveStorageAuthority == null || statsRefreshTask != null) return;
        var world = store.getExternalData().getWorld();
        statsRefreshTask = statsScheduler.scheduleAtFixedRate(() -> {
            if (!built) return;
            world.execute(() -> {
                if (built) captureStats(store, true);
            });
        }, 2, 2, TimeUnit.SECONDS);
    }

    private void closeStatsRefresh() {
        if (statsRefreshTask != null) statsRefreshTask.cancel(false);
        statsScheduler.shutdownNow();
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
        setStatsUi(commands);
        // Equipment retains bounded snapshots for its icon/visibility presentation.
        // Storage uses the exact R118 bridge presentation: fixed-capacity snapshots
        // are UI only; InventoryUtils remains the sole mutation authority.
        commands.set("#ArmorGrid.InventorySectionId", inventory.armorSectionId());
        commands.set("#PrimaryWeaponGrid.InventorySectionId", inventory.primarySectionId());
        commands.set("#OffhandGrid.InventorySectionId", inventory.offhandSectionId());
        commands.set("#AmmunitionGrid.InventorySectionId", inventory.ammunitionSectionId());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#ArmorGrid.Slots", inventory.armor(), 0, 4,
                slot -> !new NpcEquipmentCompatibilityResolver().validateArmor(
                        inventory.armorItem((short) slot), (short) slot).compatible());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PrimaryWeaponGrid.Slots", inventory.hotbar(), 0, 1,
                slot -> !new NpcEquipmentCompatibilityResolver()
                        .validatePrimaryWeapon(inventory.loadoutItem(
                                NpcInventoryRepository.Session.PRIMARY_SLOT)).compatible());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#OffhandGrid.Slots", inventory.utility(), 0, 1,
                slot -> !new NpcEquipmentCompatibilityResolver().validateOffhand(
                        inventory.loadoutItem(NpcInventoryRepository.Session.OFFHAND_SLOT),
                        inventory.loadoutItem(NpcInventoryRepository.Session.PRIMARY_SLOT))
                                .compatible());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#AmmunitionGrid.Slots", inventory.hotbar(), 1, 1,
                slot -> !new NpcEquipmentCompatibilityResolver().validateAmmunition(
                        inventory.loadoutItem(NpcInventoryRepository.Session.AMMUNITION_SLOT),
                        inventory.loadoutItem(NpcInventoryRepository.Session.PRIMARY_SLOT))
                                .compatible());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#NpcInventoryGrid.Slots", inventory.inventory());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerInventory);
    }

    private void setProfileFilesUi(UICommandBuilder commands) {
        editor.currentProfile(npcName).ifPresent(profile -> {
            commands.set("#ProfileNameValue.Text", profile.name());
            commands.set("#ProfileRoleValue.Text", profile.role());
            String biography = profile.biography() == null ? "" : profile.biography().strip();
            commands.set("#ProfileBiographyValue.Text", biography.isBlank()
                    ? "Biography not authored yet."
                    : biography.length() > 180 ? biography.substring(0, 177) + "..." : biography);
        });
        commands.set("#ProfileReadinessValue.Text",
                voiceSamples.ready() ? "PROFILE + VOICE READY" : "PROFILE READY · VOICE NEEDS REFERENCE");
        commands.set("#ProfileReadinessValue.Style.TextColor",
                voiceSamples.ready() ? "#72d58b" : "#d0a65a");
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
        commands.set("#PrimaryWeaponEmptyIcon.Visible",
                ItemStack.isEmpty(inventory.loadoutItem((short) 0)));
        commands.set("#OffhandEmptyIcon.Visible",
                ItemStack.isEmpty(inventory.loadoutItem((short) 1)));
        commands.set("#AmmoEmptyIcon.Visible",
                ItemStack.isEmpty(inventory.loadoutItem((short) 2)));
        setAmmunitionUi(commands);
        setArmorVisibilityUi(commands);
    }

    private void setStatsUi(UICommandBuilder commands) {
        commands.set("#HealthStat #Value.Text", statText(
                statsSnapshot == null ? null : statsSnapshot.health().orElse(null)));
        commands.set("#StaminaStat #Value.Text", statText(
                statsSnapshot == null ? null : statsSnapshot.stamina().orElse(null)));
        commands.set("#ManaStat #Value.Text", statText(
                statsSnapshot == null ? null : statsSnapshot.mana().orElse(null)));
        String defense = statsSnapshot == null || statsSnapshot.defense().isEmpty()
                ? "Unavailable"
                : format(statsSnapshot.defense().get().value()) + " base";
        commands.set("#DefenseStat #Value.Text", defense);
    }

    private static String statText(NpcStatsSnapshotService.StatValue value) {
        return value == null ? "Unavailable"
                : format(value.current()) + " / " + format(value.maximum());
    }

    private static String format(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void setAppearanceUi(UICommandBuilder commands) {
        commands.set("#NpcPreviewName.Text", npcName);
        commands.set("#NpcCharacterPreview.Visible", preview != null);
        commands.set("#NpcPreviewUnavailable.Visible", preview == null);
    }

    private void setAmmunitionUi(UICommandBuilder commands) {
        boolean relevant = inventory.ammunitionPolicyRelevant();
        boolean featureEnabled = NpcEquipmentRules.infiniteAmmunitionFeatureEnabled();
        commands.set("#InfiniteAmmoCheckBox.Value", inventory.infiniteAmmunition());
        commands.set("#InfiniteAmmoCheckBox.Disabled", !featureEnabled || !relevant);
        commands.set("#InfiniteAmmoHint.Text", !featureEnabled
                ? "Disabled by server policy (" + NpcEquipmentRules.INFINITE_AMMUNITION_CONFIG + ")."
                : relevant
                ? "Physical ammo stack selected; policy is "
                        + (inventory.infiniteAmmunitionEffective() ? "active." : "off.")
                : inventory.infiniteAmmunition()
                        ? "Saved policy is inactive: loadout compatibility must be resolved."
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
                .append(new KeyedCodec<>("AuthoringSchemaVersion", Codec.STRING),
                        (data, value) -> data.authoringSchemaVersion = value,
                        data -> data.authoringSchemaVersion).add()
                .append(new KeyedCodec<>("AuthoringSessionId", Codec.STRING),
                        (data, value) -> data.authoringSessionId = value,
                        data -> data.authoringSessionId).add()
                .append(new KeyedCodec<>("AuthoringViewerPlayerId", Codec.STRING),
                        (data, value) -> data.authoringViewerPlayerId = value,
                        data -> data.authoringViewerPlayerId).add()
                .append(new KeyedCodec<>("AuthoringNpcStableId", Codec.STRING),
                        (data, value) -> data.authoringNpcStableId = value,
                        data -> data.authoringNpcStableId).add()
                .append(new KeyedCodec<>("AuthoringPageGeneration", Codec.STRING),
                        (data, value) -> data.authoringPageGeneration = value,
                        data -> data.authoringPageGeneration).add()
                .append(new KeyedCodec<>("AuthoringEditor", Codec.STRING),
                        (data, value) -> data.authoringEditor = value,
                        data -> data.authoringEditor).add()
                .append(new KeyedCodec<>("AuthoringEditorGeneration", Codec.STRING),
                        (data, value) -> data.authoringEditorGeneration = value,
                        data -> data.authoringEditorGeneration).add()
                .append(new KeyedCodec<>("AuthoringAction", Codec.STRING),
                        (data, value) -> data.authoringAction = value,
                        data -> data.authoringAction).add()
                .append(new KeyedCodec<>("ProfileField", Codec.STRING),
                        (data, value) -> data.profileField = value,
                        data -> data.profileField).add()
                .append(new KeyedCodec<>("ProfileFieldValue", Codec.STRING),
                        (data, value) -> data.profileFieldValue = value,
                        data -> data.profileFieldValue).add()
                .append(new KeyedCodec<>("ProfileGenerateScope", Codec.STRING),
                        (data, value) -> data.profileGenerateScope = value,
                        data -> data.profileGenerateScope).add()
                .append(new KeyedCodec<>("ProfileProposalSelection", Codec.STRING),
                        (data, value) -> data.profileProposalSelection = value,
                        data -> data.profileProposalSelection).add()
                .append(new KeyedCodec<>("AppearancePrimary", Codec.STRING),
                        (data, value) -> data.appearancePrimary = value,
                        data -> data.appearancePrimary).add()
                .append(new KeyedCodec<>("AppearanceCategory", Codec.STRING),
                        (data, value) -> data.appearanceCategory = value,
                        data -> data.appearanceCategory).add()
                .append(new KeyedCodec<>("AppearanceSearch", Codec.STRING),
                        (data, value) -> data.appearanceSearch = value,
                        data -> data.appearanceSearch).add()
                .append(new KeyedCodec<>("AppearanceOptionId", Codec.STRING),
                        (data, value) -> data.appearanceOptionId = value,
                        data -> data.appearanceOptionId).add()
                .append(new KeyedCodec<>("AppearanceColorId", Codec.STRING),
                        (data, value) -> data.appearanceColorId = value,
                        data -> data.appearanceColorId).add()
                .append(new KeyedCodec<>("AppearanceVariantId", Codec.STRING),
                        (data, value) -> data.appearanceVariantId = value,
                        data -> data.appearanceVariantId).add()
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
        private String authoringSchemaVersion;
        private String authoringSessionId;
        private String authoringViewerPlayerId;
        private String authoringNpcStableId;
        private String authoringPageGeneration;
        private String authoringEditor;
        private String authoringEditorGeneration;
        private String authoringAction;
        private String profileField;
        private String profileFieldValue;
        private String profileGenerateScope;
        private String profileProposalSelection;
        private String appearancePrimary;
        private String appearanceCategory;
        private String appearanceSearch;
        private String appearanceOptionId;
        private String appearanceColorId;
        private String appearanceVariantId;
    }
}
