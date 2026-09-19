package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic gate for restart hydration and the native secondary-page integration. */
public final class R115NativeNpcInventoryProfileIntegrationTest {
    private R115NativeNpcInventoryProfileIntegrationTest() { }

    public static void main(String[] args) throws Exception {
        String repository = read("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java");
        String controller = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryController.java");
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java");
        String markup = read("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");

        assert repository.contains("World-restored NPC entities do not pass through spawnNpc()")
                : "Restart hydration rationale is missing";
        assert repository.contains("runtimeMatches(authored")
                && repository.contains("restore(liveStorage, authored.inventory()")
                : "Persisted storage is not hydrated into the exact live container";
        assert repository.indexOf("restore(liveStorage, authored.inventory()")
                < repository.indexOf("installRuntimePersistence(npcName, authored, liveArmor")
                : "Hydration must happen before persistence listeners are registered";
        assert repository.contains("NPC_INVENTORY_RUNTIME_CONFLICT_PRESERVED")
                && repository.indexOf("NPC_INVENTORY_RUNTIME_CONFLICT_PRESERVED")
                        < repository.indexOf("hydrateRollbackSafe(npcName")
                : "Divergent non-empty live state must be preserved before reconciliation";

        assert controller.contains("Page.Bench")
                && controller.contains("setPageWithWindows")
                && controller.contains("LIVE_NPC_ECS_STORAGE")
                : "Production launcher must retain the proven native controller";
        assert !controller.contains("import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage")
                && !controller.contains("new NativeInventoryProbePage")
                && !controller.contains("moveItem(")
                : "Native launcher must not reintroduce custom or manual transfers";

        assert !markup.contains("#OpenNativeInventoryButton")
                && !page.contains("OpenNativeInventory")
                : "The redundant native inventory launcher must remain removed";
        assert command.contains("NativeNpcInventoryController.resolve")
                : "/npc update must still resolve live NPC storage when available";
        assert page.contains("NpcCharacterPreview")
                && markup.contains("#VoiceRescanButton")
                && markup.contains("#ArmorGrid")
                : "Existing Profile mesh, voice, and gear surfaces were not preserved";

        System.out.println("R115 native NPC inventory Profile integration gate passed.");
    }

    private static String read(String value) throws Exception {
        return Files.readString(Path.of(value));
    }
}
