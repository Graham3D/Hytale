package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
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

/** Deterministic Phase 4 interruption, delivery-truth, and bounded-state tests. */
public final class R046OrbisInterruptionTest {
    private R046OrbisInterruptionTest() { }

    public static void main(String[] args) throws Exception {
        activePlaybackBecomesPartialAndStopsImmediately();
        completedQueuedAndActiveChunksKeepDeliveryTruth();
        staleChatterboxResultCannotReachPlayback();
        deferredTopicsAreBoundedAndTransient();
        sourceKeepsGeneratedAndDeliveredHistorySeparate();
        System.out.println("R046 Orbis interruption and deferred-state tests passed.");
    }

    private static void activePlaybackBecomesPartialAndStopsImmediately() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        FakePlayback playback = new FakePlayback();
        List<OrbisSpeechEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        CountDownLatch interrupted = new CountDownLatch(1);
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            OrbisSpeechRequest request = request(fixture.profile, "One active sentence.");
            speech.submit(request, event -> {
                events.add(event);
                if (event.type() == OrbisSpeechEvent.Type.SPEECH_INTERRUPTED) {
                    interrupted.countDown();
                }
            });
            assert playback.started.await(2, TimeUnit.SECONDS);
            speech.cancel(request.responseId(), CancellationReason.USER_BARGE_IN,
                    System.nanoTime());
            assert interrupted.await(2, TimeUnit.SECONDS);
            assert playback.handles.getFirst().cancelled;
            assert events.stream().anyMatch(value -> value.type()
                    == OrbisSpeechEvent.Type.TTS_CANCELLED);
            assert events.stream().anyMatch(value -> value.type()
                    == OrbisSpeechEvent.Type.PLAYBACK_INTERRUPTED
                    && "ClipPlayback.cancel".equals(value.facts().get("nativeMechanism")));
            OrbisSpeechEvent terminal = events.stream().filter(value -> value.type()
                    == OrbisSpeechEvent.Type.SPEECH_INTERRUPTED).findFirst().orElseThrow();
            assert terminal.facts().get("deliveredChunkCount").equals("0");
            assert terminal.facts().get("partialChunkCount").equals("1");
            assert terminal.facts().get("undeliveredChunkCount").equals("0");
        }
    }

    private static void completedQueuedAndActiveChunksKeepDeliveryTruth() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        FakePlayback playback = new FakePlayback();
        List<OrbisSpeechEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        CountDownLatch interrupted = new CountDownLatch(1);
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            OrbisSpeechRequest request = request(fixture.profile,
                    "First delivered.", "Second partial.", "Third unheard.");
            speech.submit(request, event -> {
                events.add(event);
                if (event.type() == OrbisSpeechEvent.Type.SPEECH_INTERRUPTED) {
                    interrupted.countDown();
                }
            });
            assert playback.started.await(2, TimeUnit.SECONDS);
            playback.handles.getFirst().complete();
            waitFor(() -> playback.handles.size() >= 2);
            speech.cancel(request.responseId(), CancellationReason.USER_BARGE_IN,
                    System.nanoTime());
            assert interrupted.await(2, TimeUnit.SECONDS);
            OrbisSpeechEvent terminal = events.stream().filter(value -> value.type()
                    == OrbisSpeechEvent.Type.SPEECH_INTERRUPTED).findFirst().orElseThrow();
            assert terminal.facts().get("deliveredChunkCount").equals("1");
            assert terminal.facts().get("partialChunkCount").equals("1");
            assert terminal.facts().get("undeliveredChunkCount").equals("1");
        }
    }

    private static void staleChatterboxResultCannotReachPlayback() throws Exception {
        Fixture fixture = fixture();
        FakeTts tts = new FakeTts();
        tts.blockFirst = true;
        FakePlayback playback = new FakePlayback();
        List<OrbisSpeechEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisSpeechCoordinator speech = fixture.coordinator(tts, playback)) {
            OrbisSpeechRequest request = request(fixture.profile,
                    "Stale generation.", "Never synthesize this.");
            speech.submit(request, events::add);
            assert tts.firstStarted.await(2, TimeUnit.SECONDS);
            speech.cancel(request.responseId(), CancellationReason.USER_BARGE_IN,
                    System.nanoTime());
            tts.firstFuture.complete(tts.clip());
            waitFor(() -> events.stream().anyMatch(value -> value.type()
                    == OrbisSpeechEvent.Type.TTS_RESULT_DISCARDED_STALE));
            assert tts.calls.get() == 1;
            assert playback.handles.isEmpty();
        }
    }

    private static void deferredTopicsAreBoundedAndTransient() {
        DeferredTopicStore store = new DeferredTopicStore();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            store.add(topic(npc, player, "topic-" + index,
                    Instant.now().plusSeconds(120), 3));
        }
        assert store.count(player, npc, Instant.now()) == 2;
        DeferredTopicStore.Result context = store.context(player, npc, Instant.now());
        assert context.summary().contains("topic-2");
        assert context.consumed().size() == 1;
        DeferredTopic expired = topic(npc, player, "expired",
                Instant.now().minusSeconds(1), 3);
        store.add(expired);
        DeferredTopicStore.Result afterExpiry = store.context(player, npc, Instant.now());
        assert afterExpiry.expired().stream().anyMatch(value ->
                value.sourceResponseId().equals(expired.sourceResponseId()));
        store.removePlayer(player);
        assert store.count(player, npc, Instant.now()) == 0;
    }

    private static void sourceKeepsGeneratedAndDeliveredHistorySeparate() throws Exception {
        Path root = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String conversation = Files.readString(
                root.resolve("conversation/ConversationService.java"));
        String bridge = Files.readString(root.resolve("hytale/HytaleConversationBridge.java"));
        String coordinator = Files.readString(root.resolve("orbis/OrbisTurnCoordinator.java"));
        assert conversation.contains("CONVERSATION_HISTORY:ACTUALLY_DELIVERED");
        assert !conversation.contains("CONVERSATION_HISTORY:NPC_GENERATED");
        assert bridge.indexOf("recordDeliveredConversation")
                > bridge.indexOf("deliveryCompleted(");
        assert coordinator.contains("speech-interrupted-action-preserved");
        assert coordinator.contains("BARGE_IN_CONFIRMED_FRAMES = 5");
    }

    private static DeferredTopic topic(UUID npc, UUID player, String value,
            Instant expires, int turns) {
        return new DeferredTopic(npc, player, TurnId.create(), ResponseId.create(), value,
                "SPEAK", List.of(), "", List.of("not delivered"), Instant.now(),
                "USER_BARGE_IN", expires, turns);
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("r046-orbis-interruption-");
        ProfileRepository repository = new ProfileRepository(root);
        NpcProfile profile = repository.loadTestProfile();
        createWave(repository.profileDirectory(profile.name()).resolve("mara-reference.wav"));
        NpcProfileRegistry registry = new NpcProfileRegistry(repository);
        registry.register(profile);
        NpcVoiceService voice = new NpcVoiceService(new VoicePresetRepository(root),
                new ChatterboxPerformanceController(), ignored -> { });
        return new Fixture(profile, registry, voice);
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
        List<CanonicalSpeechChunk> chunks = new ArrayList<>();
        for (int index = 0; index < text.length; index++) {
            chunks.add(new CanonicalSpeechChunk(SpeechChunkId.create(), index,
                    text[index], VocalState.infer(text[index])));
        }
        return new OrbisSpeechRequest(TurnId.create(), BranchId.create(), ResponseId.create(),
                profile.id(), profile.name(), 1, UUID.randomUUID(), SpeechProjection.NORMAL,
                chunks, Instant.now());
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assert condition.getAsBoolean();
    }

    private record Fixture(NpcProfile profile, NpcProfileRegistry registry,
            NpcVoiceService voice) {
        OrbisSpeechCoordinator coordinator(FakeTts tts, FakePlayback playback) {
            return new OrbisSpeechCoordinator(registry, voice, tts, playback,
                    new ResponseLatencyTraceStore(), ignored -> { }, 12, 6, 2_000);
        }
    }

    private static final class FakeTts implements TextToSpeechProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CompletableFuture<OpusClip> firstFuture = new CompletableFuture<>();
        private volatile boolean blockFirst;

        @Override public CompletableFuture<OpusClip> synthesize(UUID requestId,
                UUID responseId, VoiceRenderPlan plan, String text) {
            int call = calls.incrementAndGet();
            if (call == 1) firstStarted.countDown();
            if (call == 1 && blockFirst) return firstFuture;
            return CompletableFuture.completedFuture(clip());
        }

        OpusClip clip() {
            return new OpusClip(List.of(new byte[] {1, 2, 3}), 48_000,
                    10, 1, 2, true, 0, 10, 20, 1, "test-cpu",
                    Path.of("reference.wav"));
        }

        @Override public void cancel(UUID responseId) { }
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
        private final CountDownLatch started = new CountDownLatch(1);

        @Override public SpatialPlayback playOrbis(UUID npcId, PlaybackId playbackId,
                List<byte[]> frames) {
            FakeHandle handle = new FakeHandle(playbackId, npcId);
            handles.add(handle);
            started.countDown();
            return handle;
        }
    }

    private static final class FakeHandle implements SpatialPlayback {
        private final PlaybackId id;
        private final UUID npc;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile boolean cancelled;
        private FakeHandle(PlaybackId id, UUID npc) { this.id = id; this.npc = npc; }
        void complete() { completion.complete(null); }
        @Override public PlaybackId playbackId() { return id; }
        @Override public UUID npcStableId() { return npc; }
        @Override public UUID speakerId() { return npc; }
        @Override public java.util.concurrent.CompletionStage<Void> completion() {
            return completion;
        }
        @Override public boolean isDone() { return completion.isDone(); }
        @Override public void cancel() {
            cancelled = true;
            completion.cancel(false);
        }
    }
}
