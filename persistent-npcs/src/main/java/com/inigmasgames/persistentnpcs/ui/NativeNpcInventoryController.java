package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Opens the proven native inventory controller over a live NPC ECS Storage. */
public final class NativeNpcInventoryController {
    private NativeNpcInventoryController() { }

    /**
     * Captures the exact live NPC entity and Storage object selected for one page
     * session. Validation is deliberately object-identity based and fails closed.
     */
    public static final class LiveStorageAuthority {
        private final NpcProfile profile;
        private final HytaleNpcAdapter adapter;
        private final NpcInventoryRepository inventories;
        private final Ref<EntityStore> npcRef;
        private final UUID npcEntityId;
        private final ItemContainer storage;
        private final boolean persistenceBindingAdded;

        private LiveStorageAuthority(NpcProfile profile, HytaleNpcAdapter adapter,
                NpcInventoryRepository inventories, Ref<EntityStore> npcRef,
                UUID npcEntityId, ItemContainer storage,
                boolean persistenceBindingAdded) {
            this.profile = profile;
            this.adapter = adapter;
            this.inventories = inventories;
            this.npcRef = npcRef;
            this.npcEntityId = npcEntityId;
            this.storage = storage;
            this.persistenceBindingAdded = persistenceBindingAdded;
        }

        public NpcProfile profile() { return profile; }
        public Ref<EntityStore> npcRef() { return npcRef; }
        public UUID npcEntityId() { return npcEntityId; }
        public ItemContainer storage() { return storage; }
        public boolean persistenceBindingAdded() { return persistenceBindingAdded; }

        /** Returns null only while every captured authority identity still matches. */
        public String invalidReason(Ref<EntityStore> viewerRef, Store<EntityStore> store) {
            if (!npcRef.isValid()) return "NPC_REFERENCE_INVALID";
            UUIDComponent currentUuid = store.getComponent(
                    npcRef, UUIDComponent.getComponentType());
            if (currentUuid == null || !npcEntityId.equals(currentUuid.getUuid())) {
                return "NPC_ENTITY_UUID_MISMATCH";
            }
            InventoryComponent.Storage currentStorage = store.getComponent(
                    npcRef, InventoryComponent.Storage.getComponentType());
            if (currentStorage == null || currentStorage.getInventory() != storage) {
                return "NPC_ECS_STORAGE_IDENTITY_MISMATCH";
            }
            var persisted = inventories.load(profile.name());
            if (!Objects.equals(profile.id(), profile.stableId())
                    || !Objects.equals(profile.id(), persisted.stableNpcId())) {
                return "NPC_STABLE_PROFILE_ID_MISMATCH";
            }
            // The runtime registry is an additional discriminator when present. A
            // missing entry is tolerated because world-restored entities may have
            // been resolved authoritatively by their persistent display identity.
            var registeredProfile = adapter.profileIdForEntity(npcEntityId);
            if (registeredProfile.isPresent()
                    && !profile.id().equals(registeredProfile.get())) {
                return "NPC_RUNTIME_PROFILE_ID_MISMATCH";
            }
            return null;
        }
    }

    public static LiveStorageAuthority resolve(
            NpcProfile profile,
            HytaleNpcAdapter adapter,
            NpcInventoryRepository inventories,
            PlayerRef playerRef,
            Store<EntityStore> store) {
        return resolve(profile, adapter, inventories, playerRef, store, ignored -> { });
    }

    public static LiveStorageAuthority resolve(
            NpcProfile profile,
            HytaleNpcAdapter adapter,
            NpcInventoryRepository inventories,
            PlayerRef playerRef,
            Store<EntityStore> store,
            Consumer<String> diagnostics) {
        if (!Objects.equals(profile.id(), profile.stableId())) {
            throw new IllegalStateException(profile.name()
                    + " has inconsistent profile/stable identity.");
        }
        Ref<EntityStore> npcRef = adapter.requireLiveNpcRef(
                store, profile, playerRef.getTransform().getPosition());
        UUIDComponent uuid = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uuid == null || uuid.getUuid() == null) {
            throw new IllegalStateException(profile.name() + " has no live UUIDComponent.");
        }
        boolean persistenceBindingAdded = inventories.ensureRuntimePersistence(
                profile.name(), store, npcRef, diagnostics);
        InventoryComponent.Storage npcStorage = store.getComponent(
                npcRef, InventoryComponent.Storage.getComponentType());
        if (npcStorage == null || npcStorage.getInventory() == null) {
            throw new IllegalStateException(profile.name()
                    + " has no live InventoryComponent.Storage.");
        }
        LiveStorageAuthority authority = new LiveStorageAuthority(
                profile, adapter, inventories, npcRef, uuid.getUuid(),
                npcStorage.getInventory(), persistenceBindingAdded);
        String invalid = authority.invalidReason(playerRef.getReference(), store);
        if (invalid != null) {
            throw new IllegalStateException("Live NPC storage authority failed: " + invalid);
        }
        return authority;
    }

    public static NativeNpcInventoryProbeWindow open(
            String source,
            NpcProfile profile,
            HytaleNpcAdapter adapter,
            NpcInventoryRepository inventories,
            Player player,
            Ref<EntityStore> viewerRef,
            PlayerRef playerRef,
            Store<EntityStore> store,
            ItemContainer playerStorage,
            Consumer<String> diagnostics) {
        Consumer<String> log = diagnostics == null ? ignored -> { } : diagnostics;
        LiveStorageAuthority authority = resolve(
                profile, adapter, inventories, playerRef, store, log);

        NativeNpcInventoryProbeWindow window = new NativeNpcInventoryProbeWindow(
                profile.name(), authority.npcEntityId(), authority.npcRef(),
                authority.storage(), playerStorage, inventories,
                authority.persistenceBindingAdded(), log);
        log.accept("NATIVE_NPC_INVENTORY_BEGIN"
                + " source=" + source
                + " npc=" + profile.name()
                + " profileId=" + profile.id()
                + " npcEntityId=" + authority.npcEntityId()
                + " viewerUuid=" + playerRef.getUuid()
                + " viewerRef=" + viewerRef
                + " npcRef=" + authority.npcRef()
                + " page=Bench pageApi=PageManager.setPageWithWindows"
                + " targetContainer=LIVE_NPC_ECS_STORAGE"
                + " targetCapacity=" + authority.storage().getCapacity()
                + " persistencePath=" + inventories.path(profile.name())
                + " persistenceBindingAdded=" + authority.persistenceBindingAdded()
                + " customPage=false customItemGrid=false customTransferHandler=false");
        if (!player.getPageManager().setPageWithWindows(
                viewerRef, store, Page.Bench, true, window)) {
            throw new IllegalStateException("Native Page.Bench rejected "
                    + profile.name() + "'s live inventory window.");
        }
        log.accept("NATIVE_NPC_INVENTORY_OPENED"
                + " source=" + source
                + " npc=" + profile.name()
                + " npcEntityId=" + authority.npcEntityId()
                + " npcWindowId=" + window.getId()
                + " wireOrder=SET_PAGE_THEN_OPEN_WINDOW"
                + " recoveryOnClose=false");
        playerRef.sendMessage(Message.raw(profile.name() + "'s native inventory opened."
                + " Changes persist directly to the NPC; close does not return items."));
        return window;
    }
}
