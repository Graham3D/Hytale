package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Structural gate for the isolated live-NPC native persistence probe. */
public final class R114NativeNpcInventoryPersistenceProbeTest {
    public static void main(String[] args) throws Exception {
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/NativeInventoryProbeCommand.java");
        String controller = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryController.java");
        String window = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryProbeWindow.java");
        String repository = read("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java");
        String adapter = read("src/main/java/com/inigmasgames/persistentnpcs/hytale/HytaleNpcAdapter.java");
        String profilePage = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String profileUi = read("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");

        assert command.contains("isNpcRequest(request)")
                && command.contains("profiles.requireName(sanitized)")
                && command.contains("NativeNpcInventoryController.open")
                && controller.contains("adapter.requireLiveNpcRef(")
                && controller.contains("npcStorage.getInventory()")
                : "npc <name> must resolve the exact live ECS Storage container";
        assert controller.contains("Page.Bench, true, window")
                && controller.contains("targetContainer=LIVE_NPC_ECS_STORAGE")
                && controller.contains("customPage=false customItemGrid=false customTransferHandler=false")
                : "live NPC probe must retain proven native ownership";
        assert window.contains("extends ContainerWindow")
                && window.contains("npcStorageComponent.getInventory() == liveNpcStorage")
                && window.contains("resolvedNpc != liveNpcStorage")
                && window.contains("recoveryPerformed=false")
                : "window must bind and retain the NPC's exact authoritative container";
        assert window.contains("persistedMatchesLiveBeforeOpen")
                && window.contains("persistedMatchesLive=")
                && repository.contains("ensureRuntimePersistence(")
                && repository.contains("flushPendingWrites()")
                : "probe must prove repository observation and exact persisted state";
        assert adapter.contains("requireLiveNpcRef(")
                && adapter.contains("locateNpcs(store, selectedProfile)")
                : "live target resolution must reuse managed NPC identity rules";
        assert !window.contains("moveAllItemStacksTo")
                && !window.contains("addOrDropItemStack")
                && !window.contains("InventoryUtils.moveItem(")
                : "live probe must not recover, copy, or bridge items";

        // Production NPC Profile remains frozen.
        assert profilePage.contains("inventory.flush()")
                && profilePage.contains("boundNpcGridDocument(storageWindow.getId())")
                && profileUi.contains("CharacterPreviewComponent #NpcCharacterPreview")
                && profileUi.contains("Group #NpcGridHost")
                && profileUi.contains("Group #PlayerGridHost")
                : "production NPC Profile must remain unchanged";
        System.out.println("R114 native live-NPC inventory persistence structural gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
