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
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Isolated native window over one spawned NPC's exact ECS Storage container.
 * Unlike Probe 8's ephemeral control, close never recovers or mutates contents:
 * the live NPC container and NpcInventoryRepository remain authoritative.
 */
public final class NativeNpcInventoryProbeWindow extends ContainerWindow {
    private final String npcName;
    private final UUID npcEntityId;
    private final Ref<EntityStore> npcRef;
    private final ItemContainer liveNpcStorage;
    private final ItemContainer playerStorage;
    private final NpcInventoryRepository repository;
    private final boolean persistenceBindingAdded;
    private final Consumer<String> diagnostics;
    private EventRegistration<?, ?> npcChanges;
    private EventRegistration<?, ?> playerChanges;
    private boolean closing;

    public NativeNpcInventoryProbeWindow(
            String npcName,
            UUID npcEntityId,
            Ref<EntityStore> npcRef,
            ItemContainer liveNpcStorage,
            ItemContainer playerStorage,
            NpcInventoryRepository repository,
            boolean persistenceBindingAdded,
            Consumer<String> diagnostics) {
        super(liveNpcStorage);
        this.npcName = npcName;
        this.npcEntityId = npcEntityId;
        this.npcRef = npcRef;
        this.liveNpcStorage = liveNpcStorage;
        this.playerStorage = playerStorage;
        this.repository = repository;
        this.persistenceBindingAdded = persistenceBindingAdded;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    @Override
    public boolean onOpen0(Ref<EntityStore> viewerRef, Store<EntityStore> store) {
        if (!super.onOpen0(viewerRef, store)) return false;
        InventoryComponent.Storage npcStorageComponent = store.getComponent(
                npcRef, InventoryComponent.Storage.getComponentType());
        ItemContainer resolvedPlayer = InventoryUtils.getSectionById(
                viewerRef, InventoryComponent.STORAGE_SECTION_ID, store);
        ItemContainer resolvedNpc = InventoryUtils.getSectionById(viewerRef, getId(), store);
        Player viewer = store.getComponent(viewerRef, Player.getComponentType());
        boolean active = viewer != null && viewer.getWindowManager().getWindow(getId()) == this;
        boolean ecsIdentity = npcStorageComponent != null
                && npcStorageComponent.getInventory() == liveNpcStorage;
        repository.flushPendingWrites();
        boolean persistedMatches = persistedInventory().equals(liveInventory());

        diagnostics.accept("NATIVE_NPC_INVENTORY_RESOLUTION"
                + " npc=" + npcName
                + " npcEntityId=" + npcEntityId
                + " viewerRef=" + viewerRef
                + " npcRef=" + npcRef
                + " page=Bench customPage=false"
                + " playerStorageSectionId=" + InventoryComponent.STORAGE_SECTION_ID
                + " playerStorageResolvedIdentity=" + (resolvedPlayer == playerStorage)
                + " npcWindowId=" + getId()
                + " npcWindowResolvedIdentity=" + (resolvedNpc == liveNpcStorage)
                + " npcEcsStorageIdentity=" + ecsIdentity
                + " windowRegisteredAndActive=" + active
                + " persistenceBindingAdded=" + persistenceBindingAdded
                + " persistedMatchesLiveBeforeOpen=" + persistedMatches
                + " npcCapacity=" + liveNpcStorage.getCapacity()
                + " playerCapacity=" + playerStorage.getCapacity()
                + " customTransferHandler=false");
        if (resolvedPlayer != playerStorage || resolvedNpc != liveNpcStorage
                || !ecsIdentity || !active || !persistedMatches) {
            throw new IllegalStateException("Live NPC native inventory resolution failed for "
                    + npcName + ". playerIdentity=" + (resolvedPlayer == playerStorage)
                    + " windowIdentity=" + (resolvedNpc == liveNpcStorage)
                    + " ecsIdentity=" + ecsIdentity + " active=" + active
                    + " persistedMatches=" + persistedMatches);
        }

        diagnostics.accept("NATIVE_NPC_INVENTORY_BASELINE npc=" + npcName
                + " npcEntityId=" + npcEntityId
                + " npcWindowId=" + getId()
                + " liveNpcStorage=" + snapshot(liveNpcStorage)
                + " persistedNpcStorage=" + persistedInventory());
        npcChanges = liveNpcStorage.registerChangeEvent(event -> recordChange(
                "NPC_LIVE_STORAGE", getId(), event.transaction()));
        playerChanges = playerStorage.registerChangeEvent(event -> recordChange(
                "PLAYER_STORAGE", InventoryComponent.STORAGE_SECTION_ID,
                event.transaction()));
        return true;
    }

    @Override
    public void onClose0(Ref<EntityStore> viewerRef,
            ComponentAccessor<EntityStore> accessor) {
        if (closing) return;
        closing = true;
        unregister(npcChanges);
        unregister(playerChanges);
        diagnostics.accept("NATIVE_NPC_INVENTORY_CLOSING npc=" + npcName
                + " npcEntityId=" + npcEntityId
                + " npcWindowId=" + getId()
                + " liveBeforeClose=" + snapshot(liveNpcStorage)
                + " recoveryPerformed=false");
        super.onClose0(viewerRef, accessor);
        repository.flushPendingWrites();
        List<NpcInventoryState.PersistedItemStack> live = liveInventory();
        List<NpcInventoryState.PersistedItemStack> persisted = persistedInventory();
        diagnostics.accept("NATIVE_NPC_INVENTORY_CLOSED npc=" + npcName
                + " npcEntityId=" + npcEntityId
                + " npcWindowId=" + getId()
                + " recoveryPerformed=false"
                + " persistedMatchesLive=" + persisted.equals(live)
                + " liveNpcStorage=" + live
                + " persistedNpcStorage=" + persisted);
    }

    private void recordChange(String role, int sectionId, Transaction transaction) {
        StringBuilder details = new StringBuilder()
                .append("NATIVE_NPC_INVENTORY_TRANSACTION")
                .append(" npc=").append(npcName)
                .append(" npcEntityId=").append(npcEntityId)
                .append(" role=").append(role)
                .append(" sectionId=").append(sectionId)
                .append(" transactionClass=").append(transaction.getClass().getSimpleName())
                .append(" committed=").append(transaction.succeeded())
                .append(" customTransferHandler=false");
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
                .append(sectionId == getId()
                        ? snapshot(liveNpcStorage) : snapshot(playerStorage));
        diagnostics.accept(details.toString());
    }

    private String containerRole(ItemContainer container) {
        if (container == liveNpcStorage) return "NPC_LIVE_STORAGE";
        if (container == playerStorage) return "PLAYER_STORAGE";
        return container == null ? "null"
                : container.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(container));
    }

    private List<NpcInventoryState.PersistedItemStack> liveInventory() {
        ArrayList<NpcInventoryState.PersistedItemStack> values = new ArrayList<>();
        for (short slot = 0; slot < liveNpcStorage.getCapacity(); slot++) {
            ItemStack stack = liveNpcStorage.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                values.add(NpcInventoryState.PersistedItemStack.from(slot, stack));
            }
        }
        return List.copyOf(values);
    }

    private List<NpcInventoryState.PersistedItemStack> persistedInventory() {
        return repository.load(npcName).inventory();
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
