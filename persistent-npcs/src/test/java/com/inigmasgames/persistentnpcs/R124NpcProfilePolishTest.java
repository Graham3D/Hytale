package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression gate for the R124 NPC Profile behavior and lifecycle changes. */
public final class R124NpcProfilePolishTest {
    private R124NpcProfilePolishTest() { }

    public static void main(String[] args) throws Exception {
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "CustomInventoryTransactionBridge.java");
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NpcProfilePage.java");
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/"
                + "AbstractImmersiveNpcProfileCommand.java");
        String markup = read("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui");

        assert !bridge.contains("OPERATION_NOT_ENABLED_INTERNAL_MOVE")
                : "NPC-internal empty-slot moves must reach InventoryUtils.moveItem";
        assert page.contains("NPC_PROFILE_PREVIEW_REASSERT_AFTER_INVENTORY_MOVE")
                && page.contains("preview.refreshEquipment()")
                : "Committed storage moves must reassert the NPC preview equipment";
        assert command.contains("MUTABLE_PERSISTED_AUTHORING_STORAGE")
                && page.contains("CUSTOM_BRIDGE_TO_PERSISTED_AUTHORING_STORAGE")
                : "Unspawned profiles such as Mara need mutable persisted storage";

        assert !markup.contains("OpenNativeInventoryButton");
        for (String removed : new String[] {"Text: \"Head\"", "Text: \"Chest\"",
                "Text: \"Hands\"", "Text: \"Legs\"",
                "Text: \"Primary Weapon\"", "Text: \"Shield / Offhand\"",
                "Text: \"Preferred Ammo\""}) {
            assert !markup.contains(removed) : removed;
        }
        assert markup.contains("#DeleteButton")
                && markup.contains("#DeleteConfirmPage")
                && markup.contains("#DeleteYesButton")
                && markup.contains("#DeleteNoButton");

        Path data = Files.createTempDirectory("immersive-npc-r124-lifecycle");
        ProfileRepository repository = new ProfileRepository(data);
        var template = repository.createTemplate("Aster");
        Path profile = repository.profilePath("Aster");
        assert Files.isRegularFile(profile);
        assert repository.load("Aster").stableId().equals(template.stableId());
        Files.createDirectories(repository.profileDirectory("Aster").resolve("native-role"));
        Files.writeString(repository.profileDirectory("Aster")
                .resolve("native-role/Aster.json"), "{}");
        repository.deleteProfileDirectory("Aster");
        assert !Files.exists(repository.profileDirectory("Aster"));

        System.out.println("R124 NPC Profile polish gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
