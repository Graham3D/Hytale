package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.packets.voice.VoiceInputMode;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;
import com.inigmasgames.persistentnpcs.voice.VoiceClientCaptureContract;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRecorderControlPolicy;
import com.inigmasgames.persistentnpcs.voice.VoiceSampleType;
import com.inigmasgames.persistentnpcs.voice.VoiceWaveformPresentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Deterministic A6 hotfix gate: compact controls, waveform, and appearance lifecycle. */
public final class R136NpcAuthoringStudioA6RepairTest {
    private R136NpcAuthoringStudioA6RepairTest() { }

    public static void main(String[] args) throws Exception {
        System.out.println("R136 stage=client-capture-contract");
        var openMic = new VoiceClientCaptureContract(VoiceInputMode.VoiceActivity);
        var ptt = new VoiceClientCaptureContract(VoiceInputMode.PushToTalk);
        assert openMic.speakWithoutPushToTalk() && !openMic.serverActivationSupported();
        assert !ptt.speakWithoutPushToTalk() && !ptt.serverActivationSupported();
        assert ptt.display().contains("does not let the server activate capture");
        String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        assert page.contains("PlayerSettings::voiceSettings")
                && !page.contains("putComponent(ref, PlayerSettings.getComponentType()")
                : "Recorder may inspect, but never mutate, client voice settings";

        System.out.println("R136 stage=waveform-presentation-and-stale-rejection");
        List<Integer> silence = VoiceWaveformPresentation.heights(List.of());
        List<Integer> speech = VoiceWaveformPresentation.heights(
                List.of(0.0, 0.1, 0.4, 1.0, 0.2, 0.0));
        assert silence.size() == 32 && silence.stream().allMatch(height -> height == 2);
        assert speech.size() == 32 && speech.stream().mapToInt(Integer::intValue).max()
                .orElseThrow() == 116;
        assert NpcVoiceRecordingService.savedAnalysisIsCurrent(7, 7,
                VoiceSampleType.REFERENCE, VoiceSampleType.REFERENCE, true, "A", "A");
        assert !NpcVoiceRecordingService.savedAnalysisIsCurrent(8, 7,
                VoiceSampleType.REFERENCE, VoiceSampleType.REFERENCE, true, "A", "A");
        assert !NpcVoiceRecordingService.savedAnalysisIsCurrent(7, 7,
                VoiceSampleType.AMUSED, VoiceSampleType.REFERENCE, true, "A", "A");
        assert !NpcVoiceRecordingService.savedAnalysisIsCurrent(7, 7,
                VoiceSampleType.REFERENCE, VoiceSampleType.REFERENCE, false, "A", "A");

        System.out.println("R136 stage=compact-control-state-machine");
        var idleSaved = snapshot(NpcVoiceRecordingService.State.IDLE, false,
                VoicePresetRepository.SampleState.FOUND);
        var readyDraft = snapshot(NpcVoiceRecordingService.State.READY, true,
                VoicePresetRepository.SampleState.MISSING);
        var recording = snapshot(NpcVoiceRecordingService.State.RECORDING, false,
                VoicePresetRepository.SampleState.MISSING);
        var playing = snapshot(NpcVoiceRecordingService.State.PLAYING, false,
                VoicePresetRepository.SampleState.FOUND);
        assert VoiceRecorderControlPolicy.forSnapshot(idleSaved).playMode()
                == VoiceRecorderControlPolicy.PlayMode.PLAY;
        assert VoiceRecorderControlPolicy.forSnapshot(readyDraft).deleteMode()
                == VoiceRecorderControlPolicy.DeleteMode.DRAFT;
        assert !VoiceRecorderControlPolicy.forSnapshot(readyDraft).saveDisabled();
        assert VoiceRecorderControlPolicy.forSnapshot(recording).playMode()
                == VoiceRecorderControlPolicy.PlayMode.STOP;
        assert !VoiceRecorderControlPolicy.forSnapshot(recording).playDisabled();
        assert VoiceRecorderControlPolicy.forSnapshot(playing).playMode()
                == VoiceRecorderControlPolicy.PlayMode.STOP;

        System.out.println("R136 stage=default-and-malformed-appearance-lifecycle");
        Path root = Files.createTempDirectory("r136-appearance-");
        try {
            AppearanceRepository repository = new AppearanceRepository(
                    root.resolve("mods").resolve("ImmersiveNPCs"), ignored -> { });
            NpcSkinCodecAdapter adapter = fakeSkinAdapter();
            new ProfileRepository(root.resolve("mods").resolve("ImmersiveNPCs"))
                    .createTemplate("Hoit");
            var created = repository.materializeDefaultIfMissing("Hoit", adapter);
            assert created.state() == AppearanceRepository.AppearanceState.DEFAULT_MATERIALIZED;
            assert Files.isRegularFile(created.path());
            byte[] valid = Files.readAllBytes(created.path());
            AppearanceRepository restarted = new AppearanceRepository(
                    root.resolve("mods").resolve("ImmersiveNPCs"), ignored -> { });
            var reopened = restarted.materializeDefaultIfMissing("Hoit", adapter);
            assert reopened.state() == AppearanceRepository.AppearanceState.EXISTING_VALID;
            assert java.util.Arrays.equals(valid, Files.readAllBytes(created.path()));
            Files.writeString(created.path(), "{ malformed authored appearance");
            byte[] malformed = Files.readAllBytes(created.path());
            var degraded = repository.materializeDefaultIfMissing("Hoit", adapter);
            assert degraded.state() == AppearanceRepository.AppearanceState.MALFORMED_PRESERVED;
            assert java.util.Arrays.equals(malformed, Files.readAllBytes(created.path()))
                    : "Malformed authored appearance must not be overwritten at open";
        } finally {
            deleteTree(root);
        }

        System.out.println("R136 stage=packaged-ui-assets-and-privacy");
        String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        for (String selector : List.of("#VoiceRecordButton", "#VoicePlayStopButton",
                "#VoiceDeleteButton", "#VoiceSaveButton", "#VoiceWaveformBar0",
                "#VoiceWaveformBar31", "#VoiceSelectedREFERENCE")) {
            assert ui.contains(selector) : "Missing compact recorder selector " + selector;
        }
        assert !ui.contains("#VoicePlayDraftButton") && !ui.contains("#VoiceRecordAgainButton")
                && !ui.contains("#VoiceWaveformText");
        for (String asset : List.of("NpcIconRecord.png", "NpcIconPlay.png",
                "NpcIconStop.png", "NpcIconDelete.png", "NpcIconSelectSample.png",
                "WeaponSlotIconShield@2x.png", "WeaponSlotIconSword@2x.png")) {
            assert Files.size(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                    + "ImmersiveNpcInventory", asset)) > 0 : "Missing packaged asset " + asset;
        }
        String worker = source("src/main/resources/tools/immersive_voice_worker.py");
        String analyze = worker.substring(worker.indexOf("def analyze_saved_wav"),
                worker.indexOf("def invalidate_conditioning"));
        assert analyze.contains("waveformBuckets") && analyze.contains("AudioResampler")
                && !analyze.contains("transcribe(") && !analyze.contains("warm_tts")
                : "Saved waveform analysis must remain bounded and model-free";
        String recorder = source("src/main/java/com/inigmasgames/persistentnpcs/voice/"
                + "NpcVoiceRecordingService.java");
        assert recorder.contains("frame.drop()") && !recorder.contains(".transcribe(")
                && recorder.contains("WAVEFORM_STALE_REJECTED");
        String command = source("src/main/java/com/inigmasgames/persistentnpcs/command/"
                + "AbstractImmersiveNpcProfileCommand.java");
        assert command.contains("studioOpenContinues=true")
                && command.contains("previewAppearanceForStudio")
                && !command.contains("No valid NPC appearance is available for preview")
                : "Preview must be a Studio consumer, not its open prerequisite";
        System.out.println("R136 bounded A6 connected-validation repair gate passed.");
    }

    private static NpcVoiceRecordingService.Snapshot snapshot(
            NpcVoiceRecordingService.State state, boolean draft,
            VoicePresetRepository.SampleState saved) {
        return new NpcVoiceRecordingService.Snapshot(state, VoiceSampleType.REFERENCE, 1,
                0, 30_000, "", false, List.of(), 0, -120, -120, 0, 0, 0, 0,
                Map.of(VoiceSampleType.REFERENCE, saved), saved ==
                        VoicePresetRepository.SampleState.FOUND, draft, "test");
    }

    private static NpcSkinCodecAdapter fakeSkinAdapter() {
        return new NpcSkinCodecAdapter(new NpcSkinCodecAdapter.RuntimeApi() {
            @Override public PlayerSkin parse(String json) {
                var value = JsonParser.parseString(json).getAsJsonObject();
                PlayerSkin skin = new PlayerSkin();
                skin.bodyCharacteristic = text(value, "bodyCharacteristic");
                skin.face = text(value, "face");
                skin.ears = text(value, "ears");
                skin.eyes = text(value, "eyes");
                skin.mouth = text(value, "mouth");
                skin.underwear = text(value, "underwear");
                return skin;
            }
            @Override public void validate(PlayerSkin skin) {
                if (skin.bodyCharacteristic == null || skin.face == null || skin.ears == null
                        || skin.eyes == null || skin.mouth == null) {
                    throw new IllegalArgumentException("required neutral appearance field missing");
                }
            }
            @Override public com.hypixel.hytale.server.core.asset.type.model.config.Model
                    createModel(PlayerSkin skin) { return null; }
            private String text(com.google.gson.JsonObject object, String key) {
                return object.has(key) ? object.get(key).getAsString() : null;
            }
        });
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
