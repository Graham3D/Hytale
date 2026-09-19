package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class R020VoiceLatencyTest {
    private R020VoiceLatencyTest() { }

    public static void main(String[] args) throws Exception {
        Path save = Files.createTempDirectory("persistent-npcs-r020-");
        Path data = save.resolve("mods/ImmersiveNPCs");
        var config = new VoiceRuntimeConfigRepository(data).load();
        assert config.effectiveUtteranceGapMillis() == 250;
        assert "AUTO".equals(config.effectiveSttProvider());
        assert "TINY_STREAMING".equals(config.effectiveMoonshineModel());

        var preset = new VoicePresetRepository(data).loadMaraPreset();
        assert preset.outputGainDb() == 4.0;
        String worker = Files.readString(save.resolve("exports/voices/immersive_voice_worker.py"));
        assert worker.contains("stt_stream_start");
        assert worker.contains("voice_conditionals");
        assert worker.contains("PCM_LEVELS preOpus");

        List<String> phrases = new ArrayList<>();
        SpeechPhraseChunker chunker = new SpeechPhraseChunker(
                (phrase, state) -> phrases.add(phrase));
        chunker.accept("Hello, Graham.", VocalState.infer("Hello, Graham."));
        assert phrases.isEmpty();
        chunker.complete("", VocalState.infer("Hello, Graham."));
        assert phrases.equals(List.of("Hello, Graham."));
        System.out.println("R020 targeted voice latency tests passed.");
    }
}
