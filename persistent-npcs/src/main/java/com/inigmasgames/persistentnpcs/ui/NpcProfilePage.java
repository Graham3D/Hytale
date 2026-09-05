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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserConfig;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserEventData;
import com.hypixel.hytale.server.core.ui.browser.ServerFileBrowser;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
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
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService.Snapshot;
import com.inigmasgames.persistentnpcs.voice.VoiceClientCaptureContract;
import com.inigmasgames.persistentnpcs.voice.VoiceRecorderControlPolicy;
import com.inigmasgames.persistentnpcs.voice.VoiceWaveformPresentation;
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
    private static final String APPEARANCE_NATIVE_ICON_RESOLVED =
            "APPEARANCE_NATIVE_ICON_RESOLVED";
    private static final String APPEARANCE_NATIVE_ICON_MISSING =
            "APPEARANCE_NATIVE_ICON_MISSING";
    private static final String APPEARANCE_ICON_FRAMING_RESOLVED =
            "APPEARANCE_ICON_FRAMING_RESOLVED";
    private static final String APPEARANCE_COLOR_ICON_STATE_CHANGED =
            "APPEARANCE_COLOR_ICON_STATE_CHANGED";
    private static final String APPEARANCE_COLOR_ICON_STATE_UNCHANGED =
            "APPEARANCE_COLOR_ICON_STATE_UNCHANGED";
    /**
     * ItemGrid must receive a literal positive window section in its construction
     * document. WindowManager allocates monotonically increasing positive IDs for
     * the lifetime of a connection, so the original probe-only 1..8 bundle was not
     * sufficient for repeated Profile opens.
     */
    private static final int MAX_PACKAGED_NPC_SECTION_ID = 1024;
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "INVENTORY_DROP", "NPC_PAGE_PREV", "NPC_PAGE_NEXT", "PLAYER_PAGE_PREV", "PLAYER_PAGE_NEXT",
            "NAV_OVERVIEW", "CANCEL", "ENTER", "DELETE_PROMPT",
            "DELETE_CANCEL", "DELETE_CONFIRM", "ADVANCED_FILE_OPEN",
            "BROWSER_EVENT", "INFINITE_AMMO", "ARMOR_VISIBILITY",
            "VOICE_RESCAN", "OPEN_PROFILE_EDITOR", "OPEN_APPEARANCE_EDITOR",
            "OPEN_VOICE_EDITOR", "CLOSE_EDITOR", "DIRTY_SAVE",
            "DIRTY_DISCARD", "DIRTY_STAY", "PROFILE_FIELD", "PROFILE_SAVE",
            "PROFILE_RESET", "PROFILE_CANCEL", "PROFILE_GENERATE",
            "PROFILE_CATEGORY", "PROFILE_SCOPE",
            "PROFILE_PROPOSAL_ACCEPT", "PROFILE_PROPOSAL_ACCEPT_SELECTED",
            "PROFILE_PROPOSAL_DISCARD", "APPEARANCE_PRIMARY",
            "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH", "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT",
            "APPEARANCE_OPTION", "APPEARANCE_COLOR",
            "APPEARANCE_VARIANT", "APPEARANCE_VARIANT_PREV",
            "APPEARANCE_VARIANT_NEXT", "APPEARANCE_RANDOMIZE", "APPEARANCE_RESET",
            "APPEARANCE_CANCEL", "APPEARANCE_SAVE", "VOICE_SELECT",
            "VOICE_RECORD", "VOICE_STOP", "VOICE_PLAY_DRAFT", "VOICE_PLAY_SAVED",
            "VOICE_STOP_PLAYBACK", "VOICE_RECORD_AGAIN", "VOICE_DELETE_DRAFT",
            "VOICE_SAVE", "VOICE_DELETE_SAVED_PROMPT", "VOICE_DELETE_SAVED_CONFIRM",
            "VOICE_DELETE_SAVED_CANCEL", "VOICE_PLAY_STOP", "VOICE_DELETE");
    private static final Set<String> NON_LOCKING_APPEARANCE_ACTIONS = Set.of(
            "APPEARANCE_PRIMARY", "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH",
            "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT", "APPEARANCE_OPTION",
            "APPEARANCE_COLOR", "APPEARANCE_VARIANT", "APPEARANCE_VARIANT_PREV",
            "APPEARANCE_VARIANT_NEXT", "APPEARANCE_RANDOMIZE", "APPEARANCE_RESET");
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
    private int npcInventoryPage;
    private int playerInventoryPage;
    private long inventoryViewRevision;
    private com.inigmasgames.persistentnpcs.stats.NpcStatState savedVitals;
    private java.util.Optional<Boolean> statInvulnerable = java.util.Optional.empty();
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
    private ProfileCategory profileCategory = ProfileCategory.BASIC_INFO;
    private String profileEditorStatus = "Draft valid.";
    private boolean profileEditorError;
    private String profileGenerationScope = "BIOGRAPHY";

    private enum ProfileCategory {
        BASIC_INFO("BasicInfo"), BACKGROUND("Background"), PERSONALITY("Personality"),
        VALUES_BELIEFS("ValuesBeliefs"), MOTIVATIONS("Motivations"),
        RELATIONSHIPS("Relationships"), SPEECH_STYLE("SpeechStyle"), NOTES("Notes");

        private final String resourceName;
        ProfileCategory(String resourceName) { this.resourceName = resourceName; }
        String resource() { return "Pages/ProfileEditor/" + resourceName + ".ui"; }
    }
    private NpcAppearanceDraft appearanceDraft;
    private final AppearanceUiState appearanceUiState = new AppearanceUiState();
    private final AppearancePreviewGate appearancePreviewGate = new AppearancePreviewGate();
    private int appearancePage;
    private String appearancePaletteKey = "";
    private String appearancePreviewHash = "";
    private String appearanceRequestedPreviewHash = "";
    private boolean appearanceGridMounted;
    private final Set<String> appearanceThumbnailReferencesLogged = new java.util.HashSet<>();
    private long appearanceThumbnailCardBuildCount;
    private long appearanceThumbnailGridRebuildCount;
    private long appearancePreviewJobsScheduled;
    private long appearancePreviewJobsCoalesced;
    private long appearancePreviewJobsApplied;
    private ScheduledFuture<?> appearanceSearchTask;
    private long appearanceSearchGeneration;
    private String appearanceCatalogRefreshReason = "INITIAL";
    private final NpcAppearancePreviewService appearancePreview;
    private PrimaryCategory appearancePrimary = PrimaryCategory.BODY;
    private Category appearanceCategory = Category.BODY_CHARACTERISTIC;
    private String appearanceSearch = "";
    private String appearancePendingSearch = "";
    private int appearanceVariantPage;
    private String appearanceEditorStatus = "Choose a registry-backed appearance option.";
    private boolean appearanceEditorError;
    private final NpcVoiceRecordingService voiceRecorder;
    private NpcVoiceRecordingService.Handle voiceRecording;
    private ScheduledFuture<?> voiceRefreshTask;
    private Snapshot voiceSnapshot;

    public NpcProfilePage(
            PlayerRef playerRef,
            String npcName,
            boolean update,
            NpcProfileEditorService editor,
            ItemContainer playerInventory,
            NativeNpcInventoryController.LiveStorageAuthority liveStorageAuthority,
            NpcAuthoringSession authoringSession,
            NpcMeshPreviewSession preview,
            NpcVoiceRecordingService voiceRecorder,
            Consumer<NpcProfile> committed,
            BiConsumer<Ref<EntityStore>, Store<EntityStore>> deleted,
            Consumer<String> diagnostics) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.npcName = editor.currentProfile(npcName).map(NpcProfile::name).orElse(npcName);
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
        this.voiceRecorder = voiceRecorder;
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
        authoringSession.addCleanup("voice-recorder", this::closeVoiceRecorder);
        authoringSession.addCleanup("appearance-preview-gate", appearancePreviewGate::close);
    }

    public ContainerWindow[] windows() {
        return inventory.windows();
    }

    /** Open-time lifecycle status; a degraded appearance never prevents Studio access. */
    public void setInitialStatus(String message, boolean failure) {
        if (message == null || message.isBlank()) return;
        diagnostics.accept("NPC_PROFILE_INITIAL_STATUS npc=" + npcName
                + " failure=" + failure + " message=" + quoted(message));
        if (!failure) return; // Successful lifecycle diagnostics belong in logs, not the front page.
        status = message;
        error = failure;
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
        boolean profileEditor = authoringSession.activeEditor()
                == NpcAuthoringSession.EditorKind.PROFILE;
        boolean appearanceEditor = authoringSession.activeEditor()
                == NpcAuthoringSession.EditorKind.APPEARANCE;
        boolean voiceEditor = authoringSession.activeEditor()
                == NpcAuthoringSession.EditorKind.VOICE;
        boolean contextualEditor = authoringSession.activeEditor()
                != NpcAuthoringSession.EditorKind.NONE && !profileEditor
                && !appearanceEditor && !voiceEditor;
        // The recorder is a child editor. Prevent the native Back action from
        // dismissing the whole page; its BackButton is bound below to the normal
        // CLOSE_EDITOR path. The Studio itself retains native page dismissal.
        setLifetime(voiceEditor
                ? CustomPageLifetime.CantClose
                : CustomPageLifetime.CanDismiss);
        commands.append("Pages/ImmersiveNpcProfile.ui");
        // R120: ItemGrid rejects .Slots if its inventory section was not present
        // during construction. The compact Profile documents retain the R118
        // section-bound construction and flags; only cell geometry differs:
        // each grid is born section-bound before any slot snapshot is sent.
        commands.append("#NpcGridHost",
                boundNpcGridDocument(storageWindow.getId()));
        commands.append("#PlayerGridHost",
                "Pages/ProfileInventory/PlayerStorage.ui");
        commands.set("#ProfileTitle.Text", npcName + "'s NPC Profile");
        commands.set("#ProfilePanelTitle.Text", npcName + "'s Profile");
        commands.set("#NpcInventoryTitle.Text", npcName + "'s Inventory");
        commands.set("#PlayerInventoryTitle.Text", playerRef.getUsername() + "'s Inventory");
        commands.set("#AuthoringBackNavigation.Visible", voiceEditor);
        setOverviewNavigationUi(commands);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#OverviewButton", authoringEvent("NAV_OVERVIEW"));
        for (String side : new String[] { "Npc", "Player" }) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + side + "PagePrev",
                    authoringEvent(side.toUpperCase(Locale.ROOT) + "_PAGE_PREV"));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + side + "PageNext",
                    authoringEvent(side.toUpperCase(Locale.ROOT) + "_PAGE_NEXT"));
        }
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

        bindVoiceRecorderEvents(events);
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
        commands.set("#ProfileEditorPage.Visible", profileEditor);
        if (profileEditor && profileDraft != null) {
            setProfileEditorUi(commands);
            mountProfileEditorForm(commands, events, false);
        }
        commands.set("#AppearanceEditorPage.Visible", appearanceEditor);
        appearanceGridMounted = false;
        appearancePaletteKey = "";
        appearanceUiState.remount();
        if (appearanceEditor && appearanceDraft != null) {
            setAppearanceEditorUi(commands, events);
            appearanceUiState.seed(commands);
        }
        bindAppearanceEditorEvents(events);
        commands.set("#VoiceRecorderPage.Visible", voiceEditor);
        if (voiceEditor && voiceRecording != null) setVoiceRecorderUi(commands);
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
        commands.set("#ProfilePage.Visible", !browsing && !profileEditor
                && !appearanceEditor && !voiceEditor && !contextualEditor);
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
        startVoiceRefresh(store);
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
        AppearanceEventTrace appearanceEvent = null;
        try {
            String authoringAction = resolveAuthoringAction(data);
            if (authoringAction.startsWith("APPEARANCE_")) {
                appearanceEvent = beginAppearanceEvent(authoringAction, data);
            }
            authoringSession.validate(authoringEnvelope(data, authoringAction),
                    ALLOWED_ACTIONS, permissionFor(authoringAction, data));
            if (authoringAction.startsWith("NPC_PAGE_") || authoringAction.startsWith("PLAYER_PAGE_")) {
                ProfileInventoryPaging.requireRevision(data.inventoryViewRevision, inventoryViewRevision);
                if (authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.NONE)
                    throw new IllegalStateException("Return to the Profile before changing inventory pages.");
                int delta = authoringAction.endsWith("NEXT") ? 1 : -1;
                if (authoringAction.startsWith("NPC_")) npcInventoryPage = npcPaging().shifted(delta);
                else playerInventoryPage = playerPaging().shifted(delta);
                inventoryViewRevision++;
                // Remount presentation/bindings only, retaining the same native window and containers.
                rebuild();
                return;
            }
            if ("NAV_OVERVIEW".equals(authoringAction)) {
                if (authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.NONE) {
                    throw new IllegalStateException("Return to the Profile before navigating.");
                }
                UICommandBuilder commands = new UICommandBuilder();
                setOverviewNavigationUi(commands);
                // Selection only: never rebuild section-bound grids or move items.
                sendUpdate(commands, false);
                return;
            }
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
                profileCategory = ProfileCategory.BASIC_INFO;
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
                appearancePage = 0;
                appearanceSearch = "";
                appearancePendingSearch = "";


                appearanceVariantPage = 0;
                appearanceEditorStatus = "Draft valid. Registry snapshot is pinned for this page.";
                appearanceEditorError = false;
                rebuild();
                return;
            }
            if ("OPEN_VOICE_EDITOR".equals(authoringAction)) {
                if (voiceRecorder == null) throw new IllegalStateException(
                        "Voice Recorder is unavailable in this server session.");
                long generation = authoringSession.openEditor(
                        NpcAuthoringSession.EditorKind.VOICE);
                try {
                    voiceRecording = voiceRecorder.open(playerRef.getUuid(),
                            authoringSession.sessionId(), authoringSession.npcStableId(),
                            npcName, authoringSession.pageGeneration(), generation,
                            new VoiceClientCaptureContract(java.util.Optional.ofNullable(
                                    store.getComponent(ref,
                                            PlayerSettings.getComponentType()))
                                    .map(PlayerSettings::voiceSettings)
                                    .map(value -> value.voiceInputMode()).orElse(null)));
                    voiceSnapshot = voiceRecording.snapshot();
                } catch (RuntimeException failure) {
                    authoringSession.closeEditor(false);
                    throw failure;
                }
                rebuild();
                return;
            }
            if ("CLOSE_EDITOR".equals(authoringAction)) {
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.VOICE) {
                    quiesceVoiceRecorderForBack();
                }
                if (authoringSession.isDirty(authoringSession.activeEditor())) {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#DirtyEditorConfirmPage.Visible", true);
                    sendUpdate(commands, false);
                    return;
                }
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE) {
                    restoreAndClearAppearanceDraft();
                } else if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.VOICE) {
                    closeVoiceRecorder();
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
                } else if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.VOICE) {
                    requireVoiceRecorder();
                    voiceRecording.save();
                    voiceSamples = editor.rescanVoiceSamples(npcName);
                } else {
                    authoringSession.markSaved(authoringSession.activeEditor());
                }
                authoringSession.closeEditor(false);
                clearProfileDraft();
                clearAppearanceDraft(false);
                closeVoiceRecorder();
                rebuild();
                return;
            }
            if ("DIRTY_DISCARD".equals(authoringAction)) {
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE) {
                    restoreAndClearAppearanceDraft();
                } else if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.VOICE) {
                    closeVoiceRecorder();
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
                handleAppearanceAction(store, data, authoringAction, appearanceEvent);
                if (!appearanceEvent.terminal && appearanceEvent.currentAtReceive) {
                    acknowledgeAppearanceEvent(appearanceEvent, "handlerFallback=true");
                }
                return;
            }
            if (authoringAction.startsWith("VOICE_") && !"VOICE_RESCAN".equals(authoringAction)) {
                handleVoiceAction(data, authoringAction);
                return;
            }
            if ("PROFILE_CATEGORY".equals(authoringAction)) {
                requireProfileDraft();
                validateProfileCategory(profileCategory);
                profileCategory = ProfileCategory.valueOf(
                        data.profileCategory.toUpperCase(Locale.ROOT));
                refreshProfileEditorForm();
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
                refreshProfileEditorForm();
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
                refreshProfileEditorForm();
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
                profileDraft.acceptProposal(Set.of(NpcProfileDraft.Field.BIOGRAPHY));
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.PROFILE);
                profileEditorStatus = "Proposal accepted into the draft. Review, then Save Profile.";
                profileEditorError = false;
                refreshProfileEditorForm();
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
                refreshProfileEditorForm();
                return;
            }
            if ("PROFILE_PROPOSAL_DISCARD".equals(authoringAction)) {
                requireProfileDraft();
                profileDraft.discardProposal();
                profileEditorStatus = "Generated proposal discarded; canonical profile unchanged.";
                profileEditorError = false;
                refreshProfileEditorForm();
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
            if (appearanceEvent != null) {
                rejectAppearanceEvent(appearanceEvent, failure.getMessage());
                if (!appearanceEvent.currentAtReceive) return;
                status = failure.getMessage() == null ? "Appearance operation failed."
                        : failure.getMessage();
                error = true;
                if (authoringSession.activeEditor()
                        == NpcAuthoringSession.EditorKind.APPEARANCE
                        && appearanceDraft != null) {
                    appearanceUiState.observeFailure(status);
                    appearanceEditorStatus = status;
                    appearanceEditorError = true;
                    refreshAppearanceEditorUi(appearanceEvent);
                } else if (!appearanceEvent.terminal) {
                    acknowledgeAppearanceEvent(appearanceEvent,
                            "handlerStateChanged=true error=" + quoted(status));
                }
                return;
            }
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
                appearanceUiState.observeFailure(status);
                appearanceEditorStatus = status;
                appearanceEditorError = true;
                refreshAppearanceEditorUi();
                return;
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
        for (ProfileCategory category : ProfileCategory.values()) {
            String selector = "#ProfileCategory" + category.resourceName;
            events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    authoringEvent("PROFILE_CATEGORY")
                            .append("ProfileCategory", category.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileSaveButton", authoringEvent("PROFILE_SAVE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileResetButton", authoringEvent("PROFILE_RESET"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#ProfileCancelButton", authoringEvent("PROFILE_CANCEL"));
    }

    private void bindAppearanceEditorEvents(UIEventBuilder events) {
        for (PrimaryCategory primary : PrimaryCategory.values()) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearancePrimary" + primary.name(),
                    authoringEvent("APPEARANCE_PRIMARY")
                            .append("AppearancePrimary", primary.name()), false);
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceSearchButton", authoringEvent("APPEARANCE_SEARCH")
                        .append("@AppearanceSearch", "#AppearanceSearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged,
                "#AppearanceSearchInput", authoringEvent("APPEARANCE_SEARCH")
                        .append("@AppearanceSearch", "#AppearanceSearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceVariantPreviousButton", authoringEvent("APPEARANCE_VARIANT_PREV"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceVariantNextButton", authoringEvent("APPEARANCE_VARIANT_NEXT"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceRandomizeButton", authoringEvent("APPEARANCE_RANDOMIZE"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceResetButton", authoringEvent("APPEARANCE_RESET"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceCancelButton", authoringEvent("APPEARANCE_CANCEL"), true);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceSaveButton", authoringEvent("APPEARANCE_SAVE"), true);
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#AppearanceBackButton", authoringEvent("APPEARANCE_CANCEL"), true);
    }

    private void bindAppearanceCatalogEvents(UIEventBuilder events, String pageHash,
            List<Category> categories, List<String> optionIds, String currentId,
            String currentColor, List<String> visibleVariants) {
        for (int index = 0; index < categories.size(); index++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceCategory" + index,
                    authoringEvent("APPEARANCE_CATEGORY")
                            .append("AppearanceCategory", categories.get(index).name()), false);
        }
        for (int index = 0; index < optionIds.size(); index++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceOption" + index + " #Choice",
                    authoringEvent("APPEARANCE_OPTION")
                            .append("AppearanceCatalogHash", pageHash)
                            .append("AppearanceOptionId", optionIds.get(index)), false);
        }
        for (String direction : new String[] { "PREV", "NEXT" }) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearancePage" + direction,
                    authoringEvent("APPEARANCE_PAGE_" + direction)
                            .append("AppearanceCatalogHash", pageHash), false);
        }
        for (int index = 0; index < visibleVariants.size(); index++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceVariant" + index,
                    authoringEvent("APPEARANCE_VARIANT")
                            .append("AppearanceCatalogHash", pageHash)
                            .append("AppearanceOptionId", currentId)
                            .append("AppearanceColorId", currentColor)
                            .append("AppearanceVariantId", visibleVariants.get(index)), false);
        }
    }

    private void bindAppearanceColors(UIEventBuilder events, List<String> colors,
            String pageHash, String currentId, String currentVariant) {
        for (int index = 0; index < colors.size(); index++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AppearanceColor" + index + " #Choice",
                    authoringEvent("APPEARANCE_COLOR")
                            .append("AppearanceCatalogHash", pageHash)
                            .append("AppearanceOptionId", currentId)
                            .append("AppearanceColorId", colors.get(index))
                            .append("AppearanceVariantId", currentVariant), false);
        }
    }

    private void bindVoiceRecorderEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceBackButton", authoringEvent("CLOSE_EDITOR"));
        for (VoiceSampleType type : VoiceSampleType.values()) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#VoiceEmotion" + type.name(), voiceEvent("VOICE_SELECT")
                            .append("VoiceEmotion", type.name()));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceRecordButton", voiceEvent("VOICE_RECORD"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoicePlayStopButton", voiceEvent("VOICE_PLAY_STOP"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceDeleteButton", voiceEvent("VOICE_DELETE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceSaveButton", voiceEvent("VOICE_SAVE"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceDeleteSavedConfirmButton", voiceEvent("VOICE_DELETE_SAVED_CONFIRM"));
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#VoiceDeleteSavedCancelButton", voiceEvent("VOICE_DELETE_SAVED_CANCEL"));
        if (authoringSession.activeEditor() == NpcAuthoringSession.EditorKind.VOICE) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#AuthoringBackButton", authoringEvent("CLOSE_EDITOR"));
        }
    }

    private EventData voiceEvent(String action) {
        EventData event = authoringEvent(action);
        return voiceRecording == null ? event
                : event.append("VoiceRecordingGeneration",
                        Long.toString(voiceRecording.generation()));
    }

    private void handleVoiceAction(PageData data, String action) {
        requireVoiceRecorder();
        long suppliedGeneration = parseLong(data.voiceRecordingGeneration) == null
                ? -1 : parseLong(data.voiceRecordingGeneration);
        if (suppliedGeneration != voiceRecording.generation()) {
            throw new IllegalStateException("Stale Voice Recorder action rejected.");
        }
        switch (action) {
            case "VOICE_SELECT" -> voiceRecording.select(VoiceSampleType.valueOf(
                    data.voiceEmotion == null ? "" : data.voiceEmotion
                            .strip().toUpperCase(Locale.ROOT)));
            case "VOICE_RECORD" -> voiceRecording.record();
            case "VOICE_STOP" -> voiceRecording.stop();
            case "VOICE_PLAY_DRAFT" -> voiceRecording.playDraft();
            case "VOICE_PLAY_SAVED" -> voiceRecording.playSaved();
            case "VOICE_STOP_PLAYBACK" -> voiceRecording.stopPlayback();
            case "VOICE_RECORD_AGAIN" -> voiceRecording.recordAgain();
            case "VOICE_DELETE_DRAFT" -> {
                voiceRecording.deleteDraft();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.VOICE);
            }
            case "VOICE_PLAY_STOP" -> {
                Snapshot snapshot = voiceRecording.snapshot();
                if (snapshot.state() == NpcVoiceRecordingService.State.ARMED
                        || snapshot.state() == NpcVoiceRecordingService.State.RECORDING) {
                    voiceRecording.stop();
                } else if (snapshot.state() == NpcVoiceRecordingService.State.PLAYING) {
                    voiceRecording.stopPlayback();
                } else if (snapshot.draftAvailable()) {
                    voiceRecording.playDraft();
                } else {
                    voiceRecording.playSaved();
                }
            }
            case "VOICE_DELETE" -> {
                Snapshot snapshot = voiceRecording.snapshot();
                if (snapshot.draftAvailable()) {
                    voiceRecording.deleteDraft();
                    authoringSession.markSaved(NpcAuthoringSession.EditorKind.VOICE);
                } else if (snapshot.savedStates().getOrDefault(snapshot.selected(),
                        VoicePresetRepository.SampleState.MISSING)
                        != VoicePresetRepository.SampleState.MISSING) {
                    showVoiceDeleteConfirmation(snapshot);
                    return;
                } else {
                    throw new IllegalStateException("No draft or saved sample is available to delete.");
                }
            }
            case "VOICE_SAVE" -> {
                voiceRecording.save();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.VOICE);
                voiceSamples = editor.rescanVoiceSamples(npcName);
            }
            case "VOICE_DELETE_SAVED_PROMPT" -> {
                showVoiceDeleteConfirmation(voiceRecording.snapshot());
                return;
            }
            case "VOICE_DELETE_SAVED_CONFIRM" -> {
                voiceRecording.deleteSaved();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.VOICE);
                voiceSamples = editor.rescanVoiceSamples(npcName);
                hideVoiceDeleteConfirmation();
            }
            case "VOICE_DELETE_SAVED_CANCEL" -> hideVoiceDeleteConfirmation();
            default -> throw new IllegalArgumentException("Unknown Voice Recorder action.");
        }
        voiceSnapshot = voiceRecording.snapshot();
        if (voiceSnapshot.draftAvailable()) {
            authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.VOICE);
        }
        rebuild();
    }

    private void hideVoiceDeleteConfirmation() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#VoiceDeleteConfirmPage.Visible", false);
        sendUpdate(commands, false);
    }

    private void showVoiceDeleteConfirmation(Snapshot snapshot) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#VoiceDeleteWarning.Text", snapshot.selected()
                == VoiceSampleType.REFERENCE
                        ? "Deleting Reference makes this NPC voice profile invalid until a new Reference is saved. The current file will be moved to recoverable trash."
                        : "Delete saved " + snapshot.selected().label()
                                + "? The NPC will fall back to Reference. The file is moved to recoverable trash.");
        commands.set("#VoiceDeleteConfirmPage.Visible", true);
        sendUpdate(commands, false);
    }

    private void requireVoiceRecorder() {
        if (authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.VOICE
                || voiceRecording == null) {
            throw new IllegalStateException("Voice Recorder is not active.");
        }
    }

    private void setVoiceRecorderUi(UICommandBuilder commands) {
        Snapshot snapshot = voiceRecording.snapshot();
        voiceSnapshot = snapshot;
        commands.set("#VoiceRecorderTitle.Text", "VOICE RECORDER - " + npcName.toUpperCase(Locale.ROOT));
        commands.set("#VoiceRecorderMeta.Text", "Recording generation "
                + snapshot.recordingGeneration() + " · " + snapshot.captureContract());
        commands.set("#VoiceSelectedEmotion.Text", snapshot.selected().label().toUpperCase(Locale.ROOT));
        commands.set("#VoiceRecorderState.Text", snapshot.state().name());
        boolean recording = snapshot.state() == NpcVoiceRecordingService.State.ARMED
                || snapshot.state() == NpcVoiceRecordingService.State.RECORDING;
        commands.set("#VoiceRecordingIndicator.Visible", recording);
        commands.set("#VoiceRecordingIndicator.Text", snapshot.state()
                == NpcVoiceRecordingService.State.ARMED
                        ? "● ARMED" : "● RECORDING");
        long elapsedSeconds = snapshot.elapsedMillis() / 1000L;
        long maximumSeconds = snapshot.maximumMillis() / 1000L;
        commands.set("#VoiceElapsed.Text", String.format(Locale.ROOT,
                "%02d:%02d / %02d:%02d",
                elapsedSeconds / 60L, elapsedSeconds % 60L,
                maximumSeconds / 60L, maximumSeconds % 60L));
        List<Integer> waveformHeights = VoiceWaveformPresentation.heights(snapshot.waveform());
        for (int index = 0; index < waveformHeights.size(); index++) {
            Anchor waveformAnchor = new Anchor();
            waveformAnchor.setWidth(Value.of(5));
            waveformAnchor.setHeight(Value.of(waveformHeights.get(index)));
            commands.setObject("#VoiceWaveformBar" + index + ".Anchor", waveformAnchor);
        }
        commands.set("#VoiceRecorderStatus.Text", snapshot.message());
        commands.set("#VoiceRecorderStatus.Style.TextColor",
                snapshot.error() ? "#e76f6f" : "#9ed7a6");
        commands.set("#VoiceQualityMetrics.Text", snapshot.durationMillis() <= 0
                ? "Record at least five seconds of natural speech."
                : String.format(Locale.ROOT,
                        "Duration %.2fs  ·  Peak %.1f dBFS  ·  RMS %.1f dBFS  ·  Clipping %.2f%%  ·  Silence %.1f%%  ·  Sequence gaps %d",
                        snapshot.durationMillis() / 1000.0, snapshot.peakDbfs(),
                        snapshot.rmsDbfs(), snapshot.clippingRatio() * 100.0,
                        snapshot.silenceRatio() * 100.0, snapshot.sequenceGaps()));
        commands.set("#VoiceProfileReadiness.Text", snapshot.profileReady()
                ? "Voice profile READY. Missing optional emotions fall back to Reference."
                : "Voice profile NOT READY. A valid Reference sample is required.");
        commands.set("#VoiceProfileReadiness.Style.TextColor",
                snapshot.profileReady() ? "#72d58b" : "#e76f6f");
        for (VoiceSampleType type : VoiceSampleType.values()) {
            VoicePresetRepository.SampleState state = snapshot.savedStates().getOrDefault(
                    type, VoicePresetRepository.SampleState.MISSING);
            String selector = "#VoiceSaved" + type.name();
            commands.set(selector + ".Text", switch (state) {
                case FOUND -> "SAVED";
                case MISSING -> type == VoiceSampleType.REFERENCE ? "REQUIRED" : "—";
                case INVALID -> "INVALID";
            });
            commands.set(selector + ".Style.TextColor", switch (state) {
                case FOUND -> "#72d58b";
                case MISSING -> type == VoiceSampleType.REFERENCE ? "#e76f6f" : "#d0a65a";
                case INVALID -> "#e76f6f";
            });
            commands.set("#VoiceSelected" + type.name() + ".Visible",
                    type == snapshot.selected());
        }
        VoiceRecorderControlPolicy.Controls controls =
                VoiceRecorderControlPolicy.forSnapshot(snapshot);
        commands.set("#VoiceRecordButton.Disabled", controls.recordDisabled());
        commands.set("#VoicePlayStopButton.Disabled", controls.playDisabled());
        commands.set("#VoicePlayStopLabel.Text", controls.playMode().name());
        commands.set("#VoicePlayIcon.Visible",
                controls.playMode() == VoiceRecorderControlPolicy.PlayMode.PLAY);
        commands.set("#VoiceStopIcon.Visible",
                controls.playMode() == VoiceRecorderControlPolicy.PlayMode.STOP);
        commands.set("#VoiceDeleteButton.Disabled", controls.deleteDisabled());
        commands.set("#VoiceSaveButton.Disabled", controls.saveDisabled());
    }

    private void startVoiceRefresh(Store<EntityStore> store) {
        if (voiceRefreshTask != null) return;
        var world = store.getExternalData().getWorld();
        voiceRefreshTask = statsScheduler.scheduleAtFixedRate(() -> {
            if (!built || voiceRecording == null
                    || authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.VOICE) {
                return;
            }
            Snapshot before = voiceSnapshot;
            Snapshot current;
            try { current = voiceRecording.snapshot(); }
            catch (RuntimeException ignored) { return; }
            if (before != null && before.equals(current)) return;
            voiceSnapshot = current;
            if (current.draftAvailable()) {
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.VOICE);
            }
            world.execute(() -> {
                if (!built || voiceRecording == null) return;
                UICommandBuilder commands = new UICommandBuilder();
                setVoiceRecorderUi(commands);
                sendUpdate(commands, false);
            });
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void closeVoiceRecorder() {
        NpcVoiceRecordingService.Handle current = voiceRecording;
        voiceRecording = null;
        voiceSnapshot = null;
        if (current != null) current.close();
    }

    private void quiesceVoiceRecorderForBack() {
        NpcVoiceRecordingService.Handle current = voiceRecording;
        if (current == null) return;
        Snapshot before = current.snapshot();
        if (before.state() == NpcVoiceRecordingService.State.ARMED
                || before.state() == NpcVoiceRecordingService.State.RECORDING) {
            current.stop();
        } else if (before.state() == NpcVoiceRecordingService.State.PLAYING) {
            current.stopPlayback();
        }
        voiceSnapshot = current.snapshot();
        if (voiceSnapshot.draftAvailable()) {
            authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.VOICE);
        }
    }

    private void handleAppearanceAction(Store<EntityStore> store, PageData data,
            String action, AppearanceEventTrace event) {
        requireAppearanceDraft();
        if (action.equals("APPEARANCE_OPTION") || action.equals("APPEARANCE_COLOR")
                || action.equals("APPEARANCE_VARIANT") || action.startsWith("APPEARANCE_PAGE_")) {
            if (!expectedAppearanceCatalogHash().equals(data.appearanceCatalogHash)) {
                rejectAppearanceEvent(event, "STALE_CATALOG_HASH");
                acknowledgeAppearanceEvent(event, "staleCatalogHash=true");
                return;
            }
        }
        switch (action) {
            case "APPEARANCE_PRIMARY" -> {
                var primary = PrimaryCategory.valueOf(data.appearancePrimary.toUpperCase(Locale.ROOT));
                if (primary == appearancePrimary) {
                    acknowledgeAppearanceNoop(event, "alreadySelectedPrimary=true");
                    return;
                }
                cancelAppearanceSearch();
                appearancePrimary = primary;
                List<Category> categories = editor.appearanceCatalog()
                        .categories(appearancePrimary);
                appearanceCategory = categories.getFirst();
                appearanceSearch = "";
                appearancePendingSearch = "";
                appearancePage = 0;


                appearanceVariantPage = 0;
                appearanceEditorStatus = "Browsing " + appearancePrimary.name()
                        + " from the pinned live registry snapshot.";
                appearanceEditorError = false;
                appearanceCatalogRefreshReason = "PRIMARY_CATEGORY";
                scheduleAppearancePreview(store, event);
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_CATEGORY" -> {
                Category category = Category.valueOf(
                        data.appearanceCategory.toUpperCase(Locale.ROOT));
                if (category.primary() != appearancePrimary) {
                    throw new IllegalArgumentException("Appearance category is stale.");
                }
                if (appearanceCategory == category) {
                    acknowledgeAppearanceNoop(event, "alreadySelectedCategory=true");
                    return;
                }
                cancelAppearanceSearch();
                appearanceCategory = category;
                appearanceSearch = "";
                appearancePendingSearch = "";
                appearancePage = 0;


                appearanceVariantPage = 0;
                appearanceEditorStatus = "Choose " + category.label()
                        + ". Existing missing values remain retained until Save.";
                appearanceEditorError = false;
                appearanceCatalogRefreshReason = "SECONDARY_CATEGORY";
                scheduleAppearancePreview(store, event);
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_SEARCH" -> {
                String query = data.appearanceSearch == null ? "" : data.appearanceSearch.strip();
                if (appearancePendingSearch.equalsIgnoreCase(query)) {
                    acknowledgeAppearanceNoop(event, "unchangedSearch=true");
                    return;
                }
                appearancePendingSearch = query;
                scheduleAppearanceSearch(store, query);
                acknowledgeAppearanceEvent(event, "debouncedSearch=true");
            }
            case "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT" -> {
                var page = appearanceCatalogPage();
                int next = Math.max(0, Math.min(page.pageCount() - 1, appearancePage
                        + (action.endsWith("NEXT") ? 1 : -1)));
                if (next == appearancePage) {
                    acknowledgeAppearanceNoop(event, "pageBoundary=true");
                    return;
                }
                appearancePage = next;
                appearanceCatalogRefreshReason = "PAGE";
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_VARIANT_PREV" -> {
                int next = Math.max(0, appearanceVariantPage - 1);
                if (next == appearanceVariantPage) {
                    acknowledgeAppearanceNoop(event, "variantPageBoundary=true");
                    return;
                }
                appearanceVariantPage = next;
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_VARIANT_NEXT" -> {
                var descriptor = currentAppearanceDescriptor();
                int count = descriptor == null ? 0 : descriptor.variants().size();
                int pages = Math.max(1, (count + 5) / 6);
                int next = Math.min(pages - 1, appearanceVariantPage + 1);
                if (next == appearanceVariantPage) {
                    acknowledgeAppearanceNoop(event, "variantPageBoundary=true");
                    return;
                }
                appearanceVariantPage = next;
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_OPTION", "APPEARANCE_COLOR", "APPEARANCE_VARIANT" -> {
                String priorSelection = currentAppearanceSelection();
                var descriptor = editor.appearanceCatalog().require(appearanceCategory, data.appearanceOptionId);
                if (action.equals("APPEARANCE_OPTION")
                        && appearanceCatalogOptions().stream().noneMatch(option -> option.cosmeticId().equals(data.appearanceOptionId))) {
                    throw new IllegalArgumentException("Cosmetic is not on the current page.");
                }
                if (!action.equals("APPEARANCE_OPTION") && !data.appearanceOptionId.equals(currentAppearanceCosmeticId())) {
                    rejectAppearanceEvent(event, "STALE_SELECTED_COSMETIC");
                    acknowledgeAppearanceEvent(event, "staleSelectedCosmetic=true");
                    return;
                }
                if (action.equals("APPEARANCE_OPTION") && data.appearanceOptionId.equals(currentAppearanceCosmeticId())
                        || java.util.Objects.equals(currentAppearanceSelection(),
                                descriptor.encoded(data.appearanceColorId, data.appearanceVariantId))) {
                    acknowledgeAppearanceNoop(event, "unchangedSelection=true");
                    return;
                }
                var selected = editor.appearanceAuthoring().select(appearanceDraft,
                        appearanceCategory, data.appearanceOptionId,
                        data.appearanceColorId, data.appearanceVariantId);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.APPEARANCE);
                scheduleAppearancePreview(store, event);
                if ("APPEARANCE_OPTION".equals(action)) {

                    appearanceVariantPage = 0;
                }
                appearanceEditorStatus = "Previewing " + selected.option().displayName()
                        + ". Save Appearance commits; Cancel restores persisted appearance.";
                appearanceEditorError = false;
                if ("APPEARANCE_COLOR".equals(action)) {
                    String currentSelection = currentAppearanceSelection();
                    var nativeIcon = editor.appearanceCatalog().nativeIconPresentation(
                            appearanceCategory, data.appearanceOptionId,
                            NpcSkinCodecAdapter.variantId(currentSelection),
                            NpcSkinCodecAdapter.colorId(currentSelection));
                    boolean galleryPresentationChanged = nativeIcon.nativeIconAvailable();
                    diagnostics.accept((galleryPresentationChanged
                            ? APPEARANCE_COLOR_ICON_STATE_CHANGED
                            : APPEARANCE_COLOR_ICON_STATE_UNCHANGED)
                            + " cosmeticId=" + nativeIcon.cosmeticId()
                            + " modelVariant=" + nativeIcon.modelVariant()
                            + " iconPath=" + nativeIcon.iconPath()
                            + " framing=" + nativeIcon.framing()
                            + " textureGradient=" + nativeIcon.textureGradient().replace(' ', '_')
                            + " colorBefore=" + NpcSkinCodecAdapter.colorId(priorSelection)
                            + " colorAfter=" + NpcSkinCodecAdapter.colorId(currentSelection)
                            + " colorChanged=true galleryPresentationChanged="
                            + galleryPresentationChanged
                            + " centralPreviewChanged=true reason=CLIENT_PART_PREVIEW_NOT_EXPOSED");
                }
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_RANDOMIZE" -> {
                editor.appearanceAuthoring().randomize(appearanceDraft);
                authoringSession.markDirty(NpcAuthoringSession.DirtyDomain.APPEARANCE);
                scheduleAppearancePreview(store, event);
                appearanceEditorStatus = "Randomized from Hytale's current registry. Review before saving.";
                appearanceEditorError = false;
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_RESET" -> {
                cancelAppearancePreview();
                appearanceDraft.reset();
                authoringSession.markSaved(NpcAuthoringSession.EditorKind.APPEARANCE);
                appearancePreview.restore(appearanceDraft);
                appearancePreviewHash = "";
                scheduleAppearancePreview(store, event);
                appearanceEditorStatus = "Draft reset to the persisted appearance.";
                appearanceEditorError = false;
                refreshAppearanceEditorUi(event);
            }
            case "APPEARANCE_CANCEL" -> {
                if (appearanceDraft.dirty()) {
                    UICommandBuilder commands = new UICommandBuilder();
                    commands.set("#DirtyEditorConfirmPage.Visible", true);
                    sendUpdate(commands, false);
                    markAppearanceUpdateSent(event, "dirtyConfirmation=true");
                } else {
                    restoreAndClearAppearanceDraft();
                    authoringSession.closeEditor(false);
                    rebuild();
                    markAppearanceUpdateSent(event, "pageTransition=STUDIO");
                }
            }
            case "APPEARANCE_SAVE" -> {
                if (!appearanceDraft.dirty()) {
                    appearanceEditorStatus = "No appearance changes to save.";
                    appearanceEditorError = false;
                    refreshAppearanceEditorUi(event);
                    return;
                }
                saveAppearanceDraft(store);
                long generation = authoringSession.editorGeneration();
                appearanceDraft = editor.appearanceAuthoring().begin(npcName,
                        authoringSession.npcStableId(), authoringSession.sessionId(), generation);
                scheduleAppearancePreview(store, event);
                refreshAppearanceEditorUi(event);
            }
            default -> throw new IllegalArgumentException("Unknown appearance action.");
        }
    }

    private void setAppearanceEditorUi(UICommandBuilder commands, UIEventBuilder events) {
        long started = System.nanoTime();
        var page = appearanceCatalogPage();
        AppearanceUiAssetBudget.PRODUCTION.requireUsage(page.descriptors().size(), 0, 0, 0);
        String pageHash = AppearanceUiState.pageHash(page);
        boolean pageChanged = appearanceUiState.hashes() == null
                || !pageHash.equals(appearanceUiState.hashes().catalogPageHash());
        commands.set("#AppearanceCatalogHash.Text", pageHash);
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
        commands.set("#AppearancePrimaryHeading.Text", appearancePrimary.name());
        commands.set("#AppearanceCategoryHeading.Text", appearanceCategory.label().toUpperCase(Locale.ROOT));
        for (PrimaryCategory primary : PrimaryCategory.values()) {
            String selector = "#AppearancePrimary" + primary.name();
            setAppearanceSelection(commands, selector, primary == appearancePrimary);
            setAppearanceIcon(commands, selector, AppearanceEditorPresentation.icon(primary),
                    primary == appearancePrimary);
        }
        List<Category> categories = editor.appearanceCatalog().categories(appearancePrimary);
        for (int index = 0; index < 7; index++) {
            boolean visible = index < categories.size();
            commands.set("#AppearanceCategory" + index + ".Visible", visible);
            if (visible) {
                Category category = categories.get(index);
                String selector = "#AppearanceCategory" + index;
                commands.set(selector + ".TooltipText", category.label());
                commands.set(selector + " #Id.Text", category.name());
                setAppearanceSelection(commands, selector, category == appearanceCategory);
                setAppearanceIcon(commands, selector, AppearanceEditorPresentation.icon(category),
                        category == appearanceCategory);
            }
        }
        var options = appearanceCatalogOptions();
        String currentId = currentAppearanceCosmeticId();
        commands.set("#AppearanceEmptyState.Visible", options.isEmpty());
        boolean rebuildCards = !appearanceGridMounted || pageChanged;
        if (rebuildCards) {
            appearanceUiState.forget("#AppearanceOption");
            AppearanceEditorPresentation.appendGrid(commands, "#AppearanceOptionGrid", "AppearanceOption",
                    AppearanceUiAssetBudget.MAX_VISIBLE_CARDS, AppearanceEditorPresentation.CARD_COLUMNS, 92, 149, 10,
                    "Pages/ImmersiveNpcAppearanceCard.ui");
            appearanceGridMounted = true;
            appearanceThumbnailGridRebuildCount++;
            appearanceThumbnailCardBuildCount += options.size();
        }
        for (int index = 0; index < AppearanceUiAssetBudget.MAX_VISIBLE_CARDS; index++) {
            String host = "#AppearanceOption" + index;
            commands.set(host + ".Visible", index < options.size());
            if (index >= options.size()) {
                if (rebuildCards) commands.setNull(host + " #Choice #Thumbnail.AssetPath");
                continue;
            }
            var option = options.get(index);
            String selector = host + " #Choice";
            commands.set(host + " #Id.Text", option.cosmeticId());
            commands.set(selector + " #Name.Text", AppearanceEditorPresentation.label(option.displayName(), 48));
            commands.set(selector + ".TooltipText", option.displayName() + " — Select for live preview");
            setAppearanceIcon(commands, selector, AppearanceEditorPresentation.icon(appearanceCategory), false);
            setAppearanceThumbnail(commands, selector, option.cosmeticId(), index, rebuildCards);
            setAppearanceSelection(commands, selector, option.cosmeticId().equals(currentId));
        }
        commands.set("#AppearancePagePREV.Disabled", page.pageIndex() == 0);
        commands.set("#AppearancePageNEXT.Disabled", page.pageIndex() + 1 >= page.pageCount());
        commands.set("#AppearancePageLabel.Text", (page.pageIndex() + 1) + " / " + page.pageCount()
                + "   ·   " + page.totalCount() + " options");
        var descriptor = currentAppearanceDescriptor();
        String current = currentAppearanceSelection();
        List<String> colors = descriptor == null ? List.of()
                : descriptor.colors(NpcSkinCodecAdapter.variantId(current));
        commands.set("#AppearanceCurrentId.Text", currentId);
        commands.set("#AppearanceCurrentColor.Text", NpcSkinCodecAdapter.colorId(current));
        commands.set("#AppearanceCurrentVariant.Text", NpcSkinCodecAdapter.variantId(current));
        commands.set("#AppearanceColorLabel.Text", "COLOR");
        String paletteKey = AppearanceUiState.hash(appearanceCategory + "|" + currentId + "|"
                + NpcSkinCodecAdapter.variantId(current) + "|" + colors);
        if (!paletteKey.equals(appearancePaletteKey)) {
            appearanceUiState.forget("#AppearanceColor");
            AppearanceEditorPresentation.appendGrid(commands, "#AppearanceColorGrid", "AppearanceColor",
                    colors.size(), AppearanceEditorPresentation.COLOR_COLUMNS, 38, 38, 0,
                    "Pages/ImmersiveNpcAppearanceSwatch.ui");
            bindAppearanceColors(events, colors, pageHash, currentId,
                    NpcSkinCodecAdapter.variantId(current));
            appearancePaletteKey = paletteKey;
        }
        var paletteAnchor = new com.hypixel.hytale.server.core.ui.Anchor();
        paletteAnchor.setHeight(com.hypixel.hytale.server.core.ui.Value.of(
                AppearanceEditorPresentation.paletteHeight(colors.size())));
        paletteAnchor.setBottom(com.hypixel.hytale.server.core.ui.Value.of(8));
        commands.setObject("#AppearanceColorSection.Anchor", paletteAnchor);
        for (int index = 0; index < colors.size(); index++) {
            String color = colors.get(index);
            String selector = "#AppearanceColor" + index + " #Choice";
            commands.set(selector + ".TooltipText", color);
            setAppearanceSelection(commands, selector,
                    color.equals(NpcSkinCodecAdapter.colorId(current)));
            List<String> swatch = editor.appearanceCatalog().swatchColors(
                    appearanceCategory, currentId, NpcSkinCodecAdapter.variantId(current), color);
            commands.set(selector + " #Unknown.Visible", swatch.isEmpty());
            for (int half = 0; half < 2; half++) {
                String hex = swatch.isEmpty() ? "#293547" : swatch.get(Math.min(half, swatch.size() - 1));
                commands.setObject(selector + (half == 0 ? " #ColorA.Background" : " #ColorB.Background"),
                        new com.hypixel.hytale.server.core.ui.PatchStyle().setColor(
                                com.hypixel.hytale.server.core.ui.Value.of(hex)));
            }
        }
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
            if (visible) {
                String selector = "#AppearanceVariant" + index;
                commands.set(selector + " #Name.Text",
                        AppearanceEditorPresentation.label(visibleVariants.get(index), 18));
                commands.set(selector + ".TooltipText", visibleVariants.get(index));
                commands.set(selector + " #Id.Text", visibleVariants.get(index));
                setAppearanceSelection(commands, selector,
                        visibleVariants.get(index).equals(NpcSkinCodecAdapter.variantId(current)));
            }
        }
        commands.set("#AppearanceVariantPageText.Text", "Variants "
                + (appearanceVariantPage + 1) + " / " + variantPages);
        commands.set("#AppearanceVariantPreviousButton.Disabled",
                appearanceVariantPage == 0);
        commands.set("#AppearanceVariantNextButton.Disabled",
                appearanceVariantPage + 1 >= variantPages);
        commands.set("#AppearanceColorSection.Visible", !colors.isEmpty());
        commands.set("#AppearanceVariantSection.Visible", !variants.isEmpty());
        bindAppearanceCatalogEvents(events, pageHash, categories,
                options.stream().map(option -> option.cosmeticId()).toList(),
                currentId, NpcSkinCodecAdapter.colorId(current), visibleVariants);
        boolean missing = descriptor == null && current != null && !current.isBlank();
        commands.set("#AppearanceSelectionInfo.Visible", missing);
        commands.set("#AppearanceSelectionInfo.Text", "Unavailable saved option");
        commands.set("#AppearanceSelectionInfo.TooltipText", missing ? current : "");
        commands.set("#AppearanceValidationStatus.Visible", appearanceEditorError || appearanceUiState.degraded());
        commands.set("#AppearanceValidationStatus.Text", appearanceUiState.degraded()
                ? appearanceUiState.degradedMessage() : appearanceEditorError ? appearanceEditorStatus : "");
        commands.set("#AppearanceSaveButton.Disabled", !appearanceDraft.dirty());
        commands.set("#AppearanceCancelButton.Disabled", !appearanceDraft.dirty());
        commands.set("#AppearanceResetButton.Disabled", !appearanceDraft.dirty());
        commands.set("#AppearancePreviewCharacter.Visible", preview != null);
        commands.set("#AppearancePreviewFallback.Visible", preview == null);
        appearanceUiState.updateHashes(page, appearanceDraft.currentSkin(), appearancePreviewHash);
        appearanceTelemetry("APPEARANCE_PAGE_RENDERED", options.size(), started,
                "pageChanged=" + pageChanged + " rebuildReason=" + appearanceCatalogRefreshReason);
        appearanceCatalogRefreshReason = "SELECTION";
        if (pageChanged && !options.isEmpty()) {
            appearanceTelemetry("APPEARANCE_UI_ASSET_CREATED", 0, started,
                    "created=0 policy=RUNTIME_GENERATION_DISABLED");
            appearanceTelemetry("APPEARANCE_UI_ASSET_REUSED", options.size(), started,
                    "source=IMMUTABLE_CANONICAL_CATALOG colorVariant=CANONICAL_ONLY"
                            + " dimensions=92x149 creationTime=BUILD_TIME lastUse=" + Instant.now()
                            + " owningPage=" + authoringSession.sessionId() + " sentNewAsset=false");
        }
    }

    private static void setAppearanceSelection(UICommandBuilder commands, String selector,
            boolean selected) {
        commands.set(selector + " #Selected.Visible", selected);
    }

    private static void setAppearanceIcon(UICommandBuilder commands, String selector,
            String icon, boolean selected) {
        commands.set(selector + " #Icon.Background", com.hypixel.hytale.server.core.ui.Value.ref(
                "Pages/ImmersiveNpcProfile.ui", "AppearanceIcon" + icon + (selected ? "Selected" : "")));
    }

    private void setAppearanceThumbnail(UICommandBuilder commands, String selector,
            String cosmeticId, int cardIndex, boolean rebuildCards) {
        var reference = AppearanceThumbnailCatalog.find(appearanceCategory, cosmeticId).orElse(null);
        commands.set(selector + " #Icon.Visible", reference == null);
        commands.set(selector + " #Name.Visible", reference == null);
        String thumbnailSelector = selector + " #Thumbnail";
        commands.set(thumbnailSelector + ".Visible", reference != null);
        if (reference == null) {
            if (rebuildCards) commands.setNull(thumbnailSelector + ".AssetPath");
            if (cosmeticId != null && !cosmeticId.isBlank()) {
                diagnostics.accept("APPEARANCE_THUMBNAIL_MISSING cosmeticId=" + cosmeticId
                        + " thumbnailAssetPath=NONE cardIndex=" + cardIndex
                        + " selector=" + thumbnailSelector + ".AssetPath"
                        + " packagedAssetPresent=false");
            }
            return;
        }

        boolean packagedAssetPresent = AppearanceThumbnailCatalog.packagedAssetPresent(reference);
        commands.set(thumbnailSelector + ".AssetPath", reference.uiTexturePath());
        if (rebuildCards) {
            diagnostics.accept("APPEARANCE_THUMBNAIL_BOUND cosmeticId=" + cosmeticId
                    + " thumbnailAssetPath=" + reference.uiTexturePath()
                    + " cardIndex=" + cardIndex
                    + " selector=" + thumbnailSelector + ".AssetPath"
                    + " packagedAssetPresent=" + packagedAssetPresent);
        }
        if (appearanceThumbnailReferencesLogged.add(reference.key())) {
            var nativeIcon = editor.appearanceCatalog().nativeIconPresentation(
                    appearanceCategory, cosmeticId, "", "");
            diagnostics.accept((nativeIcon.nativeIconAvailable()
                    ? APPEARANCE_NATIVE_ICON_RESOLVED : APPEARANCE_NATIVE_ICON_MISSING)
                    + " cosmeticId=" + nativeIcon.cosmeticId()
                    + " modelVariant=" + nativeIcon.modelVariant()
                    + " iconPath=" + nativeIcon.iconPath()
                    + " framing=" + nativeIcon.framing()
                    + " textureGradient=" + nativeIcon.textureGradient().replace(' ', '_')
                    + " nativeIconAvailable=" + nativeIcon.nativeIconAvailable());
            diagnostics.accept(APPEARANCE_ICON_FRAMING_RESOLVED
                    + " cosmeticId=" + nativeIcon.cosmeticId()
                    + " modelVariant=" + nativeIcon.modelVariant()
                    + " iconPath=" + reference.uiTexturePath()
                    + " framing=" + nativeIcon.framing()
                    + " textureGradient=" + nativeIcon.textureGradient().replace(' ', '_')
                    + " sourceDimensions=" + reference.width() + "x" + reference.height()
                    + " hostDimensions=92x149 aspectPreserved=true categoryFallback=true");
            diagnostics.accept("APPEARANCE_THUMBNAIL_REFERENCE cosmeticId=" + reference.key()
                    + " assetPath=" + reference.packagedAssetPath()
                    + " dimensions=" + reference.width() + "x" + reference.height()
                    + " packagedStatic=" + packagedAssetPresent
                    + " dynamicThumbnailCreates=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_CREATES
                    + " runtimeThumbnailWrites=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_WRITES
                    + " runtimeThumbnailRecolors=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_RECOLORS);
        }
    }

    private void scheduleAppearanceSearch(Store<EntityStore> store, String query) {
        long request = ++appearanceSearchGeneration;
        ScheduledFuture<?> previous = appearanceSearchTask;
        if (previous != null) previous.cancel(false);
        UUID draftId = appearanceDraft.draftId();
        long editorGeneration = authoringSession.editorGeneration();
        var world = store.getExternalData().getWorld();
        appearanceSearchTask = statsScheduler.schedule(() -> world.execute(() -> {
            if (!built || appearanceDraft == null || !appearanceDraft.draftId().equals(draftId)
                    || authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.APPEARANCE
                    || authoringSession.editorGeneration() != editorGeneration
                    || request != appearanceSearchGeneration || !appearancePendingSearch.equals(query)) return;
            appearanceSearchTask = null;
            appearanceSearch = query;
            appearancePage = 0;
            appearanceEditorStatus = "Registry search applied locally; no model request was made.";
            appearanceEditorError = false;
            appearanceCatalogRefreshReason = "SEARCH";
            refreshAppearanceEditorUi();
        }), 180, TimeUnit.MILLISECONDS);
    }

    private void cancelAppearanceSearch() {
        appearanceSearchGeneration++;
        ScheduledFuture<?> current = appearanceSearchTask;
        appearanceSearchTask = null;
        if (current != null) current.cancel(false);
    }

    private void refreshAppearanceEditorUi() {
        refreshAppearanceEditorUi(null);
    }

    private void refreshAppearanceEditorUi(AppearanceEventTrace event) {
        if (appearanceDraft == null || !built) return;
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        setAppearanceEditorUi(commands, events);
        UICommandBuilder changed = appearanceUiState.filter(commands);
        if (changed.getCommands().length == 0 && events.getEvents().length == 0) {
            appearanceTelemetry("APPEARANCE_FULL_REBUILD_SUPPRESSED", 0, 0,
                    "unchanged=true acknowledgement=EMPTY_UPDATE");
            sendUpdate();
            markAppearanceUpdateSent(event, "emptyAcknowledgement=true");
            return;
        }
        sendUpdate(changed, events, false);
        markAppearanceUpdateSent(event, "partialCommands=" + changed.getCommands().length
                + " reboundEvents=" + events.getEvents().length);
        appearanceTelemetry("APPEARANCE_FULL_REBUILD_SUPPRESSED", 0, 0,
                "partialCommands=" + changed.getCommands().length);
    }

    private void acknowledgeAppearanceNoop(AppearanceEventTrace event, String reason) {
        appearanceEventTelemetry("APPEARANCE_EVENT_NOOP", event, reason);
        acknowledgeAppearanceEvent(event, reason);
    }

    private void scheduleAppearancePreview(Store<EntityStore> store, AppearanceEventTrace event) {
        Category focusCategory = appearanceCategory;
        String hash = AppearanceUiState.hash(
                AppearanceUiState.skinHash(appearanceDraft.currentSkin())
                        + "|focus=" + focusCategory.name());
        if (hash.equals(appearanceRequestedPreviewHash) || hash.equals(appearancePreviewHash)) {
            // Returning to the applied skin must also cancel a queued different skin.
            if (hash.equals(appearancePreviewHash)) cancelAppearancePreview();
            appearancePreviewJobsCoalesced++;
            appearanceTelemetry("APPEARANCE_PREVIEW_COALESCED", 0, 0, "unchangedSkin=true");
            appearanceEventTelemetry("APPEARANCE_PREVIEW_REQUESTED", event,
                    "coalesced=true unchangedSkin=true");
            return;
        }
        appearanceRequestedPreviewHash = hash;
        UUID draftId = appearanceDraft.draftId();
        boolean coalesced = appearancePreviewGate.request(store.getExternalData().getWorld()::execute, generation -> {
            if (!built || appearanceDraft == null || !appearanceDraft.draftId().equals(draftId)
                    || authoringSession.activeEditor() != NpcAuthoringSession.EditorKind.APPEARANCE
                    || appearanceCategory != focusCategory
                    || !appearancePreviewGate.current(generation)
                    || !hash.equals(AppearanceUiState.hash(
                            AppearanceUiState.skinHash(appearanceDraft.currentSkin())
                                    + "|focus=" + focusCategory.name()))) return;
            long started = System.nanoTime();
            try {
                appearancePreview.show(appearanceDraft, focusCategory);
                appearancePreviewHash = hash;
                appearancePreviewJobsApplied++;
                appearanceUiState.updateHashes(appearanceCatalogPage(), appearanceDraft.currentSkin(), hash);
                appearanceTelemetry("APPEARANCE_PREVIEW_APPLIED", 0, started, "previewGeneration=" + generation);
                appearanceEventTelemetry("APPEARANCE_PREVIEW_APPLIED", event,
                        "previewGeneration=" + generation + " newestGeneration=true");
            } catch (RuntimeException failure) {
                appearanceUiState.observeFailure(failure.getMessage());
                appearanceEditorError = true;
                appearanceEditorStatus = "Preview unavailable; draft retained. " + failure.getMessage();
                refreshAppearanceEditorUi();
            } finally { appearanceRequestedPreviewHash = ""; }
        });
        if (coalesced) appearancePreviewJobsCoalesced++;
        else appearancePreviewJobsScheduled++;
        appearanceTelemetry(coalesced ? "APPEARANCE_PREVIEW_COALESCED" : "APPEARANCE_PREVIEW_SCHEDULED",
                0, 0, "generationBounded=true");
        appearanceEventTelemetry("APPEARANCE_PREVIEW_REQUESTED", event,
                "coalesced=" + coalesced + " generationBounded=true");
    }

    private void cancelAppearancePreview() {
        appearancePreviewGate.cancel();
        appearanceRequestedPreviewHash = "";
    }

    private AppearanceEventTrace beginAppearanceEvent(String action, PageData data) {
        boolean locksInterface = !NON_LOCKING_APPEARANCE_ACTIONS.contains(action);
        boolean current = built && appearanceDraft != null
                && authoringSession.activeEditor() == NpcAuthoringSession.EditorKind.APPEARANCE
                && java.util.Objects.equals(data.authoringSchemaVersion,
                        Integer.toString(NpcAuthoringEventEnvelope.CURRENT_SCHEMA_VERSION))
                && java.util.Objects.equals(data.authoringSessionId,
                        authoringSession.sessionId().toString())
                && java.util.Objects.equals(data.authoringViewerPlayerId,
                        authoringSession.viewerPlayerId().toString())
                && java.util.Objects.equals(data.authoringNpcStableId,
                        authoringSession.npcStableId().toString())
                && java.util.Objects.equals(data.authoringPageGeneration,
                        Long.toString(authoringSession.pageGeneration()))
                && java.util.Objects.equals(data.authoringEditor,
                        NpcAuthoringSession.EditorKind.APPEARANCE.name())
                && java.util.Objects.equals(data.authoringEditorGeneration,
                        Long.toString(authoringSession.editorGeneration()));
        AppearanceEventTrace event = new AppearanceEventTrace(action, locksInterface, current,
                data.authoringSessionId, data.authoringPageGeneration,
                data.authoringEditorGeneration, expectedAppearanceCatalogHash(),
                data.appearanceCatalogHash, data.appearanceOptionId);
        boolean selectorsResolved = selectorResolved(data.appearanceCatalogHash)
                && selectorResolved(data.appearanceOptionId)
                && selectorResolved(data.appearanceColorId)
                && selectorResolved(data.appearanceVariantId)
                && selectorResolved(data.appearanceSearch);
        appearanceEventTelemetry("APPEARANCE_EVENT_RECEIVED", event,
                "payloadSelectorsResolved=" + selectorsResolved);
        return event;
    }

    private static boolean selectorResolved(String value) {
        return value == null || !value.stripLeading().startsWith("#");
    }

    private String expectedAppearanceCatalogHash() {
        AppearanceUiState.Hashes hashes = appearanceUiState.hashes();
        // Event admission must be read-only. A stale event from a dismissed or
        // replaced page may arrive after the editor has changed; never query the
        // live catalog (which normalizes page state) merely to trace/reject it.
        return hashes == null ? "" : hashes.catalogPageHash();
    }

    private void acknowledgeAppearanceEvent(AppearanceEventTrace event, String reason) {
        if (event == null || event.terminal || !event.currentAtReceive) return;
        sendUpdate();
        markAppearanceUpdateSent(event, "emptyAcknowledgement=true " + reason);
    }

    private void markAppearanceUpdateSent(AppearanceEventTrace event, String disposition) {
        if (event != null) {
            event.updateSent = true;
            event.terminal = true;
        }
        appearanceEventTelemetry("APPEARANCE_UPDATE_SENT", event, disposition);
    }

    private void rejectAppearanceEvent(AppearanceEventTrace event, String reason) {
        if (event == null || event.rejected) return;
        event.rejected = true;
        appearanceEventTelemetry("APPEARANCE_EVENT_REJECTED", event,
                "reason=" + quoted(reason == null ? "unspecified" : reason));
    }

    private void appearanceEventTelemetry(String marker, AppearanceEventTrace event,
            String extra) {
        diagnostics.accept(marker + " timestamp=" + Instant.now()
                + " eventType=" + (event == null ? "ASYNC_PREVIEW" : event.action)
                + " session=" + authoringSession.sessionId()
                + " pageGeneration=" + authoringSession.pageGeneration()
                + " editorGeneration=" + authoringSession.editorGeneration()
                + " suppliedSession=" + quoted(event == null ? null : event.suppliedSession)
                + " suppliedPageGeneration=" + quoted(event == null ? null : event.suppliedPageGeneration)
                + " suppliedEditorGeneration=" + quoted(event == null ? null : event.suppliedEditorGeneration)
                + " locksInterface=" + (event != null && event.locksInterface)
                + " updateSent=" + (event != null && event.updateSent)
                + " currentAtReceive=" + (event == null || event.currentAtReceive)
                + " expectedCatalogHash=" + quoted(event == null ? null : event.expectedCatalogHash)
                + " suppliedCatalogHash=" + quoted(event == null ? null : event.suppliedCatalogHash)
                + " optionId=" + quoted(event == null ? null : event.optionId)
                + " " + (extra == null ? "" : extra));
    }

    private static final class AppearanceEventTrace {
        private final String action;
        private final boolean locksInterface;
        private final boolean currentAtReceive;
        private final String suppliedSession;
        private final String suppliedPageGeneration;
        private final String suppliedEditorGeneration;
        private final String expectedCatalogHash;
        private final String suppliedCatalogHash;
        private final String optionId;
        private boolean updateSent;
        private boolean terminal;
        private boolean rejected;

        private AppearanceEventTrace(String action, boolean locksInterface,
                boolean currentAtReceive, String suppliedSession,
                String suppliedPageGeneration, String suppliedEditorGeneration,
                String expectedCatalogHash, String suppliedCatalogHash, String optionId) {
            this.action = action;
            this.locksInterface = locksInterface;
            this.currentAtReceive = currentAtReceive;
            this.suppliedSession = suppliedSession;
            this.suppliedPageGeneration = suppliedPageGeneration;
            this.suppliedEditorGeneration = suppliedEditorGeneration;
            this.expectedCatalogHash = expectedCatalogHash;
            this.suppliedCatalogHash = suppliedCatalogHash;
            this.optionId = optionId;
        }
    }

    private void appearanceTelemetry(String event, int reused, long start, String extra) {
        int visible = appearanceDraft == null ? 0 : appearanceCatalogPage().descriptors().size();
        diagnostics.accept(event + " timestamp=" + Instant.now()
                + " session=" + authoringSession.sessionId() + " npc=" + npcName
                + " visibleCards=" + visible + " dynamicImages=0 dynamicPixels=0 dynamicEncodedBytes=0"
                + " category=" + appearanceCategory + " page=" + appearancePage
                + " assetsUploaded=0 reused=" + reused + " discarded=0"
                + " previewJobsActive=" + appearancePreviewGate.active()
                + " previewJobsPending=" + appearancePreviewGate.pending()
                + " previewJobsCancelled=" + appearancePreviewGate.cancelled()
                + " previewJobsScheduled=" + appearancePreviewJobsScheduled
                + " previewJobsCoalesced=" + appearancePreviewJobsCoalesced
                + " previewJobsApplied=" + appearancePreviewJobsApplied
                + " realizedCardCount=" + visible
                + " staticThumbnailReferencesThisCategory="
                + AppearanceThumbnailCatalog.count(appearanceCategory)
                + " totalThumbnailReferencesUsedThisSession="
                + appearanceThumbnailReferencesLogged.size()
                + " cardRebuildCount=" + appearanceThumbnailCardBuildCount
                + " gridRebuildCount=" + appearanceThumbnailGridRebuildCount
                + " runtimeThumbnailCreates=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_CREATES
                + " runtimeThumbnailWrites=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_WRITES
                + " runtimeThumbnailRecolors=" + AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_RECOLORS
                + " durationMs=" + (start == 0 ? 0 : (System.nanoTime() - start) / 1_000_000.0)
                + " degraded=" + appearanceUiState.degraded() + " " + extra);
    }

    private NpcAppearanceCatalogService.CatalogPage appearanceCatalogPage() {
        var page = editor.appearanceCatalog().query(appearanceCategory, appearanceSearch, appearancePage);
        appearancePage = page.pageIndex();
        return page;
    }

    private List<NpcAppearanceCatalogService.CosmeticOptionDescriptor> appearanceCatalogOptions() {
        return appearanceCatalogPage().descriptors();
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
        cancelAppearancePreview();
        authoringSession.beginCommit();
        try {
            NpcAppearanceAuthoringService.SaveResult result =
                    editor.appearanceAuthoring().save(appearanceDraft, playerRef.getUuid());
            appearancePreview.commit(appearanceDraft, result);
            appearancePreviewHash = "";
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
        cancelAppearancePreview();
        cancelAppearanceSearch();
        if (appearanceDraft != null) {
            appearanceTelemetry("APPEARANCE_UI_ASSET_RELEASE_REQUESTED", 0, 0, "ownedDynamicAssets=0 clientAtlasReleaseClaim=false");
            appearanceTelemetry("APPEARANCE_PAGE_UNLOADED", 0, 0, "reason=EDITOR_EXIT");
        }
        appearanceGridMounted = false;
        appearancePaletteKey = "";
        appearancePreviewHash = "";
        appearanceUiState.remount();
        appearancePage = 0;
        appearanceDraft = null;
        appearanceSearch = "";
        appearancePendingSearch = "";


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
        commands.set("#ProfileEditorTitle.Text", "PROFILE EDITOR - "
                + profileDraft.profileName().toUpperCase(Locale.ROOT));
        commands.set("#ProfileDraftMeta.Text", profileDraft.draftId().toString());
        commands.set("#ProfileValidationStatus.Text", profileEditorStatus);
        commands.set("#ProfileValidationStatus.Style.TextColor",
                profileEditorError ? "#e76f6f" : "#9ed7a6");
        for (ProfileCategory category : ProfileCategory.values()) {
            String label = switch (category) {
                case BASIC_INFO -> "Basic Info";
                case BACKGROUND -> "Background";
                case PERSONALITY -> "Personality";
                case VALUES_BELIEFS -> "Values & Beliefs";
                case MOTIVATIONS -> "Motivations";
                case RELATIONSHIPS -> "Relationships";
                case SPEECH_STYLE -> "Speech Style";
                case NOTES -> "Notes";
            };
            commands.set("#ProfileCategory" + category.resourceName + ".Text",
                    category == profileCategory ? "▶ " + label : label);
            commands.set("#ProfileCategory" + category.resourceName + "Selected.Visible",
                    category == profileCategory);
        }
    }

    private void refreshProfileEditorUi() {
        UICommandBuilder commands = new UICommandBuilder();
        setProfileEditorUi(commands);
        setProfileCategoryStatus(commands);
        sendUpdate(commands, false);
    }

    private void refreshProfileEditorForm() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        setProfileEditorUi(commands);
        mountProfileEditorForm(commands, events, true);
        sendUpdate(commands, events, false);
    }

    private void mountProfileEditorForm(UICommandBuilder commands, UIEventBuilder events,
            boolean clear) {
        if (clear) commands.clear("#ProfileForm");
        commands.append("#ProfileForm", profileCategory.resource());
        for (NpcProfileDraft.Field field : profileFields(profileCategory)) {
            String selector = "#" + profileFieldControl(field);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector,
                    authoringEvent("PROFILE_FIELD")
                            .append("ProfileField", field.name())
                            .append("ProfileFieldValue", selector + ".Value"));
            commands.set(selector + ".Value", profileDraft.value(field));
        }
        if (profileCategory == ProfileCategory.BASIC_INFO) {
            commands.set("#ProfileDisplayName.Text", profileDraft.profileName());
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ProfileGenerateButton", authoringEvent("PROFILE_GENERATE")
                            .append("ProfileGenerateScope", "BIOGRAPHY"));
        } else if (profileCategory == ProfileCategory.BACKGROUND) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ProfileAcceptProposalButton", authoringEvent("PROFILE_PROPOSAL_ACCEPT"));
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ProfileDiscardProposalButton", authoringEvent("PROFILE_PROPOSAL_DISCARD"));
            NpcProfileDraft.Proposal proposal = profileDraft.proposal();
            commands.set("#ProfileProposalPanel.Visible", proposal != null);
            if (proposal != null) {
                String proposed = proposal.changes().getOrDefault(
                        NpcProfileDraft.Field.BIOGRAPHY, "No biography was returned.");
                commands.set("#ProfileProposalDiff.Text", proposed);
            }
        } else if (profileCategory == ProfileCategory.RELATIONSHIPS) {
            editor.currentProfile(npcName).ifPresent(profile -> commands.set(
                    "#ProfileRelationshipsSummary.Text", profile.relationships().size()
                            + " authored relationship"
                            + (profile.relationships().size() == 1 ? "" : "s") + "."));
        }
        setProfileCategoryStatus(commands);
    }

    private void setProfileCategoryStatus(UICommandBuilder commands) {
        if (profileCategory == ProfileCategory.BASIC_INFO) {
            int count = profileDraft.value(NpcProfileDraft.Field.SUMMARY).length();
            commands.set("#ProfileSummaryCounter.Text", count + " / 500");
            boolean generating = profileGeneration != null;
            commands.set("#ProfileGenerateButton.Disabled", generating || !basicInfoValid());
            commands.set("#ProfileGenerationStatus.Text", generating
                    ? "Generating biography proposal..."
                    : "Generate a biography with AI after filling out all basic information.");
        }
    }

    private boolean basicInfoValid() {
        return !profileDraft.profileName().isBlank()
                && !profileDraft.value(NpcProfileDraft.Field.ROLE).isBlank()
                && !profileDraft.value(NpcProfileDraft.Field.SPECIES_ARCHETYPE).isBlank()
                && !profileDraft.value(NpcProfileDraft.Field.AGE_CATEGORY).isBlank()
                && !profileDraft.value(NpcProfileDraft.Field.HOME).isBlank()
                && !profileDraft.value(NpcProfileDraft.Field.SUMMARY).isBlank()
                && profileDraft.value(NpcProfileDraft.Field.SUMMARY).length() <= 500;
    }

    private void validateProfileCategory(ProfileCategory category) {
        for (NpcProfileDraft.Field field : profileFields(category)) {
            if (profileDraft.value(field).length() > field.maxLength()) {
                throw new IllegalArgumentException(field.name() + " exceeds "
                        + field.maxLength() + " characters.");
            }
        }
    }

    private static Set<NpcProfileDraft.Field> profileFields(ProfileCategory category) {
        return switch (category) {
            case BASIC_INFO -> Set.of(NpcProfileDraft.Field.ROLE,
                    NpcProfileDraft.Field.SPECIES_ARCHETYPE,
                    NpcProfileDraft.Field.AGE_CATEGORY, NpcProfileDraft.Field.HOME,
                    NpcProfileDraft.Field.SUMMARY);
            case BACKGROUND -> Set.of(NpcProfileDraft.Field.BIOGRAPHY,
                    NpcProfileDraft.Field.SELF_IDENTITY,
                    NpcProfileDraft.Field.WORKPLACE,
                    NpcProfileDraft.Field.KNOWLEDGE_DOMAINS);
            case PERSONALITY -> Set.of(NpcProfileDraft.Field.PERSONALITY,
                    NpcProfileDraft.Field.PERSONALITY_TRAITS,
                    NpcProfileDraft.Field.LIKES, NpcProfileDraft.Field.DISLIKES);
            case VALUES_BELIEFS -> Set.of(NpcProfileDraft.Field.VALUES);
            case MOTIVATIONS -> Set.of(NpcProfileDraft.Field.PURPOSE,
                    NpcProfileDraft.Field.GOALS, NpcProfileDraft.Field.FEARS);
            case RELATIONSHIPS -> Set.of();
            case SPEECH_STYLE -> Set.of(NpcProfileDraft.Field.SPEAKING_STYLE);
            case NOTES -> Set.of(NpcProfileDraft.Field.CREATOR_NOTES);
        };
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
        if (!basicInfoValid()) throw new IllegalArgumentException(
                "Complete all Basic Info fields before generating a biography.");
        cancelProfileGeneration();
        NpcProfileGenerationService service = editor.generation().orElseThrow(() ->
                new IllegalStateException("Generation provider is unavailable; manual editing remains available."));
        NpcProfileGenerationService.Scope scope = NpcProfileGenerationService.Scope.BIOGRAPHY;
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
                        profileEditorStatus = "Biography proposal ready. Review it before applying.";
                        profileEditorError = false;
                        profileCategory = ProfileCategory.BACKGROUND;
                    }
                    refreshProfileEditorForm();
                }));
        refreshProfileEditorUi();
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
            case SUMMARY -> "ProfileSummaryInput";
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
            case CREATOR_NOTES -> "ProfileNotesInput";
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
        ProfileInventoryPaging.requireRevision(data.inventoryViewRevision, inventoryViewRevision);
        int sourceSection = value(data.sourceInventorySectionId, Integer.MIN_VALUE);
        int sourceSlot = value(data.sourceSlotId, -1);
        int targetSection = parseSection(data.section);
        int targetSlot = value(data.slotIndex, -1);
        // SourceSlotId is already absolute (InventorySlotIndex). Dropped SlotIndex is visual.
        if (targetSection == storageWindow.getId()) targetSlot = npcPaging().targetSlot(targetSlot);
        else if (targetSection == InventoryComponent.STORAGE_SECTION_ID)
            targetSlot = playerPaging().targetSlot(targetSlot);
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
                .append("InventoryViewRevision", Long.toString(inventoryViewRevision))
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
            case "VOICE_RESCAN", "OPEN_VOICE_EDITOR", "VOICE_SELECT", "VOICE_RECORD",
                    "VOICE_STOP", "VOICE_PLAY_DRAFT", "VOICE_PLAY_SAVED",
                    "VOICE_STOP_PLAYBACK", "VOICE_RECORD_AGAIN", "VOICE_DELETE_DRAFT",
                    "VOICE_SAVE", "VOICE_DELETE_SAVED_PROMPT",
                    "VOICE_DELETE_SAVED_CONFIRM", "VOICE_DELETE_SAVED_CANCEL",
                    "VOICE_PLAY_STOP", "VOICE_DELETE"
                    -> NpcAuthoringPermissions.VOICE;
            case "OPEN_APPEARANCE_EDITOR", "APPEARANCE_PRIMARY",
                    "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH", "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT",
                    "APPEARANCE_OPTION", "APPEARANCE_COLOR",
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
            case "NPC_PAGE_PREV", "NPC_PAGE_NEXT", "PLAYER_PAGE_PREV", "PLAYER_PAGE_NEXT",
                    "NAV_OVERVIEW", "CANCEL", "CLOSE_EDITOR", "DIRTY_DISCARD", "DIRTY_STAY"
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
        return "Pages/ProfileInventory/NpcSection" + sectionId + ".ui";
    }

    private void setOverviewNavigationUi(UICommandBuilder commands) {
        commands.set("#OverviewSelected.Visible", true);
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
        statInvulnerable = editor.persistentStats() == null ? java.util.Optional.empty()
                : editor.persistentStats().invulnerable(authoringSession.npcStableId(), store,
                        liveStorageAuthority == null ? null : liveStorageAuthority.npcRef());
        if (liveStorageAuthority == null) {
            savedVitals = editor.persistentStats() == null ? null
                    : editor.persistentStats().repository().cached(authoringSession.npcStableId()).orElse(null);
            statsSnapshot = statsService.captureSaved(authoringSession.npcStableId(),
                    inventory.armor(), savedVitals, authoringSession.sessionId(),
                    authoringSession.pageGeneration(), inventory.equipmentRevision());
            statsFailure = "";
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
                        + statsSnapshot.equipmentRevision() + ":invulnerable=" + statInvulnerable;
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
                commands, "#NpcInventoryGrid.Slots", inventory.inventory(), npcPaging().firstSlot(), npcPaging().slotCount());
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerInventory, playerPaging().firstSlot(), playerPaging().slotCount());
        setPagingUi(commands, "Npc", npcPaging());
        setPagingUi(commands, "Player", playerPaging());
    }

    private ProfileInventoryPaging npcPaging() { return new ProfileInventoryPaging(inventory.inventory().getCapacity(), npcInventoryPage); }
    private ProfileInventoryPaging playerPaging() { return new ProfileInventoryPaging(playerInventory.getCapacity(), playerInventoryPage); }
    private static void setPagingUi(UICommandBuilder commands, String side, ProfileInventoryPaging paging) {
        commands.set("#" + side + "PageLabel.Text", paging.label());
        commands.set("#" + side + "PagePrev.Disabled", paging.page() == 0);
        commands.set("#" + side + "PageNext.Disabled", paging.page() + 1 == paging.pageCount());
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
                statsSnapshot == null ? null : statsSnapshot.health().orElse(null), "Health"));
        commands.set("#StaminaStat #Value.Text", statText(
                statsSnapshot == null ? null : statsSnapshot.stamina().orElse(null), "Stamina"));
        commands.set("#ManaStat #Value.Text", statText(
                statsSnapshot == null ? null : statsSnapshot.mana().orElse(null), "Mana"));
        String defense = statsSnapshot == null || statsSnapshot.defense().isEmpty()
                ? "—"
                : statsSnapshot.defense().get().summary();
        commands.set("#DefenseStat #Value.Text", defense);
        commands.set("#DefenseStat #Value.TooltipText", statsSnapshot == null || statsSnapshot.defense().isEmpty()
                ? "Armor resistance unavailable."
                : (liveStorageAuthority == null
                        ? "SAVED: resistance calculated from durable offline equipment."
                        : "LIVE: resistance calculated from authoritative NPC equipment.")
                        + "\n" + statsSnapshot.defense().get().details());
        for (String stat : new String[] { "Health", "Stamina", "Mana" })
            commands.set("#" + stat + "Stat #Value.TooltipText", (liveStorageAuthority == null
                    ? savedStatTooltip(stat) : "LIVE: native NPC EntityStatMap current / effective maximum.")
                    + "\nInvulnerable: " + statInvulnerable.map(flag -> flag ? "Yes" : "No").orElse("Unknown")
                    + " (separate native role policy; not armor resistance).");
    }

    private String statText(NpcStatsSnapshotService.StatValue value, String id) {
        return value == null ? "—"
                : format(value.current()) + " / " + format(value.maximum());
    }

    private String savedStatTooltip(String id) {
        var value = savedVitals == null ? null : savedVitals.stats().get(id);
        return value == null ? "NPC stat authority unavailable. No current value is assumed."
                : "SAVED: persistent current / native base maximum; frozen while unspawned."
                    + "\nLast observed effective range: " + format(value.lastKnownEffectiveMin()) + " to "
                    + format(value.lastKnownEffectiveMax()) + "; not a recalculated offline maximum."
                    + "\nSource: " + value.source() + "; revision " + savedVitals.revision()
                    + ". Invulnerability is a separate role policy.";
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
        commands.set("#InfiniteAmmoCheckBox.TooltipText", !featureEnabled
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
                .append(new KeyedCodec<>("InventoryViewRevision", Codec.STRING),
                        (data, value) -> data.inventoryViewRevision = value, data -> data.inventoryViewRevision).add()
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
                .append(new KeyedCodec<>("ProfileCategory", Codec.STRING),
                        (data, value) -> data.profileCategory = value,
                        data -> data.profileCategory).add()
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
                .append(new KeyedCodec<>("@AppearanceSearch", Codec.STRING),
                        (data, value) -> data.appearanceSearch = value,
                        data -> data.appearanceSearch).add()
                .append(new KeyedCodec<>("AppearanceCatalogHash", Codec.STRING),
                        (data, value) -> data.appearanceCatalogHash = value,
                        data -> data.appearanceCatalogHash).add()
                .append(new KeyedCodec<>("AppearanceOptionId", Codec.STRING),
                        (data, value) -> data.appearanceOptionId = value,
                        data -> data.appearanceOptionId).add()
                .append(new KeyedCodec<>("AppearanceColorId", Codec.STRING),
                        (data, value) -> data.appearanceColorId = value,
                        data -> data.appearanceColorId).add()
                .append(new KeyedCodec<>("AppearanceVariantId", Codec.STRING),
                        (data, value) -> data.appearanceVariantId = value,
                        data -> data.appearanceVariantId).add()
                .append(new KeyedCodec<>("VoiceEmotion", Codec.STRING),
                        (data, value) -> data.voiceEmotion = value,
                        data -> data.voiceEmotion).add()
                .append(new KeyedCodec<>("VoiceRecordingGeneration", Codec.STRING),
                        (data, value) -> data.voiceRecordingGeneration = value,
                        data -> data.voiceRecordingGeneration).add()
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
        private String inventoryViewRevision;
        private String profileField;
        private String profileFieldValue;
        private String profileCategory;
        private String profileGenerateScope;
        private String profileProposalSelection;
        private String appearancePrimary;
        private String appearanceCategory;
        private String appearanceSearch;
        private String appearanceCatalogHash;
        private String appearanceOptionId;
        private String appearanceColorId;
        private String appearanceVariantId;
        private String voiceEmotion;
        private String voiceRecordingGeneration;
    }
}
