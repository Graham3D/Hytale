package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.Consumer;

/**
 * Probe 8's native control window. It deliberately contains no Custom UI page or
 * ItemGrid. Page.Bench owns the player-side inventory controller exactly as the
 * installed InventorySeeCommand and chest interactions do.
 */
public final class NativeInventoryControlWindow extends ContainerWindow {
    private final SimpleItemContainer npcInventory;
    private final ItemContainer playerStorage;
    private final Consumer<String> diagnostics;
    private EventRegistration<?, ?> npcChanges;
    private EventRegistration<?, ?> storageChanges;
    private boolean closing;

    public NativeInventoryControlWindow(SimpleItemContainer npcInventory,
            ItemContainer playerStorage, Consumer<String> diagnostics) {
        super(npcInventory);
        this.npcInventory = npcInventory;
        this.playerStorage = playerStorage;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    @Override
    public boolean onOpen0(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!super.onOpen0(ref, store)) return false;

        ItemContainer resolvedStorage = InventoryUtils.getSectionById(
                ref, InventoryComponent.STORAGE_SECTION_ID, store);
        ItemContainer resolvedNpc = InventoryUtils.getSectionById(ref, getId(), store);
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean windowActive = player != null
                && player.getWindowManager().getWindow(getId()) == this;
        boolean storageIdentity = resolvedStorage == playerStorage;
        boolean npcIdentity = resolvedNpc == npcInventory;

        diagnostics.accept("NATIVE_INVENTORY_CONTROL_RESOLUTION variant=8"
                + " viewerEntityRef=" + ref
                + " page=Bench customPage=false"
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " playerStorageResolvedIdentity=" + storageIdentity
                + " npcWindowId=" + getId()
                + " npcWindowResolvedIdentity=" + npcIdentity
                + " windowRegisteredAndActive=" + windowActive
                + " playerStorageCapacity=" + playerStorage.getCapacity()
                + " npcCapacity=" + npcInventory.getCapacity()
                + " packetObservation=TRANSACTION_EVENTS_ONLY"
                + " customTransferHandler=false");
        if (!storageIdentity || !npcIdentity || !windowActive) {
            throw new IllegalStateException("Native control section resolution failed."
                    + " storageIdentity=" + storageIdentity
                    + " npcIdentity=" + npcIdentity
                    + " windowActive=" + windowActive);
        }

        diagnostics.accept("NATIVE_INVENTORY_CONTROL_BASELINE variant=8"
                + " playerStorage=" + snapshot(playerStorage)
                + " npcContainer=" + snapshot(npcInventory));
        npcChanges = npcInventory.registerChangeEvent(event ->
                recordChange("NPC_WINDOW_" + getId(), getId(), event.transaction()));
        storageChanges = playerStorage.registerChangeEvent(event ->
                recordChange("PLAYER_STORAGE", InventoryComponent.STORAGE_SECTION_ID,
                        event.transaction()));
        return true;
    }

    @Override
    public void onClose0(Ref<EntityStore> ref,
            ComponentAccessor<EntityStore> accessor) {
        if (closing) return;
        closing = true;
        unregister(npcChanges);
        unregister(storageChanges);
        diagnostics.accept("NATIVE_INVENTORY_CONTROL_CLOSING variant=8"
                + " npcWindowId=" + getId()
                + " playerStorageBeforeRecovery=" + snapshot(playerStorage)
                + " npcBeforeRecovery=" + snapshot(npcInventory));

        var moves = npcInventory.moveAllItemStacksTo(playerStorage);
        int fallbackStacks = 0;
        boolean fallbackSucceeded = true;
        boolean storeAvailable = accessor instanceof Store<?>;
        if (storeAvailable) {
            @SuppressWarnings("unchecked")
            Store<EntityStore> store = (Store<EntityStore>) accessor;
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
        } else if (!npcInventory.isEmpty()) {
            fallbackSucceeded = false;
        }
        diagnostics.accept("NATIVE_INVENTORY_CONTROL_CLOSED variant=8"
                + " npcWindowId=" + getId()
                + " recoveryTransactions=" + moves.size()
                + " recoverySucceeded=" + moves.succeeded()
                + " addOrDropFallbackStacks=" + fallbackStacks
                + " storeAvailable=" + storeAvailable
                + " fallbackSucceeded=" + fallbackSucceeded
                + " npcContainerEmpty=" + npcInventory.isEmpty()
                + " playerStorageAfterRecovery=" + snapshot(playerStorage));
        super.onClose0(ref, accessor);
    }

    private void recordChange(String role, int sectionId, Transaction transaction) {
        StringBuilder details = new StringBuilder()
                .append("NATIVE_INVENTORY_CONTROL_TRANSACTION variant=8")
                .append(" role=").append(role)
                .append(" sectionId=").append(sectionId)
                .append(" transactionClass=").append(transaction.getClass().getSimpleName())
                .append(" committed=").append(transaction.succeeded())
                .append(" packetCapture=false");
        if (transaction instanceof MoveTransaction<?> move) {
            SlotTransaction remove = move.getRemoveTransaction();
            details.append(" moveType=").append(move.getMoveType())
                    .append(" sourceSlot=").append(remove == null ? -1 : remove.getSlot())
                    .append(" sourceBefore=").append(stack(remove == null
                            ? null : remove.getSlotBefore()))
                    .append(" sourceAfter=").append(stack(remove == null
                            ? null : remove.getSlotAfter()))
                    .append(" otherContainerIdentity=")
                    .append(containerRole(move.getOtherContainer()));
            Transaction add = move.getAddTransaction();
            if (add instanceof SlotTransaction target) {
                details.append(" destinationSlot=").append(target.getSlot())
                        .append(" destinationBefore=").append(stack(target.getSlotBefore()))
                        .append(" destinationAfter=").append(stack(target.getSlotAfter()));
            }
        } else if (transaction instanceof SlotTransaction slot) {
            details.append(" slot=").append(slot.getSlot())
                    .append(" action=").append(slot.getAction())
                    .append(" before=").append(stack(slot.getSlotBefore()))
                    .append(" after=").append(stack(slot.getSlotAfter()));
        }
        details.append(" authoritativeState=")
                .append(sectionId == getId() ? snapshot(npcInventory) : snapshot(playerStorage));
        diagnostics.accept(details.toString());
    }

    private String containerRole(ItemContainer container) {
        if (container == npcInventory) return "NPC_WINDOW_" + getId();
        if (container == playerStorage) return "PLAYER_STORAGE";
        return container == null ? "null"
                : container.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(container));
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
}
