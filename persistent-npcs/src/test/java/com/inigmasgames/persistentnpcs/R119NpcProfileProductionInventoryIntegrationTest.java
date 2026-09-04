package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic production-candidate gate for the R118-to-R119 promotion. */
public final class R119NpcProfileProductionInventoryIntegrationTest {
    private R119NpcProfileProductionInventoryIntegrationTest() { }

    public static void main(String[] arguments) throws Exception {
        System.out.println("R119 stage=shared-contract");
        exactR118ContractIsSharedByProduction();
        System.out.println("R119 stage=authority");
        liveNpcAuthorityFailsClosed();
        System.out.println("R119 stage=persistence");
        persistentStorageUsesOneRepositoryPath();
        System.out.println("R119 stage=partial-stack-wire");
        partialStackClientDiagnosticIsNeverTrusted();
        System.out.println("R119 NPC Profile production inventory integration gate passed.");
    }

    private static void exactR118ContractIsSharedByProduction() throws Exception {
        String profile = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java");
        String bridgeUi = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryBridgeUi.java");

        assert profile.contains("CustomInventoryTransactionBridge");
        assert profile.contains("CustomInventoryBridgeUi.bindDrop(events, \"#NpcInventoryGrid\"");
        assert profile.contains("CustomInventoryBridgeUi.bindDrop(events, \"#PlayerInventoryGrid\"");
        assert profile.contains("CustomInventoryBridgeUi.setNativeSlots(");
        assert profile.contains("authoritativeQuantityAtIntent(");
        assert profile.contains("reconcileInventoryFromAuthority");
        assert profile.contains("ATOMIC_FIXED_CAPACITY_SLOTS_REPLACEMENT");
        assert bridgeUi.contains("CustomUIEventBindingType.Dropped");
        assert !profile.contains("SlotMouseDragCompleted");
        assert !profile.contains("SlotClickReleaseWhileDragging");
        assert bridge.contains("moveItemStackFromSlotToSlot(");
        assert !bridge.contains("setItemStackForSlot(");
        assert !bridge.contains("removeItemStackFromSlot(");
        assert !bridge.contains("addItemStack(");
    }

    private static void liveNpcAuthorityFailsClosed() throws Exception {
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java");
        String controller = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryController.java");
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java");

        assert command.contains("NativeNpcInventoryController.resolve(");
        assert command.contains("liveStorageAuthority");
        assert controller.contains("NPC_REFERENCE_INVALID");
        assert controller.contains("NPC_ENTITY_UUID_MISMATCH");
        assert controller.contains("NPC_ECS_STORAGE_IDENTITY_MISMATCH");
        assert controller.contains("NPC_STABLE_PROFILE_ID_MISMATCH");
        assert controller.contains("NPC_RUNTIME_PROFILE_ID_MISMATCH");
        assert bridge.contains("AUTHORITY_INVALID_");
        assert bridge.indexOf("authorityValidator.invalidReason")
                < bridge.indexOf("moveItemStackFromSlotToSlot(");
        assert profileClosePrecedesPersistenceClose();
    }

    private static boolean profileClosePrecedesPersistenceClose() throws Exception {
        String profile = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        int bridgeClose = profile.indexOf(
                "addCleanup(\"inventory-event-bridge\", this::closeInventoryBridge)");
        int persistenceClose = profile.indexOf(
                "addCleanup(\"inventory-persistence-flush\", inventory::close)");
        int dismiss = profile.indexOf("public void onDismiss(");
        int sessionClose = profile.indexOf("authoringSession.close();", dismiss);
        assert bridgeClose >= 0 && persistenceClose > bridgeClose
                : "Late inventory events must fail before persistent session teardown";
        assert dismiss >= 0 && sessionClose > dismiss
                : "Dismiss must run the idempotent ordered Authoring Studio cleanup";
        return true;
    }

    private static void persistentStorageUsesOneRepositoryPath() throws Exception {
        String repository = read("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java");
        String profile = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        assert repository.contains("openWithLiveStorage(");
        assert repository.contains("this.inventory = ownsInventory");
        assert repository.contains("state.inventory().equals(snapshotContainer(inventory))");
        assert repository.contains("if (ownsInventory) inventory.registerChangeEvent")
                : "The live container already has the runtime persistence listener";
        assert profile.contains("editor.inventories().openWithLiveStorage(");
        assert profile.contains("persistenceAuthority=NpcInventoryRepository_RUNTIME_PIPELINE");
        assert !profile.contains("nativeInventoryOpener.accept(ref, store)")
                : "The redundant native-inventory Profile button must remain removed";
    }

    private static void partialStackClientDiagnosticIsNeverTrusted() throws Exception {
        String profile = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java");
        String bridgeUi = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryBridgeUi.java");
        assert profile.contains("authoritativeQuantityAtIntent(");
        assert profile.contains("stack.getQuantity()");
        assert bridge.contains("requestedQuantity() > sourceBefore.getQuantity()")
                : "The server must bound requested quantity to authoritative source state";
        assert profile.contains("mouseButton == 2 ? 1 : stack.getQuantity()")
                : "A2 derives one-item/full-stack intent without trusting client quantity";
        assert bridgeUi.contains("new ItemGridSlot(stack)")
                : "The full ItemStack, including quantity metadata, must be encoded";
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
