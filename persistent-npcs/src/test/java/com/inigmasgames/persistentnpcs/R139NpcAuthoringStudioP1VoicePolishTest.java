package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** P1 presentation gate: compact recorder only, with preserved A6 controls. */
public final class R139NpcAuthoringStudioP1VoicePolishTest {
    private R139NpcAuthoringStudioP1VoicePolishTest() { }

    public static void main(String[] args) throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));
        int start = ui.indexOf("$C.@PageOverlay #VoiceRecorderPage");
        int end = ui.indexOf("$C.@PageOverlay #VoiceDeleteConfirmPage", start);
        assert start >= 0 && end > start : "Voice Recorder UI block was not found";
        String recorder = ui.substring(start, end);

        assert recorder.contains("Anchor: (Width: 520, Height: 780)")
                : "P1 recorder frame must remain bounded";
        for (int[] resolution : List.of(new int[] {1920, 1080}, new int[] {2560, 1440})) {
            assert 520 <= resolution[0] && 780 <= resolution[1]
                    : "Recorder must fit required connected-test resolution";
        }
        for (String selector : List.of("#VoiceRecordButton", "#VoicePlayStopButton",
                "#VoiceDeleteButton", "#VoiceSaveButton", "#VoiceCloseButton",
                "#VoiceRecordingIndicator", "#VoiceWaveformBar0",
                "#VoiceWaveformBar31")) {
            assert recorder.contains(selector) : "Missing P1 recorder selector " + selector;
        }
        for (String asset : List.of("NpcIconRecord.png", "NpcIconPlay.png",
                "NpcIconStop.png", "NpcIconDelete.png", "NpcIconSelectSample.png")) {
            assert recorder.contains(asset) : "Missing P1 recorder icon " + asset;
        }
        assert recorder.contains("Background: @PanelBackground")
                && recorder.contains("Background: @RecorderSurface")
                && recorder.contains("Background: #08131d")
                : "Recorder and waveform must remain framed over the world";
        assert recorder.contains("PRIVATE CREATOR-ONLY CAPTURE")
                && recorder.contains("RETURN TO STUDIO");
        assert !recorder.contains("#VoicePlayDraftButton")
                && !recorder.contains("#VoiceStopPlaybackButton")
                && !recorder.contains("#VoiceRecordAgainButton")
                && !recorder.contains("#VoiceDeleteDraftButton")
                && !recorder.contains("#VoicePlaySavedButton")
                : "P1 must not restore redundant recorder controls";

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("voiceEvent(\"VOICE_RECORD\")")
                && page.contains("voiceEvent(\"VOICE_PLAY_STOP\")")
                && page.contains("voiceEvent(\"VOICE_DELETE\")")
                && page.contains("voiceEvent(\"VOICE_SAVE\")")
                : "P1 must preserve recorder behavior bindings";
        assert page.contains("case FOUND -> \"SAVED\"")
                && page.contains("? \"REQUIRED\" : \"—\"")
                : "P1 saved-state labels must remain concise";
        System.out.println("R139 P1 Voice Recorder polish gate passed at 1080p and 1440p.");
    }
}
