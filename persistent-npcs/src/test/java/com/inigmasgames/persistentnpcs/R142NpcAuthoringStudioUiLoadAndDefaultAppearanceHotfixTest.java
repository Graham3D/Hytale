package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** R142 gate: client-loadable Custom UI styles and safe missing-appearance/audio lifecycle. */
public final class R142NpcAuthoringStudioUiLoadAndDefaultAppearanceHotfixTest {
    private R142NpcAuthoringStudioUiLoadAndDefaultAppearanceHotfixTest() { }

    public static void main(String[] args) throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));
        assert !ui.contains("$C.@DestructiveButtonStyle")
                && !ui.contains("$C.@PrimaryButtonStyle")
                : "Custom UI must not reference main-menu-only button style names";
        assert ui.contains("Style: $C.@CancelButtonStyle")
                && ui.contains("Style: $C.@SecondaryButtonStyle")
                && ui.contains("Style: $C.@DefaultButtonStyle")
                : "Recorder must use button styles exported by Common/UI/Custom/Common.ui";

        String editor = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/profile/NpcProfileEditorService.java"));
        assert editor.contains("appearances.materializePackagedDefaultIfMissing(safe)")
                : "NPC creation must materialize the packaged neutral appearance";
        assert editor.contains("appearances.materializeDefaultIfMissing(safe, skinCodec)")
                : "Update/reopen must repair a missing legacy appearance";

        String neutral = Files.readString(Path.of(
                "src/main/resources/defaults/profiles/neutral-appearance.json"));
        assert neutral.contains("\"underwear\": \"Boxer.Red\"")
                && neutral.contains("\"face\": \"Face\"")
                && !neutral.contains("haircut")
                && !neutral.contains("overtop")
                : "Neutral NPC template must be valid, bald, and free of authored clothing";

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        String persistence = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/voice/NpcVoiceSamplePersistenceService.java"));
        assert page.contains("showVoiceDeleteConfirmation(snapshot)")
                && page.contains("VOICE_DELETE_SAVED_CONFIRM")
                && persistence.contains(".voice-trash")
                : "Established saved voice samples must remain confirmation-gated and recoverable";

        System.out.println("R142 UI-load/default-appearance/audio-preservation gate passed.");
    }
}
