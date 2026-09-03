package com.inigmasgames.taverns;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin.BuilderState;
import com.hypixel.hytale.builtin.buildertools.PrototypePlayerBuilderToolSettings;
import com.hypixel.hytale.builtin.buildertools.tooloperations.ToolOperation;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLaserPointer;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetTransformationModeState;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolState;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.protocol.packets.interface_.UpdateVisibleHudComponents;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.player.SetGameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.joml.Vector3i;

/** Runs the shared six-face zoning editor for any implemented Core definition. */
final class CoreModeManager {
    static final String SELECTION_TOOL_ITEM_ID = "EditorTool_Selection";
    private static final long FEEDBACK_THROTTLE_NANOS = 500_000_000L;
    private static final long FACE_PREVIEW_THROTTLE_NANOS = 100_000_000L;
    private static final long CLIENT_PRESENTATION_REFRESH_NANOS = 1_000_000_000L;
    private static final long EDITOR_EXIT_ARM_NANOS = 1_000_000_000L;
    private static final int FACE_PREVIEW_DURATION_MS = 180;
    private static final int AFFORDABLE_COLOR = 0x00FF00;
    private static final int UNAFFORDABLE_COLOR = 0xFF0000;

    private final TavernRepository repository;
    private final CoreValidator validator;
    private final CoreModePermissionProvider permissionProvider = new CoreModePermissionProvider();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastToggleNanos = new ConcurrentHashMap<>();
    private ItemBase selectionToolTemplate;
    private BiConsumer<PlayerRef, Store<EntityStore>> previousBoundsUpdatedCallback;
    private BiConsumer<PlayerRef, Store<EntityStore>> boundsUpdatedCallback;
    private BiConsumer<PlayerRef, Store<EntityStore>> previousModeDeactivatedCallback;
    private BiConsumer<PlayerRef, Store<EntityStore>> modeDeactivatedCallback;
    private BiConsumer<PlayerRef, Store<EntityStore>> previousClearedCallback;
    private BiConsumer<PlayerRef, Store<EntityStore>> clearedCallback;
    private boolean transformationObserverRegistered;

    CoreModeManager(TavernRepository repository, CoreValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    void start() {
        PermissionsModule.get().addProvider(permissionProvider);
        BuilderToolsPlugin builderTools = BuilderToolsPlugin.get();
        if (builderTools == null || builderTools.isDisabled()) {
            throw new IllegalStateException("Taverns requires Hytale's Builder Tools plugin");
        }
        Item selectionTool = Item.getAssetMap().getAsset(SELECTION_TOOL_ITEM_ID);
        if (selectionTool == null || selectionTool.toPacket().builderToolData == null) {
            throw new IllegalStateException("Hytale's Selection Tool asset is unavailable");
        }
        selectionToolTemplate = new ItemBase(selectionTool.toPacket());
        selectionToolTemplate.interactions = selectionToolTemplate.interactions == null
                ? new HashMap<>()
                : new HashMap<>(selectionToolTemplate.interactions);
        selectionToolTemplate.interactions.put(InteractionType.Use,
                RootInteraction.getRootInteractionIdOrUnknown("Block_Secondary"));
        previousBoundsUpdatedCallback = builderTools.getSelectionBoundsUpdatedCallback();
        boundsUpdatedCallback = (playerRef, store) -> {
            Session session = sessions.get(playerRef.getUuid());
            boolean confirmingResize = session != null && session.resizeActive;
            beginResizeFromBuilderTool(playerRef, store);
            if (confirmingResize) {
                requestCommitFromBuilderTool(playerRef);
            }
            if (previousBoundsUpdatedCallback != null) {
                previousBoundsUpdatedCallback.accept(playerRef, store);
            }
        };
        builderTools.setSelectionBoundsUpdatedCallback(boundsUpdatedCallback);
        previousModeDeactivatedCallback = builderTools.getBuilderToolModeDeactivatedCallback();
        modeDeactivatedCallback = (playerRef, store) -> {
            requestCommitFromBuilderTool(playerRef);
            if (previousModeDeactivatedCallback != null) {
                previousModeDeactivatedCallback.accept(playerRef, store);
            }
        };
        builderTools.setBuilderToolModeDeactivatedCallback(modeDeactivatedCallback);
        previousClearedCallback = builderTools.getSelectionClearedCallback();
        clearedCallback = (playerRef, store) -> {
            Session session = sessions.get(playerRef.getUuid());
            if (session == null) {
                if (previousClearedCallback != null) {
                    previousClearedCallback.accept(playerRef, store);
                }
                return;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            Player player = ref == null ? null : store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            BuilderState state = builderTools.getBuilderState(player, playerRef);
            applySelection(state, session.acceptedBounds);
            playerRef.sendMessage(Message.raw(
                    "A Core selection cannot be cancelled. Interact with the Core again to close Zoning Editor."));
        };
        builderTools.setSelectionClearedCallback(clearedCallback);
        if (!transformationObserverRegistered) {
            ServerManager.get().registerSubPacketHandlers(
                    packetHandler -> () -> installTransformationModeObserver(packetHandler));
            transformationObserverRegistered = true;
        }
    }

    void shutdown() {
        BuilderToolsPlugin builderTools = BuilderToolsPlugin.get();
        if (builderTools != null) {
            if (builderTools.getSelectionBoundsUpdatedCallback() == boundsUpdatedCallback) {
                builderTools.setSelectionBoundsUpdatedCallback(previousBoundsUpdatedCallback);
            }
            if (builderTools.getBuilderToolModeDeactivatedCallback() == modeDeactivatedCallback) {
                builderTools.setBuilderToolModeDeactivatedCallback(previousModeDeactivatedCallback);
            }
            if (builderTools.getSelectionClearedCallback() == clearedCallback) {
                builderTools.setSelectionClearedCallback(previousClearedCallback);
            }
        }
        for (Session session : sessions.values()) {
            restoreClientPresentation(session, session.latestInventory);
        }
        sessions.clear();
        lastToggleNanos.clear();
        permissionProvider.clear();
        PermissionsModule.get().removeProvider(permissionProvider);
    }

    boolean isActive(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    void handleMouseButton(PlayerMouseButtonEvent event) {
        if (event.getMouseButton() == null
                || event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }
        PlayerRef playerRef = event.getPlayerRefComponent();
        Ref<EntityStore> ref = event.getPlayerRef();
        if (playerRef == null || ref == null || !ref.isValid()
                || !sessions.containsKey(playerRef.getUuid())) {
            return;
        }
        if (event.getMouseButton().mouseButtonType == MouseButtonType.Left) {
            beginResizeFromBuilderTool(playerRef, ref.getStore());
        } else if (event.getMouseButton().mouseButtonType == MouseButtonType.Right) {
            requestCommitFromBuilderTool(playerRef);
        }
    }
    boolean isActiveCoreTarget(UUID playerId, int x, int y, int z) {
        Session session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        Optional<CoreRecord> core = repository.findCoreById(session.coreId);
        return core.isPresent()
                && core.get().coreX() == x
                && core.get().coreY() == y
                && core.get().coreZ() == z;
    }

    private void beginResizeFromBuilderTool(PlayerRef playerRef, Store<EntityStore> store) {
        Session session = sessions.get(playerRef.getUuid());
        Ref<EntityStore> ref = playerRef.getReference();
        if (session == null || session.resizeActive || ref == null || !ref.isValid()) {
            return;
        }
        Optional<CoreRecord> stored = repository.findCoreById(session.coreId);
        if (stored.isEmpty()) {
            return;
        }
        CoreDefinition definition = CoreDefinitions.require(stored.get().type());
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, ref, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        int availableShards = countExpansionItems(inventory, definition);
        session.beginResize(availableShards);
        session.hud.showShardBaseline(definition.expansionItemId(), availableShards);
    }

    private void requestCommitFromBuilderTool(PlayerRef playerRef) {
        Session session = sessions.get(playerRef.getUuid());
        if (session != null && session.resizeActive) {
            session.selectionTransformActive = false;
            session.requestCommit();
        }
    }

    private void installTransformationModeObserver(IPacketHandler packetHandler) {
        Consumer<ToServerPacket> nativeHandler = registeredPacketHandler(
                packetHandler, BuilderToolSetTransformationModeState.PACKET_ID);
        if (nativeHandler == null) {
            return;
        }
        packetHandler.registerHandler(
                BuilderToolSetTransformationModeState.PACKET_ID,
                packet -> {
                    nativeHandler.accept(packet);
                    if (packet instanceof BuilderToolSetTransformationModeState transformation) {
                        observeTransformationModePacket(packetHandler, transformation.enabled);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ToServerPacket> registeredPacketHandler(
            IPacketHandler packetHandler, int packetId) {
        if (!(packetHandler instanceof GenericPacketHandler genericPacketHandler)) {
            return null;
        }
        try {
            Field handlersField = GenericPacketHandler.class.getDeclaredField("handlers");
            if (!handlersField.trySetAccessible()) {
                return null;
            }
            Consumer<ToServerPacket>[] handlers =
                    (Consumer<ToServerPacket>[]) handlersField.get(genericPacketHandler);
            return packetId < handlers.length ? handlers[packetId] : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private void observeTransformationModePacket(
            IPacketHandler packetHandler, boolean enabled) {
        PlayerRef playerRef = packetHandler.getPlayerRef();
        if (playerRef == null || !sessions.containsKey(playerRef.getUuid())) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> handleTransformationModeChanged(
                playerRef, ref, store, enabled));
    }

    private void handleTransformationModeChanged(
            PlayerRef playerRef,
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            boolean enabled) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null || !ref.isValid()) {
            return;
        }
        if (enabled) {
            session.selectionTransformActive = true;
            beginResizeFromBuilderTool(playerRef, store);
            return;
        }
        session.selectionTransformActive = false;
        if (session.resizeActive) {
            session.requestCommit();
        }
    }
    private void detectBuilderToolActivation(PlayerRef playerRef, Ref<EntityStore> ref,
                                             Store<EntityStore> store, Session session) {
        PrototypePlayerBuilderToolSettings settings =
                ToolOperation.getOrCreatePrototypeSettings(playerRef.getUuid());
        boolean active = settings.isInSelectionTransformationMode();
        if (active && !session.selectionTransformActive) {
            session.selectionTransformActive = true;
            beginResizeFromBuilderTool(playerRef, store);
        } else if (!active && session.selectionTransformActive) {
            session.selectionTransformActive = false;
            if (session.resizeActive) {
                session.requestCommit();
            }
        }
    }
    void toggle(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, CoreRecord core) {
        long now = System.nanoTime();
        Session existing = sessions.get(playerRef.getUuid());
        if (existing != null && now < existing.exitArmedNanos) {
            return;
        }
        Long previous = lastToggleNanos.put(playerRef.getUuid(), now);
        if (previous != null && now - previous < 300_000_000L) {
            return;
        }
        if (existing != null) {
            exit(playerRef, ref, store, true);
            return;
        }
        enter(playerRef, ref, store, core);
    }

    void enter(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, CoreRecord core) {
        Player player = store.getComponent(ref, Player.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (player == null || hotbar == null || hotbar.getInventory().getCapacity() == 0) {
            playerRef.sendMessage(Message.raw("Zoning Editor could not start because the player hotbar is unavailable."));
            return;
        }

        InventorySection initialHotbar = new InventorySection(hotbar.getInventory().toPacket());
        TavernsHud hud;
        if (player.getHudManager().getCustomHud(TavernsHud.KEY) instanceof TavernsHud existingHud) {
            hud = existingHud;
        } else {
            hud = new TavernsHud(playerRef);
            player.getHudManager().addCustomHud(playerRef, hud);
        }
        CoreDefinition definition = CoreDefinitions.require(core.type());
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, ref, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        int shardBalance = countExpansionItems(inventory, definition);
        Session session = new Session(playerRef, core.coreId(), core.bounds(), player.getGameMode(),
                new HashSet<>(player.getHudManager().getVisibleHudComponents()), initialHotbar,
                createInventorySnapshot(ref, store, initialHotbar), hud,
                definition.expansionItemId(), shardBalance);
        sessions.put(playerRef.getUuid(), session);
        hud.showShardBalance(definition.expansionItemId(), shardBalance);
        permissionProvider.activate(playerRef.getUuid());
        hotbar.setActiveSlot(hotbar.getActiveSlot(), ref, store);
        syncClientPresentation(playerRef, ref, store, player, hotbar, session, true);

        BuilderState state = BuilderToolsPlugin.get().getBuilderState(player, playerRef);
        applySelection(state, core.bounds());
        playerRef.sendMessage(Message.raw(
                "Zoning Editor active. Your hotbar items are temporarily hidden. "
                        + "Left-click a Core face to toggle resizing, move it with your mouse, "
                        + "then right-click to save. "
                        + "Interact with the Core again to finish."));
    }

    void exit(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, boolean notify) {
        Session pending = sessions.get(playerRef.getUuid());
        if (pending != null && pending.resizeActive
                && repository.findCoreById(pending.coreId).isPresent()) {
            pending.requestCommit();
            validateCurrentSelection(playerRef, ref, store);
        }
        Session session = sessions.remove(playerRef.getUuid());
        permissionProvider.deactivate(playerRef.getUuid());
        if (session == null) {
            return;
        }
        lastToggleNanos.put(playerRef.getUuid(), System.nanoTime());
        session.hud.hideShardCounter();

        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            session.serverGameMode = player.getGameMode();
            session.visibleHudComponents = new HashSet<>(
                    player.getHudManager().getVisibleHudComponents());
        }
        UpdatePlayerInventory currentInventory = hotbar == null
                ? session.latestInventory
                : createInventorySnapshot(ref, store, hotbar.getInventory().toPacket());
        restoreClientPresentation(session, currentInventory);
        if (hotbar != null) {
            hotbar.setActiveSlot(hotbar.getActiveSlot(), ref, store);
        }

        if (player != null) {
            BuilderState state = BuilderToolsPlugin.get().getBuilderState(player, playerRef);
            state.deselect(store);
        }
        if (notify) {
            playerRef.sendMessage(Message.raw(
                    "Zoning Editor closed. Your hotbar items are visible again; the Core volume remains saved."));
        }
    }

    void abandon(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null) {
            session.hud.hideShardCounter();
            restoreClientPresentation(session, session.latestInventory);
        }
        permissionProvider.deactivate(playerId);
        lastToggleNanos.remove(playerId);
    }

    void validateCurrentSelection(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (player == null || hotbar == null) {
            abandon(playerRef.getUuid());
            return;
        }

        syncClientPresentation(playerRef, ref, store, player, hotbar, session, false);
        detectBuilderToolActivation(playerRef, ref, store, session);

        BuilderState state = BuilderToolsPlugin.get().getBuilderState(player, playerRef);
        BlockSelection selection = state.getSelection();
        long now = System.nanoTime();
        Cuboid selectionBounds;
        if (selection == null || !selection.hasSelectionBounds()) {
            // Losing the live selection cancels the proposal without inventory mutation.
            applySelection(state, session.acceptedBounds);
            session.finishResize();
            return;
        }

        selectionBounds = Cuboid.fromSelection(selection.getSelectionMin(), selection.getSelectionMax());
        if (session.resizeActive && session.proposedBounds != null
                && selectionBounds.equals(session.acceptedBounds)) {
            selectionBounds = session.proposedBounds;
        }
        Optional<CoreRecord> stored = repository.findCoreById(session.coreId);
        if (stored.isEmpty()) {
            exit(playerRef, ref, store, false);
            playerRef.sendMessage(Message.raw("This Core no longer has a persistent record."));
            return;
        }
        CoreRecord core = stored.get();
        CoreDefinition definition = CoreDefinitions.require(core.type());
        CombinedItemContainer liveInventory = InventoryComponent.getCombined(
                store, ref, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        int liveShardBalance = countExpansionItems(liveInventory, definition);
        if (liveShardBalance != session.baselineShardCount) {
            session.baselineShardCount = liveShardBalance;
            if (!session.resizeActive) {
                session.hud.showShardBalance(definition.expansionItemId(), liveShardBalance);
            }
        }

        if (!session.resizeActive && !selectionBounds.equals(session.acceptedBounds)) {
            session.beginResize(liveShardBalance);
        }
        if (session.holdInitialShardBaseline()) {
            session.hud.showShardBaseline(
                    definition.expansionItemId(), session.baselineShardCount);
            return;
        }

        if (selectionBounds.equals(session.acceptedBounds)) {
            if (session.resizeActive) {
                updateShardCounterFrame(
                        session, ref, store, player, core, definition, selectionBounds);
            }
            if (session.commitRequested) {
                session.finishResize();
            }
            return;
        }
        session.observeProposal(selectionBounds, now);

        // Recalculate from the live selection during every active drag tick.
        ShardCounterFrame counterFrame = updateShardCounterFrame(
                session, ref, store, player, core, definition, selectionBounds);
        Cuboid proposedBounds = clampToMinimum(selectionBounds, session.activeFace, definition);
        if (!proposedBounds.equals(selectionBounds)) {
            applySelection(state, proposedBounds);
            session.observeProposal(proposedBounds, now);
            counterFrame = updateShardCounterFrame(
                    session, ref, store, player, core, definition, proposedBounds);
            showResizeFeedback(session, playerRef, ref, store, proposedBounds, false,
                    "Core resize stopped at its minimum dimensions: "
                            + definition.startingWidth() + " x " + definition.startingDepth()
                            + " x " + definition.startingHeight() + ".",
                    now);
        }

        int newUnits = counterFrame.newUnits();
        ExpansionTransfer transfer = counterFrame.transfer();
        int unitDifference = transfer.unitDifference();
        int shardTransfer = transfer.shardTransfer();
        CombinedItemContainer inventory = counterFrame.inventory();
        int availableShards = counterFrame.baselineShards();

        if (player.getGameMode() != GameMode.Creative
                && availableShards == 0 && shardTransfer > 0) {
            boolean conclude = session.commitRequested;
            applySelection(state, session.acceptedBounds);
            session.discardProposalKeepResize();
            showResizeFeedback(session, playerRef, ref, store, session.acceptedBounds, false,
                    "Core face locked: no Crystal Shards are available.", now);
            if (conclude) {
                session.finishResize();
            }
            return;
        }

        CoreRecord proposed = core.withBounds(
                proposedBounds, newUnits, transfer.paidExpansionUnits());
        Optional<String> invalidReason = validator.validate(proposed);
        if (invalidReason.isPresent()) {
            boolean rejectedCommit = session.commitRequested;
            session.commitRequested = false;
            showResizeFeedback(session, playerRef, ref, store, proposedBounds, false,
                    "Core resize invalid: " + invalidReason.get(), now);
            if (rejectedCommit) {
                applySelection(state, session.acceptedBounds);
                session.finishResize();
            }
            return;
        }

        boolean affordablePreview = player.getGameMode() == GameMode.Creative
                || shardTransfer <= session.baselineShardCount;
        showResizeFeedback(session, playerRef, ref, store, proposedBounds, affordablePreview,
                affordablePreview
                        ? affordableMessage(player, unitDifference, shardTransfer)
                        : "Core resize unaffordable: need " + shardTransfer + " Crystal Shard(s).",
                now);
        if (!session.commitRequested) {
            // Left release only leaves the proposed visual bounds in place.
            return;
        }
        session.commitRequested = false;

        // Right-click is the sole transaction boundary. Recheck the live authoritative
        // inventory before consuming shards or allocating a refund.
        String transferProblem = transferProblem(inventory, definition, shardTransfer);
        if (transferProblem != null) {
            showResizeFeedback(session, playerRef, ref, store, proposedBounds, false,
                    transferProblem, now);
            applySelection(state, session.acceptedBounds);
            session.finishResize();
            return;
        }
        if (!transferExpansionItems(ref, store, inventory, definition, shardTransfer)) {
            showResizeFeedback(session, playerRef, ref, store, proposedBounds, false,
                    shardTransfer > 0
                            ? "Core resize unaffordable: not enough Crystal Shards."
                            : "Core resize blocked: make room for the Crystal Shard refund.",
                    now);
            applySelection(state, session.acceptedBounds);
            session.finishResize();
            return;
        }

        int committedShardBalance = countExpansionItems(inventory, definition);
        session.baselineShardCount = committedShardBalance;
        repository.updateCore(proposed);
        session.acceptedBounds = proposedBounds;
        session.finishResize();
        InventorySection committedHotbar = new InventorySection(hotbar.getInventory().toPacket());
        UpdatePlayerInventory committedInventory =
                createInventorySnapshot(ref, store, committedHotbar);
        sendInventorySnapshot(playerRef, committedInventory);
        session.latestHotbar = committedHotbar;
        session.latestInventory = committedInventory;
        session.virtualToolVisible = false;
        session.virtualToolSlot = -1;
        syncVirtualSelectionTool(playerRef, ref, store, hotbar, session, true);
        String transferSummary = shardTransfer < 0
                ? " Refunded " + (-shardTransfer) + " Crystal Shard(s)."
                : shardTransfer > 0
                        ? " Spent " + shardTransfer + " Crystal Shard(s)."
                        : "";
        playerRef.sendMessage(Message.raw(
                "Core volume saved: " + proposedBounds.volume() + " blocks across "
                        + proposed.intersectedChunks().size() + " chunk(s)." + transferSummary));
    }

    static int projectedShardTotal(int availableShards, int shardTransfer) {
        return availableShards - shardTransfer;
    }

    static TavernsHud.CounterTone counterTone(int availableShards, int shardTransfer) {
        if (shardTransfer < 0) {
            return TavernsHud.CounterTone.GREEN;
        }
        if (shardTransfer > availableShards) {
            return TavernsHud.CounterTone.RED;
        }
        return TavernsHud.CounterTone.WHITE;
    }

    private ShardCounterFrame updateShardCounterFrame(
            Session session,
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            Player player,
            CoreRecord core,
            CoreDefinition definition,
            Cuboid bounds) {
        int newUnits = definition.expansionUnits(bounds.volume());
        ExpansionTransfer transfer = planExpansionTransfer(core, newUnits, player.getGameMode());
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, ref, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        int baselineShards = countExpansionItems(inventory, definition);
        session.baselineShardCount = baselineShards;
        session.hud.updateShardCounter(
                definition.expansionItemId(), baselineShards, transfer.shardTransfer());
        return new ShardCounterFrame(newUnits, transfer, inventory, baselineShards);
    }

    private record ShardCounterFrame(
            int newUnits,
            ExpansionTransfer transfer,
            CombinedItemContainer inventory,
            int baselineShards) {
    }

    private static int countExpansionItems(CombinedItemContainer inventory, CoreDefinition definition) {
        return inventory.countItemStacks(
                stack -> definition.expansionItemId().equals(stack.getItemId()));
    }
    static ExpansionTransfer planExpansionTransfer(CoreRecord core, int newUnits, GameMode gameMode) {
        int unitDifference = newUnits - core.expansionUnits();
        int shardTransfer = 0;
        int paidExpansionUnits = core.paidExpansionUnits();
        if (unitDifference > 0 && gameMode != GameMode.Creative) {
            shardTransfer = unitDifference;
            paidExpansionUnits += unitDifference;
        } else if (unitDifference < 0) {
            int refundableUnits = Math.min(core.paidExpansionUnits(), -unitDifference);
            shardTransfer = -refundableUnits;
            paidExpansionUnits -= refundableUnits;
        }
        return new ExpansionTransfer(unitDifference, shardTransfer, paidExpansionUnits);
    }

    private static Cuboid clampToMinimum(Cuboid bounds, ResizeFace activeFace,
                                          CoreDefinition definition) {
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();
        if (bounds.width() < definition.startingWidth()) {
            if (activeFace == ResizeFace.MIN_X) {
                minX = maxX - definition.startingWidth() + 1;
            } else {
                maxX = minX + definition.startingWidth() - 1;
            }
        }
        if (bounds.height() < definition.startingHeight()) {
            if (activeFace == ResizeFace.MIN_Y) {
                minY = maxY - definition.startingHeight() + 1;
            } else {
                maxY = minY + definition.startingHeight() - 1;
            }
        }
        if (bounds.depth() < definition.startingDepth()) {
            if (activeFace == ResizeFace.MIN_Z) {
                minZ = maxZ - definition.startingDepth() + 1;
            } else {
                maxZ = minZ + definition.startingDepth() - 1;
            }
        }
        return new Cuboid(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String transferProblem(CombinedItemContainer inventory,
                                          CoreDefinition definition, int shardTransfer) {
        if (shardTransfer == 0) {
            return null;
        }
        if (shardTransfer > 0) {
            ItemStack cost = new ItemStack(definition.expansionItemId(), shardTransfer);
            return inventory.canRemoveItemStack(cost)
                    ? null
                    : "Core resize unaffordable: need " + shardTransfer + " Crystal Shard(s).";
        }
        ItemStack refund = new ItemStack(definition.expansionItemId(), -shardTransfer);
        return inventory.canAddItemStack(refund)
                ? null
                : "Core resize blocked: make room for " + (-shardTransfer)
                        + " Crystal Shard refund(s).";
    }

    private static boolean transferExpansionItems(Ref<EntityStore> ref, Store<EntityStore> store,
                                                   CombinedItemContainer inventory,
                                                   CoreDefinition definition, int shardTransfer) {
        int before = countExpansionItems(inventory, definition);
        if (shardTransfer == 0) {
            return true;
        }
        boolean changed;
        if (shardTransfer > 0) {
            ItemStack cost = new ItemStack(definition.expansionItemId(), shardTransfer);
            changed = inventory.canRemoveItemStack(cost)
                    && inventory.removeItemStack(cost).succeeded();
        } else {
            ItemStack refund = new ItemStack(definition.expansionItemId(), -shardTransfer);
            if (!inventory.canAddItemStack(refund)) {
                return false;
            }
            // Match Hytale's /give command: let the player's pickup settings choose
            // the authoritative destination container and report any remainder.
            var transaction = Player.giveItem(refund, ref, store);
            changed = transaction.succeeded()
                    && ItemStack.isEmpty(transaction.getRemainder());
            if (changed) {
                Player.notifyPickupItem(ref, refund, null, store);
            }
        }
        int expected = before - shardTransfer;
        int after = countExpansionItems(inventory, definition);
        return changed && after == expected;
    }


    private static String affordableMessage(Player player, int unitDifference, int shardTransfer) {
        if (shardTransfer > 0) {
            return "Core resize affordable: " + shardTransfer + " Crystal Shard(s) required.";
        }
        if (shardTransfer < 0) {
            return "Core resize valid: " + (-shardTransfer) + " Crystal Shard(s) will be refunded.";
        }
        if (unitDifference > 0 && player.getGameMode() == GameMode.Creative) {
            return "Core resize valid: Creative mode has no shard cost.";
        }
        if (unitDifference < 0) {
            return "Core resize valid: no paid expansion remains to refund.";
        }
        return "Core resize valid: no shard transfer required.";
    }
    private static void showResizeFeedback(Session session, PlayerRef playerRef,
                                           Ref<EntityStore> ref, Store<EntityStore> store,
                                           Cuboid bounds, boolean affordable, String text, long now) {
        showFacePreview(session, playerRef, ref, store, bounds,
                affordable ? AFFORDABLE_COLOR : UNAFFORDABLE_COLOR, now);
        if (session.lastFeedbackAffordable != null
                && session.lastFeedbackAffordable == affordable) {
            return;
        }
        if (now - session.lastFeedbackNanos < FEEDBACK_THROTTLE_NANOS) {
            return;
        }
        session.lastFeedbackAffordable = affordable;
        session.lastFeedbackNanos = now;
        playerRef.sendMessage(Message.raw(text));
    }

    private static void showFacePreview(Session session, PlayerRef playerRef,
                                        Ref<EntityStore> ref, Store<EntityStore> store,
                                        Cuboid bounds, int color, long now) {
        if (session.activeFace == null
                || now - session.lastFacePreviewNanos < FACE_PREVIEW_THROTTLE_NANOS) {
            return;
        }
        NetworkId networkId = store.getComponent(ref, NetworkId.getComponentType());
        if (networkId == null) {
            return;
        }
        session.lastFacePreviewNanos = now;
        float minX = bounds.minX();
        float minY = bounds.minY();
        float minZ = bounds.minZ();
        float maxX = bounds.maxX() + 1.0f;
        float maxY = bounds.maxY() + 1.0f;
        float maxZ = bounds.maxZ() + 1.0f;
        int id = networkId.getId();
        switch (session.activeFace) {
            case MIN_X -> sendFaceOutline(playerRef, id, color,
                    minX, minY, minZ, minX, maxY, maxZ, FaceAxis.X);
            case MAX_X -> sendFaceOutline(playerRef, id, color,
                    maxX, minY, minZ, maxX, maxY, maxZ, FaceAxis.X);
            case MIN_Y -> sendFaceOutline(playerRef, id, color,
                    minX, minY, minZ, maxX, minY, maxZ, FaceAxis.Y);
            case MAX_Y -> sendFaceOutline(playerRef, id, color,
                    minX, maxY, minZ, maxX, maxY, maxZ, FaceAxis.Y);
            case MIN_Z -> sendFaceOutline(playerRef, id, color,
                    minX, minY, minZ, maxX, maxY, minZ, FaceAxis.Z);
            case MAX_Z -> sendFaceOutline(playerRef, id, color,
                    minX, minY, maxZ, maxX, maxY, maxZ, FaceAxis.Z);
        }
    }

    private static void sendFaceOutline(PlayerRef playerRef, int networkId, int color,
                                        float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ, FaceAxis axis) {
        switch (axis) {
            case X -> {
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, maxX, maxY, minZ);
                sendPreviewLine(playerRef, networkId, color, minX, minY, maxZ, maxX, maxY, maxZ);
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, maxX, minY, maxZ);
                sendPreviewLine(playerRef, networkId, color, minX, maxY, minZ, maxX, maxY, maxZ);
            }
            case Y -> {
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, maxX, maxY, minZ);
                sendPreviewLine(playerRef, networkId, color, minX, minY, maxZ, maxX, maxY, maxZ);
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, minX, maxY, maxZ);
                sendPreviewLine(playerRef, networkId, color, maxX, minY, minZ, maxX, maxY, maxZ);
            }
            case Z -> {
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, maxX, minY, maxZ);
                sendPreviewLine(playerRef, networkId, color, minX, maxY, minZ, maxX, maxY, maxZ);
                sendPreviewLine(playerRef, networkId, color, minX, minY, minZ, minX, maxY, maxZ);
                sendPreviewLine(playerRef, networkId, color, maxX, minY, minZ, maxX, maxY, maxZ);
            }
        }
    }

    private static void sendPreviewLine(PlayerRef playerRef, int networkId, int color,
                                        float startX, float startY, float startZ,
                                        float endX, float endY, float endZ) {
        playerRef.getPacketHandler().writeNoCache(new BuilderToolLaserPointer(
                networkId,
                startX, startY, startZ,
                endX, endY, endZ,
                color, FACE_PREVIEW_DURATION_MS));
    }

    private static void applySelection(BuilderState state, Cuboid bounds) {
        state.update(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        state.sendArea();
    }

    private void syncClientPresentation(PlayerRef playerRef, Ref<EntityStore> ref,
                                        Store<EntityStore> store, Player player,
                                        InventoryComponent.Hotbar hotbar, Session session,
                                        boolean entering) {
        long now = System.nanoTime();
        boolean refreshDue = entering
                || now - session.lastClientPresentationNanos >= CLIENT_PRESENTATION_REFRESH_NANOS;
        GameMode actualGameMode = player.getGameMode();
        if (entering || actualGameMode != session.serverGameMode) {
            session.serverGameMode = actualGameMode;
            // Client presentation only: the authoritative Player remains in its real game mode.
            playerRef.getPacketHandler().writeNoCache(new SetGameMode(GameMode.Creative));
        }

        Set<HudComponent> actualHud = new HashSet<>(
                player.getHudManager().getVisibleHudComponents());
        if (entering || !actualHud.equals(session.visibleHudComponents)) {
            session.visibleHudComponents = actualHud;
            sendHudWithoutHotbar(playerRef, actualHud);
        }

        applyEditorBehaviorToHotbar(playerRef, session);
        syncVirtualSelectionTool(playerRef, ref, store, hotbar, session, refreshDue);
        if (refreshDue) {
            session.lastClientPresentationNanos = now;
        }
    }

    private static void sendHudWithoutHotbar(PlayerRef playerRef,
                                             Set<HudComponent> visibleComponents) {
        Set<HudComponent> zoningHud = new HashSet<>(visibleComponents);
        zoningHud.remove(HudComponent.Hotbar);
        playerRef.getPacketHandler().writeNoCache(new UpdateVisibleHudComponents(
                zoningHud.toArray(HudComponent[]::new)));
    }

    private static void syncVirtualSelectionTool(PlayerRef playerRef, Ref<EntityStore> ref,
                                                 Store<EntityStore> store,
                                                 InventoryComponent.Hotbar hotbar,
                                                 Session session, boolean forceRefresh) {
        InventorySection actualHotbar = new InventorySection(hotbar.getInventory().toPacket());
        boolean hotbarChanged = !actualHotbar.equals(session.latestHotbar);
        session.latestHotbar = actualHotbar;
        session.latestInventory = createInventorySnapshot(ref, store, actualHotbar);

        short activeSlot = (short) Byte.toUnsignedInt(hotbar.getActiveSlot());
        if (forceRefresh || !session.virtualToolVisible || hotbarChanged || session.virtualToolSlot != activeSlot) {
            Map<Integer, com.hypixel.hytale.protocol.ItemWithAllMetadata> clientItems =
                    new HashMap<>(actualHotbar.items);
            clientItems.put((int) activeSlot, new ItemStack(SELECTION_TOOL_ITEM_ID).toPacket());
            UpdatePlayerInventory virtualInventory =
                    new UpdatePlayerInventory(session.latestInventory);
            virtualInventory.hotbar = new InventorySection(clientItems, actualHotbar.capacity);
            sendInventorySnapshot(playerRef, virtualInventory);
            session.virtualToolVisible = true;
            session.virtualToolSlot = activeSlot;
        }
    }

    private static UpdatePlayerInventory createInventorySnapshot(
            Ref<EntityStore> ref, Store<EntityStore> store, InventorySection hotbar) {
        InventoryComponent.Storage storage =
                store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Armor armor =
                store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Utility utility =
                store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Tool tools =
                store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        InventoryComponent.Backpack backpack =
                store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        return new UpdatePlayerInventory(
                section(storage), section(armor), hotbar, section(utility), section(tools),
                section(backpack));
    }

    private static InventorySection section(InventoryComponent component) {
        return component == null ? null : component.getInventory().toPacket();
    }

    private static void sendInventorySnapshot(PlayerRef playerRef,
                                              UpdatePlayerInventory inventory) {
        playerRef.getPacketHandler().writeNoCache(inventory);
    }

    private void restoreClientPresentation(Session session,
                                           UpdatePlayerInventory currentInventory) {
        restoreClientItemDefinitions(session);
        if (!session.playerRef.getPacketHandler().stillActive()) {
            session.virtualToolVisible = false;
            session.virtualToolSlot = -1;
            return;
        }
        if (currentInventory != null) {
            sendInventorySnapshot(session.playerRef, new UpdatePlayerInventory(currentInventory));
        }
        session.playerRef.getPacketHandler().writeNoCache(new UpdateVisibleHudComponents(
                session.visibleHudComponents.toArray(HudComponent[]::new)));
        session.playerRef.getPacketHandler().writeNoCache(new SetGameMode(session.serverGameMode));
        session.virtualToolVisible = false;
        session.virtualToolSlot = -1;
    }

    private void applyEditorBehaviorToHotbar(PlayerRef playerRef, Session session) {
        if (session.overriddenItemIds.contains(SELECTION_TOOL_ITEM_ID)) {
            return;
        }
        Map<String, ItemBase> updates = new LinkedHashMap<>();
        updates.put(SELECTION_TOOL_ITEM_ID, new ItemBase(selectionToolTemplate));
        playerRef.getPacketHandler().writeNoCache(new UpdateItems(
                UpdateType.AddOrUpdate, updates, new String[0], false, false));
        session.overriddenItemIds.add(SELECTION_TOOL_ITEM_ID);
    }
    private void restoreClientItemDefinitions(Session session) {
        if (session.overriddenItemIds.isEmpty() || !session.playerRef.getPacketHandler().stillActive()) {
            session.overriddenItemIds.clear();
            return;
        }
        Map<String, ItemBase> originals = new LinkedHashMap<>();
        for (String itemId : session.overriddenItemIds) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null) {
                originals.put(itemId, new ItemBase(item.toPacket()));
            }
        }
        if (!originals.isEmpty()) {
            session.playerRef.getPacketHandler().writeNoCache(new UpdateItems(
                    UpdateType.AddOrUpdate, originals, new String[0], false, false));
        }
        session.overriddenItemIds.clear();
    }

    record ExpansionTransfer(int unitDifference, int shardTransfer, int paidExpansionUnits) {
    }

    private static final class Session {
        private final PlayerRef playerRef;
        private final UUID coreId;
        private Cuboid acceptedBounds;
        private Cuboid proposedBounds;
        private ResizeFace activeFace;
        private boolean resizeActive;
        private boolean commitRequested;
        private int baselineShardCount;
        private int initialBaselineFramesRemaining;
        private boolean selectionTransformActive;
        private Boolean lastFeedbackAffordable;
        private long lastFeedbackNanos;
        private long lastFacePreviewNanos;
        private final Set<String> overriddenItemIds = new HashSet<>();
        private GameMode serverGameMode;
        private Set<HudComponent> visibleHudComponents;
        private InventorySection latestHotbar;
        private UpdatePlayerInventory latestInventory;
        private boolean virtualToolVisible;
        private short virtualToolSlot = -1;
        private long lastClientPresentationNanos;
        private final long exitArmedNanos;
        private final TavernsHud hud;
        private final String expansionItemId;

        private Session(PlayerRef playerRef, UUID coreId, Cuboid acceptedBounds,
                        GameMode serverGameMode, Set<HudComponent> visibleHudComponents,
                        InventorySection latestHotbar,
                        UpdatePlayerInventory latestInventory, TavernsHud hud,
                        String expansionItemId, int shardBalance) {
            this.playerRef = playerRef;
            this.coreId = coreId;
            this.acceptedBounds = acceptedBounds;
            this.serverGameMode = serverGameMode;
            this.visibleHudComponents = visibleHudComponents;
            this.latestHotbar = latestHotbar;
            this.latestInventory = latestInventory;
            this.hud = hud;
            this.expansionItemId = expansionItemId;
            this.baselineShardCount = shardBalance;
            long now = System.nanoTime();
            this.exitArmedNanos = now + EDITOR_EXIT_ARM_NANOS;
        }

        private void observeProposal(Cuboid bounds, long now) {
            Cuboid previous = proposedBounds == null ? acceptedBounds : proposedBounds;
            ResizeFace changedFace = ResizeFace.between(previous, bounds);
            if (!bounds.equals(proposedBounds)) {
                proposedBounds = bounds;
                if (changedFace != null) {
                    activeFace = changedFace;
                }
            }
        }

        private void beginResize(int availableShards) {
            resizeActive = true;
            baselineShardCount = availableShards;
            initialBaselineFramesRemaining = 1;
            proposedBounds = null;
            activeFace = null;
            commitRequested = false;
        }

        private void requestCommit() {
            commitRequested = true;
            initialBaselineFramesRemaining = 0;
        }

        private boolean holdInitialShardBaseline() {
            if (!resizeActive || initialBaselineFramesRemaining <= 0) {
                return false;
            }
            initialBaselineFramesRemaining--;
            return true;
        }

        private void discardProposalKeepResize() {
            proposedBounds = null;
            commitRequested = false;
        }

        private void finishResize() {
            proposedBounds = null;
            activeFace = null;
            resizeActive = false;
            commitRequested = false;
            initialBaselineFramesRemaining = 0;
            selectionTransformActive = false;
            lastFeedbackAffordable = null;
            lastFeedbackNanos = 0L;
            lastFacePreviewNanos = 0L;
            hud.showShardBalance(expansionItemId, baselineShardCount);
        }
    }

    private enum FaceAxis {
        X, Y, Z
    }

    private enum ResizeFace {
        MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z;

        private static ResizeFace between(Cuboid before, Cuboid after) {
            ResizeFace result = null;
            long largestChange = 0L;
            for (ResizeFace face : values()) {
                long change = Math.abs((long) face.coordinate(after) - face.coordinate(before));
                if (change > largestChange) {
                    largestChange = change;
                    result = face;
                }
            }
            return result;
        }

        private int coordinate(Cuboid bounds) {
            return switch (this) {
                case MIN_X -> bounds.minX();
                case MAX_X -> bounds.maxX();
                case MIN_Y -> bounds.minY();
                case MAX_Y -> bounds.maxY();
                case MIN_Z -> bounds.minZ();
                case MAX_Z -> bounds.maxZ();
            };
        }
    }
}
