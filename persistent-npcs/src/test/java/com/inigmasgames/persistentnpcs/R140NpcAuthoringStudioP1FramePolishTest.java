package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** P1 second-pass gate: decorated frame, quiet copy, and one bounded waveform canvas. */
public final class R140NpcAuthoringStudioP1FramePolishTest {
    private R140NpcAuthoringStudioP1FramePolishTest() { }

    public static void main(String[] args) throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui"));
        int start = ui.indexOf("$C.@PageOverlay #VoiceRecorderPage");
        int end = ui.indexOf("$C.@PageOverlay #VoiceDeleteConfirmPage", start);
        assert start >= 0 && end > start : "Voice Recorder UI block was not found";
        String recorder = ui.substring(start, end);

        assert recorder.contains("$C.@DecoratedContainer")
                && recorder.contains("$C.@Title #VoiceRecorderTitle")
                && recorder.contains("#Content {")
                : "Recorder must use the proven Hytale decorated-window hierarchy";
        assert recorder.contains("Background: #09141e")
                && recorder.contains("Background: @RecorderSurface")
                : "Recorder content must be separated from the world by opaque dark surfaces";

        int viewport = recorder.indexOf("Anchor: (Width: 468, Height: 124)");
        int controls = recorder.indexOf("#VoiceRecordButton", viewport);
        assert viewport >= 0 && controls > viewport : "Bounded waveform viewport is missing";
        String waveform = recorder.substring(viewport, controls);
        assert waveform.contains("Anchor: (Width: 448, Height: 2, Top: 57)")
                && waveform.contains("Background: @RecorderBaseline")
                : "Waveform center line must be centered and light blue";
        assert waveform.contains("Anchor: (Width: 448, Height: 116); LayoutMode: Left")
                && waveform.contains("#VoiceWaveformBar0")
                && waveform.contains("#VoiceWaveformBar31")
                : "All waveform buckets must share the fixed inner canvas";

        assert recorder.contains("Format: WAV   •   16bit   •   48kHz")
                : "Approved recorder format footer is missing";
        for (String selector : new String[] {"#VoiceRecorderMeta", "#VoiceQualityMetrics",
                "#VoiceRecorderStatus", "#VoiceProfileReadiness"}) {
            int selectorIndex = recorder.indexOf(selector);
            assert selectorIndex >= 0 : "Server update selector was removed: " + selector;
            String declaration = recorder.substring(selectorIndex,
                    Math.min(recorder.length(), selectorIndex + 150));
            assert declaration.contains("Visible: false")
                    : "Superfluous recorder copy must remain hidden: " + selector;
        }
        for (String selector : new String[] {"#VoiceRecordButton", "#VoicePlayStopButton",
                "#VoiceDeleteButton", "#VoiceSaveButton"}) {
            assert recorder.contains(selector) : "Compact control missing: " + selector;
        }
        assert recorder.contains("Anchor: (Width: 520, Height: 600)")
                : "Recorder must remain safe at both required resolutions";

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("voiceEvent(\"VOICE_RECORD\")")
                && page.contains("voiceEvent(\"VOICE_PLAY_STOP\")")
                && page.contains("voiceEvent(\"VOICE_DELETE\")")
                && page.contains("voiceEvent(\"VOICE_SAVE\")")
                : "P1 second pass must not change recorder authority or event intents";
        assert page.contains("\"%02d:%02d / %02d:%02d\"")
                : "Elapsed time must use the compact mockup clock treatment";

        System.out.println("R140 P1 decorated-frame and waveform-containment gate passed.");
    }
}
