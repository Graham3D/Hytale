package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.ChatterboxControls;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalIntensity;
import com.inigmasgames.persistentnpcs.voice.VocalPace;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePreset;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceProvider;
import com.inigmasgames.persistentnpcs.voice.VoiceRenderPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

public final class R013MaraVoiceTest {
    public static void main(String[] args) throws Exception {
        Path save = Files.createTempDirectory("persistent-npcs-r013");
        Path data = save.resolve("mods").resolve("ImmersiveNPCs");
        NpcProfile mara = new ProfileRepository(data).loadTestProfile();
        assert "mara".equals(mara.voicePreset());
        assert "none".equals(mara.voiceEffectPreset());

        VoicePresetRepository repository = new VoicePresetRepository(data);
        VoicePreset preset = repository.loadMaraPreset();
        assert preset.provider() == VoiceProvider.CHATTERBOX;
        assert "reference.wav".equals(preset.referenceAudioPath());
        assert repository.referenceAudio(preset).isEmpty();
        assert Files.isRegularFile(save.resolve("exports/voices/Mara/preset.json"));
        assert Files.isRegularFile(save.resolve("exports/voices/immersive_voice_worker.py"));
        createWave(save.resolve("exports/voices/Mara/mara-reference.wav"));

        NpcVoiceService voice = new NpcVoiceService(repository,
                new ChatterboxPerformanceController(), ignored -> { });
        VoiceRenderPlan calm = voice.plan(mara,
                new VocalState(VocalEmotion.CALM, VocalIntensity.LOW, VocalPace.NORMAL));
        VoiceRenderPlan curious = independentPlan(repository, mara, VocalEmotion.CURIOUS,
                VocalIntensity.MEDIUM, VocalPace.NORMAL);
        VoiceRenderPlan excited = independentPlan(repository, mara, VocalEmotion.EXCITED,
                VocalIntensity.HIGH, VocalPace.FAST);
        VoiceRenderPlan uneasy = independentPlan(repository, mara, VocalEmotion.UNEASY,
                VocalIntensity.MEDIUM, VocalPace.NORMAL);
        assert !calm.usingTemporaryProviderVoice();
        assert calm.chatterboxControls().exaggeration()
                < curious.chatterboxControls().exaggeration();
        assert curious.chatterboxControls().exaggeration()
                < uneasy.chatterboxControls().exaggeration();
        assert uneasy.chatterboxControls().exaggeration()
                < excited.chatterboxControls().exaggeration();
        assert excited.chatterboxControls().cfgWeight()
                < calm.chatterboxControls().cfgWeight();

        ChatterboxPerformanceController smoothing = new ChatterboxPerformanceController();
        ChatterboxControls first = smoothing.controls(mara.id(), preset,
                new VocalState(VocalEmotion.CALM, VocalIntensity.LOW, VocalPace.NORMAL));
        ChatterboxControls second = smoothing.controls(mara.id(), preset,
                new VocalState(VocalEmotion.EXCITED, VocalIntensity.HIGH, VocalPace.FAST));
        ChatterboxControls third = smoothing.controls(mara.id(), preset,
                new VocalState(VocalEmotion.EXCITED, VocalIntensity.HIGH, VocalPace.FAST));
        assert second.exaggeration() > first.exaggeration();
        assert second.exaggeration() - first.exaggeration() <= 0.1001;
        assert third.exaggeration() > second.exaggeration();
        assert third.exaggeration() - second.exaggeration() <= 0.1001;

        VoicePreset afterReload = new VoicePresetRepository(data).resolve(mara);
        assert afterReload.equals(preset);
        NpcProfile routedDifferently = copyWithModelTier(mara, "DEEP");
        assert repository.resolve(routedDifferently).equals(preset);
        assert voice.plan(routedDifferently, VocalState.infer("curious"))
                .voicePresetId().equals("mara");
        System.out.println("R013 Mara voice preset and modulation tests passed.");
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

    private static VoiceRenderPlan independentPlan(
            VoicePresetRepository repository, NpcProfile profile, VocalEmotion emotion,
            VocalIntensity intensity, VocalPace pace) {
        NpcProfile separateIdentity = copyWithId(profile, UUID.randomUUID());
        return new NpcVoiceService(repository, new ChatterboxPerformanceController(),
                ignored -> { }).plan(separateIdentity, new VocalState(emotion, intensity, pace));
    }

    private static NpcProfile copyWithModelTier(NpcProfile profile, String tier) {
        return fullCopy(profile, profile.id(), tier);
    }

    private static NpcProfile copyWithId(NpcProfile profile, UUID id) {
        return fullCopy(profile, id, profile.modelTier());
    }

    private static NpcProfile fullCopy(NpcProfile p, UUID id, String tier) {
        return new NpcProfile(id, p.name(), p.role(), p.personality(), p.biography(),
                p.purpose(), p.home(), p.workplace(), p.likes(), p.dislikes(), p.roleIds(),
                p.capabilities(), p.defaultDisposition(), p.schemaVersion(), p.selfIdentity(),
                p.ageCategory(), p.speakingStyle(), p.knowledgeDomains(), p.defaultSchedule(),
                p.appearancePreset(), id, p.speciesArchetype(), p.personalityTraits(),
                p.values(), p.fears(), p.goals(), p.voicePreset(), p.voiceEffectPreset(), tier,
                p.riskTolerance(), p.sociability(), p.curiosity(), p.trustDisposition())
                .validated();
    }
}
