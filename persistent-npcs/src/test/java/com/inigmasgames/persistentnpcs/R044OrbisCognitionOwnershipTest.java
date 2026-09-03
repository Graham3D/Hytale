package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.conversation.ConversationLifecycleObserver;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmUsage;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.orbis.BranchCognitionSnapshot;
import com.inigmasgames.persistentnpcs.orbis.CancellationReason;
import com.inigmasgames.persistentnpcs.orbis.CapturedVoiceFrame;
import com.inigmasgames.persistentnpcs.orbis.OrbisAudienceGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisCognitionGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisDiagnostics;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import com.inigmasgames.persistentnpcs.orbis.OrbisTurnCoordinator;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceEvent;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic Phase 2 ownership, pinning, stale-callback, and commit tests. */
public final class R044OrbisCognitionOwnershipTest {
    private R044OrbisCognitionOwnershipTest() { }

    public static void main(String[] args) throws Exception {
        oneBranchOwnsOnePinnedRequestAndOneCommit();
        providerSwitchAffectsOnlyFutureBranches();
        providerFailureIsExplicitAndDoesNotFallback();
        staleBranchCannotCommitAndCancellationReachesProvider();
        actionPromiseWithoutActionIsRejected();
        exactTextEchoOfLiveVoiceTurnIsSuppressed();
        System.out.println("R044 Orbis cognition/LLM ownership tests passed.");
    }

    private static void exactTextEchoOfLiveVoiceTurnIsSuppressed() throws Exception {
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        try (OrbisTurnCoordinator coordinator = coordinator(
                npc, cognition, diagnostics, () -> pinned("NEMOTRON", "nemotron-3-nano:4b"))) {
            coordinator.accept(new CapturedVoiceFrame(player, world, 1, 2, 3,
                    (short) 1, 1, new byte[] {1, 2}, Instant.now(), System.nanoTime()));
            waitFor(() -> cognition.begins.get() == 1);
            coordinator.accept(new TranscribedPlayerUtterance(UUID.randomUUID(), player,
                    "Hello Mara", world, 1, 2, 3, Instant.now(), 0, 0, 0, 0));
            waitFor(() -> diagnostics.latest().stream().anyMatch(value -> value.type()
                    == OrbisEventType.DUPLICATE_UTTERANCE_SUPPRESSED));
            Thread.sleep(100);
            assert cognition.begins.get() == 1 : "duplicate ingress started a second turn";
            assert diagnostics.latest().stream().noneMatch(value -> value.type()
                    == OrbisEventType.TURN_CANCELLED
                    && "USER_BARGE_IN".equals(value.facts().get("reason")));
        }
    }

    private static void providerFailureIsExplicitAndDoesNotFallback() throws Exception {
        UUID npc = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        AtomicInteger pins = new AtomicInteger();
        MockLlm exact = new MockLlm("QWEN");
        try (OrbisTurnCoordinator coordinator = coordinator(npc, cognition, diagnostics,
                () -> {
                    pins.incrementAndGet();
                    return new PinnedLlmProvider("QWEN", "qwen-4b", "local", exact);
                })) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            cognition.pending.get().completeExceptionally(
                    new IllegalStateException("selected provider unavailable"));
            waitFor(() -> cognition.failures.get() == 1);
            assert pins.get() == 1 : "failure cannot pin or retry another provider";
            assert cognition.commits.get() == 0;
            assert exact.cancels.get() == 1;
            assert diagnostics.latest().stream().anyMatch(value -> value.type()
                    == OrbisEventType.BRANCH_CANCELLED
                    && "PROVIDER_FAILURE".equals(value.facts().get("reason")));
        }
    }

    private static void oneBranchOwnsOnePinnedRequestAndOneCommit() throws Exception {
        UUID npc = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        AtomicInteger pins = new AtomicInteger();
        try (OrbisTurnCoordinator coordinator = coordinator(npc, cognition, diagnostics,
                () -> {
                    pins.incrementAndGet();
                    return pinned("QWEN", "qwen-4b");
                })) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            BranchCognitionSnapshot snapshot = cognition.latest.get();
            cognition.pending.get().complete(outcome(snapshot, "Good. I heard you."));
            waitFor(() -> cognition.commits.get() == 1);
            assert pins.get() == 1;
            assert cognition.begins.get() == 1;
            assert cognition.commits.get() == 1;
            assert diagnostics.latest().stream().filter(value -> value.type()
                    == OrbisEventType.DECISION_COMMITTED).count() == 1;
            assert diagnostics.latest().stream().anyMatch(value -> value.type()
                    == OrbisEventType.LLM_DISPATCHED
                    && snapshot.providerRequestId().value().toString().equals(
                            value.facts().get("providerRequestId")));
            assert !snapshot.responseId().value().equals(snapshot.providerRequestId().value());
        }
    }

    private static void providerSwitchAffectsOnlyFutureBranches() throws Exception {
        UUID npc = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        AtomicReference<PinnedLlmProvider> active = new AtomicReference<>(
                pinned("QWEN", "qwen-4b"));
        try (OrbisTurnCoordinator coordinator = coordinator(
                npc, cognition, diagnostics, active::get)) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            BranchCognitionSnapshot first = cognition.latest.get();
            active.set(pinned("NEMOTRON", "nemotron-mini"));
            cognition.pending.get().complete(outcome(first, "First response."));
            waitFor(() -> cognition.commits.get() == 1);
            cognition.resetFuture();
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 2);
            BranchCognitionSnapshot second = cognition.latest.get();
            assert "QWEN".equals(first.provider());
            assert "qwen-4b".equals(first.model());
            assert "NEMOTRON".equals(second.provider());
            assert "nemotron-mini".equals(second.model());
            assert !first.providerRequestId().equals(second.providerRequestId());
            cognition.pending.get().complete(outcome(second, "Second response."));
            waitFor(() -> cognition.commits.get() == 2);
        }
    }

    private static void staleBranchCannotCommitAndCancellationReachesProvider()
            throws Exception {
        UUID npc = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        MockLlm provider = new MockLlm("QWEN");
        PinnedLlmProvider pinned = new PinnedLlmProvider(
                "QWEN", "qwen-4b", "local", provider);
        try (OrbisTurnCoordinator coordinator = coordinator(
                npc, cognition, diagnostics, () -> pinned)) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            CompletableFuture<ConversationOutcome> staleFuture = cognition.pending.get();
            BranchCognitionSnapshot stale = cognition.latest.get();
            cognition.resetFuture();
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 2);
            BranchCognitionSnapshot current = cognition.latest.get();
            staleFuture.complete(outcome(stale, "This must be discarded."));
            cognition.pending.get().complete(outcome(current, "Current response."));
            waitFor(() -> cognition.commits.get() == 1);
            assert provider.cancels.get() >= 1 : "supersession must reach exact provider";
            assert cognition.committed.get().responseId().equals(current.responseId());
            assert diagnostics.latest().stream().anyMatch(value -> value.type()
                    == OrbisEventType.CALLBACK_REJECTED_STALE);
        }
    }

    private static void actionPromiseWithoutActionIsRejected() throws Exception {
        UUID npc = UUID.randomUUID();
        TestGateway cognition = new TestGateway();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        try (OrbisTurnCoordinator coordinator = coordinator(
                npc, cognition, diagnostics, () -> pinned("QWEN", "qwen-4b"))) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            BranchCognitionSnapshot snapshot = cognition.latest.get();
            cognition.pending.get().complete(outcome(snapshot, "I'll follow you."));
            waitFor(() -> diagnostics.latest().stream().anyMatch(value -> value.type()
                    == OrbisEventType.DECISION_REJECTED)
                    && cognition.failures.get() == 1);
            assert cognition.commits.get() == 0;
            assert cognition.failures.get() == 1;
        }
    }

    private static OrbisTurnCoordinator coordinator(UUID npc, TestGateway cognition,
            OrbisDiagnostics diagnostics,
            java.util.function.Supplier<PinnedLlmProvider> provider) {
        return new OrbisTurnCoordinator(new MockStt(), audience(npc), cognition,
                ignored -> true, provider, diagnostics, 80, 100, 5_000, ignored -> { });
    }

    private static OrbisAudienceGateway audience(UUID npc) {
        return new OrbisAudienceGateway() {
            @Override public CompletableFuture<PlayerUtteranceAudienceService.Resolution>
                    resolve(TranscribedPlayerUtterance utterance) {
                EligibleNpcListener listener = new EligibleNpcListener(npc, "Mara", 2,
                        "nearby", "ahead", UtteranceRangeClass.ORDINARY,
                        true, true, 1000);
                PlayerUtteranceEvent event = new PlayerUtteranceEvent(
                        utterance.utteranceId(), utterance.playerId(), utterance.transcript(),
                        utterance.worldId(), utterance.playerX(), utterance.playerY(),
                        utterance.playerZ(), utterance.timestamp(), Set.of(npc),
                        PlayerSpeechIntent.DIRECT_ADDRESS, List.of(listener),
                        utterance.endpointMillis(), utterance.sttMillis(), 1);
                return CompletableFuture.completedFuture(
                        new PlayerUtteranceAudienceService.Resolution(event,
                                List.of(listener), Map.of(npc, GroundedIntent.PROCESS_INFORMATION),
                                Map.of(), Map.of()));
            }
        };
    }

    private static ConversationOutcome outcome(
            BranchCognitionSnapshot snapshot, String speech) {
        NpcDecision decision = new NpcDecision(snapshot.responseId().value(),
                snapshot.npcStableId(), GroundedIntent.PROCESS_INFORMATION, speech,
                VocalEmotion.CALM, Optional.empty(), List.of(), List.of("test:evidence"));
        return new ConversationOutcome(UUID.randomUUID(), speech,
                new LlmLatency(Instant.now(), 30, 90, true), 100,
                VocalState.forEmotion(VocalEmotion.CALM),
                DialogueMode.ORDINARY_CONVERSATION, snapshot.responseId().value(),
                snapshot.providerRequestId().value(), decision, null,
                new LlmUsage(100, 12, 112, true), "structured-test-output",
                CognitiveDepth.CONTEXTUAL_CONVERSATION,
                List.of("PROFILE", "MEMORIES", "RELATIONSHIPS"), 800, 2, 1);
    }

    private static PinnedLlmProvider pinned(String provider, String model) {
        return new PinnedLlmProvider(provider, model, "local", new MockLlm(provider));
    }

    private static CapturedVoiceFrame frame(UUID player) {
        return new CapturedVoiceFrame(player, UUID.randomUUID(), 1, 2, 3,
                (short) 1, 1, new byte[] {1, 2}, Instant.now(), System.nanoTime());
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assert condition.getAsBoolean() : "condition timed out";
    }

    private static final class TestGateway implements OrbisCognitionGateway {
        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicReference<BranchCognitionSnapshot> latest = new AtomicReference<>();
        private final AtomicReference<BranchCognitionSnapshot> committed =
                new AtomicReference<>();
        private final AtomicReference<CompletableFuture<ConversationOutcome>> pending =
                new AtomicReference<>(new CompletableFuture<>());

        @Override public CompletableFuture<ConversationOutcome> begin(
                BranchCognitionSnapshot snapshot, PinnedLlmProvider provider,
                ConversationLifecycleObserver observer) {
            latest.set(snapshot);
            begins.incrementAndGet();
            observer.onStage(ConversationLifecycleObserver.Stage.CONTEXT_BUILDING, Map.of());
            observer.onStage(ConversationLifecycleObserver.Stage.LLM_QUEUED,
                    Map.of("queueMs", "0"));
            observer.onStage(ConversationLifecycleObserver.Stage.LLM_DISPATCHED,
                    Map.of("providerRequestId", snapshot.providerRequestId().value().toString(),
                            "promptCharacters", "800"));
            observer.onStage(ConversationLifecycleObserver.Stage.LLM_STREAMING,
                    Map.of("firstDeltaCharacters", "4"));
            observer.onStage(ConversationLifecycleObserver.Stage.DECISION_VALIDATING,
                    Map.of("rawCharacters", "100"));
            return pending.get();
        }

        @Override public CompletableFuture<Void> commit(
                BranchCognitionSnapshot snapshot, ConversationOutcome outcome) {
            committed.set(snapshot);
            commits.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override public void failed(BranchCognitionSnapshot snapshot,
                CancellationReason reason, Throwable failure) {
            failures.incrementAndGet();
        }

        private void resetFuture() { pending.set(new CompletableFuture<>()); }
    }

    private static final class MockStt implements SpeechToTextProvider {
        @Override public CompletableFuture<SpeechTranscript> transcribe(
                UUID requestId, List<byte[]> frames) {
            return CompletableFuture.completedFuture(
                    new SpeechTranscript("Hello Mara", 1, 2, "en"));
        }
        @Override public String providerId() { return "test-stt"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(false, true, true, Set.of("opus"));
        }
    }

    private static final class MockLlm implements LlmProvider {
        private final String name;
        private final AtomicInteger cancels = new AtomicInteger();
        private MockLlm(String name) { this.name = name; }
        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return CompletableFuture.failedFuture(new AssertionError(
                    "Coordinator test gateway owns the synthetic result"));
        }
        @Override public void cancel(UUID id) { cancels.incrementAndGet(); }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    "local", name, true, true, true,
                    "configured model is available"));
        }
        @Override public String providerId() { return name; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("json"));
        }
    }
}
