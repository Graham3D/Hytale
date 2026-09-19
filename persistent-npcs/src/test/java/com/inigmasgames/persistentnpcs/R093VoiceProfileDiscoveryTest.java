package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePreset;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceSampleType;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regression for deterministic seven-sample discovery and conditioning identity. */
public final class R093VoiceProfileDiscoveryTest {
    private R093VoiceProfileDiscoveryTest() { }

    public static void main(String[] args) throws Exception {
        filenamesAndAutomaticDiscovery();
        emotionResolutionAndFallback();
        readinessIsolationAndRescan();
        replacementAndRenameInvalidateDeterministically();
        legacyAssetsMigrateWithoutDestruction();
        cacheAndCanonicalSpeechContractsRemainOwnedByExistingPipeline();
        System.out.println("R093 deterministic NPC voice-profile discovery tests passed.");
    }

    private static void filenamesAndAutomaticDiscovery() throws Exception {
        Path data = Files.createTempDirectory("immersive-r093-discovery");
        Path directory = data.resolve("profiles/Jonalith");
        Files.createDirectories(directory);
        for (VoiceSampleType type : VoiceSampleType.values()) {
            String expected = "jonalith-" + type.filenameToken() + ".wav";
            assert VoicePresetRepository.expectedFilename("Jonalith", type).equals(expected);
            createWave(directory.resolve(expected), 6.0 + type.ordinal() / 10.0);
        }
        VoicePresetRepository.VoiceSampleScan scan =
                new VoicePresetRepository(data).scan("Jonalith");
        assert scan.ready();
        assert scan.samples().size() == 7;
        assert scan.samples().values().stream().allMatch(
                VoicePresetRepository.VoiceSampleStatus::present);
        assert scan.migrations().isEmpty();
        assert VoicePresetRepository.voiceFileStem("Lady Élanor Vale").equals(
                "lady-elanor-vale");
    }

    private static void emotionResolutionAndFallback() throws Exception {
        Fixture fixture = fixture("Jonalith");
        Map<VocalEmotion, VoiceSampleType> expected = Map.of(
                VocalEmotion.AFFECTIONATE, VoiceSampleType.AFFECTIONATE,
                VocalEmotion.AMUSED, VoiceSampleType.AMUSED,
                VocalEmotion.EXCITED, VoiceSampleType.EXCITED,
                VocalEmotion.ANGRY, VoiceSampleType.ANGRY,
                VocalEmotion.SAD, VoiceSampleType.SAD,
                VocalEmotion.SCARED, VoiceSampleType.SCARED);
        assert selected(fixture, VocalEmotion.CALM).equals("jonalith-reference.wav");
        for (var entry : expected.entrySet()) {
            assert selected(fixture, entry.getKey()).equals(
                    "jonalith-" + entry.getValue().filenameToken() + ".wav");
        }
        Files.delete(fixture.directory.resolve("jonalith-angry.wav"));
        var fallback = fixture.voice.plan(fixture.profile,
                VocalState.forEmotion(VocalEmotion.ANGRY));
        assert fallback.referenceAudio().orElseThrow().getFileName().toString()
                .equals("jonalith-reference.wav");
        assert fallback.requestedSampleType() == VoiceSampleType.ANGRY;
        assert fallback.resolvedSampleType() == VoiceSampleType.REFERENCE;
    }

    private static void readinessIsolationAndRescan() throws Exception {
        Path data = Files.createTempDirectory("immersive-r093-readiness");
        Path mara = data.resolve("profiles/Mara");
        Path other = data.resolve("profiles/Jonalith");
        Files.createDirectories(mara);
        Files.createDirectories(other);
        createWave(other.resolve("jonalith-reference.wav"), 6.0);
        VoicePresetRepository presets = new VoicePresetRepository(data);
        assert !presets.scan("Mara").ready()
                : "An NPC must never resolve another NPC's exact-name WAV";
        createWave(mara.resolve("mara-reference.wav"), 6.0);
        assert presets.scan("Mara").ready();
        Files.delete(mara.resolve("mara-reference.wav"));
        assert !presets.scan("Mara").ready();

        createWave(mara.resolve("mara-sad.wav"), 6.0);
        VoicePreset preset = new VoicePreset("mara", null, "mara-reference.wav",
                VocalEmotion.CALM, null, null, 0.0).normalized();
        NpcProfile profile = profile("Mara", "mara");
        boolean failedExplicitly = false;
        try {
            presets.resolve(profile);
            new NpcVoiceService(presets, new ChatterboxPerformanceController(), ignored -> { })
                    .plan(profile, VocalState.forEmotion(VocalEmotion.SAD));
        } catch (IllegalStateException expected) {
            failedExplicitly = expected.getMessage().contains("voice is not ready");
        }
        assert failedExplicitly : "Missing Reference must be an explicit readiness failure";
        assert preset.referenceAudioPath().equals("mara-reference.wav");
    }

    private static void replacementAndRenameInvalidateDeterministically() throws Exception {
        Fixture fixture = fixture("Jonalith");
        String before = fixture.voice.plan(fixture.profile,
                VocalState.forEmotion(VocalEmotion.SAD)).voiceRevision();
        Thread.sleep(5);
        createWave(fixture.directory.resolve("jonalith-sad.wav"), 7.25);
        String after = fixture.voice.plan(fixture.profile,
                VocalState.forEmotion(VocalEmotion.SAD)).voiceRevision();
        assert !before.equals(after) : "Replacing a WAV must change its conditioning revision";

        Path renamed = fixture.data.resolve("profiles/Jonalith Smith");
        Files.createDirectories(renamed);
        byte[] protectedFile = new byte[] { 7, 7, 7 };
        Files.write(renamed.resolve("jonalith-smith-angry.wav"), protectedFile);
        int copied = fixture.presets.migrateManagedVoiceFiles("Jonalith", "Jonalith Smith");
        assert copied == 6 : "One pre-existing target must remain untouched";
        assert Files.readAllBytes(renamed.resolve("jonalith-smith-angry.wav"))[0] == 7;
        assert Files.isRegularFile(fixture.directory.resolve("jonalith-reference.wav"))
                : "Rename migration must preserve old voice assets";
        assert fixture.presets.scan("Jonalith Smith").ready();
    }

    private static void legacyAssetsMigrateWithoutDestruction() throws Exception {
        Path data = Files.createTempDirectory("immersive-r093-legacy");
        Path directory = data.resolve("profiles/Mara");
        Files.createDirectories(directory);
        createWave(directory.resolve("reference.wav"), 6.0);
        createWave(directory.resolve("sample-tender.wav"), 6.0);
        createWave(directory.resolve("sample-uneasy.wav"), 6.0);
        VoicePresetRepository.VoiceSampleScan scan = new VoicePresetRepository(data).scan("Mara");
        assert scan.ready();
        assert Files.isRegularFile(directory.resolve("mara-reference.wav"));
        assert Files.isRegularFile(directory.resolve("mara-affectionate.wav"));
        assert Files.isRegularFile(directory.resolve("mara-scared.wav"));
        assert Files.isRegularFile(directory.resolve("reference.wav"));
        assert Files.isRegularFile(directory.resolve("sample-tender.wav"));
        assert scan.migrations().size() == 3;
        assert scan.samples().get(VoiceSampleType.AMUSED).state()
                == VoicePresetRepository.SampleState.MISSING;
    }

    private static void cacheAndCanonicalSpeechContractsRemainOwnedByExistingPipeline()
            throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisSpeechCoordinator.java"));
        String ledger = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/CanonicalSpeechLedger.java"));
        assert worker.contains("profile_stable_identity");
        assert worker.contains("resolved.name.lower()");
        assert worker.contains("sha256_file(resolved)");
        assert ledger.contains("canonical segments must be ordered");
        assert ledger.contains("discardUndelivered");
        assert coordinator.contains("chunk.index() == 0");
    }

    private static Fixture fixture(String name) throws Exception {
        Path data = Files.createTempDirectory("immersive-r093-fixture");
        Path directory = data.resolve("profiles").resolve(name);
        Files.createDirectories(directory);
        for (VoiceSampleType type : VoiceSampleType.values()) {
            createWave(directory.resolve(VoicePresetRepository.expectedFilename(name, type)),
                    6.0 + type.ordinal() / 10.0);
        }
        NpcProfile profile = profile(name, VoicePresetRepository.voiceFileStem(name));
        VoicePresetRepository presets = new VoicePresetRepository(data);
        presets.resolve(profile);
        NpcVoiceService voice = new NpcVoiceService(presets,
                new ChatterboxPerformanceController(), ignored -> { });
        return new Fixture(data, directory, profile, presets, voice);
    }

    private static NpcProfile profile(String name, String preset) {
        UUID id = UUID.randomUUID();
        return new NpcProfile(id, name, "Villager", "Direct", "Biography", "Purpose",
                "home", "work", List.of(), List.of(), List.of(), List.of(), 0,
                1, name, "ADULT", "Natural", List.of(), List.of(), name, id,
                "HUMAN", List.of(), List.of(), List.of(), List.of(), preset, "none",
                "GENERIC", 0.3, 0.5, 0.5, 0.4).validated();
    }

    private static String selected(Fixture fixture, VocalEmotion emotion) {
        return fixture.voice.plan(fixture.profile, VocalState.forEmotion(emotion))
                .referenceAudio().orElseThrow().getFileName().toString();
    }

    private static void createWave(Path path, double seconds) throws Exception {
        Files.createDirectories(path.getParent());
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                8_000, 16, 1, true, false);
        int frames = (int) Math.round(8_000 * seconds);
        byte[] silence = new byte[frames * 2];
        try (var input = new javax.sound.sampled.AudioInputStream(
                new ByteArrayInputStream(silence), format, frames)) {
            javax.sound.sampled.AudioSystem.write(input,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    private record Fixture(Path data, Path directory, NpcProfile profile,
            VoicePresetRepository presets, NpcVoiceService voice) { }
}
