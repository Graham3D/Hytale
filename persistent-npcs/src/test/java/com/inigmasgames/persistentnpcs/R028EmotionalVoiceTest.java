package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.CommittedDialogueResponse;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.LysanderVoiceBehavior;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEventPolicy;
import com.inigmasgames.persistentnpcs.voice.TtsTextNormalizer;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class R028EmotionalVoiceTest {
    private R028EmotionalVoiceTest() { }

    public static void main(String[] args) throws Exception {
        everyEmotionSelectsItsCanonicalReference();
        invalidEmotionSamplesFallBackToReference();
        onlyOfficialEventsAreStructuredAndCooledDown();
        lysanderRemainsRestrainedAcrossEveryState();
        canonicalDialogueRemainsIndependentFromPerformanceMetadata();
        profileUiMigrationAndCacheRefreshAreWired();
        System.out.println("R028 expanded emotional voice tests passed.");
    }

    private static void everyEmotionSelectsItsCanonicalReference() throws Exception {
        Fixture fixture = fixture();
        Map<VocalEmotion, String> expected = Map.ofEntries(
                Map.entry(VocalEmotion.CALM, "lycander-reference.wav"),
                Map.entry(VocalEmotion.FRIENDLY, "lycander-affectionate.wav"),
                Map.entry(VocalEmotion.AFFECTIONATE, "lycander-affectionate.wav"),
                Map.entry(VocalEmotion.CURIOUS, "lycander-reference.wav"),
                Map.entry(VocalEmotion.EXCITED, "lycander-excited.wav"),
                Map.entry(VocalEmotion.UNEASY, "lycander-scared.wav"),
                Map.entry(VocalEmotion.ANGRY, "lycander-angry.wav"),
                Map.entry(VocalEmotion.AFRAID, "lycander-scared.wav"),
                Map.entry(VocalEmotion.SCARED, "lycander-scared.wav"),
                Map.entry(VocalEmotion.SAD, "lycander-sad.wav"),
                Map.entry(VocalEmotion.TENDER, "lycander-affectionate.wav"),
                Map.entry(VocalEmotion.AMUSED, "lycander-amused.wav"),
                Map.entry(VocalEmotion.WHISPERING, "lycander-reference.wav"));
        assert expected.size() == VocalEmotion.values().length;
        for (var entry : expected.entrySet()) {
            String selected = fixture.voice.plan(fixture.profile,
                    VocalState.forEmotion(entry.getKey())).referenceAudio()
                    .orElseThrow().getFileName().toString();
            assert selected.equals(entry.getValue()) : entry.getKey() + " -> " + selected;
        }
    }

    private static void invalidEmotionSamplesFallBackToReference() throws Exception {
        Fixture fixture = fixture();
        Files.delete(fixture.directory.resolve("lycander-angry.wav"));
        assert selected(fixture, VocalEmotion.ANGRY).equals("lycander-reference.wav");

        Files.write(fixture.directory.resolve("lycander-sad.wav"), new byte[] { 1, 2, 3 });
        assert selected(fixture, VocalEmotion.SAD).equals("lycander-reference.wav");

        createWave(fixture.directory.resolve("lycander-affectionate.wav"), 5.0);
        assert !VoicePresetRepository.validWave(
                fixture.directory.resolve("lycander-affectionate.wav"));
        assert selected(fixture, VocalEmotion.TENDER).equals("lycander-reference.wav");
    }

    private static void onlyOfficialEventsAreStructuredAndCooledDown() {
        assert Arrays.stream(ParalinguisticEvent.values()).map(ParalinguisticEvent::tag)
                .collect(Collectors.toSet()).equals(java.util.Set.of(
                        "[clear throat]", "[sigh]", "[shush]", "[cough]", "[groan]",
                        "[sniff]", "[gasp]", "[chuckle]", "[laugh]"));
        ParalinguisticEventPolicy policy = new ParalinguisticEventPolicy();
        UUID npc = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-28T12:00:00Z");
        assertEvent(policy, VocalEmotion.CALM, "Speak up and clear your throat.", start,
                ParalinguisticEvent.CLEAR_THROAT);
        assertEvent(policy, VocalEmotion.SAD, "I regret it and fear losing her.", start,
                ParalinguisticEvent.SIGH);
        assertEvent(policy, VocalEmotion.CALM, "Be quiet and hide.", start,
                ParalinguisticEvent.SHUSH);
        assertEvent(policy, VocalEmotion.CALM, "There is thick smoke in the air.", start,
                ParalinguisticEvent.COUGH);
        assertEvent(policy, VocalEmotion.CALM, "I am exhausted and in pain.", start,
                ParalinguisticEvent.GROAN);
        assertEvent(policy, VocalEmotion.SAD, "The grief brought tears.", start,
                ParalinguisticEvent.SNIFF);
        assertEvent(policy, VocalEmotion.CALM, "That was suddenly unexpected.", start,
                ParalinguisticEvent.GASP);
        assertEvent(policy, VocalEmotion.AMUSED, "That dry humor was a good joke.", start,
                ParalinguisticEvent.CHUCKLE);
        assertEvent(policy, VocalEmotion.EXCITED, "That was the best joke, hilarious.", start,
                ParalinguisticEvent.LAUGH);
        VocalState amused = policy.select(npc, VocalState.forEmotion(VocalEmotion.AMUSED),
                "That joke was funny; you pulled it off.", start);
        assert amused.paralinguisticEvent().orElseThrow()
                == ParalinguisticEvent.CHUCKLE;
        assert policy.select(npc, VocalState.forEmotion(VocalEmotion.AMUSED),
                "Another funny joke.", start.plusSeconds(60)).paralinguisticEvent().isEmpty();
        VocalState cough = policy.select(npc, VocalState.forEmotion(VocalEmotion.CALM),
                "There is thick smoke in the air.", start.plusSeconds(61));
        assert cough.paralinguisticEvent().orElseThrow() == ParalinguisticEvent.COUGH;
        assert policy.select(UUID.randomUUID(), VocalState.forEmotion(VocalEmotion.CALM),
                "An ordinary practical answer.", start).paralinguisticEvent().isEmpty();

        UUID restrained = UUID.randomUUID();
        assert policy.select(restrained, VocalState.forEmotion(VocalEmotion.SAD),
                "I regret it.", start).paralinguisticEvent().orElseThrow()
                == ParalinguisticEvent.SIGH;
        assert policy.select(restrained, VocalState.forEmotion(VocalEmotion.SAD),
                "I still regret it.", start.plusSeconds(180)).paralinguisticEvent().isEmpty();
    }

    private static void lysanderRemainsRestrainedAcrossEveryState() {
        assert LysanderVoiceBehavior.select("A normal day at the forge.", false, false, false)
                == VocalEmotion.CALM;
        assert LysanderVoiceBehavior.select("This workmanship is worth examining.", false,
                false, false) == VocalEmotion.CURIOUS;
        assert LysanderVoiceBehavior.select("This is an exceptional craftsmanship masterwork.",
                false, false, false) == VocalEmotion.EXCITED;
        assert LysanderVoiceBehavior.select("Mara is missing and I am worried.", false,
                false, false) == VocalEmotion.UNEASY;
        assert LysanderVoiceBehavior.select("I lied and betrayed your trust.", false,
                false, false) == VocalEmotion.ANGRY;
        assert LysanderVoiceBehavior.select("Do you regret your family loss?", false,
                false, false) == VocalEmotion.SAD;
        assert LysanderVoiceBehavior.select("You must be proud of Mara, your granddaughter.",
                false, false, false) == VocalEmotion.TENDER;
        assert LysanderVoiceBehavior.select("That dry humor was a good joke.", false,
                false, false) == VocalEmotion.AMUSED;
        NpcProfile profile = profile("Lycander", "lycander");
        assert LysanderVoiceBehavior.appliesTo(profile);
        assert LysanderVoiceBehavior.guidance(profile,
                VocalState.forEmotion(VocalEmotion.EXCITED)).contains("do not become broadly cheerful");
        assert LysanderVoiceBehavior.guidance(profile,
                VocalState.forEmotion(VocalEmotion.AMUSED)).contains("never exuberant");
    }

    private static void canonicalDialogueRemainsIndependentFromPerformanceMetadata() {
        UUID responseId = UUID.randomUUID();
        VocalState state = VocalState.forEmotion(VocalEmotion.SAD)
                .withEvent(ParalinguisticEvent.SIGH);
        CommittedDialogueResponse response = new CommittedDialogueResponse(responseId,
                ignored -> { });
        response.commit("She's all I have left.", state);
        assert response.text().equals("She's all I have left.");
        assert response.chunks().getFirst().text().equals("She's all I have left.");
        assert TtsTextNormalizer.performanceText(response.chunks().getFirst().text(),
                state, true).equals("[sigh] She's all I have left.");
        assert TtsTextNormalizer.performanceText("I will keep watch.", state, false)
                .equals("I will keep watch.");
    }

    private static void profileUiMigrationAndCacheRefreshAreWired() throws Exception {
        String editor = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileEditorService.java"));
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisSpeechCoordinator.java"));
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));
        String migration = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/persistence/ImmersiveNpcDataMigration.java"));
        for (String emotion : List.of("REFERENCE", "AFFECTIONATE", "AMUSED", "EXCITED",
                "ANGRY", "SAD", "SCARED")) {
            assert ui.contains("@VoiceSummaryRow #Voice" + emotion);
            assert !ui.contains("#" + emotion + "Open");
        }
        assert ui.contains("Label #VoiceFilename");
        assert ui.contains("Label #VoiceState");
        assert editor.contains("rescanVoiceSamples");
        assert pipeline.contains("chunk.index() == 0");
        assert worker.contains("st_mtime_ns");
        assert worker.contains("conditionalsCached");
        assert migration.contains("VoicePresetRepository");
        assert !pipeline.contains("[laugh]");
    }

    private static Fixture fixture() throws Exception {
        Path data = Files.createTempDirectory("immersive-r028");
        NpcProfile profile = profile("Lycander", "lycander");
        Path voiceDirectory = data.resolve("profiles/Lycander");
        Files.createDirectories(voiceDirectory);
        for (String file : List.of("lycander-reference.wav", "lycander-affectionate.wav",
                "lycander-amused.wav", "lycander-excited.wav", "lycander-angry.wav",
                "lycander-sad.wav", "lycander-scared.wav")) {
            createWave(voiceDirectory.resolve(file), 6.0);
        }
        VoicePresetRepository presets = new VoicePresetRepository(data);
        NpcVoiceService voice = new NpcVoiceService(presets,
                new ChatterboxPerformanceController(), ignored -> { });
        // Resolve once so this profile's preset id maps to its canonical profile directory.
        presets.resolve(profile);
        return new Fixture(profile, voiceDirectory, voice);
    }

    private static String selected(Fixture fixture, VocalEmotion emotion) {
        return fixture.voice.plan(fixture.profile, VocalState.forEmotion(emotion))
                .referenceAudio().orElseThrow().getFileName().toString();
    }

    private static void assertEvent(
            ParalinguisticEventPolicy policy,
            VocalEmotion emotion,
            String context,
            Instant now,
            ParalinguisticEvent expected) {
        ParalinguisticEvent selected = policy.select(UUID.randomUUID(),
                VocalState.forEmotion(emotion), context, now)
                .paralinguisticEvent().orElseThrow();
        assert selected == expected : context + " -> " + selected;
    }

    private static NpcProfile profile(String name, String preset) {
        UUID id = UUID.randomUUID();
        return new NpcProfile(id, name, "Senior Blacksmith", "Stern and practical",
                "Mara's grandfather and an experienced smith.", "Respond truthfully.",
                "home", "forge", List.of(), List.of(), List.of(), List.of(), 4,
                1, name, "ELDER", "Controlled and concise", List.of(), List.of(),
                name, id, "HUMAN", List.of("stern"), List.of("integrity"),
                List.of("losing Mara"), List.of("protect Mara"), preset, "GENERIC",
                0.28, 0.38, 0.32, 0.30).validated();
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

    private record Fixture(
            NpcProfile profile, Path directory, NpcVoiceService voice) { }
}
