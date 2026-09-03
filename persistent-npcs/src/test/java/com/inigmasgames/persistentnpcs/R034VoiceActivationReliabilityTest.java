package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Regression coverage for the R034.1 intermittent voice activation fix. */
public final class R034VoiceActivationReliabilityTest {
    private R034VoiceActivationReliabilityTest() { }

    public static void main(String[] args) throws Exception {
        transcriptionHasAWorkerIndependentFromSynthesis();
        operatorTracesCaptureThePreCognitionVoiceLifecycle();
        System.out.println("R034.1 voice activation reliability tests passed.");
    }

    private static void transcriptionHasAWorkerIndependentFromSynthesis() throws Exception {
        String pipeline = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/orbis/OrbisSpeechCoordinator.java"));
        String coordinator = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        String bridge = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/voice/TurboVoiceWorker.java"));
        String localStt = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/voice/LocalWorkerSpeechToTextProvider.java"));
        String localTts = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/voice/LocalWorkerTextToSpeechProvider.java"));
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));

        assert pipeline.contains("TextToSpeechProvider tts");
        assert pipeline.contains("tts.synthesize(");
        assert coordinator.contains("SpeechToTextProvider stt");
        assert coordinator.contains("stt.transcribe(");
        assert localTts.contains("WorkerRole.TTS");
        assert localStt.contains("WorkerRole.STT");
        assert bridge.contains("--worker-role");
        assert worker.contains("self.tts_enabled = self.worker_role in");
        assert worker.contains("self.stt_enabled = self.worker_role in");
        assert worker.contains("transcription is unavailable on the TTS worker");
    }

    private static void operatorTracesCaptureThePreCognitionVoiceLifecycle()
            throws Exception {
        Path root = Files.createTempDirectory("r0341-voice-trace-");
        NpcTraceManager manager = new NpcTraceManager(new ProfileRepository(root),
                Clock.fixed(Instant.parse("2026-08-29T13:00:00Z"), ZoneOffset.UTC),
                ignored -> { });
        UUID operator = UUID.randomUUID();
        UUID otherOperator = UUID.randomUUID();
        NpcProfile lycander = profile("Lycander");
        NpcProfile mara = profile("Mara");
        Path lycanderTrace = manager.toggle(operator, lycander).path();
        Path maraTrace = manager.toggle(operator, mara).path();
        Path unrelatedTrace = manager.toggle(otherOperator, mara).path();

        UUID utteranceId = UUID.randomUUID();
        JsonObject started = voiceEvent("VOICE_CAPTURE_STARTED", utteranceId);
        manager.recordOperator(operator, started);
        JsonObject completed = voiceEvent("VOICE_TRANSCRIPTION_COMPLETED", utteranceId);
        completed.addProperty("transcript", "Can either of you hear me?");
        manager.recordOperator(operator, completed);
        manager.awaitIdle();

        assert count(lycanderTrace, "VOICE_CAPTURE_STARTED") == 1;
        assert count(maraTrace, "VOICE_CAPTURE_STARTED") == 1;
        assert count(lycanderTrace, "VOICE_TRANSCRIPTION_COMPLETED") == 1;
        assert count(maraTrace, "VOICE_TRANSCRIPTION_COMPLETED") == 1;
        assert count(unrelatedTrace, "VOICE_CAPTURE_STARTED") == 0;
        assert Files.readString(lycanderTrace).contains(utteranceId.toString());

        manager.toggle(operator, lycander);
        manager.recordOperator(operator,
                voiceEvent("VOICE_TRANSCRIPTION_FAILED", UUID.randomUUID()));
        manager.awaitIdle();
        assert count(lycanderTrace, "VOICE_TRANSCRIPTION_FAILED") == 0;
        assert count(maraTrace, "VOICE_TRANSCRIPTION_FAILED") == 1;
        manager.close();
    }

    private static long count(Path path, String event) throws Exception {
        return Files.readAllLines(path).stream()
                .filter(line -> line.contains("\"event\":\"" + event + "\""))
                .count();
    }

    private static JsonObject voiceEvent(String type, UUID utteranceId) {
        JsonObject event = new JsonObject();
        event.addProperty("event", type);
        event.addProperty("responseId", "");
        event.addProperty("utteranceId", utteranceId.toString());
        return event;
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                "A grounded authored NPC.", "Live a grounded life.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }
}
