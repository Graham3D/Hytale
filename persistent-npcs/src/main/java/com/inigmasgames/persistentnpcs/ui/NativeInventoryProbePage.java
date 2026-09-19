package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.Consumer;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;

/**
 * Isolated native-container experiment. Probe 5 supplies the ItemGrid's initial
 * slot presentation and the client-required native slot identity while keeping
 * movement authoritative through the native inventory section. There is no
 * custom item-transfer handler.
 */
public final class NativeInventoryProbePage
        extends InteractiveCustomUIPage<NativeInventoryProbePage.PageData> {
    private final int variant;
    private final ContainerWindow npcWindow;
    private final SimpleItemContainer npcInventory;
    private final ItemContainer playerStorage;
    private final Consumer<String> diagnostics;
    private boolean dismissed;

    public NativeInventoryProbePage(PlayerRef playerRef, int variant,
            ContainerWindow npcWindow, SimpleItemContainer npcInventory,
            ItemContainer playerStorage, Consumer<String> diagnostics) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.variant = variant;
        this.npcWindow = npcWindow;
        this.npcInventory = npcInventory;
        this.playerStorage = playerStorage;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        int npcSectionId = npcWindow.getId();
        diagnostics.accept("NATIVE_INVENTORY_PROBE_PAGE_BUILD variant=" + variant
                + " npcSectionId=" + npcSectionId
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " initialConstruction=true literalSectionIds=" + (variant >= 3)
                + " initialSlotPresentation=" + (variant >= 4)
                + " nativeSlotIdentity=" + (variant >= 5)
                + " transferHandler=false");
        commands.append("Pages/NativeInventoryProbe.ui");
        if (variant >= 3) {
            // Append a packaged document whose ItemGrid is born with the actual
            // allocated section ID. This avoids both post-creation Set and unsafe
            // runtime parsing of a dynamically assembled AppendInline document.
            String npcGridDocument = boundNpcGridDocument(npcSectionId);
            commands.append("#NpcGridHost", npcGridDocument);
            commands.append("#PlayerGridHost",
                    "Pages/NativeInventoryProbe/PlayerStorage.ui");
            diagnostics.accept("NATIVE_INVENTORY_PROBE_BOUND_GRID_DOCUMENTS_APPENDED"
                    + " variant=" + variant + " npcDocument=" + npcGridDocument
                    + " npcSectionLiteral=" + npcSectionId
                    + " playerDocument=Pages/NativeInventoryProbe/PlayerStorage.ui"
                    + " playerSectionLiteral=" + InventoryComponent.STORAGE_SECTION_ID);
        } else {
            // Controls preserve the failed create-then-Set behavior.
            commands.append("#NpcGridHost",
                    "Pages/NativeInventoryProbe/NpcUnbound.ui");
            commands.append("#PlayerGridHost",
                    "Pages/NativeInventoryProbe/PlayerUnbound.ui");
            commands.set("#NpcInventoryGrid.InventorySectionId", npcSectionId);
            commands.set("#PlayerInventoryGrid.InventorySectionId",
                    InventoryComponent.STORAGE_SECTION_ID);
        }
        if (variant >= 7) {
            setNativeItemStacks(commands, "#NpcInventoryGrid.ItemStacks", npcInventory);
            setNativeItemStacks(commands, "#PlayerInventoryGrid.ItemStacks", playerStorage);
            diagnostics.accept("NATIVE_INVENTORY_PROBE_ITEM_STACKS_SET variant="
                    + variant
                    + " npcSlotCount=" + npcInventory.getCapacity()
                    + " playerSlotCount=" + playerStorage.getCapacity()
                    + " emptyEncoding=BSON_NULL"
                    + " slotIdentity=ARRAY_INDEX"
                    + " transferAuthority=NATIVE_INVENTORY_SECTION"
                    + " updateAuthority=OPEN_AND_UPDATE_WINDOW");
        } else if (variant >= 4) {
            ItemGridSlot[] npcSlots = initialSlots(npcInventory);
            ItemGridSlot[] playerSlots = initialSlots(playerStorage);
            if (variant >= 5) {
                setNativeSlots(commands, "#NpcInventoryGrid.Slots", npcSlots);
                setNativeSlots(commands, "#PlayerInventoryGrid.Slots", playerSlots);
            } else {
                commands.set("#NpcInventoryGrid.Slots", npcSlots);
                commands.set("#PlayerInventoryGrid.Slots", playerSlots);
            }
            diagnostics.accept("NATIVE_INVENTORY_PROBE_SLOT_PRESENTATION_SET variant="
                    + variant
                    + " npcSlotCount=" + npcSlots.length
                    + " playerSlotCount=" + playerSlots.length
                    + " purpose=INITIAL_GRID_MATERIALIZATION"
                    + " inventorySlotIndexEncoded=" + (variant >= 5)
                    + " slotsActivatable=" + (variant >= 5)
                    + " transferAuthority=NATIVE_INVENTORY_SECTION"
                    + " updateAuthority=OPEN_AND_UPDATE_WINDOW");
        }
        commands.set("#ProbeTitle.Text", "NATIVE INVENTORY PROBE " + variant);
        commands.set("#OrderingText.Text", orderingDescription());
        commands.set("#SectionText.Text", "LEFT section " + npcSectionId
                + " (40-slot ContainerWindow)   |   RIGHT section "
                + InventoryComponent.STORAGE_SECTION_ID + " (Player Storage)");
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CloseButton", EventData.of("Close", "true"));
    }

    /** Reproduces R107's post-mount section reassignment for Probe 1 only. */
    public void rebindAfterOpen() {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#NpcInventoryGrid.InventorySectionId", npcWindow.getId());
        commands.set("#PlayerInventoryGrid.InventorySectionId",
                InventoryComponent.STORAGE_SECTION_ID);
        sendUpdate(commands, false);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_POST_MOUNT_REBIND variant=" + variant
                + " npcSectionId=" + npcWindow.getId()
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID);
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
        int windowId = npcWindow.getId();
        diagnostics.accept("NATIVE_INVENTORY_PROBE_SESSION_CLOSING variant=" + variant
                + " npcWindowId=" + windowId);
        recoverProbeItems(ref, store);
        diagnostics.accept("NATIVE_INVENTORY_PROBE_SESSION_CLOSED variant=" + variant
                + " npcWindowId=" + windowId
                + " closePacketSent=false"
                + " awaitingClientWindowClose=true"
                + " containerReleased=true");
        super.onDismiss(ref, store);
    }

    /**
     * The left container is intentionally ephemeral. Normal moves are still native;
     * this teardown-only guard returns remaining stacks to Storage, or drops them at
     * the viewer through Hytale's standard add-or-drop helper if Storage is full.
     */
    private void recoverProbeItems(Ref<EntityStore> ref, Store<EntityStore> store) {
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
        diagnostics.accept("NATIVE_INVENTORY_PROBE_TEARDOWN_RECOVERY variant=" + variant
                + " storageMoveSucceeded=" + moves.succeeded()
                + " storageMoveTransactions=" + moves.size()
                + " addOrDropFallbackStacks=" + fallbackStacks
                + " fallbackSucceeded=" + fallbackSucceeded
                + " npcContainerEmpty=" + npcInventory.isEmpty());
    }

    private String orderingDescription() {
        return switch (variant) {
            case 1 -> "CustomPage packet, then OpenWindow packet, then late rebind (R107 control)";
            case 2 -> "Window registered before build; CustomPage packet, then OpenWindow packet";
            case 3 -> "IDs embedded when ItemGrids are created; CustomPage then OpenWindow";
            case 4 -> "Literal native sections plus initial ItemGrid slot materialization";
            case 5 -> "Literal sections plus indexed, activatable native ItemGrid slots";
            case 6 -> "OpenWindow first, then indexed native ItemGrids on CustomPage mount";
            case 7 -> "Native ItemStacks arrays plus literal sections on first construction";
            default -> "Unknown ordering";
        };
    }

    private static ItemGridSlot[] initialSlots(ItemContainer container) {
        ItemGridSlot[] slots = new ItemGridSlot[container.getCapacity()];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            slots[slot] = ItemStack.isEmpty(stack)
                    ? new ItemGridSlot()
                    : new ItemGridSlot(stack);
            slots[slot].setActivatable(true);
        }
        return slots;
    }

    /**
     * The client-side ItemGridSlot contract includes InventorySlotIndex, but the
     * current server ItemGridSlot codec omits it. Enrich the normal encoded Set
     * command so each rendered cell resolves to its authoritative native slot.
     * Movement is still emitted and validated by Hytale's MoveItemStack path.
     */
    private static void setNativeSlots(UICommandBuilder commands, String selector,
            ItemGridSlot[] slots) {
        int commandCount = commands.getCommands().length;
        commands.set(selector, slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != commandCount + 1) {
            throw new IllegalStateException("Unable to identify native ItemGrid slot command.");
        }
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encodedSlots = data.getArray("0");
        if (encodedSlots.size() != slots.length) {
            throw new IllegalStateException("Native ItemGrid slot encoding changed shape.");
        }
        for (int slot = 0; slot < encodedSlots.size(); slot++) {
            encodedSlots.get(slot).asDocument().put(
                    "InventorySlotIndex", new BsonInt32(slot));
        }
        command.data = data.toJson();
    }

    /**
     * Native inventory ItemGrids model their cells through the ItemStacks array.
     * Preserve every authoritative slot position by encoding empty cells as BSON
     * null instead of the synthetic ItemGridSlot descriptors used by Probes 4-6.
     * The array index is consequently the native slot ID used by MoveItemStack.
     */
    private static void setNativeItemStacks(UICommandBuilder commands, String selector,
            ItemContainer container) {
        ItemStack[] stacks = new ItemStack[container.getCapacity()];
        boolean[] empty = new boolean[stacks.length];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            empty[slot] = ItemStack.isEmpty(stack);
            stacks[slot] = empty[slot] ? ItemStack.EMPTY : stack;
        }

        int commandCount = commands.getCommands().length;
        commands.set(selector, stacks);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != commandCount + 1) {
            throw new IllegalStateException("Unable to identify native ItemGrid ItemStacks command.");
        }
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encodedStacks = data.getArray("0");
        if (encodedStacks.size() != stacks.length) {
            throw new IllegalStateException("Native ItemGrid ItemStacks encoding changed shape.");
        }
        for (int slot = 0; slot < encodedStacks.size(); slot++) {
            if (empty[slot]) encodedStacks.set(slot, BsonNull.VALUE);
        }
        command.data = data.toJson();
    }

    private static String boundNpcGridDocument(int sectionId) {
        // The isolated probe deliberately bounds the precompiled validation bank.
        // WindowManager remains the allocator; this code never assigns an ID.
        if (sectionId < 1 || sectionId > 8) {
            throw new IllegalStateException("Probe 3/4/5/6 requires an allocated window ID"
                    + " between 1 and 8; actual ID was " + sectionId + ".");
        }
        return "Pages/NativeInventoryProbe/NpcSection" + sectionId + ".ui";
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec
                .builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Close", Codec.STRING),
                        (data, value) -> data.close = value, data -> data.close)
                .add()
                .build();
        private String close;
    }
}
