package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.orbis.BranchId;
import com.inigmasgames.persistentnpcs.orbis.CancellationReason;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechChunk;
import com.inigmasgames.persistentnpcs.orbis.OrbisSpeechCoordinator;
import com.inigmasgames.persistentnpcs.orbis.OrbisSpeechEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisSpeechRequest;
import com.inigmasgames.persistentnpcs.orbis.PlaybackId;
import com.inigmasgames.persistentnpcs.orbis.ResponseId;
import com.inigmasgames.persistentnpcs.orbis.SpeechChunkId;
import com.inigmasgames.persistentnpcs.orbis.TurnId;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.OpusClip;
import com.inigmasgames.persistentnpcs.voice.SpatialPlayback;
import com.inigmasgames.persistentnpcs.voice.SpatialPlaybackAdapter;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.TextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRenderPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic Phase 3 queue, identity, cancellation, and playback-terminal tests. */
public final class R045OrbisSpeechOwnershipTest {
    private R045OrbisSpeechOwnershipTest() { }

    public static void main(String[] args) throws Exception {
        exactChunksRemainOrderedUntilNativeCompletion();
        cancellationRemovesObsoleteProviderWork();
        npcBranchesKeepIndependentPlaybackAndCancellation();
        providerFailureDoesNotPoisonLaterSpeech();
        sourceRetiresLegacyProductionOwnership();
        System.out.println("R045 Orbis TTS/spatial playback ownership tests passed.");
    }

    private static void exactChunksRemainOrderedUntilNativeCompletion() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        FakePlayback playback = new FakePlayback();
        List<OrbisSpeechEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        CountDownLatch complete = new CountDownLatch(1);
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            OrbisSpeechRequest request = request(fixture.profile, "First exact phrase.",
                    "Second exact phrase.");
            speech.submit(request, event -> {
                events.add(event);
                if (event.type() == OrbisSpeechEvent.Type.SPEECH_COMPLETE) {
                    complete.countDown();
                }
            });
            assert playback.firstStarted.await(2, TimeUnit.SECONDS);
            assert !complete.await(100, TimeUnit.MILLISECONDS)
                    : "Synthesis/submission cannot complete the branch";
            assert tts.texts.equals(List.of("First exact phrase.", "Second exact phrase."));
            assert playback.npcIds.getFirst().equals(fixture.profile.id());
            playback.handles.getFirst().complete();
            assert playback.secondStarted.await(2, TimeUnit.SECONDS);
            assert !complete.await(100, TimeUnit.MILLISECONDS);
            playback.handles.get(1).complete();
            assert complete.await(2, TimeUnit.SECONDS);
            List<Integer> indexes = events.stream()
                    .filter(value -> value.type() == OrbisSpeechEvent.Type.SPEECH_QUEUED)
                    .map(value -> Integer.parseInt(value.facts().get("chunkIndex"))).toList();
            assert indexes.equals(List.of(0, 1));
            assert events.stream().allMatch(value -> value.responseId().equals(
                    request.responseId()));
            assert events.stream().filter(value -> value.speechChunkId() != null)
                    .map(OrbisSpeechEvent::speechChunkId).distinct().count() == 2;
            assert events.stream().filter(value -> value.ttsRequestId() != null)
                    .allMatch(value -> value.playbackId() != null);
        }
    }

    private static void cancellationRemovesObsoleteProviderWork() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        tts.blockFirst = true;
        FakePlayback playback = new FakePlayback();
        CountDownLatch cancelled = new CountDownLatch(1);
        OrbisSpeechRequest request = request(fixture.profile, "Obsolete one.",
                "Obsolete two.");
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            speech.submit(request, event -> {
                if (event.type() == OrbisSpeechEvent.Type.SPEECH_CANCELLED) {
                    cancelled.countDown();
                }
            });
            assert tts.firstStarted.await(2, TimeUnit.SECONDS);
            speech.cancel(request.responseId(), CancellationReason.SUPERSEDED);
            assert cancelled.await(2, TimeUnit.SECONDS);
            tts.firstFuture.complete(tts.clip());
            Thread.sleep(100);
            assert tts.calls.get() == 1 : "Queued obsolete chunk entered provider";
            assert tts.cancelled.contains(request.responseId().value());
            assert playback.handles.isEmpty() : "Stale synthesized audio reached playback";
        }
    }

    private static void providerFailureDoesNotPoisonLaterSpeech() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        tts.failFirst = true;
        FakePlayback playback = new FakePlayback();
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            speech.submit(request(fixture.profile, "Failure stays isolated."), event -> {
                if (event.type() == OrbisSpeechEvent.Type.TTS_FAILED) failed.countDown();
            });
            assert failed.await(2, TimeUnit.SECONDS);
            tts.failFirst = false;
            speech.submit(request(fixture.profile, "Later response still speaks."), event -> {
                if (event.type() == OrbisSpeechEvent.Type.SPEAKING) secondStarted.countDown();
            });
            assert secondStarted.await(2, TimeUnit.SECONDS);
            playback.handles.getLast().complete();
        }
    }

    private static void npcBranchesKeepIndependentPlaybackAndCancellation() throws Exception {
        Fixture fixture = fixture();
        NpcProfile lycander = copyIdentity(fixture.profile, UUID.randomUUID(), "Lycander");
        fixture.registry.register(lycander);
        FakeTts tts = new FakeTts();
        FakePlayback playback = new FakePlayback();
        OrbisSpeechRequest mara = request(fixture.profile, "Mara's own response.");
        OrbisSpeechRequest lycanderRequest = request(lycander, "Lycander's own response.");
        CountDownLatch lycanderComplete = new CountDownLatch(1);
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            speech.submit(mara, ignored -> { });
            speech.submit(lycanderRequest, event -> {
                if (event.type() == OrbisSpeechEvent.Type.SPEECH_COMPLETE) {
                    lycanderComplete.countDown();
                }
            });
            assert playback.secondStarted.await(2, TimeUnit.SECONDS);
            assert playback.npcIds.equals(List.of(fixture.profile.id(), lycander.id()));
            assert !playback.handles.getFirst().playbackId().equals(
                    playback.handles.get(1).playbackId());
            speech.cancel(mara.responseId(), CancellationReason.SUPERSEDED);
            Thread.sleep(50);
            assert playback.handles.getFirst().isDone();
            assert !playback.handles.get(1).isDone()
                    : "Cancelling Mara cancelled Lycander's branch";
            playback.handles.get(1).complete();
            assert lycanderComplete.await(2, TimeUnit.SECONDS);
            assert tts.cancelled.contains(mara.responseId().value());
            assert !tts.cancelled.contains(lycanderRequest.responseId().value());
        }
    }

    private static void sourceRetiresLegacyProductionOwnership() throws Exception {
        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String pipeline = Files.readString(source.resolve("voice/HytaleSpatialVoiceAdapter.java"));
        String plugin = Files.readString(source.resolve("PersistentNpcsPlugin.java"));
        String bridge = Files.readString(source.resolve("hytale/HytaleConversationBridge.java"));
        assert pipeline.contains("playOrbis");
        assert !pipeline.contains("LEGACY_SPEECH_ENTRYPOINTS_ENABLED");
        assert !pipeline.contains("activeResponseByNpc");
        assert plugin.contains("authoritativeTextToSpeech()");
        assert plugin.contains("new OrbisSpeechCoordinator");
        assert !bridge.contains("speech.enqueuePhrase(current");
        assert !bridge.contains("speech.completeResponse(current");
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("r045-orbis-speech-");
        ProfileRepository repository = new ProfileRepository(root);
        NpcProfile profile = repository.loadTestProfile();
        createWave(repository.profileDirectory(profile.name()).resolve("mara-reference.wav"));
        NpcProfileRegistry registry = new NpcProfileRegistry(repository);
        registry.register(profile);
        VoicePresetRepository presets = new VoicePresetRepository(root);
        NpcVoiceService plans = new NpcVoiceService(presets,
                new ChatterboxPerformanceController(), ignored -> { });
        return new Fixture(profile, registry, plans);
    }

    private static void createWave(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        var format = new javax.sound.sampled.AudioFormat(8_000, 16, 1, true, false);
        byte[] silence = new byte[8_000 * 2 * 6];
        try (var input = new javax.sound.sampled.AudioInputStream(
                new ByteArrayInputStream(silence), format, silence.length / 2)) {
            javax.sound.sampled.AudioSystem.write(input,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    private static OrbisSpeechRequest request(NpcProfile profile, String... text) {
        ResponseId responseId = ResponseId.create();
        List<CanonicalSpeechChunk> chunks = new ArrayList<>();
        for (int index = 0; index < text.length; index++) {
            chunks.add(new CanonicalSpeechChunk(SpeechChunkId.create(), index,
                    text[index], VocalState.infer(text[index])));
        }
        return new OrbisSpeechRequest(TurnId.create(), BranchId.create(), responseId,
                profile.id(), profile.name(), 1, UUID.randomUUID(),
                SpeechProjection.NORMAL, chunks, Instant.now());
    }

    private static NpcProfile copyIdentity(NpcProfile source, UUID id, String name) {
        return new NpcProfile(id, name, source.role(), source.personality(),
                source.biography(), source.purpose(), source.home(), source.workplace(),
                source.likes(), source.dislikes(), source.roleIds(), source.capabilities(),
                source.defaultDisposition(), source.schemaVersion(), name,
                source.ageCategory(), source.speakingStyle(), source.knowledgeDomains(),
                source.defaultSchedule(), source.appearancePreset(), id,
                source.speciesArchetype(), source.personalityTraits(), source.values(),
                source.fears(), source.goals(), source.voicePreset(),
                source.voiceEffectPreset(), source.modelTier(), source.riskTolerance(),
                source.sociability(), source.curiosity(), source.trustDisposition(),
                source.relationships()).validated();
    }

    private record Fixture(NpcProfile profile, NpcProfileRegistry registry,
            NpcVoiceService plans) {
        OrbisSpeechCoordinator coordinator(FakeTts tts, FakePlayback playback) {
            return new OrbisSpeechCoordinator(registry, plans, tts, playback,
                    new ResponseLatencyTraceStore(), ignored -> { }, 8, 4, 2_000);
        }
    }

    private static final class FakeTts implements TextToSpeechProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> texts = java.util.Collections.synchronizedList(
                new ArrayList<>());
        private final Set<UUID> cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CompletableFuture<OpusClip> firstFuture = new CompletableFuture<>();
        private volatile boolean blockFirst;
        private volatile boolean failFirst;

        @Override public CompletableFuture<OpusClip> synthesize(UUID requestId,
                UUID responseId, VoiceRenderPlan plan, String text) {
            int call = calls.incrementAndGet();
            texts.add(text);
            if (call == 1) firstStarted.countDown();
            if (call == 1 && blockFirst) return firstFuture;
            if (call == 1 && failFirst) return CompletableFuture.failedFuture(
                    new IllegalStateException("synthetic TTS failure"));
            return CompletableFuture.completedFuture(clip());
        }

        OpusClip clip() {
            return new OpusClip(List.of(new byte[] {1, 2, 3}), 48_000,
                    10, 1, 2, true, 0, 12, 20, 1,
                    "test-cpu", Path.of("reference.wav"));
        }

        @Override public void cancel(UUID responseId) { cancelled.add(responseId); }
        @Override public String providerId() { return "fake-chatterbox"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(false, true, true, Set.of("opus"));
        }
        @Override public CompletableFuture<AiProviderHealth> health() {
            return CompletableFuture.completedFuture(AiProviderHealth.healthy("test"));
        }
    }

    private static final class FakePlayback implements SpatialPlaybackAdapter {
        private final List<FakeHandle> handles = java.util.Collections.synchronizedList(
                new ArrayList<>());
        private final List<UUID> npcIds = java.util.Collections.synchronizedList(
                new ArrayList<>());
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch secondStarted = new CountDownLatch(1);

        @Override public SpatialPlayback playOrbis(UUID npcId, PlaybackId playbackId,
                List<byte[]> frames) {
            FakeHandle handle = new FakeHandle(playbackId, npcId);
            npcIds.add(npcId);
            handles.add(handle);
            if (handles.size() == 1) firstStarted.countDown();
            if (handles.size() == 2) secondStarted.countDown();
            return handle;
        }
    }

    private static final class FakeHandle implements SpatialPlayback {
        private final PlaybackId playbackId;
        private final UUID npcId;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private FakeHandle(PlaybackId playbackId, UUID npcId) {
            this.playbackId = playbackId;
            this.npcId = npcId;
        }
        void complete() { completion.complete(null); }
        @Override public PlaybackId playbackId() { return playbackId; }
        @Override public UUID npcStableId() { return npcId; }
        @Override public UUID speakerId() { return UUID.nameUUIDFromBytes(
                npcId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        @Override public java.util.concurrent.CompletionStage<Void> completion() {
            return completion;
        }
        @Override public boolean isDone() { return completion.isDone(); }
        @Override public void cancel() { completion.cancel(false); }
    }
}
