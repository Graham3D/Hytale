package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Deterministic zero-capacity and retained-identity regression coverage for R165. */
public final class R165OfflineEquipmentHydrationTest {
    public static void main(String[] args) throws Exception {
        System.out.println("R165 stage=zero-capacity-defaults");
        zeroCapacityNativeDefaultsAreInitializedBeforeSlotAccess();
        System.out.println("R165 stage=retained-identities");
        validNativeContainerIdentitiesAreRetained();
        System.out.println("R165 stage=bounded-slot-access");
        zeroCapacitySlotReadsAndWritesAreBounded();
        System.out.println("R165 stage=lifecycle-wiring");
        lifecycleWiringAndAuthorityAreExplicit();
        System.out.println("R165 offline equipment hydration PASS: zero-capacity defaults expanded, valid identities retained, slot access bounded, offline authority isolated, spawn hydration ordered, and lifecycle telemetry present.");
    }

    private static void zeroCapacityNativeDefaultsAreInitializedBeforeSlotAccess() {
        InventoryComponent.Armor armor = new InventoryComponent.Armor();
        InventoryComponent.Hotbar hotbar = new InventoryComponent.Hotbar();
        InventoryComponent.Utility utility = new InventoryComponent.Utility();
        InventoryComponent.Storage storage = new InventoryComponent.Storage();
        assert armor.getInventory().getCapacity() == 0;
        assert hotbar.getInventory().getCapacity() == 0;
        assert utility.getInventory().getCapacity() == 0;
        assert storage.getInventory().getCapacity() == 0;

        NpcInventoryRepository.ensureMinimumCapacity(
                armor, NpcInventoryState.ARMOR_CAPACITY, "armor");
        NpcInventoryRepository.ensureMinimumCapacity(hotbar, (short) 8, "hotbar");
        NpcInventoryRepository.ensureMinimumCapacity(utility, (short) 1, "utility");
        NpcInventoryRepository.ensureMinimumCapacity(
                storage, NpcInventoryState.INVENTORY_CAPACITY, "storage");
        assert armor.getInventory().getCapacity() == NpcInventoryState.ARMOR_CAPACITY;
        assert hotbar.getInventory().getCapacity() == 8;
        assert utility.getInventory().getCapacity() == 1;
        assert storage.getInventory().getCapacity() == NpcInventoryState.INVENTORY_CAPACITY;
    }

    private static void validNativeContainerIdentitiesAreRetained() {
        SimpleItemContainer armorContainer = new SimpleItemContainer(
                NpcInventoryState.ARMOR_CAPACITY);
        InventoryComponent.Armor armor = new InventoryComponent.Armor(armorContainer);
        NpcInventoryRepository.ensureMinimumCapacity(
                armor, NpcInventoryState.ARMOR_CAPACITY, "armor");
        assert armor.getInventory() == armorContainer;

        SimpleItemContainer storageContainer = new SimpleItemContainer(
                NpcInventoryState.INVENTORY_CAPACITY);
        InventoryComponent.Storage storage = new InventoryComponent.Storage(storageContainer);
        NpcInventoryRepository.ensureMinimumCapacity(
                storage, NpcInventoryState.INVENTORY_CAPACITY, "storage");
        assert storage.getInventory() == storageContainer;
    }

    private static void zeroCapacitySlotReadsAndWritesAreBounded() throws Exception {
        var empty = new InventoryComponent.Armor().getInventory();
        Method add = NpcInventoryRepository.class.getDeclaredMethod("addRuntimeSlot",
                java.util.List.class,
                com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,
                short.class, short.class);
        add.setAccessible(true);
        var captured = new ArrayList<NpcInventoryState.PersistedItemStack>();
        add.invoke(null, captured, empty, (short) 0, (short) 0);
        assert captured.isEmpty();

        Method restore = NpcInventoryRepository.class.getDeclaredMethod("restoreOne",
                com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,
                short.class, ItemStack.class, String.class);
        restore.setAccessible(true);
        try {
            restore.invoke(null, empty, (short) 0, null,
                    "test");
            throw new AssertionError("Zero-capacity write was not rejected");
        } catch (InvocationTargetException expected) {
            assert expected.getCause() instanceof IllegalStateException;
            assert expected.getCause().getMessage().contains("against capacity 0");
        }
    }

    private static void lifecycleWiringAndAuthorityAreExplicit() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/hytale/HytaleNpcAdapter.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java"));
        String profile = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        for (String marker : new String[] {"PROFILE_OPEN_ABSENT",
                "PERSISTED_EQUIPMENT_MUTATION", "SPAWN_COMPONENT_CREATION",
                "SPAWN_HYDRATION", "STAT_MODIFIERS_SYNC_",
                "NPC_INVENTORY_CONTAINER_AUDIT"}) assert repository.contains(marker);
        assert repository.indexOf("ensureLiveInventoryComponents(")
                < repository.indexOf("if (!runtimeMatches(authored");
        String spawnApply = repository.substring(repository.indexOf("public boolean applyToSpawnedNpc"),
                repository.indexOf("private record LiveContainers"));
        assert !spawnApply.contains("new SimpleItemContainer")
                : "Spawn hydration must retain valid native container identities";
        assert adapter.contains("authoredInventories.applyToSpawnedNpc")
                && adapter.indexOf("authoredInventories.applyToSpawnedNpc")
                        < adapter.indexOf("ensureInventory(store, ref)",
                                adapter.indexOf("authoredInventories.applyToSpawnedNpc"));
        assert command.contains("inventoryMode=MUTABLE_PERSISTED_AUTHORING_STORAGE");
        assert profile.contains("liveStorageAuthority == null")
                && profile.contains("captureSaved(authoringSession.npcStableId()");
    }
}
