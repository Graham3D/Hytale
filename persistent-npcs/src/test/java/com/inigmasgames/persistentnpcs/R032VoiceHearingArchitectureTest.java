package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceEvent;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class R032VoiceHearingArchitectureTest {
    private R032VoiceHearingArchitectureTest() { }

    public static void main(String[] args) throws Exception {
        rangesAreIndependentAndDerivedSafely();
        immutableUtteranceFansOneTranscriptToManyListeners();
        genericAliasResolutionAcceptsNearNameWithoutHardCoding();
        projectionIsPerformanceMetadata();
        firstStreamingSpeechChunkIsLatencyBoundedAndLexicallyExact();
        voiceTraceTracksResponseScopedCancellation();
        sourceKeepsPlaybackIndependentFromSocialFocus();
        nativeInspectorContainsVoiceSection();
        System.out.println("R032 voice/hearing architecture tests passed.");
    }

    private static void rangesAreIndependentAndDerivedSafely() {
        VoiceRuntimeConfig defaults = config(null, null, null);
        assert defaults.effectiveConversationListenRadius() == 5.0;
        assert defaults.effectiveRemoteHailRadius() == 15.0;
        assert defaults.effectiveNpcSpeechMaxRadius() == 15.0;
        VoiceRuntimeConfig custom = config(6.0, null, 24.0);
        assert custom.effectiveConversationListenRadius() == 6.0;
        assert custom.effectiveRemoteHailRadius() == 18.0;
        assert custom.effectiveNpcSpeechMaxRadius() == 24.0;
    }

    private static void immutableUtteranceFansOneTranscriptToManyListeners() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<EligibleNpcListener> listeners = new ArrayList<>(List.of(
                listener(first, "Mara", 3, UtteranceRangeClass.ORDINARY, false),
                listener(second, "Lycander", 4, UtteranceRangeClass.ORDINARY, true)));
        Set<UUID> targets = new HashSet<>(Set.of(second));
        PlayerUtteranceEvent event = new PlayerUtteranceEvent(UUID.randomUUID(),
                UUID.randomUUID(), "Lycander, where are you?", UUID.randomUUID(),
                1, 2, 3, Instant.now(), targets, PlayerSpeechIntent.LOCATE_SPEAKER,
                listeners, 250, 410, 2);
        listeners.clear();
        targets.clear();
        assert event.eligibleNpcListeners().size() == 2;
        assert event.directAddressTargets().equals(Set.of(second));
        assert event.eligibleNpcListeners().stream().allMatch(value ->
                event.transcript().equals("Lycander, where are you?"));
    }

    private static void genericAliasResolutionAcceptsNearNameWithoutHardCoding()
            throws Exception {
        Path root = Files.createTempDirectory("r032-alias-");
        NpcProfileRegistry profiles = new NpcProfileRegistry(new ProfileRepository(root));
        NpcProfile lycander = profile("Lycander");
        NpcProfile mara = profile("Mara");
        profiles.register(lycander);
        profiles.register(mara);
        PlayerUtteranceAudienceService audience = new PlayerUtteranceAudienceService(
                profiles, new NpcRuntimeRegistry(),
                new ConversationSessionManager(Duration.ofMinutes(2)),
                new RelationshipStore(root), new MemoryStore(root, 32),
                config(null, null, null), ignored -> { });
        String transcript = "Lysander, where are you?";
        Set<UUID> resolved = audience.resolveDirectTargets(transcript,
                PlayerUtteranceAudienceService.classify(transcript));
        assert resolved.equals(Set.of(lycander.id())) : resolved;
    }

    private static void projectionIsPerformanceMetadata() {
        EligibleNpcListener call = listener(UUID.randomUUID(), "Rowan", 9,
                UtteranceRangeClass.REMOTE_HAIL, true);
        EligibleNpcListener shout = listener(UUID.randomUUID(), "Tessa", 14,
                UtteranceRangeClass.REMOTE_HAIL, true);
        assert PlayerUtteranceAudienceService.projectionFor(call, 15)
                == SpeechProjection.CALL;
        assert PlayerUtteranceAudienceService.projectionFor(shout, 15)
                == SpeechProjection.SHOUT;
        assert SpeechProjection.SHOUT.gainBoostDb() <= 3.0;
    }

    private static void firstStreamingSpeechChunkIsLatencyBoundedAndLexicallyExact() {
        String dialogue = "This deliberately long answer has no early punctuation and keeps "
                + "adding grounded lexical dialogue so the first Chatterbox request can begin "
                + "without waiting for a very long sentence to finish generating while every "
                + "word remains authoritative and unchanged between display and speech.";
        List<String> chunks = new ArrayList<>();
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact(
                (index, phrase, state) -> chunks.add(phrase));
        for (String token : dialogue.split("(?<=\\s)")) {
            chunker.accept(token, null);
        }
        chunker.complete(dialogue, null);
        assert chunks.size() >= 2 : chunks;
        assert chunks.getFirst().length() <= 120 : chunks.getFirst().length();
        assert String.join(" ", chunks).equals(dialogue) : chunks;
    }

    private static void voiceTraceTracksResponseScopedCancellation() {
        VoiceInteractionTraceStore traces = new VoiceInteractionTraceStore();
        UUID utteranceId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        traces.begin(utteranceId, UUID.randomUUID(), System.nanoTime());
        traces.bindResponse(utteranceId, npcId, responseId, SpeechProjection.CALL);
        traces.playback(responseId, "PLAYING");
        traces.cancelled(responseId, "superseded");
        traces.playback(responseId, "COMPLETED");
        var snapshot = traces.latest(npcId).orElseThrow();
        assert snapshot.responseId().equals(responseId);
        assert snapshot.projection() == SpeechProjection.CALL;
        assert snapshot.playbackState().equals("CANCELLED");
        assert snapshot.cancellationReason().equals("superseded");
    }

    private static void sourceKeepsPlaybackIndependentFromSocialFocus() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/voice/HytaleSpatialVoiceAdapter.java"));
        assert !source.contains("!listening || entityRef");
        assert !source.contains("social-focus-ended");
        assert source.contains("closeSpeaker(npcId, current, \"entity-invalidated\")");
        assert !source.contains("setMaxHearingDistance(");
    }

    private static void nativeInspectorContainsVoiceSection() throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcCognitionInspector.ui"));
        for (String required : List.of("VOICE / HEARING", "#VoiceEvent",
                "#VoiceRouting", "#VoicePlayback", "#VoiceTiming")) {
            assert ui.contains(required) : required;
        }
    }

    private static VoiceRuntimeConfig config(Double listen, Double hail, Double speech) {
        return new VoiceRuntimeConfig(true, "", "auto", "base.en", "cpu", "int8",
                "AUTO", "TINY_STREAMING", 250, 24_000, true, false, true,
                listen, hail, speech).validated();
    }

    private static EligibleNpcListener listener(UUID id, String name, double distance,
            UtteranceRangeClass range, boolean direct) {
        return new EligibleNpcListener(id, name, distance, "nearby", "north", range,
                direct, false, direct ? 1_000 : 10);
    }

    private static NpcProfile profile(String name) {
        UUID id = UUID.randomUUID();
        return new NpcProfile(id, name, "villager", "grounded", "An authored villager.",
                "Live an ordinary grounded life.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }
}
