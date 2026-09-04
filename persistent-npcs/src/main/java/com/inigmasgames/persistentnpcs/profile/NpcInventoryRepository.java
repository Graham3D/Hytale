package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainerUtil;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Profile-local persistence and native container composition for NPC authoring. */
public final class NpcInventoryRepository implements AutoCloseable {
    public static final String FILENAME = "npc-inventory.json";
    private final ProfileRepository profiles;
    private final Set<ItemContainer> runtimePersistenceBindings = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "immersive-npc-inventory-writer");
        thread.setDaemon(true);
        return thread;
    });

    public NpcInventoryRepository(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public Path path(String npcName) {
        return profiles.profileDirectory(npcName).resolve(FILENAME);
    }

    public Optional<NpcInventoryState> find(String npcName) {
        Path path = path(npcName);
        return Files.isRegularFile(path)
                ? Optional.of(JsonFiles.read(path, NpcInventoryState.class)) : Optional.empty();
    }

    public NpcInventoryState load(String npcName) {
        return find(npcName).orElseGet(NpcInventoryState::empty);
    }

    public Session open(String npcName) {
        profiles.createProfileDirectory(npcName);
        return new Session(npcName, load(npcName), null, null, null, null);
    }

    /**
     * Opens the authoring session over the exact live NPC Storage object. Armor and
     * loadout remain the existing bounded authoring containers; the lower storage
     * grid and every persisted snapshot use the supplied live authority directly.
     */
    public Session openWithLiveStorage(String npcName, ItemContainer liveStorage) {
        profiles.createProfileDirectory(npcName);
        return new Session(npcName, load(npcName), null, null, null,
                java.util.Objects.requireNonNull(liveStorage, "liveStorage"));
    }

    /** Opens A3 over the exact live NPC Armor, Hotbar, Utility, and Storage authorities. */
    public Session openWithLiveInventory(String npcName, ItemContainer liveArmor,
            ItemContainer liveHotbar, ItemContainer liveUtility, ItemContainer liveStorage) {
        profiles.createProfileDirectory(npcName);
        return new Session(npcName, load(npcName),
                java.util.Objects.requireNonNull(liveArmor, "liveArmor"),
                java.util.Objects.requireNonNull(liveHotbar, "liveHotbar"),
                java.util.Objects.requireNonNull(liveUtility, "liveUtility"),
                java.util.Objects.requireNonNull(liveStorage, "liveStorage"));
    }

    public void save(String npcName, NpcInventoryState state) {
        JsonFiles.writeAtomic(path(npcName), state);
    }

    /**
     * Ensures deserialized/live NPC ECS containers are observed without replacing them.
     * A repository instance loses runtime listeners across server restarts, so the
     * native persistence probe re-establishes the listener on the exact live objects.
     */
    public boolean ensureRuntimePersistence(
            String npcName, Store<EntityStore> store, Ref<EntityStore> npcRef) {
        return ensureRuntimePersistence(npcName, store, npcRef, ignored -> { });
    }

    public boolean ensureRuntimePersistence(
            String npcName, Store<EntityStore> store, Ref<EntityStore> npcRef,
            Consumer<String> diagnostics) {
        Consumer<String> log = diagnostics == null ? ignored -> { } : diagnostics;
        NpcInventoryState authored = find(npcName).orElseThrow(() ->
                new IllegalStateException("No persisted NPC inventory exists for " + npcName));
        InventoryComponent.Armor armor = store.getComponent(
                npcRef, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(
                npcRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Utility utility = store.getComponent(
                npcRef, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(
                npcRef, InventoryComponent.Storage.getComponentType());
        if (armor == null || hotbar == null || utility == null || storage == null
                || armor.getInventory() == null || hotbar.getInventory() == null
                || utility.getInventory() == null || storage.getInventory() == null) {
            throw new IllegalStateException("Live NPC inventory components are incomplete for "
                    + npcName);
        }
        ItemContainer liveArmor = armor.getInventory();
        ItemContainer liveHotbar = hotbar.getInventory();
        ItemContainer liveUtility = utility.getInventory();
        ItemContainer liveStorage = storage.getInventory();

        // World-restored NPC entities do not pass through spawnNpc(), so their ECS
        // containers can exist but be empty after a server restart. The profile-local
        // inventory is authoritative. Hydrate those exact live container objects before
        // attaching listeners or exposing a ContainerWindow; replacing the component here
        // would invalidate the window/section identity proven by the native control.
        if (!runtimeMatches(authored, liveArmor, liveHotbar, liveUtility, liveStorage)) {
            log.accept("NPC_INVENTORY_HYDRATION_NORMALIZATION"
                    + " npc=" + npcName
                    + " persistedEmptyMetadataCanonicalized=true"
                    + " liveStorageInitiallyEmpty=" + liveStorage.isEmpty());
            if (!liveArmor.isEmpty() || !liveHotbar.isEmpty()
                    || !liveUtility.isEmpty() || !liveStorage.isEmpty()) {
                log.accept("NPC_INVENTORY_HYDRATION_REFUSED npc=" + npcName
                        + " reason=NON_EMPTY_DIVERGENT_RUNTIME");
                throw new IllegalStateException("Live NPC inventory differs from persisted state for "
                        + npcName + "; refusing to overwrite non-empty runtime containers.");
            }
            hydrateRollbackSafe(npcName,
                    List.of(liveArmor, liveHotbar, liveUtility, liveStorage),
                    () -> {
                        ItemContainerUtil.trySetArmorFilters(liveArmor);
                        restore(liveArmor, authored.armor(), "armor");
                        restore(liveStorage, authored.inventory(), "inventory");
                        authored.loadout().stream()
                                .filter(value -> value.slot() == Session.PRIMARY_SLOT)
                                .findFirst().ifPresent(value -> restoreOne(liveHotbar, (short) 0,
                                        value.toItemStack(), "primary weapon"));
                        authored.loadout().stream()
                                .filter(value -> value.slot() == Session.AMMUNITION_SLOT)
                                .findFirst().ifPresent(value -> restoreOne(liveHotbar, (short) 1,
                                        value.toItemStack(), "preferred ammunition"));
                        authored.loadout().stream()
                                .filter(value -> value.slot() == Session.OFFHAND_SLOT)
                                .findFirst().ifPresent(value -> restoreOne(liveUtility, (short) 0,
                                        value.toItemStack(), "offhand"));
                        armor.setOutdatedEquipment(true);
                        hotbar.setOutdatedEquipment(true);
                        utility.setOutdatedEquipment(true);
                    },
                    () -> runtimeMatches(authored, liveArmor, liveHotbar,
                            liveUtility, liveStorage),
                    log);
        }
        log.accept("NPC_INVENTORY_HYDRATION_VALIDATION npc=" + npcName
                + " authoritativeMatch=true"
                + " liveStorageCapacity=" + liveStorage.getCapacity());
        return installRuntimePersistence(npcName, authored, liveArmor,
                liveHotbar, liveUtility, liveStorage);
    }

    private static boolean runtimeMatches(
            NpcInventoryState authored,
            ItemContainer armor,
            ItemContainer hotbar,
            ItemContainer utility,
            ItemContainer storage) {
        List<NpcInventoryState.PersistedItemStack> loadout = new ArrayList<>();
        addRuntimeSlot(loadout, hotbar, (short) 0, Session.PRIMARY_SLOT);
        addRuntimeSlot(loadout, utility, (short) 0, Session.OFFHAND_SLOT);
        addRuntimeSlot(loadout, hotbar, (short) 1, Session.AMMUNITION_SLOT);
        return canonicalItems(authored.armor()).equals(canonicalItems(snapshotContainer(armor)))
                && canonicalItems(authored.loadout()).equals(canonicalItems(List.copyOf(loadout)))
                && canonicalItems(authored.inventory()).equals(canonicalItems(snapshotContainer(storage)));
    }

    private static List<NpcInventoryState.PersistedItemStack> canonicalItems(
            List<NpcInventoryState.PersistedItemStack> values) {
        return values.stream().map(value -> new NpcInventoryState.PersistedItemStack(
                value.slot(), value.itemId(), value.quantity(), value.durability(),
                value.maxDurability(), value.qualityIndex(), value.metadataJson(),
                value.overrideDroppedItemAnimation())).toList();
    }

    private record ContainerSnapshot(ItemContainer container, ItemStack[] slots) { }

    private static void hydrateRollbackSafe(
            String npcName,
            List<ItemContainer> containers,
            Runnable hydration,
            BooleanSupplier validation,
            Consumer<String> diagnostics) {
        List<ContainerSnapshot> before = containers.stream()
                .map(NpcInventoryRepository::captureExact)
                .toList();
        diagnostics.accept("NPC_INVENTORY_HYDRATION_BEGIN npc=" + npcName
                + " rollbackSnapshotCaptured=true");
        try {
            hydration.run();
            boolean valid = validation.getAsBoolean();
            diagnostics.accept("NPC_INVENTORY_HYDRATION_VALIDATION npc=" + npcName
                    + " authoritativeMatch=" + valid);
            if (!valid) {
                throw new IllegalStateException("Persisted NPC inventory hydration did not"
                        + " reproduce the authoritative state for " + npcName);
            }
        } catch (RuntimeException failure) {
            RuntimeException rollbackFailure = null;
            try {
                for (ContainerSnapshot snapshot : before) restoreExact(snapshot);
            } catch (RuntimeException rollback) {
                rollbackFailure = rollback;
                failure.addSuppressed(rollback);
            }
            diagnostics.accept("NPC_INVENTORY_HYDRATION_ROLLBACK npc=" + npcName
                    + " completed=" + (rollbackFailure == null)
                    + " reason=" + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private static ContainerSnapshot captureExact(ItemContainer container) {
        ItemStack[] slots = new ItemStack[container.getCapacity()];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            slots[slot] = container.getItemStack(slot);
        }
        return new ContainerSnapshot(container, slots);
    }

    private static void restoreExact(ContainerSnapshot snapshot) {
        if (!snapshot.container().clear().succeeded()) {
            throw new IllegalStateException("Could not clear NPC container during hydration rollback.");
        }
        for (short slot = 0; slot < snapshot.slots().length; slot++) {
            ItemStack stack = snapshot.slots()[slot];
            if (!ItemStack.isEmpty(stack)
                    && !snapshot.container().setItemStackForSlot(slot, stack).succeeded()) {
                throw new IllegalStateException(
                        "Could not restore NPC container slot " + slot + " during hydration rollback.");
            }
        }
    }

    /** Waits for all persistence work submitted before this call. */
    public void flushPendingWrites() {
        try {
            writer.submit(() -> { }).get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while flushing NPC inventory", failure);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Could not flush NPC inventory", failure.getCause());
        }
    }

    /** Applies authoring state to the freshly spawned authoritative NPC ECS inventory. */
    public boolean applyToSpawnedNpc(
            String npcName, Store<EntityStore> store, Ref<EntityStore> npcRef) {
        Optional<NpcInventoryState> existing = find(npcName);
        if (existing.isEmpty()) return false;
        NpcInventoryState state = existing.get();
        SimpleItemContainer armor = new SimpleItemContainer(NpcInventoryState.ARMOR_CAPACITY);
        ItemContainerUtil.trySetArmorFilters(armor);
        restore(armor, state.armor(), "armor");
        SimpleItemContainer storage = new SimpleItemContainer(NpcInventoryState.INVENTORY_CAPACITY);
        restore(storage, state.inventory(), "inventory");
        SimpleItemContainer hotbar = new SimpleItemContainer((short) 8);
        state.loadout().stream().filter(value -> value.slot() == Session.PRIMARY_SLOT)
                .findFirst().ifPresent(value -> restoreOne(hotbar, (short) 0, value.toItemStack(), "primary weapon"));
        state.loadout().stream().filter(value -> value.slot() == Session.AMMUNITION_SLOT)
                .findFirst().ifPresent(value -> restoreOne(hotbar, (short) 1, value.toItemStack(), "preferred ammunition"));
        SimpleItemContainer utility = new SimpleItemContainer((short) 1);
        state.loadout().stream().filter(value -> value.slot() == Session.OFFHAND_SLOT)
                .findFirst().ifPresent(value -> restoreOne(utility, (short) 0, value.toItemStack(), "offhand"));
        InventoryComponent.Armor armorComponent = new InventoryComponent.Armor(armor);
        InventoryComponent.Storage storageComponent = new InventoryComponent.Storage(storage);
        InventoryComponent.Hotbar hotbarComponent = new InventoryComponent.Hotbar(hotbar, (byte) 0);
        InventoryComponent.Utility utilityComponent = new InventoryComponent.Utility(utility, (byte) 0);
        armorComponent.setOutdatedEquipment(true);
        hotbarComponent.setOutdatedEquipment(true);
        utilityComponent.setOutdatedEquipment(true);
        store.putComponent(npcRef, InventoryComponent.Armor.getComponentType(), armorComponent);
        store.putComponent(npcRef, InventoryComponent.Storage.getComponentType(),
                storageComponent);
        store.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(),
                hotbarComponent);
        store.putComponent(npcRef, InventoryComponent.Utility.getComponentType(),
                utilityComponent);
        PlayerSettings base = Optional.ofNullable(store.getComponent(
                npcRef, PlayerSettings.getComponentType())).orElseGet(PlayerSettings::defaults);
        store.putComponent(npcRef, PlayerSettings.getComponentType(), new PlayerSettings(
                base.showEntityMarkers(),
                base.armorItemsPreferredPickupLocation(),
                base.weaponAndToolItemsPreferredPickupLocation(),
                base.usableItemsItemsPreferredPickupLocation(),
                base.solidBlockItemsPreferredPickupLocation(),
                base.miscItemsPreferredPickupLocation(),
                base.creativeSettings(),
                state.hideHelmet(), state.hideCuirass(), state.hideGauntlets(), state.hidePants(),
                base.voiceSettings()));
        installRuntimePersistence(npcName, state, armor, hotbar, utility, storage);
        return true;
    }

    /**
     * Keeps the profile-local file authoritative as native gameplay mutates the NPC's
     * live containers (pickups, ammunition consumption, equips and drops). The event
     * callback only snapshots RAM and queues disk work on the dedicated writer.
     */
    private boolean installRuntimePersistence(
            String npcName,
            NpcInventoryState authored,
            ItemContainer armor,
            ItemContainer hotbar,
            ItemContainer utility,
            ItemContainer storage) {
        if (!runtimePersistenceBindings.add(storage)) return false;
        Runnable persist = () -> {
            NpcInventoryState policy = find(npcName).orElse(authored);
            List<NpcInventoryState.PersistedItemStack> loadout = new ArrayList<>();
            addRuntimeSlot(loadout, hotbar, (short) 0, Session.PRIMARY_SLOT);
            addRuntimeSlot(loadout, utility, (short) 0, Session.OFFHAND_SLOT);
            addRuntimeSlot(loadout, hotbar, (short) 1, Session.AMMUNITION_SLOT);
            NpcInventoryState snapshot = new NpcInventoryState(
                    NpcInventoryState.CURRENT_SCHEMA_VERSION,
                    authored.stableNpcId(),
                    snapshotContainer(armor),
                    List.copyOf(loadout),
                    snapshotContainer(storage),
                    policy.infiniteAmmunition(),
                    policy.hideHelmet(), policy.hideCuirass(),
                    policy.hideGauntlets(), policy.hidePants());
            writer.execute(() -> save(npcName, snapshot));
        };
        armor.registerChangeEvent(ignored -> persist.run());
        hotbar.registerChangeEvent(ignored -> persist.run());
        utility.registerChangeEvent(ignored -> persist.run());
        storage.registerChangeEvent(ignored -> persist.run());
        return true;
    }

    private static void addRuntimeSlot(
            List<NpcInventoryState.PersistedItemStack> target,
            ItemContainer source,
            short sourceSlot,
            short persistedSlot) {
        ItemStack stack = source.getItemStack(sourceSlot);
        if (!ItemStack.isEmpty(stack)) {
            target.add(NpcInventoryState.PersistedItemStack.from(persistedSlot, stack));
        }
    }

    private static List<NpcInventoryState.PersistedItemStack> snapshotContainer(
            ItemContainer container) {
        List<NpcInventoryState.PersistedItemStack> values = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                values.add(NpcInventoryState.PersistedItemStack.from(slot, stack));
            }
        }
        return List.copyOf(values);
    }

    @Override
    public void close() {
        writer.close();
    }

    private static void restore(
            ItemContainer container,
            List<NpcInventoryState.PersistedItemStack> values,
            String section) {
        for (NpcInventoryState.PersistedItemStack value : values) {
            restoreOne(container, value.slot(), value.toItemStack(), section);
        }
    }

    private static void restoreOne(
            ItemContainer container, short slot, ItemStack stack, String section) {
        if (!container.setItemStackForSlot(slot, stack).succeeded()) {
            throw new IllegalStateException("Persisted NPC " + section
                    + " item is incompatible with slot " + slot + ": " + stack.getItemId());
        }
    }

    public final class Session implements AutoCloseable {
        public static final short PRIMARY_SLOT = 0;
        public static final short OFFHAND_SLOT = 1;
        public static final short AMMUNITION_SLOT = 2;

        private final String npcName;
        private final ItemContainer armor;
        private final ItemContainer hotbar;
        private final ItemContainer utility;
        private final ItemContainer inventory;
        private final ContainerWindow armorWindow;
        private final ContainerWindow hotbarWindow;
        private final ContainerWindow utilityWindow;
        private final ContainerWindow inventoryWindow;
        private final boolean ownsEquipment;
        private final boolean ownsInventory;
        private final AtomicReference<NpcInventoryState> pending = new AtomicReference<>();
        private final AtomicBoolean writeScheduled = new AtomicBoolean();
        private volatile UUID stableNpcId;
        private volatile boolean infiniteAmmunition;
        private volatile boolean hideHelmet;
        private volatile boolean hideCuirass;
        private volatile boolean hideGauntlets;
        private volatile boolean hidePants;
        private final AtomicLong equipmentRevision = new AtomicLong();
        private volatile Runnable changedCallback = () -> { };
        private volatile boolean restoring = true;

        private Session(String npcName, NpcInventoryState state,
                ItemContainer liveArmor, ItemContainer liveHotbar,
                ItemContainer liveUtility, ItemContainer liveStorage) {
            this.npcName = npcName;
            this.ownsEquipment = liveArmor == null;
            if (ownsEquipment != (liveHotbar == null) || ownsEquipment != (liveUtility == null)) {
                throw new IllegalArgumentException("Live NPC equipment authorities must be supplied together.");
            }
            this.armor = ownsEquipment
                    ? new SimpleItemContainer(NpcInventoryState.ARMOR_CAPACITY) : liveArmor;
            this.hotbar = ownsEquipment ? new SimpleItemContainer((short) 2) : liveHotbar;
            this.utility = ownsEquipment ? new SimpleItemContainer((short) 1) : liveUtility;
            this.ownsInventory = liveStorage == null;
            this.inventory = ownsInventory
                    ? new SimpleItemContainer(NpcInventoryState.INVENTORY_CAPACITY)
                    : liveStorage;
            if (inventory.getCapacity() != NpcInventoryState.INVENTORY_CAPACITY) {
                throw new IllegalStateException("NPC live Storage capacity is "
                        + inventory.getCapacity() + "; expected "
                        + NpcInventoryState.INVENTORY_CAPACITY + '.');
            }
            if (armor.getCapacity() != NpcInventoryState.ARMOR_CAPACITY
                    || hotbar.getCapacity() < 2 || utility.getCapacity() < 1) {
                throw new IllegalStateException("NPC live equipment capacities are incomplete.");
            }
            this.armorWindow = new ContainerWindow(armor);
            this.hotbarWindow = new ContainerWindow(hotbar);
            this.utilityWindow = new ContainerWindow(utility);
            this.inventoryWindow = new ContainerWindow(inventory);
            stableNpcId = state.stableNpcId();
            ItemContainerUtil.trySetArmorFilters(armor);
            if (ownsEquipment) {
                restore(armor, state.armor(), "armor");
                state.loadout().stream().filter(value -> value.slot() == PRIMARY_SLOT)
                        .findFirst().ifPresent(value -> restoreOne(hotbar, (short) 0,
                                value.toItemStack(), "primary weapon"));
                state.loadout().stream().filter(value -> value.slot() == AMMUNITION_SLOT)
                        .findFirst().ifPresent(value -> restoreOne(hotbar, (short) 1,
                                value.toItemStack(), "preferred ammunition"));
                state.loadout().stream().filter(value -> value.slot() == OFFHAND_SLOT)
                        .findFirst().ifPresent(value -> restoreOne(utility, (short) 0,
                                value.toItemStack(), "offhand"));
            }
            if (ownsInventory) {
                restore(inventory, state.inventory(), "inventory");
            } else if (!state.inventory().equals(snapshotContainer(inventory))) {
                throw new IllegalStateException(
                        "Live NPC Storage does not match persisted NPC inventory.");
            }
            if (!ownsEquipment && (!canonicalItems(state.armor()).equals(
                    canonicalItems(snapshotContainer(armor)))
                    || !canonicalItems(state.loadout()).equals(
                            canonicalItems(snapshotLoadout())))) {
                throw new IllegalStateException("Live NPC equipment does not match persisted state.");
            }
            installLoadoutFilters();
            validateRestoredLoadout();
            infiniteAmmunition = state.infiniteAmmunition();
            hideHelmet = state.hideHelmet();
            hideCuirass = state.hideCuirass();
            hideGauntlets = state.hideGauntlets();
            hidePants = state.hidePants();
            armor.registerChangeEvent(ignored -> changed());
            hotbar.registerChangeEvent(ignored -> changed());
            utility.registerChangeEvent(ignored -> changed());
            // Live Storage is already observed by installRuntimePersistence(). The
            // bridge performs its own UI reconciliation; registering this authoring
            // listener too would create a competing persistence callback.
            if (ownsInventory) inventory.registerChangeEvent(ignored -> changed());
            armorWindow.registerCloseEvent(ignored -> flush());
            hotbarWindow.registerCloseEvent(ignored -> flush());
            utilityWindow.registerCloseEvent(ignored -> flush());
            inventoryWindow.registerCloseEvent(ignored -> flush());
            restoring = false;
        }

        public ContainerWindow[] windows() {
            return new ContainerWindow[] {
                    armorWindow, hotbarWindow, utilityWindow, inventoryWindow };
        }

        public int armorSectionId() { return armorWindow.getId(); }
        public int primarySectionId() { return hotbarWindow.getId(); }
        public int ammunitionSectionId() { return hotbarWindow.getId(); }
        public int offhandSectionId() { return utilityWindow.getId(); }
        public int inventorySectionId() { return inventoryWindow.getId(); }
        public ItemContainer armor() { return armor; }
        public ItemContainer hotbar() { return hotbar; }
        public ItemContainer utility() { return utility; }
        public ItemContainer inventory() { return inventory; }
        public ContainerWindow armorWindow() { return armorWindow; }
        public ContainerWindow hotbarWindow() { return hotbarWindow; }
        public ContainerWindow utilityWindow() { return utilityWindow; }
        public ContainerWindow inventoryWindow() { return inventoryWindow; }
        public boolean usesLiveEquipment() { return !ownsEquipment; }
        public boolean usesLiveStorage() { return !ownsInventory; }
        public boolean infiniteAmmunition() { return infiniteAmmunition; }
        public boolean infiniteAmmunitionEffective() {
            return infiniteAmmunition
                    && NpcEquipmentRules.infiniteAmmunitionFeatureEnabled()
                    && ammunitionPolicyRelevant();
        }
        public long equipmentRevision() { return equipmentRevision.get(); }
        public long markEquipmentCommitted() { return equipmentRevision.incrementAndGet(); }

        public ItemStack armorItem(short slot) {
            requireSlot(slot, armor.getCapacity(), "armor");
            return armor.getItemStack(slot);
        }

        public ItemStack loadoutItem(short slot) {
            return switch (slot) {
                case PRIMARY_SLOT -> hotbar.getItemStack((short) 0);
                case OFFHAND_SLOT -> utility.getItemStack((short) 0);
                case AMMUNITION_SLOT -> hotbar.getItemStack((short) 1);
                default -> throw new IllegalArgumentException("Invalid loadout slot: " + slot);
            };
        }

        /**
         * Moves the selected authoritative NPC-inventory slot into an equipment
         * slot, or unequips the target when no non-empty inventory slot is
         * selected. Hytale's ItemContainer transaction remains the sole mutation
         * path so filters, metadata, quantities, durability and window updates are
         * preserved.
         */
        public String activateEquipmentSlot(
                boolean armorSection, short targetSlot, int selectedInventorySlot) {
            ItemContainer target = armorSection ? armor : switch (targetSlot) {
                case PRIMARY_SLOT, AMMUNITION_SLOT -> hotbar;
                case OFFHAND_SLOT -> utility;
                default -> throw new IllegalArgumentException("Invalid loadout slot: " + targetSlot);
            };
            short physicalTarget = armorSection ? targetSlot : switch (targetSlot) {
                case PRIMARY_SLOT, OFFHAND_SLOT -> (short) 0;
                case AMMUNITION_SLOT -> (short) 1;
                default -> throw new IllegalArgumentException("Invalid loadout slot: " + targetSlot);
            };
            requireSlot(physicalTarget, target.getCapacity(), armorSection ? "armor" : "loadout");

            boolean selected = selectedInventorySlot >= 0
                    && selectedInventorySlot < inventory.getCapacity()
                    && !ItemStack.isEmpty(inventory.getItemStack((short) selectedInventorySlot));
            if (selected) {
                if (!inventory.swapItems((short) selectedInventorySlot, target,
                        physicalTarget, (short) 1).succeeded()) {
                    throw new IllegalArgumentException(
                            "That item is not compatible with the selected equipment slot.");
                }
                return "Equipped selected NPC inventory item.";
            }

            if (ItemStack.isEmpty(target.getItemStack(physicalTarget))) {
                throw new IllegalArgumentException(
                        "Select an occupied NPC inventory slot, then choose an equipment slot.");
            }
            if (!target.moveItemStackFromSlot(physicalTarget, inventory).succeeded()) {
                throw new IllegalStateException("NPC inventory is full; the item was not removed.");
            }
            return "Returned equipment to the NPC inventory.";
        }

        public boolean armorHidden(short slot) {
            return switch (slot) {
                case 0 -> hideHelmet;
                case 1 -> hideCuirass;
                case 2 -> hideGauntlets;
                case 3 -> hidePants;
                default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
            };
        }

        public void toggleArmorVisibility(short slot) {
            requireSlot(slot, armor.getCapacity(), "armor");
            if (ItemStack.isEmpty(armor.getItemStack(slot))) {
                throw new IllegalArgumentException(
                        "Armor visibility is only available for equipped armor.");
            }
            switch (slot) {
                case 0 -> hideHelmet = !hideHelmet;
                case 1 -> hideCuirass = !hideCuirass;
                case 2 -> hideGauntlets = !hideGauntlets;
                case 3 -> hidePants = !hidePants;
                default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
            }
            changed();
            flush();
        }

        public boolean ammunitionPolicyRelevant() {
            ItemStack weapon = loadoutItem(PRIMARY_SLOT);
            ItemStack ammunition = loadoutItem(AMMUNITION_SLOT);
            return NpcEquipmentRules.requiresAmmunition(weapon)
                    && !ItemStack.isEmpty(ammunition)
                    && NpcEquipmentRules.isCompatibleAmmunition(weapon, ammunition);
        }

        public void setInfiniteAmmunition(boolean value) {
            if (value && !NpcEquipmentRules.infiniteAmmunitionFeatureEnabled()) {
                throw new IllegalArgumentException(
                        "Infinite ammunition is disabled by server policy.");
            }
            if (value && !ammunitionPolicyRelevant()) {
                throw new IllegalArgumentException(
                        "Select a compatible ranged weapon and preferred ammunition first.");
            }
            infiniteAmmunition = value;
            changed();
            flush();
        }

        public void bindStableIdentity(UUID stableId) {
            stableNpcId = stableId;
            changed();
        }

        public void onChanged(Runnable callback) {
            changedCallback = callback == null ? () -> { } : callback;
        }

        public NpcInventoryState snapshot() {
            return new NpcInventoryState(NpcInventoryState.CURRENT_SCHEMA_VERSION, stableNpcId,
                    snapshot(armor), snapshotLoadout(), snapshot(inventory),
                    infiniteAmmunition,
                    hideHelmet, hideCuirass, hideGauntlets, hidePants);
        }

        public void flush() {
            NpcInventoryState state = snapshot();
            pending.set(null);
            try {
                // Serialize the terminal write behind any queued change write so an older
                // snapshot can never overwrite the page-close/Enter snapshot.
                writer.submit(() -> save(npcName, state)).get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while saving NPC inventory", interrupted);
            } catch (java.util.concurrent.ExecutionException failure) {
                throw new IllegalStateException("Could not save NPC inventory", failure.getCause());
            }
        }

        @Override
        public void close() {
            flush();
        }

        private void installLoadoutFilters() {
            SlotFilter primary = (action, container, slot, stack) -> {
                if (action != FilterActionType.ADD || ItemStack.isEmpty(stack)) return true;
                if (!NpcEquipmentRules.isPrimaryWeapon(stack)) return false;
                ItemStack ammunition = hotbar.getItemStack((short) 1);
                return ItemStack.isEmpty(ammunition)
                        || NpcEquipmentRules.isCompatibleAmmunition(stack, ammunition);
            };
            SlotFilter offhand = (action, container, slot, stack) -> action != FilterActionType.ADD
                    || NpcEquipmentRules.isShieldOrOffhand(stack);
            SlotFilter ammunition = (action, container, slot, stack) -> action != FilterActionType.ADD
                    || NpcEquipmentRules.isCompatibleAmmunition(
                            hotbar.getItemStack((short) 0), stack);
            if (hotbar instanceof SimpleItemContainer simpleHotbar) {
                simpleHotbar.setSlotFilter(FilterActionType.ADD, (short) 0, primary);
                simpleHotbar.setSlotFilter(FilterActionType.ADD, (short) 1, ammunition);
            }
            if (utility instanceof SimpleItemContainer simpleUtility) {
                simpleUtility.setSlotFilter(FilterActionType.ADD, (short) 0, offhand);
            }
        }

        private static void requireSlot(short slot, short capacity, String section) {
            if (slot < 0 || slot >= capacity) {
                throw new IllegalArgumentException("Invalid " + section + " slot: " + slot);
            }
        }

        private void validateRestoredLoadout() {
            // Historical or externally-mutated equipment is never silently moved or
            // deleted. Compatibility is re-evaluated at each transaction and render;
            // invalid dependent state remains physically present but fail-closed for
            // gameplay policies such as infinite ammunition.
        }

        private void changed() {
            if (restoring) return;
            pending.set(snapshot());
            changedCallback.run();
            if (writeScheduled.compareAndSet(false, true)) writer.execute(this::drainWrites);
        }

        private void drainWrites() {
            try {
                NpcInventoryState state;
                while ((state = pending.getAndSet(null)) != null) save(npcName, state);
            } finally {
                writeScheduled.set(false);
                if (pending.get() != null && writeScheduled.compareAndSet(false, true)) {
                    writer.execute(this::drainWrites);
                }
            }
        }

        private List<NpcInventoryState.PersistedItemStack> snapshot(ItemContainer container) {
            List<NpcInventoryState.PersistedItemStack> values = new ArrayList<>();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!ItemStack.isEmpty(stack)) {
                    values.add(NpcInventoryState.PersistedItemStack.from(slot, stack));
                }
            }
            return List.copyOf(values);
        }

        private List<NpcInventoryState.PersistedItemStack> snapshotLoadout() {
            List<NpcInventoryState.PersistedItemStack> values = new ArrayList<>();
            addRuntimeSlot(values, hotbar, (short) 0, PRIMARY_SLOT);
            addRuntimeSlot(values, utility, (short) 0, OFFHAND_SLOT);
            addRuntimeSlot(values, hotbar, (short) 1, AMMUNITION_SLOT);
            return List.copyOf(values);
        }
    }
}
