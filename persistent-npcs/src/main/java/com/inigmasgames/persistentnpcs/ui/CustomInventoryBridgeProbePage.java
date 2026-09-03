package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Isolated Option 2 bridge probe. Production NPC Profile is deliberately untouched. */
public final class CustomInventoryBridgeProbePage
        extends InteractiveCustomUIPage<CustomInventoryBridgeProbePage.PageData> {
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private final PlayerRef viewer;
    private final ContainerWindow npcWindow;
    private final SimpleItemContainer npcInventory;
    private final ItemContainer playerStorage;
    private final Consumer<String> diagnostics;
    private final UUID sessionId = UUID.randomUUID();
    private final long pageGeneration = GENERATIONS.incrementAndGet();
    private final AtomicLong eventSequence = new AtomicLong();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final CustomInventoryTransactionBridge bridge;
    private boolean dismissed;

    public CustomInventoryBridgeProbePage(PlayerRef viewer,
            ContainerWindow npcWindow, SimpleItemContainer npcInventory,
            ItemContainer playerStorage, Consumer<String> diagnostics) {
        super(viewer, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.viewer = viewer;
        this.npcWindow = npcWindow;
        this.npcInventory = npcInventory;
        this.playerStorage = playerStorage;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.bridge = new CustomInventoryTransactionBridge(
                sessionId, pageGeneration, viewer, this, npcWindow,
                npcInventory, playerStorage, this.diagnostics);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        int npcSectionId = npcWindow.getId();
        ItemContainer resolvedNpc = InventoryUtils.getSectionById(ref, npcSectionId, store);
        ItemContainer resolvedPlayer = InventoryUtils.getSectionById(ref,
                InventoryComponent.STORAGE_SECTION_ID, store);
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean windowActive = player != null
                && player.getWindowManager().getWindow(npcSectionId) == npcWindow;
        if (resolvedNpc != npcInventory || resolvedPlayer != playerStorage
                || !windowActive) {
            throw new IllegalStateException(
                    "Probe 11 native section identity assertion failed.");
        }

        commands.append("Pages/NativeInventoryProbe.ui");
        commands.append("#NpcGridHost", boundNpcGridDocument(npcSectionId));
        commands.append("#PlayerGridHost",
                "Pages/NativeInventoryProbe/PlayerStorage.ui");
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#NpcInventoryGrid.Slots", npcInventory);
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerStorage);
        commands.set("#ProbeTitle.Text", "SERVER-AUTHORITATIVE BRIDGE PROBE 11");
        commands.set("#OrderingText.Text",
                "LEFT-CLICK FULL STACK -> EMPTY SLOT ONLY");
        commands.set("#SectionText.Text", "LEFT section " + npcSectionId
                + " (ephemeral ContainerWindow)   |   RIGHT section "
                + InventoryComponent.STORAGE_SECTION_ID + " (Player Storage)");

        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CloseButton", EventData.of("Close", "true"));
        CustomInventoryBridgeUi.bindDrop(events, "#NpcInventoryGrid", npcSectionId);
        CustomInventoryBridgeUi.bindDrop(events, "#PlayerInventoryGrid",
                InventoryComponent.STORAGE_SECTION_ID);

        diagnostics.accept("CUSTOM_BRIDGE_PROBE11_BUILD"
                + " timestamp=" + Instant.now()
                + " player=" + viewer.getUuid()
                + " sessionId=" + sessionId
                + " pageGeneration=" + pageGeneration
                + " npcWindowId=" + npcSectionId
                + " playerStorageSectionId="
                + InventoryComponent.STORAGE_SECTION_ID
                + " trigger=Dropped"
                + " supportedOperation=LEFT_CLICK_FULL_STACK_TO_EMPTY_SLOT"
                + " nativeMutationApi=InventoryUtils.moveItem"
                + " manualStackMutation=false"
                + " uiSnapshot=FIXED_CAPACITY_SLOTS"
                + " reconciliationDifferential=A_ATOMIC_BOTH_SLOTS_REFRESH");
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
            String rawData) {
        diagnostics.accept("CUSTOM_BRIDGE_RAW_EVENT"
                + " timestamp=" + Instant.now()
                + " sessionId=" + sessionId
                + " pageGeneration=" + pageGeneration
                + " payload=" + quoted(rawData));
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
            PageData data) {
        if (data == null) return;
        if ("true".equalsIgnoreCase(data.close)) {
            close();
            return;
        }
        if (!CustomInventoryBridgeUi.DROP_MARKER.equals(data.marker)
                || !"Dropped".equals(data.event)) {
            return;
        }

        int sourceSection = value(data.sourceInventorySectionId, Integer.MIN_VALUE);
        int sourceSlot = value(data.sourceSlotId, -1);
        int targetSection = parseSection(data.section);
        int targetSlot = value(data.slotIndex, -1);
        int clientQuantity = value(data.itemStackQuantity, -1);
        int requestedQuantity = authoritativeQuantityAtIntent(
                sourceSection, sourceSlot, clientQuantity);
        var intent = new CustomInventoryTransactionBridge.InventoryMoveIntent(
                sessionId,
                pageGeneration,
                sourceSection,
                sourceSlot,
                targetSection,
                targetSlot,
                requestedQuantity,
                value(data.pressedMouseButton, -1),
                eventSequence.incrementAndGet(),
                data.itemStackId,
                clientQuantity);
        bridge.submit(ref, store, intent, this::reconcileFromAuthority);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed) return;
        dismissed = true;
        bridge.close();

        var moves = npcInventory.moveAllItemStacksTo(playerStorage);
        int fallbackStacks = 0;
        boolean fallbackSucceeded = true;
        for (short slot = 0; slot < npcInventory.getCapacity(); slot++) {
            ItemStack remaining = npcInventory.getItemStack(slot);
            if (ItemStack.isEmpty(remaining)) continue;
            var removed = npcInventory.removeItemStackFromSlot(slot);
            if (!removed.succeeded() || ItemStack.isEmpty(removed.getOutput())) {
                fallbackSucceeded = false;
                continue;
            }
            fallbackStacks++;
            fallbackSucceeded &= SimpleItemContainer.addOrDropItemStack(
                    store, ref, playerStorage, removed.getOutput());
        }
        diagnostics.accept("CUSTOM_BRIDGE_PROBE11_TEARDOWN"
                + " timestamp=" + Instant.now()
                + " player=" + viewer.getUuid()
                + " sessionId=" + sessionId
                + " pageGeneration=" + pageGeneration
                + " npcWindowId=" + npcWindow.getId()
                + " storageMoveSucceeded=" + moves.succeeded()
                + " storageMoveTransactions=" + moves.size()
                + " addOrDropFallbackStacks=" + fallbackStacks
                + " fallbackSucceeded=" + fallbackSucceeded
                + " npcContainerEmpty=" + npcInventory.isEmpty());
        super.onDismiss(ref, store);
    }

    private void reconcileFromAuthority(
            CustomInventoryTransactionBridge.BridgeResult result) {
        if (dismissed) return;
        long refresh = refreshGeneration.incrementAndGet();
        UICommandBuilder commands = new UICommandBuilder();
        // Differential A: both complete, fixed-capacity slot arrays are replaced in
        // one CustomPage update. No positional ItemStacks representation is used.
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#NpcInventoryGrid.Slots", npcInventory);
        CustomInventoryBridgeUi.setNativeSlots(
                commands, "#PlayerInventoryGrid.Slots", playerStorage);
        commands.set("#OrderingText.Text", "Operation " + result.operationId()
                + ": " + result.type() + " — " + result.reason());
        sendUpdate(commands, false);
        diagnostics.accept("CUSTOM_BRIDGE_REFRESH"
                + " timestamp=" + Instant.now()
                + " BridgeOperationId=" + result.operationId()
                + " player=" + viewer.getUuid()
                + " sessionId=" + sessionId
                + " pageGeneration=" + pageGeneration
                + " uiRefreshGeneration=" + refresh
                + " sourceAfter=" + result.sourceAfter()
                + " targetAfter=" + result.targetAfter()
                + " grids=NPC_THEN_PLAYER"
                + " mechanism=ATOMIC_FIXED_CAPACITY_SLOTS_REPLACEMENT"
                + " authoritativeReread=true");
        diagnostics.accept("CUSTOM_BRIDGE_DRAG_RESET"
                + " timestamp=" + Instant.now()
                + " BridgeOperationId=" + result.operationId()
                + " uiRefreshGeneration=" + refresh
                + " method=DIFFERENTIAL_A_SLOTS_REFRESH_ONLY"
                + " explicitClientDragClearCommand=false"
                + " connectedOutcome=PENDING");
    }

    /**
     * Convert the untrusted client quantity to a server-bounded full-stack request.
     * The bridge re-reads and validates this value again immediately before mutation.
     */
    private int authoritativeQuantityAtIntent(int sectionId, int slot,
            int clientQuantityDiagnostic) {
        ItemContainer container;
        if (sectionId == InventoryComponent.STORAGE_SECTION_ID) {
            container = playerStorage;
        } else if (sectionId == npcWindow.getId()) {
            container = npcInventory;
        } else {
            return clientQuantityDiagnostic;
        }
        if (slot < 0 || slot >= container.getCapacity()) return clientQuantityDiagnostic;
        ItemStack stack = container.getItemStack((short) slot);
        return ItemStack.isEmpty(stack) ? clientQuantityDiagnostic : stack.getQuantity();
    }

    private static String boundNpcGridDocument(int sectionId) {
        if (sectionId < 1 || sectionId > 8) {
            throw new IllegalStateException("Probe 11 requires an allocated window ID"
                    + " between 1 and 8; actual ID was " + sectionId + '.');
        }
        return "Pages/NativeInventoryProbe/NpcSection" + sectionId + ".ui";
    }

    private static int parseSection(String value) {
        if (value == null) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String quoted(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec
                .builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Close", Codec.STRING),
                        (data, value) -> data.close = value, data -> data.close)
                .add()
                .append(new KeyedCodec<>("Marker", Codec.STRING),
                        (data, value) -> data.marker = value, data -> data.marker)
                .add()
                .append(new KeyedCodec<>("Event", Codec.STRING),
                        (data, value) -> data.event = value, data -> data.event)
                .add()
                .append(new KeyedCodec<>("Section", Codec.STRING),
                        (data, value) -> data.section = value, data -> data.section)
                .add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER),
                        (data, value) -> data.slotIndex = value, data -> data.slotIndex)
                .add()
                .append(new KeyedCodec<>("SourceSlotId", Codec.INTEGER),
                        (data, value) -> data.sourceSlotId = value,
                        data -> data.sourceSlotId)
                .add()
                .append(new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER),
                        (data, value) -> data.sourceInventorySectionId = value,
                        data -> data.sourceInventorySectionId)
                .add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING),
                        (data, value) -> data.itemStackId = value,
                        data -> data.itemStackId)
                .add()
                .append(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER),
                        (data, value) -> data.itemStackQuantity = value,
                        data -> data.itemStackQuantity)
                .add()
                .append(new KeyedCodec<>("PressedMouseButton", Codec.INTEGER),
                        (data, value) -> data.pressedMouseButton = value,
                        data -> data.pressedMouseButton)
                .add()
                .build();

        private String close;
        private String marker;
        private String event;
        private String section;
        private Integer slotIndex;
        private Integer sourceSlotId;
        private Integer sourceInventorySectionId;
        private String itemStackId;
        private Integer itemStackQuantity;
        private Integer pressedMouseButton;
    }
}
