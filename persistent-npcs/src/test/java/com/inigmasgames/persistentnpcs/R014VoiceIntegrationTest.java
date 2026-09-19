package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.modules.voice.ClipPlayback;
import com.hypixel.hytale.server.core.modules.voice.PlayerVoiceFrame;
import com.hypixel.hytale.server.core.modules.voice.PlayerVoiceInterceptor;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoiceSpeaker;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalIntensity;
import com.inigmasgames.persistentnpcs.voice.VocalPace;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class R014VoiceIntegrationTest {
    public static void main(String[] args) throws Exception {
        Path save = Files.createTempDirectory("persistent-npcs-r014");
        Path data = save.resolve("mods/ImmersiveNPCs");
        Files.createDirectories(data);
        NpcProfile mara = new ProfileRepository(data).loadTestProfile();
        VoicePresetRepository presets = new VoicePresetRepository(data);
        presets.loadMaraPreset();
        Path maraVoices = save.resolve("exports/voices/Mara");
        createWave(maraVoices.resolve("mara-reference.wav"));
        createWave(maraVoices.resolve("mara-affectionate.wav"));
        createWave(maraVoices.resolve("mara-excited.wav"));
        createWave(maraVoices.resolve("mara-scared.wav"));

        NpcVoiceService voice = new NpcVoiceService(presets,
                new ChatterboxPerformanceController(), ignored -> { });
        assert "mara-reference.wav".equals(file(voice, mara, VocalEmotion.CALM));
        assert "mara-reference.wav".equals(file(voice, mara, VocalEmotion.CURIOUS));
        assert "mara-excited.wav".equals(file(voice, mara, VocalEmotion.EXCITED));
        assert "mara-scared.wav".equals(file(voice, mara, VocalEmotion.UNEASY));
        assert "mara-affectionate.wav".equals(file(voice, mara, VocalEmotion.FRIENDLY));
        Files.delete(maraVoices.resolve("mara-excited.wav"));
        assert "mara-reference.wav".equals(file(voice, mara, VocalEmotion.EXCITED));

        List<String> phrases = new ArrayList<>();
        SpeechPhraseChunker chunker = new SpeechPhraseChunker(
                (phrase, state) -> phrases.add(phrase));
        VocalState calm = new VocalState(
                VocalEmotion.CALM, VocalIntensity.LOW, VocalPace.NORMAL);
        chunker.accept("Hello there. I was ", calm);
        chunker.accept("wondering how you are?", calm);
        chunker.complete("", calm);
        assert phrases.equals(List.of("Hello there.", "I was wondering how you are?"));
        assert String.join(" ", phrases).equals(
                "Hello there. I was wondering how you are?");

        assert VoiceModule.class.getMethod("openEntityVoice",
                com.hypixel.hytale.component.Ref.class) != null;
        assert VoiceModule.class.getMethod("addPlayerVoiceInterceptor",
                PlayerVoiceInterceptor.class) != null;
        assert VoiceSpeaker.class.getMethod("play", List.class) != null;
        assert ClipPlayback.class.getMethod("completion") != null;
        assert PlayerVoiceFrame.class.getMethod("opus") != null;
        assert VoiceSpeaker.MAX_OPUS_FRAME_BYTES == 512;

        String worker = Files.readString(
                save.resolve("exports/voices/immersive_voice_worker.py"));
        assert worker.contains("ChatterboxTurboTTS.from_pretrained");
        assert worker.contains("AudioFrame.from_ndarray");
        assert worker.contains("frame.opus") == false;
        assert !worker.contains("ChatterboxTTS.from_pretrained");
        System.out.println("R014 Update 6 voice integration tests passed.");
    }

    private static String file(
            NpcVoiceService voice, NpcProfile mara, VocalEmotion emotion) {
        return voice.plan(mara, new VocalState(
                emotion, VocalIntensity.LOW, VocalPace.NORMAL))
                .referenceAudio().orElseThrow().getFileName().toString();
    }

    private static void createWave(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                8_000, 16, 1, true, false);
        byte[] silence = new byte[8_000 * 2 * 6];
        try (var input = new javax.sound.sampled.AudioInputStream(
                new ByteArrayInputStream(silence), format, silence.length / 2)) {
            javax.sound.sampled.AudioSystem.write(input,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
