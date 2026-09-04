package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** P1 approval-candidate gate: native button chrome, selected state, and child-editor Back. */
public final class R141NpcAuthoringStudioP1UxPolishTest {
    private R141NpcAuthoringStudioP1UxPolishTest() { }

    public static void main(String[] args) throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));
        int start = ui.indexOf("$C.@PageOverlay #VoiceRecorderPage");
        int end = ui.indexOf("$C.@PageOverlay #VoiceDeleteConfirmPage", start);
        assert start >= 0 && end > start : "Voice Recorder UI block was not found";
        String recorder = ui.substring(start, end);

        assert recorder.contains("Anchor: (Width: 520, Height: 720)")
                : "Recorder must fit tightly at 1080p and 1440p";
        assert recorder.contains("#VoiceRecordButton")
                && recorder.contains("Style: $C.@CancelButtonStyle")
                && recorder.contains("#VoicePlayStopButton")
                && recorder.contains("Style: $C.@SecondaryButtonStyle")
                && recorder.contains("#VoiceSaveButton")
                && recorder.contains("Style: $C.@DefaultButtonStyle")
                : "Recorder actions must use native stateful Hytale button chrome";
        assert recorder.contains("@VoiceSelectionMarker #VoiceSelectedREFERENCE")
                && recorder.contains("TextColor: @Gold")
                && ui.contains("@VoiceSelectionMarker = Group")
                : "Selected emotion must have a moving gold treatment";
        assert ui.contains("@RecorderWaveform = #6d8798")
                && ui.contains("@RecorderBaseline = #496274")
                && recorder.contains("Background: @RecorderWaveform")
                && recorder.contains("Background: @RecorderBaseline")
                : "Waveform must use the subdued blue-gray palette";
        assert !recorder.contains("#VoiceCloseButton")
                && !recorder.contains("RETURN TO STUDIO")
                : "Recorder must use native Back navigation, not an in-panel return button";
        assert ui.contains("BackButton #AuthoringBackButton")
                : "The page must expose exactly one native Back control";

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("CustomPageLifetime.CantClose")
                && page.contains("CustomPageLifetime.CanDismiss")
                && page.contains("\"#AuthoringBackButton\", authoringEvent(\"CLOSE_EDITOR\")")
                : "Recorder Back must route to the child-editor close path";
        assert page.contains("quiesceVoiceRecorderForBack()")
                && page.contains("current.stop()")
                && page.contains("current.stopPlayback()")
                : "Recorder Back must stop active capture or playback before navigation";
        assert page.contains("voiceEvent(\"VOICE_RECORD\")")
                && page.contains("voiceEvent(\"VOICE_PLAY_STOP\")")
                && page.contains("voiceEvent(\"VOICE_DELETE\")")
                && page.contains("voiceEvent(\"VOICE_SAVE\")")
                : "Recorder authority and compact state machine must remain intact";

        System.out.println("R141 P1 Voice Recorder UX-polish gate passed at 1080p and 1440p.");
    }
}
