package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

/** Final isolated differential, copied from the successful Probe 5 construction. */
public final class CustomGridDifferentialProbePage
        extends InteractiveCustomUIPage<CustomGridDifferentialProbePage.PageData> {
    public enum Mode {
        BASELINE(false, false, false),
        EVENTS_EMPTY(true, false, false),
        EVENTS_OCCUPIED(true, false, true),
        HIT(true, true, false),
        HIT_OCCUPIED(true, true, true);

        private final boolean events;
        private final boolean hitTestVisible;
        private final boolean seedOccupied;

        Mode(boolean events, boolean hitTestVisible, boolean seedOccupied) {
            this.events = events;
            this.hitTestVisible = hitTestVisible;
            this.seedOccupied = seedOccupied;
        }

        public static Mode parse(String value) {
            String normalized = value == null ? "baseline"
                    : value.strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "", "baseline" -> BASELINE;
                case "events", "events-empty" -> EVENTS_EMPTY;
                case "occupied", "events-occupied" -> EVENTS_OCCUPIED;
                case "hit", "hittest" -> HIT;
                case "hit-occupied", "hittest-occupied" -> HIT_OCCUPIED;
                default -> throw new IllegalArgumentException(
                        "Probe 10 mode must be baseline, events-empty, "
                                + "events-occupied, hit, or hit-occupied.");
            };
        }

        public String commandToken() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private static final String SEED_METADATA_KEY = "ImmersiveNpcProbe10Seed";
    private final PlayerRef viewer;
    private final Mode mode;
    private final ContainerWindow npcWindow;
    private final SimpleItemContainer npcInventory;
    private final ItemContainer playerStorage;
    private final Consumer<String> diagnostics;
    private final String seedId = UUID.randomUUID().toString();
    private EventRegistration<?, ?> npcChanges;
    private EventRegistration<?, ?> playerChanges;
    private boolean dismissed;

    public CustomGridDifferentialProbePage(PlayerRef viewer, Mode mode,
            ContainerWindow npcWindow, SimpleItemContainer npcInventory,
            ItemContainer playerStorage, Consumer<String> diagnostics) {
        super(viewer, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.viewer = viewer;
        this.mode = mode;
        this.npcWindow = npcWindow;
        this.npcInventory = npcInventory;
        this.playerStorage = playerStorage;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        if (mode.seedOccupied) seedOccupiedSlot();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        int npcSectionId = npcWindow.getId();
        ItemContainer resolvedNpc = InventoryUtils.getSectionById(ref, npcSectionId, store);
        ItemContainer resolvedPlayer = InventoryUtils.getSectionById(ref,
                InventoryComponent.STORAGE_SECTION_ID, store);
        boolean npcIdentity = resolvedNpc == npcInventory;
        boolean playerIdentity = resolvedPlayer == playerStorage;
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean windowActive = player != null
                && player.getWindowManager().getWindow(npcSectionId) == npcWindow;
        diagnostics.accept("CUSTOM_GRID_SECTION_RESOLUTION"
                + " timestamp=" + Instant.now()
                + " mode=" + mode.commandToken()
                + " viewerUuid=" + viewer.getUuid()
                + " npcSectionId=" + npcSectionId
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " npcIdentity=" + npcIdentity
                + " playerIdentity=" + playerIdentity
                + " windowRegisteredAndActive=" + windowActive
                + " npcResolvedClass=" + className(resolvedNpc)
                + " playerResolvedClass=" + className(resolvedPlayer));
        if (!npcIdentity || !playerIdentity || !windowActive) {
            throw new IllegalStateException("Probe 10 native section identity assertion failed.");
        }

        commands.append("Pages/NativeInventoryProbe.ui");
        String npcDocument = boundNpcGridDocument(npcSectionId);
        commands.append("#NpcGridHost", npcDocument);
        commands.append("#PlayerGridHost", "Pages/NativeInventoryProbe/PlayerStorage.ui");

        ItemGridSlot[] npcSlots = initialSlots(npcInventory);
        ItemGridSlot[] playerSlots = initialSlots(playerStorage);
        String npcSample = setNativeSlots(commands, "#NpcInventoryGrid.Slots",
                npcSlots, npcInventory);
        String playerSample = setNativeSlots(commands,
                "#PlayerInventoryGrid.Slots", playerSlots, playerStorage);

        // This is the sole UI property changed in the HitTest differential.
        if (mode.hitTestVisible) {
            commands.set("#NpcInventoryGrid.HitTestVisible", true);
        }

        commands.set("#ProbeTitle.Text", "CUSTOM GRID DIFFERENTIAL PROBE 10");
        commands.set("#OrderingText.Text", "Probe 5 baseline; mode="
                + mode.commandToken() + "; observation only");
        commands.set("#SectionText.Text", "LEFT section " + npcSectionId
                + " (40-slot ContainerWindow)   |   RIGHT section "
                + InventoryComponent.STORAGE_SECTION_ID + " (Player Storage)");
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CloseButton", EventData.of("Close", "true"));
        if (mode.events) addObservationBindings(events);

        diagnostics.accept("CUSTOM_GRID_PROBE10_BUILD"
                + " timestamp=" + Instant.now()
                + " mode=" + mode.commandToken()
                + " baseline=PROBE_5"
                + " npcDocument=" + npcDocument
                + " npcSectionId=" + npcSectionId
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " npcCapacity=" + npcSlots.length
                + " playerCapacity=" + playerSlots.length
                + " fixedCapacitySlots=true"
                + " inventorySlotIndex=true"
                + " isActivatable=true"
                + " isItemIncompatible=false"
                + " areItemsDraggable=true"
                + " itemStacksProperty=false"
                + " customTransferHandler=false"
                + " uiTelemetry=" + mode.events
                + " telemetryLocksInterface=false"
                + " npcHitTestVisibleOverride=" + mode.hitTestVisible
                + " occupiedSeed=" + mode.seedOccupied
                + " npcEncodedSlotSample=" + quoted(npcSample)
                + " playerEncodedSlotSample=" + quoted(playerSample));
        CustomGridDifferentialTelemetry.activate(viewer, mode.commandToken(),
                npcSectionId, diagnostics);
        npcChanges = npcInventory.registerChangeEvent(event ->
                recordChange("NPC_WINDOW_" + npcWindow.getId(), npcWindow.getId(),
                        event.transaction()));
        playerChanges = playerStorage.registerChangeEvent(event ->
                recordChange("PLAYER_STORAGE", InventoryComponent.STORAGE_SECTION_ID,
                        event.transaction()));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
            String rawData) {
        CustomGridDifferentialTelemetry.observeUiEvent(viewer.getUuid(),
                mode.commandToken(), rawData);
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
            PageData data) {
        if (data != null && "true".equalsIgnoreCase(data.close)) close();
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed) return;
        dismissed = true;
        CustomGridDifferentialTelemetry.deactivate(viewer.getUuid(), "PAGE_DISMISSED");
        unregister(npcChanges);
        unregister(playerChanges);
        int removedSeeds = removeProbeSeeds(npcInventory) + removeProbeSeeds(playerStorage);
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
        diagnostics.accept("CUSTOM_GRID_PROBE10_TEARDOWN"
                + " timestamp=" + Instant.now()
                + " mode=" + mode.commandToken()
                + " npcWindowId=" + npcWindow.getId()
                + " taggedProbeSeedsRemoved=" + removedSeeds
                + " storageMoveSucceeded=" + moves.succeeded()
                + " storageMoveTransactions=" + moves.size()
                + " addOrDropFallbackStacks=" + fallbackStacks
                + " fallbackSucceeded=" + fallbackSucceeded
                + " npcContainerEmpty=" + npcInventory.isEmpty());
        super.onDismiss(ref, store);
    }

    private void seedOccupiedSlot() {
        BsonDocument metadata = new BsonDocument(SEED_METADATA_KEY,
                new BsonString(seedId));
        ItemStack seed = new ItemStack("Ingredient_Stick", 1, metadata);
        var transaction = npcInventory.setItemStackForSlot((short) 0, seed);
        if (!transaction.succeeded()) {
            throw new IllegalStateException("Could not seed Probe 10 occupied destination.");
        }
        diagnostics.accept("CUSTOM_GRID_PROBE10_SEEDED"
                + " timestamp=" + Instant.now()
                + " itemId=Ingredient_Stick quantity=1 npcSlot=0"
                + " uniqueMetadataKey=" + SEED_METADATA_KEY
                + " cleanup=REMOVE_TAGGED_SEED_BEFORE_RECOVERY");
    }

    private int removeProbeSeeds(ItemContainer container) {
        int removed = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack) || stack.getMetadata() == null) continue;
            var marker = stack.getMetadata().get(SEED_METADATA_KEY);
            if (!(marker instanceof BsonString value) || !seedId.equals(value.getValue())) {
                continue;
            }
            if (container.removeItemStackFromSlot(slot).succeeded()) removed++;
        }
        return removed;
    }

    private void addObservationBindings(UIEventBuilder events) {
        bindGrid(events, "#PlayerInventoryGrid", "PLAYER",
                Integer.toString(InventoryComponent.STORAGE_SECTION_ID));
        bindGrid(events, "#NpcInventoryGrid", "NPC",
                Integer.toString(npcWindow.getId()));
    }

    private void recordChange(String role, int sectionId, Transaction transaction) {
        StringBuilder value = new StringBuilder("CUSTOM_GRID_AUTHORITATIVE_TRANSACTION")
                .append(" timestamp=").append(Instant.now())
                .append(" mode=").append(mode.commandToken())
                .append(" role=").append(role)
                .append(" sectionId=").append(sectionId)
                .append(" transactionClass=")
                .append(transaction.getClass().getSimpleName())
                .append(" committed=").append(transaction.succeeded())
                .append(" observationOnly=true");
        if (transaction instanceof MoveTransaction<?> move) {
            SlotTransaction remove = move.getRemoveTransaction();
            value.append(" moveType=").append(move.getMoveType())
                    .append(" sourceSlot=").append(remove == null ? -1 : remove.getSlot())
                    .append(" sourceBefore=").append(stack(remove == null
                            ? null : remove.getSlotBefore()))
                    .append(" sourceAfter=").append(stack(remove == null
                            ? null : remove.getSlotAfter()))
                    .append(" otherContainerIdentity=")
                    .append(containerIdentity(move.getOtherContainer()));
            if (move.getAddTransaction() instanceof SlotTransaction target) {
                value.append(" destinationSlot=").append(target.getSlot())
                        .append(" destinationBefore=").append(stack(target.getSlotBefore()))
                        .append(" destinationAfter=").append(stack(target.getSlotAfter()));
            }
        } else if (transaction instanceof SlotTransaction slot) {
            value.append(" slot=").append(slot.getSlot())
                    .append(" action=").append(slot.getAction())
                    .append(" before=").append(stack(slot.getSlotBefore()))
                    .append(" after=").append(stack(slot.getSlotAfter()));
        }
        value.append(" authoritativeState=").append(snapshot(
                sectionId == npcWindow.getId() ? npcInventory : playerStorage));
        diagnostics.accept(value.toString());
    }

    private String containerIdentity(ItemContainer container) {
        if (container == npcInventory) return "NPC_WINDOW_" + npcWindow.getId();
        if (container == playerStorage) return "PLAYER_STORAGE";
        return container == null ? "null" : container.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(container));
    }

    private static String snapshot(ItemContainer container) {
        StringBuilder value = new StringBuilder("[");
        boolean first = true;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (ItemStack.isEmpty(item)) continue;
            if (!first) value.append(',');
            first = false;
            value.append(slot).append('=').append(stack(item));
        }
        return value.append(']').toString();
    }

    private static String stack(ItemStack value) {
        return ItemStack.isEmpty(value) ? "EMPTY"
                : value.getItemId() + "x" + value.getQuantity();
    }

    private static void unregister(EventRegistration<?, ?> registration) {
        if (registration != null && registration.isRegistered()) registration.unregister();
    }

    private static void bindGrid(UIEventBuilder events, String selector,
            String grid, String section) {
        bind(events, CustomUIEventBindingType.SlotMouseEntered, selector,
                grid, section, "TARGET");
        bind(events, CustomUIEventBindingType.SlotMouseExited, selector,
                grid, section, "TARGET");
        bind(events, CustomUIEventBindingType.SlotClicking, selector,
                grid, section, "SOURCE");
        bind(events, CustomUIEventBindingType.SlotClickReleaseWhileDragging,
                selector, grid, section, "RELEASE");
        bind(events, CustomUIEventBindingType.SlotMouseDragCompleted,
                selector, grid, section, "RELEASE");
        bind(events, CustomUIEventBindingType.Dropped, selector,
                grid, section, "RELEASE");
        bind(events, CustomUIEventBindingType.DragCancelled, selector,
                grid, section, "CANCEL");
    }

    private static void bind(UIEventBuilder events,
            CustomUIEventBindingType type, String selector, String grid,
            String section, String phase) {
        EventData data = EventData.of("Marker", "CUSTOM_GRID_DRAG_EVENT")
                .append("Event", type.name())
                .append("Grid", grid)
                .append("Selector", selector)
                .append("Section", section)
                .append("Phase", phase);
        events.addEventBinding(type, selector, data, false);
    }

    private static ItemGridSlot[] initialSlots(ItemContainer container) {
        ItemGridSlot[] slots = new ItemGridSlot[container.getCapacity()];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            slots[slot] = ItemStack.isEmpty(stack)
                    ? new ItemGridSlot() : new ItemGridSlot(stack);
            slots[slot].setActivatable(true);
        }
        return slots;
    }

    private static String setNativeSlots(UICommandBuilder commands, String selector,
            ItemGridSlot[] slots, ItemContainer container) {
        int commandCount = commands.getCommands().length;
        commands.set(selector, slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != commandCount + 1) {
            throw new IllegalStateException("Unable to identify Probe 10 slot command.");
        }
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encodedSlots = data.getArray("0");
        if (encodedSlots.size() != slots.length) {
            throw new IllegalStateException("Probe 10 slot encoding changed shape.");
        }
        String sample = null;
        for (int slot = 0; slot < encodedSlots.size(); slot++) {
            BsonDocument encoded = encodedSlots.get(slot).asDocument();
            encoded.put("InventorySlotIndex", new BsonInt32(slot));
            encoded.put("IsActivatable", BsonBoolean.TRUE);
            encoded.put("IsItemIncompatible", BsonBoolean.FALSE);
            if (encoded.getInt32("InventorySlotIndex").getValue() != slot
                    || !encoded.getBoolean("IsActivatable").getValue()
                    || encoded.getBoolean("IsItemIncompatible").getValue()) {
                throw new IllegalStateException(
                        "Probe 10 native slot identity verification failed at " + slot);
            }
            if (sample == null && ItemStack.isEmpty(
                    container.getItemStack((short) slot))) {
                sample = "physicalSlot=" + slot + " encoded=" + encoded.toJson();
            }
        }
        command.data = data.toJson();
        return sample == null ? "NONE" : sample;
    }

    private static String boundNpcGridDocument(int sectionId) {
        if (sectionId < 1 || sectionId > 8) {
            throw new IllegalStateException("Probe 10 requires an allocated window ID"
                    + " between 1 and 8; actual ID was " + sectionId + ".");
        }
        return "Pages/NativeInventoryProbe/NpcSection" + sectionId + ".ui";
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String quoted(String value) {
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
                .append(new KeyedCodec<>("Grid", Codec.STRING),
                        (data, value) -> data.grid = value, data -> data.grid)
                .add()
                .append(new KeyedCodec<>("Selector", Codec.STRING),
                        (data, value) -> data.selector = value, data -> data.selector)
                .add()
                .append(new KeyedCodec<>("Section", Codec.STRING),
                        (data, value) -> data.section = value, data -> data.section)
                .add()
                .append(new KeyedCodec<>("Phase", Codec.STRING),
                        (data, value) -> data.phase = value, data -> data.phase)
                .add()
                .build();
        private String close;
        private String marker;
        private String event;
        private String grid;
        private String selector;
        private String section;
        private String phase;
    }
}
